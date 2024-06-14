package ca.gc.cyber.kangooroo.browser;

import ca.gc.cyber.kangooroo.browser.chrome.ChromeExtender;
import ca.gc.cyber.kangooroo.browser.chrome.CustomChromeDriver;
import ca.gc.cyber.kangooroo.report.KangoorooResult;
import ca.gc.cyber.kangooroo.utils.io.net.http.HarUtils;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.browserup.bup.BrowserUpProxy;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.client.ClientUtil;
import com.browserup.bup.proxy.CaptureType;
import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

public class KangoorooChromeBrowser extends KangoorooBrowser {

    private static final Logger log = LoggerFactory.getLogger(KangoorooChromeBrowser.class);

    private static final File CHROME_DRIVER_EXECUTABLE = new File("chromedriver");
    private static final int DEFAULT_PAGE_LOAD_TIMEOUT = 60;
    private static final int MAX_SCREENSHOT_HEIGHT = 10_000;
    private static final CaptureType[] CAPTURE_TYPES = new CaptureType[] {
            CaptureType.REQUEST_HEADERS,
            CaptureType.RESPONSE_CONTENT,
            CaptureType.RESPONSE_HEADERS,
            CaptureType.RESPONSE_COOKIES,
            CaptureType.RESPONSE_BINARY_CONTENT
    };

    private boolean useSandbox = true;

    private boolean useCaptchaSolver = false;

    private boolean saveOutputFiles = true;

    public KangoorooChromeBrowser(File resultFolder, File tempFolder, Optional<InetSocketAddress> upstreamProxy,
            Optional<String> username, Optional<String> password, boolean useSandbox) {
        super(resultFolder, tempFolder, upstreamProxy, username, password);
        this.useSandbox = useSandbox;
    }

    public KangoorooChromeBrowser(File resultFolder, File tempFolder, Optional<InetSocketAddress> upstreamProxy,
            Optional<String> username, Optional<String> password, boolean useSandbox, boolean useCaptchaSolver) {
        this(resultFolder, tempFolder, upstreamProxy, username, password, useSandbox);
        this.useCaptchaSolver = useCaptchaSolver;
    }

    public KangoorooChromeBrowser(File resultFolder, File tempFolder, Optional<InetSocketAddress> upstreamProxy,
            Optional<String> username, Optional<String> password, boolean useSandbox, boolean useCaptchaSolver,
            boolean saveOutputFiles) {
        this(resultFolder, tempFolder, upstreamProxy, username, password, useSandbox, useCaptchaSolver);
        this.saveOutputFiles = saveOutputFiles;
    }

    @Override
    protected KangoorooResult execute(URL initialURL, String windowSize, String userAgent) {

        String initialUrlMd5 = DigestUtils.md5Hex(initialURL.toExternalForm());
        log.info("Fetching using Chrome: " + initialURL + " [" + initialUrlMd5 + "]");

        RemoteWebDriver driver = createDriver(userAgent, windowSize, tempFolder);
        AudioCaptchaSolver captchaSolver = new AudioCaptchaSolver(driver);

        KangoorooResult.CaptchaResult captResult = KangoorooResult.CaptchaResult.NONE;
        Pair<Har, URL> pair = null;
        try {
            driver.getWindowHandle();

            log.debug("Output folder: " + resultFolder);
            log.debug("Main tab: " + driver.getWindowHandle());

            // open the webpage
            Har har = get(driver, initialURL.toExternalForm(), tempFolder);

            if (useCaptchaSolver) {
                captResult = captchaSolver.removeCaptcha(tempFolder);
            }

            // process result, get favicon, screenshots etc
            pair = processResult(har, userAgent, driver, tempFolder, resultFolder);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            driver.quit();
        }

        return new KangoorooResult(pair, captResult);

    }

    /**
     * After making connection to the webpage, get source html, screenshot, and
     * favicon.
     *
     * @param har
     * @param userAgent
     * @param driver
     * @param tmpDownloadFolder
     * @param resultFolder
     * @return
     */
    protected Pair<Har, URL> processResult(Har har, String userAgent, RemoteWebDriver driver, File tmpDownloadFolder,
            File resultFolder) {
        log.info("Fetch done, processing the results..");
        if (HarUtils.isConnectionSuccess(har)) {

            // First check
            if (isUnresponsive(driver, 5)) {
                log.warn("Chromium is unresponsive");
                messageLog.warn( "Chromium is unresponsive");
                return Pair.of(har, null);
            }

            displayConsoleLog(driver);

            HarEntry firstNotRedirectedEntry = HarUtils.getFirstEntryNotRedirected(har);
            URL actualUrl = null;

            // Try to handle alerts if there is any. See issue #6
            if (!handleAlertIfExist(driver)) {
                try {
                    return Pair.of(har, new URL(firstNotRedirectedEntry.getRequest().getUrl()));
                } catch (Exception e) {
                    return Pair.of(har, null);
                }
            }

            // Second check, because the alert box may trigger something else...
            if (isUnresponsive(driver, 60)) {
                log.warn("Chromium is unresponsive");
                return Pair.of(har, null);
            }

            log.debug("Current URL: " + driver.getCurrentUrl());

            try {
                if (driver.getCurrentUrl().startsWith("data:")) {
                    log.warn("The current URL on the address bar is a data URL, ignoring..");
                    actualUrl = firstNotRedirectedEntry != null ? new URL(HarUtils.getFirstEntryNotRedirected(har)
                            .getRequest()
                            .getUrl()) : null;
                } else if (driver.getCurrentUrl().startsWith("about:blank")) {
                    log.warn(
                            "The current URL on the address bar is the about page.. This link may be a download or Chromium did not send the last request.");
                    actualUrl = firstNotRedirectedEntry != null ? new URL(HarUtils.getFirstEntryNotRedirected(har)
                            .getRequest()
                            .getUrl()) : null;
                } else {
                    actualUrl = new URL(driver.getCurrentUrl());
                }
            } catch (MalformedURLException e) {
                log.warn("Unable to parse the actual URL");
            }

            log.debug("Number of tabs after execution: " + driver.getWindowHandles()
                    .size()); // Future use: detect popups

            if (this.saveOutputFiles) {
                log.info("We are saving page source, favicon, and screenshot.");
                savePageSource(driver, resultFolder);
                getFavicon(driver, userAgent, resultFolder);
                takeScreenshots(driver, resultFolder);
            } else {
                log.info("We are not saving any files.");
            }

            log.info("Finish processing results.");

            return Pair.of(har, actualUrl);
        } else {
            log.warn("Unable to connect");
            messageLog.warn("Unable to connect.");
            return Pair.of(har, null);
        }
    }

    /**
     * This method take care of restarting everything if the chrome driver does not
     * come back after the timeout specified
     * This method also returns the HAR object that records all network interaction
     * with the website
     */
    private Har get(WebDriver driver, String url, File downloadFolder) {
        BrowserUpProxy proxy = getPROXY();
        proxy.newHar();
        try {
            driver.manage().timeouts().pageLoadTimeout(DEFAULT_PAGE_LOAD_TIMEOUT, TimeUnit.SECONDS);
            prepareDownloadFolder(driver, downloadFolder);
            driver.get(url);
            proxy.waitForQuiescence(1, 10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout of " + DEFAULT_PAGE_LOAD_TIMEOUT + " sec. reached.");
            hasTimedOut = true;
        } catch (WebDriverException e) {
            log.warn("WebDriverException exception caught", e);
            messageLog.warn("WebDriverException exception caught " + e.getMessage());
        }

        driver.manage().timeouts().pageLoadTimeout(5, TimeUnit.SECONDS);

        return proxy.endHar();
    }

    /**
     * Try to download favicon from the website if present
     *
     * @param driver
     * @param userAgent
     * @param resultFolder
     */
    private void getFavicon(RemoteWebDriver driver, String userAgent, File resultFolder) {
        try {
            // Check if the current page is really a webpage, and not something starting by
            // "data:..."
            if (!driver.getCurrentUrl().startsWith("http")) {
                return;
            }

            String faviconUrl = null;

            for (WebElement link : driver.findElements(By.cssSelector("link"))) {
                if (link.getAttribute("rel") != null && link.getAttribute("rel").equals("icon")) {
                    faviconUrl = link.getAttribute("href");
                }
            }

            if (faviconUrl == null) {
                URL currentURL;
                try {
                    currentURL = new URL(driver.getCurrentUrl());
                    faviconUrl = currentURL.getProtocol() + "://" + currentURL.getAuthority() + "/favicon.ico";
                } catch (MalformedURLException e) {
                    log.warn("Unable to parse the current URL, we won't look further to fetch the favicon.");
                }
            }

            String extension = FilenameUtils.getExtension(faviconUrl);
            if (StringUtils.isBlank(extension)) {
                extension = "ico";
            }

            File outputFile = new File(resultFolder, "favicon." + extension);
            if (faviconUrl != null) {
                try {

                    log.info("Trying to download favicon from: " + faviconUrl);
                    KangoorooHTTPClient downloader = new KangoorooHTTPClient(resultFolder, tempFolder);

                    boolean useProxy = this.upstreamProxy.isPresent();
                    log.debug("Are we using proxy to downlaod favicon? " + useProxy);
                    downloader.downloadFile(new URL(faviconUrl), outputFile, userAgent, useProxy);

                } catch (IOException e) {
                    log.debug(e.getMessage());
                    log.warn("Cannot download Favicon.");
                    messageLog.warn("Cannot download Favicon.");
                }
            }

            if (!outputFile.exists()) {
                log.debug("No favicon found");
            } else {
                log.debug("Favicon downloaded");
            }
        } catch (Throwable t) {
            Throwable cause = ExceptionUtils.getRootCause(t);
            Throwable rootCause = cause != null ? cause : t;
            log.error("Unable to download the favicon due to " + rootCause.getClass()
                    .getSimpleName() + ": " + rootCause.getMessage());
            messageLog.error("Unable to download the favicon due to " + rootCause.getClass()
                    .getSimpleName() + ": " + rootCause.getMessage());           
        }
    }

    /**
     * Save the source html of the current webpage
     *
     * @param driver
     * @param resultFolder
     */
    private void savePageSource(RemoteWebDriver driver, File resultFolder) {
        File page = new File(resultFolder, "source.html");
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(page), StandardCharsets.UTF_8))) {
            log.debug("Saving page content to " + page);
            writer.write(driver.getPageSource());
        } catch (Throwable t) {
            Throwable cause = ExceptionUtils.getRootCause(t);
            Throwable rootCause = cause != null ? cause : t;
            log.error("Unable to save the main page due to " + rootCause.getClass()
                    .getSimpleName() + ": " + rootCause.getMessage(), true);
            messageLog.error("Unable to save the main page due to " + rootCause.getClass()
                    .getSimpleName() + ": " + rootCause.getMessage());

        }
    }

    private void displayConsoleLog(RemoteWebDriver driver) {
        LogEntries logEntries = driver.manage().logs().get(LogType.BROWSER);
        for (LogEntry entry : logEntries) {
            if (entry.getLevel() == Level.SEVERE) {
                log.debug("Console [" + entry.getLevel() + "] " + entry.getMessage());
            }
        }
    }

    /**
     * Take screenshot of the current webpage and store it in resultFolder
     *
     * @param driver
     * @param resultFolder
     */
    private void takeScreenshots(RemoteWebDriver driver, File resultFolder) {
        try {
            List<String> windowHandles = new ArrayList<>(driver.getWindowHandles());
            for (int i = 0; i < windowHandles.size(); i++) {

                // We take screenshot only of the first frame. See #4
                if (i != 0) {
                    continue;
                }

                String windowHandle = windowHandles.get(i);

                log.debug("Taking screenshot of " + windowHandle + "...");
                String outputFilename;
                if (i == 0) {
                    outputFilename = "screenshot";
                } else {
                    outputFilename = "screenshot-popup" + (i - 1);
                }

                if (!driver.getWindowHandle().equals(windowHandle)) {
                    driver.switchTo().window(windowHandle);
                }

                int height = getDocumentHeight(driver);
                log.debug("Page height: " + height + "px");

                ChromeExtender ex = new ChromeExtender((CustomChromeDriver) driver);

                File screenshotFile = new File(resultFolder, outputFilename + ".png");

                ex.takeScreenshot(screenshotFile, MAX_SCREENSHOT_HEIGHT);

                if (screenshotFile.exists()) {
                    if (height > MAX_SCREENSHOT_HEIGHT) {
                        log.warn("The height of the screenshot truncated to " + MAX_SCREENSHOT_HEIGHT
                                + "px (instead of " + height + "px)", true);
                        messageLog.warn("The height of the screenshot truncated to " + MAX_SCREENSHOT_HEIGHT
                                + "px (instead of " + height + "px)");
                    }
                    log.debug("Successful capture of screenshot of " + windowHandle);
                } else {
                    log.error("Failed to capture screenshot of " + windowHandle);
                    messageLog.error( "Failed to capture screenshot of " + windowHandle);
                }
            }
        } catch (Throwable t) {
            Throwable cause = ExceptionUtils.getRootCause(t);
            Throwable rootCause = cause != null ? cause : t;
            
            log.error("Unable to take screenshots due to " + rootCause.getClass()
                    .getSimpleName() + ": " + rootCause.getMessage(), true);
            messageLog.error("Unable to take screenshots due to " + rootCause.getClass()
                    .getSimpleName() + ": " + rootCause.getMessage());
        }
    }

    private int getDocumentHeight(RemoteWebDriver driver) {
        return ((Number) driver.executeScript("return Math.max("
                + "document.body.scrollHeight, "
                + "document.body.offsetHeight, "
                + "document.documentElement.clientHeight, "
                + "document.documentElement.scrollHeight, "
                + "document.documentElement.offsetHeight);")).intValue();
    }

    /**
     * We try to remove an alert on the browser if present
     *
     * @param driver
     * @return
     */
    private boolean handleAlertIfExist(RemoteWebDriver driver) {
        Alert alert;
        boolean alertClosed = false;
        do {
            try {
                WebDriverWait wait = new WebDriverWait(driver, 10);
                wait.until(ExpectedConditions.alertIsPresent());
                alert = driver.switchTo().alert();
                log.info("Alert box present with message: " + alert.getText());
                alert.accept();
            } catch (NoAlertPresentException e) {
                return true;
            } catch (TimeoutException e) {
                log.info("Timeout while trying to detect an alert box");
                return true;
            }
        } while (alertClosed);

        return true;
    }

    /**
     * We inject a command on the ChromeDriver that will tell Chromium how to deal
     * with a downloaded file
     *
     * @param driver
     * @param downloadFolder
     */
    private void prepareDownloadFolder(WebDriver driver, File downloadFolder) {

        ChromeDriverService driverService = ((CustomChromeDriver) driver).getService();

        // Prepare the command
        Map<String, Object> commandParams = new HashMap<>();
        commandParams.put("cmd", "Page.setDownloadBehavior");
        Map<String, String> params = new HashMap<>();
        params.put("behavior", "allow");
        params.put("downloadPath", downloadFolder.toString());
        commandParams.put("params", params);

        // Send that command to Chrome
        ObjectMapper objectMapper = new ObjectMapper();
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            String command = driverService.getUrl()
                    .toString() + "/session/" + ((CustomChromeDriver) driver).getSessionId() + "/chromium/send_command";
            HttpPost request = new HttpPost(command);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(objectMapper.writeValueAsString(commandParams)));
            httpClient.execute(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * setting up chromedriver with required arguments.
     *
     * @param userAgent
     * @param windowSize
     * @param temporaryFolder
     * @return
     */
    private RemoteWebDriver createDriver(String userAgent, String windowSize, File temporaryFolder) {
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(getPROXY());
        seleniumProxy.setSslProxy("localhost:" + getPROXY().getPort());

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, Level.ALL);

        ChromeOptions options = new ChromeOptions();

        // https://chromium.googlesource.com/chromium/src/+/master/chrome/common/chrome_switches.cc
        // https://chromium.googlesource.com/chromium/src/+/master/headless/app/headless_shell_switches.cc
        options.addArguments("--disable-extensions");
        options.addArguments("--silent");
        options.addArguments("--headless");
        options.addArguments("--disable-application-cache"); // see issue #5
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--hide-scrollbars");
        options.addArguments("--lang=en-US,en;q=0.9");
        // options.addArguments("--block-new-web-contents"); // see issue #5

        options.addArguments("--window-size=" + windowSize);
        options.addArguments("--user-agent=" + userAgent);

        if (!this.useSandbox) {
            log.warn("Chrome option --no-sandbox is enabled.");
            options.addArguments("--no-sandbox");
        }

        options.addArguments("--disable-popup-blocking"); // see issue #5
        options.addArguments("--user-data-dir=" + temporaryFolder.getAbsolutePath());
        options.setCapability(CapabilityType.PROXY, seleniumProxy);
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        options.setCapability(CapabilityType.SUPPORTS_APPLICATION_CACHE, false); // see issue #5
        options.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
        options.setCapability(CapabilityType.UNEXPECTED_ALERT_BEHAVIOUR, UnexpectedAlertBehaviour.ACCEPT);

        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(CHROME_DRIVER_EXECUTABLE)
                .withSilent(true)
                .usingAnyFreePort()
                .build();

        cleanExtensionDirectory();

        CustomChromeDriver driver = null;

        // Sometimes Chrome is unable to start due to a bug, and it should work again if
        // we simply retry
        int retry = 0;
        while (driver == null) {
            try {
                driver = new CustomChromeDriver(service, options);
            } catch (WebDriverException e) {
                if (retry >= 2) {
                    throw e;
                } else {
                    log.warn("Unable to start Chrome, trying again..", e);
                    messageLog.warn( "Unable to start Chrome, trying again.." + e.getMessage());
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e1) {
                    }
                    retry++;
                }
            }
        }

        driver.manage()
                .timeouts()
                .setScriptTimeout(5, TimeUnit.SECONDS); // Affect only scripts run with executeAsyncScript()

        return driver;
    }

    private boolean isUnresponsive(WebDriver driver, int timeoutInSeconds) {
        try {
            driver.manage().timeouts().pageLoadTimeout(timeoutInSeconds, TimeUnit.SECONDS);
            try {
                driver.switchTo().alert();
            } catch (NoAlertPresentException e) {
            }
            driver.manage().timeouts().pageLoadTimeout(DEFAULT_PAGE_LOAD_TIMEOUT, TimeUnit.SECONDS);
            return false;
        } catch (TimeoutException e) {
            return true;
        } catch (Exception e) {
            return false; // This is normal
        }
    }

    /**
     * The ChromeDriver creates a temporary folder for extensions.
     * We can't control the location of this folder, we need to clean it once in a
     * while.
     */
    private static void cleanExtensionDirectory() {
        log.debug("Cleaning Chromium extension temporary folders...");

        var processBuilder = new ProcessBuilder(
                "/bin/sh",
                "-c",
                "find /tmp/.org.chromium* -type d -cmin +60 -exec rm -rf {} \\;");

        try {
            var process = processBuilder.start();

            process.waitFor();
            String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            process.destroy();

            log.debug("Output of command: " + output);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected CaptureType[] getHarCaptureTypes() {
        return CAPTURE_TYPES;
    }

    /**
     * We created a special font called 'MergeFont' to show all the special
     * characters that we need.
     * You can use this method to check that the font is actually ued by Chromium.
     */
    public final void checkEnvironment() {
        try {
            File checkFile = new File(".mergedFontConfigured");
            if (checkFile.exists()) {
                log.warn("Font check already done in the past, ignored.");
                return;
            }

            log.info("Checking environment..");

            // STEP 1. Check if MergedFont is installed and Lato uninstalled
            boolean fontLatoExists = false;
            boolean fontMergedFontExists = false;

            for (String fontName : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (fontName.equals("Lato")) {
                    fontLatoExists = true;
                } else if (fontName.equals("MergedFont")) {
                    fontMergedFontExists = true;
                }
            }

            if (!fontMergedFontExists) {
                throw new IllegalStateException("Font missing: MergedFont");
            } else if (fontLatoExists) {
                throw new IllegalStateException("Lato font is still installed, please uninstall it");
            }

            // STEP 2. Make sure Chromium is using MergedFont to display any fonts
            BrowserUpProxyServer proxy = new BrowserUpProxyServer();
            proxy.start();
            RemoteWebDriver driver = createDriver("", "", new File("tmp"));
            driver.get("file:///" + Paths.get("etc", "unicode-test.html").toAbsolutePath());
            ChromeExtender ex = new ChromeExtender((CustomChromeDriver) driver);
            Set<String> fonts = ex.getRenderedFonts("dd"); // dd is the element <dd>...</dd> as seen in the
                                                           // unicode-test.html page
            if (fonts.size() == 1 && fonts.iterator().next().equals("MergedFont")) {
                log.info("Environment check done, MergedFont properly installed");
            } else {
                throw new IllegalStateException(
                        "Chromium is not using MergedFont to display all the characters in unicode-test.html");
            }

            // Flag to avoid having to always make this verification each time Kangooroo
            // restarts
            FileUtils.touch(checkFile);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
