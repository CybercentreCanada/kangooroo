package ca.gc.cyber.kangooroo;

import ca.gc.cyber.kangooroo.browser.KangoorooBrowser;
import ca.gc.cyber.kangooroo.browser.KangoorooChromeBrowser;
import ca.gc.cyber.kangooroo.report.KangoorooResult;
import ca.gc.cyber.kangooroo.report.KangoorooURL;
import ca.gc.cyber.kangooroo.report.KangoorooURLReport;
import ca.gc.cyber.kangooroo.utils.io.net.http.HarUtils;
import ca.gc.cyber.kangooroo.utils.io.net.url.URLRedirection;
import ca.gc.cyber.kangooroo.utils.log.MessageLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.browserup.harreader.HarReader;
import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarHeader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class KangoorooStandaloneRunner {
    private static final Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
    private static final String RESOURCES_CONF = "/conf.yml";
    protected static final String ENGINE_NAME = "Standalone-Kangooroo";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> ALLOWED_MODULES = Set.of("captcha", "file_hash", "summary");

    private static Set<String> enabledModules = new HashSet<>();

    private static String engineVersion = "-1";

    public enum URLType {
        PHISHING, SMISHING
    }

    public static Logger getLogger() {
        return log;
    }


    public static KangoorooURLReport generateKangoorooReport(KangoorooResult result, Long processTime,
            URL url, URLType urlType, String windowSize, String userAgent, boolean sanitizeSession, List<String> messageLog) throws IOException {

        KangoorooURLReport kangoorooReport = null;

        if (sanitizeSession) {
            kangoorooReport = new KangoorooURLReport(HarUtils.removeContent(result.getHar(), true, true));
        } else {
            kangoorooReport = new KangoorooURLReport(result.getHar());
        }

        HarEntry lastHop = result.getFirstNotRedirected();

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (lastHop != null && lastHop.getResponse() != null) {
            for (HarHeader header : lastHop.getResponse().getHeaders()) {
                headers.put(header.getName(), header.getValue());
            }
        }

        Map<String, String> engineInfo = Map.of("engineName", ENGINE_NAME, "engineVersion", engineVersion);
    

        kangoorooReport.setExperiment(engineInfo,
                (result.isConnectionSuccess() && result.isFetchSuccess()) ? "SUCCESS" : "FAIL", messageLog,
                result.getStartTime().toString(), processTime, url.toExternalForm(), urlType.name(), windowSize,
                userAgent, enabledModules.stream().collect(Collectors.toList()));

        if (enabledModules.contains("captcha")) {
            kangoorooReport.setCaptcha(result.getCaptchaResult());
        }

        if (enabledModules.contains("file_hash")) {
            kangoorooReport.setFileHashes(result.getHar());
        }

        if (enabledModules.contains("summary")) {
            Map<String, String> fetchResult = Map.of("response_code",
                    String.valueOf(lastHop != null ? lastHop.getResponse().getStatus() : 0),
                    "connection_success", String.valueOf(result.isConnectionSuccess()),
                    "fetch_object_success", String.valueOf(result.isFetchSuccess()),
                    // TODO: Add logic for figuring out time out
                    "has_timed_out", "false");

            KangoorooURL requestedUrl = new KangoorooURL(url.toExternalForm(),
                    DigestUtils.md5Hex(url.toExternalForm()),
                    url.getHost(), result.getInitial().getServerIPAddress());

            KangoorooURL actualUrl = null;
            
            if (result.getUrl() != null){
                actualUrl = new KangoorooURL(result.getUrl().toExternalForm(),
                        DigestUtils.md5Hex(result.getUrl().toExternalForm()),
                        result.getUrl().getHost(), lastHop.getServerIPAddress());
            }

            List<URLRedirection> urlRedirects = HarUtils.getHTTPRedirections(result.getHar());

            kangoorooReport.setSummary(fetchResult, requestedUrl, actualUrl, urlRedirects, headers);

        }

        return kangoorooReport;

    }

    private static void runKangooroo(boolean useSandbox, boolean useCaptchaSolver, boolean saveFiles,
            boolean saveOriginalHar, boolean sanitizeSession, File urlOutputDir, File urlTempDir,
            KangoorooRunnerConf configuration, URL crawlUrl, String windowSize, String userAgent, URLType urlType)
            throws IOException {

        KangoorooBrowser browser = new KangoorooChromeBrowser(
                urlOutputDir, urlTempDir, configuration.getOptionalUpstreamProxyAddress(),
                configuration.getOptionalUpstreamProxyUsername(),
                configuration.getOptionalUpstreamProxyPassword(),
                useSandbox, useCaptchaSolver, saveFiles);

        long start = System.currentTimeMillis();
        KangoorooResult res = browser.get(crawlUrl, windowSize, userAgent);
        long processingTime = System.currentTimeMillis() - start;
        
        browser.browserShutdown();

        createKangoorooOutput(urlOutputDir, configuration, crawlUrl, res, processingTime, saveOriginalHar, urlType,
                sanitizeSession, browser.getMessageLog().getMessagesAsList());


    }

    private static void createKangoorooOutput(File urlOutputDir, KangoorooRunnerConf configuration, URL crawlUrl,
            KangoorooResult res, long processingTime, boolean saveOriginalHar, URLType urlType, boolean sanitizeSession,
            List<String> messageLog)
            throws IOException {

        var report = generateKangoorooReport(res, processingTime, crawlUrl, urlType,
                configuration.getBrowserSettings().get(urlType.name()).getWindowSize(),
                configuration.getBrowserSettings().get(urlType.name()).getUserAgent(),
                sanitizeSession, messageLog);

        if (saveOriginalHar) {
            File harSanitizedFile = new File(urlOutputDir, "session.har");
            HarUtils.writeFile(res.getHar(), harSanitizedFile);
        } else {
            log.info("We are not saving the original HAR.");
        }

        File resultFile = new File(urlOutputDir, "results.json");
        FileUtils.writeStringToFile(resultFile, GSON.toJson(report), java.nio.charset.StandardCharsets.UTF_8);

    }

    /**
     * Main entry point of Kangooroo.
     *
     * @param args
     * @throws Throwable
     */
    public static void main(String[] args) throws Throwable {

        Yaml yml = new Yaml();
        Map<String, Object> baseConf = null;
        try (var is = KangoorooStandaloneRunner.class.getResourceAsStream(RESOURCES_CONF)) {
            baseConf = yml.load(is);

        } catch (IOException e) {
            getLogger().warn("Cannot find base configuration file from jar resources.");
        }

        String kangoorooVersion = baseConf != null && baseConf.containsKey("version") ? "v" + baseConf.get("version")
                : "";

        CommandLineParser cliParser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        String jarName = "kangoorooStandalone.jar " + kangoorooVersion;
        String helpDescription = jarName + "\n"
                + "A standalone commandline application that crawls a given URL that stores the HAR and screenshot of website."
                + "The results are stored stored in a zip file in the result/ directory.";

        Options options = new Options();
        Option helpOption = new Option("h", "help", false, "Prints instructions.");
        Option urlOption = new Option("u", "url", true, "Full URL to crawl.");
        Option urlTypeOption = new Option("ut", "url-type", true,
                "URL Type can be one of: [PHISHING, SMISHING]. Default is PHISHING.");
        ;
        Option confFileOption = new Option("cf", "conf-file", true, "Specify specific configuration file location.");
        Option noSandboxOption = new Option("ns", "no-sandbox", false,
                "Enable the --no-sandbox option in Chrome options.");

        Option notSanitizeSessionOption = new Option("nss", "not-sanitize-session", false,
                "Do NOT remove content from HAR file and store in report.json.");
        Option notSaveFilesOption = new Option("nsf", "not-save-file", false,
                "Do NOT save favicon.ico, website source, and screenshot to separate file.");
        Option notSaveHarOption = new Option("nsh", "not-save-har", false,
                "Do NOT save original HAR as separate file.");
        Option modulesOption = new Option("mods", "modules", true, "Use modules");

        options.addOption(notSanitizeSessionOption);
        options.addOption(notSaveFilesOption);
        options.addOption(notSaveHarOption);
        options.addOption(modulesOption);

        options.addOption(helpOption);
        options.addOption(urlTypeOption);
        options.addOption(urlOption);

        options.addOption(confFileOption);
        options.addOption(noSandboxOption);

        CommandLine params = null;
        try {
            params = cliParser.parse(options, args);
        } catch (ParseException e) {
            formatter.printHelp(helpDescription, options);
            throw e;
        }

        if (params.hasOption("help")) {
            formatter.printHelp(jarName, options);
            return;
        }

        if (!params.hasOption("url")) {
            getLogger().error("--url URL is missing.");
            return;
        }

        if (params.hasOption("modules")) {
            var modules = params.getOptionValue("modules").split(",");

            for (var mod : modules) {
                if (ALLOWED_MODULES.contains(mod)) {
                    enabledModules.add(mod);
                } else {
                    getLogger().error("Module: [" + mod + "] is not an allowed module.");
                    return;
                }
            }

        }

        KangoorooRunnerConf configuration = null;
        // load new configuration if exist
        if (params.hasOption("conf-file")) {
            File confFile = new File(params.getOptionValue("conf-file"));
            if (!confFile.exists()) {
                getLogger().error("Configuration file specified: [" + confFile.getAbsolutePath() + "] does not exist.");
                return;
            }

            Map<String, Object> secondConf = null;
            try (var is = new FileInputStream(confFile)) {
                secondConf = yml.load(is);
            } catch (IOException e) {
                getLogger().error(e.getMessage());
                throw e;
            }

            if (baseConf == null) {
                getLogger().debug("base conf is null. Replace with configs specified in conf-file option.");
                baseConf = secondConf;
            } else {
                getLogger().debug("both base conf and conf file (" + confFile.getAbsolutePath()
                        + ") exists... need to merge the two.");
                for (var es : secondConf.entrySet()) {
                    if ("version".equals(es.getKey())) {
                        continue;
                    }
                    getLogger().debug("key: " + es.getKey());
                    baseConf.put(es.getKey(), es.getValue());
                }
            }

        }

        if (baseConf != null) {
            configuration = GSON.fromJson(GSON.toJsonTree(baseConf), KangoorooRunnerConf.class);
        }

        if (configuration == null) {
            getLogger().error("No configurations file found. Exit for now.");
            throw new IllegalArgumentException("No configuration file found.");
        }

        engineVersion = configuration.getVersion();

        String urlStr = params.getOptionValue("url");
        URLType urlType = URLType.PHISHING;

        boolean sanitizeSession = !params.hasOption("not-sanitize-session");
        boolean saveOriginalHar = !params.hasOption("not-save-har");
        boolean saveFiles = !params.hasOption("not-save-file");

        log.info("To sanitize session: " + sanitizeSession);
        log.info("To save original har file: " + saveOriginalHar);
        log.info("To save all files: " + saveFiles);


        boolean useSandbox = !params.hasOption("no-sandbox");
        boolean useCaptchaSolver = enabledModules.contains("captcha");

        if (!useSandbox) {
            getLogger().warn("We are running chrome with --no-sandbox option.");
        }

        if (!sanitizeSession) {
            getLogger().warn("NOT sanitizing session is on, this could lead to very large result.json file.");
        }

        if (useCaptchaSolver) {
            getLogger().info("We are using Captcha solver. Make sure that Whisper AI is configured on the computer.");
        }

        if (params.hasOption("url-type")) {
            try {
                urlType = URLType.valueOf(params.getOptionValue("url-type"));
            } catch (IllegalArgumentException e) {
                getLogger().error("Invalid argument for (-ut --url-type): " + params.getOptionValue("url-type") +
                        ". Choose one of: PHISHING, SMISHING.");
                return;
            }
        }

        // check the type of URL to crawl which decides the user agent and window size
        // that Kangooroo uses
        var browserSetting = configuration.getBrowserSettings().get(urlType.name());
        String userAgent = browserSetting.getUserAgent();
        String windowSize = browserSetting.getWindowSize();

        URL crawlUrl = new URL(urlStr);

        // check all required files are on disk
        File outputDir = new File(configuration.getOutputFolder());
        File tempDir = new File(configuration.getTemporaryFolder());

        if (!outputDir.exists()) {
            getLogger()
                    .error("The output directory specified: " + configuration.getOutputFolder() + " does not exist.");
            return;
        }

        if (!tempDir.exists()) {
            getLogger().error(
                    "The temporary directory specified: " + configuration.getTemporaryFolder() + " does not exist.");
            return;
        }

        String crawlUrlMd5 = DigestUtils.md5Hex(crawlUrl.toExternalForm());

        File urlOutputDir = new File(outputDir, crawlUrlMd5);
        File urlTempDir = new File(tempDir, crawlUrlMd5);

        if (!urlOutputDir.exists()) {
            urlOutputDir.mkdir();
        }

        if (!urlTempDir.exists()) {
            urlTempDir.mkdir();
        }

        if (configuration.getUpstreamProxy() != null) {
            var proxyConf = configuration.getUpstreamProxy();

            if (proxyConf.getIp() == null || proxyConf.getPort() == null) {
                getLogger().error("Missing either IP or port in proxy configuration.");
                return;
            }

            getLogger().debug("We have proxy configuration: " + proxyConf.getIp() + ":" + proxyConf.getPort());

            if (proxyConf.getUsername() == null || proxyConf.getPassword() == null) {
                getLogger().debug("Using unauthenticated proxy. No username or password specified.");
            }
        }


        try {
            runKangooroo(useSandbox, useCaptchaSolver, saveFiles, saveOriginalHar, sanitizeSession, urlOutputDir,
                    urlTempDir, configuration, crawlUrl, windowSize, userAgent, urlType);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // cleanup leftover files in the temporary directory
            FileUtils.deleteQuietly(urlTempDir);
            log.debug("Temp directory should be deleted.");
        }

    }

}
