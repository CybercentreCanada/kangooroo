package ca.gc.cyber.kangooroo;


import ca.gc.cyber.kangooroo.browser.KangoorooBrowser;
import ca.gc.cyber.kangooroo.browser.KangoorooChromeBrowser;
import ca.gc.cyber.kangooroo.report.KangoorooResult;
import ca.gc.cyber.kangooroo.report.WebpageReport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarHeader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public class KangoorooStandaloneRunner {
    private static final Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
    private static final String RESOURCES_CONF = "/conf.yml";
    protected static final String ENGINE_NAME = "Standalone-Kangooroo";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    public enum URLType {
        PHISHING, SMISHING
    }


    public static Logger getLogger() {
        return log;
    }

    /**
     * Compile the whole run of kangooroo into one single report
     *
     * @param url
     * @param result
     * @param processTime
     * @param engineVersion
     * @return
     */
    public static WebpageReport generateReport(URL url, KangoorooResult result, long processTime, String engineVersion) {
        WebpageReport webpageReport = new WebpageReport(url, ENGINE_NAME, engineVersion);


        HarEntry lastHop = result.getFirstNotRedirected();

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (lastHop != null && lastHop.getResponse() != null) {
            for (HarHeader header : lastHop.getResponse().getHeaders()) {
                headers.put(header.getName(), header.getValue());
            }
        }

        webpageReport.setCrawlResult(lastHop != null ? lastHop.getResponse().getStatus() : 0,
                result.isConnectionSuccess(),
                result.isFetchSuccess(),
                url,
                result.getInitial() == null ? null : result.getInitial().getServerIPAddress(),
                result.isConnectionSuccess() ? result.getUrl() : null,
                lastHop != null ? lastHop.getServerIPAddress() : null,
                headers,
                false, // hasTimedOut,
                processTime);


        return webpageReport;
    }

    private static void runKangooroo( boolean useSandbox, boolean useCaptchaSolver, File urlOutputDir, File urlTempDir,
                                     KangoorooRunnerConf configuration, URL crawlUrl, String windowSize, String userAgent)
            throws IOException {


        KangoorooBrowser browser = new KangoorooChromeBrowser(useSandbox, useCaptchaSolver,
                urlOutputDir, urlTempDir, configuration.getOptionalUpstreamProxyAddress(),
                configuration.getOptionalUpstreamProxyUsername(),
                configuration.getOptionalUpstreamProxyPassword());

        long start = System.currentTimeMillis();
        var res = browser.get(crawlUrl, windowSize, userAgent);
        long processingTime = System.currentTimeMillis() - start;
        browser.browserShutdown();

        var report = generateReport(crawlUrl, res, processingTime, configuration.getVersion());

        File resultFile = new java.io.File(urlOutputDir, "results.json");
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

        String kangoorooVersion = baseConf != null && baseConf.containsKey("version") ? "v" + baseConf.get("version") : "";


        CommandLineParser cliParser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        String jarName = "kangoorooStandalone.jar " + kangoorooVersion;
        String helpDescription = jarName + "\n" + "A standalone commandline application that crawls a given URL that stores the HAR and screenshot of website." +
                "The results are stored stored in a zip file in the result/ directory.";

        Options options = new Options();
        Option helpOption = new Option("h", "help", false, "Prints instructions.");
        Option urlOption = new Option("u", "url", true, "Full URL to crawl.");
        Option urlTypeOption = new Option("ut", "url-type", true, "URL Type can be one of: [PHISHING, SMISHING]. Default is PHISHING.");
 ;
        Option confFileOption = new Option("cf", "conf-file", true, "Specify specific configuration file location.");
        Option noSandboxOption = new Option("ns", "no-sandbox", false, "Enable the --no-sandbox option in Chrome options.");
        Option captchaSolverOption = new Option("uc", "use-captcha-solver", false, "Enable captcha solver.");

        options.addOption(helpOption);
        options.addOption(urlTypeOption);
        options.addOption(urlOption);
        options.addOption(captchaSolverOption);

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

        KangoorooRunnerConf configuration = null;
        //load new configuration if exist
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
                getLogger().debug("both base conf and conf file (" + confFile.getAbsolutePath() + ") exists... need to merge the two.");
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

        String urlStr = params.getOptionValue("url");
        URLType type = URLType.PHISHING;

        boolean useSandbox = !params.hasOption("no-sandbox");
        boolean useCaptchaSolver = params.hasOption("use-captcha-solver");

        if (!useSandbox) {
            getLogger().warn("We are running chrome with --no-sandbox option.");
        }

        if (useCaptchaSolver) {
            getLogger().info("We are using Captcha solver. Make sure that Whisper AI is configured on the computer.");
        }



        if (params.hasOption("url-type")) {
            try {
                type = URLType.valueOf(params.getOptionValue("url-type"));
            } catch (IllegalArgumentException e) {
                getLogger().error("Invalid argument for (-ut --url-type): " + params.getOptionValue("url-type") +
                        ". Choose one of: PHISHING, SMISHING.");
                return;
            }
        }

        // check the type of URL to crawl which decides the user agent and window size that Kangooroo uses
        var browserSetting = type.equals(URLType.PHISHING) ?
                configuration.getBrowserSettings().get("DEFAULT") : configuration.getBrowserSettings().get("SMISHING");
        String userAgent = browserSetting.getUserAgent();
        String windowSize = browserSetting.getWindowSize();

        URL crawlUrl = new URL(urlStr);

        // check all required files are on disk
        File outputDir = new File(configuration.getOutputFolder());
        File tempDir = new File(configuration.getTemporaryFolder());

        if (!outputDir.exists()) {
            getLogger().error("The output directory specified: " + configuration.getOutputFolder() + " does not exist.");
            return;
        }

        if (!tempDir.exists()) {
            getLogger().error("The temporary directory specified: " + configuration.getTemporaryFolder() + " does not exist.");
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
            runKangooroo( useSandbox, useCaptchaSolver, urlOutputDir, urlTempDir, configuration, crawlUrl, windowSize, userAgent);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // cleanup leftover files in the temporary directory
            FileUtils.deleteQuietly(urlTempDir);
        }

    }


}
