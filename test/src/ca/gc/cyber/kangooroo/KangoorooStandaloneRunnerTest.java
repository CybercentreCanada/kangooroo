package ca.gc.cyber.kangooroo;

import ca.gc.cyber.kangooroo.browser.KangoorooChromeBrowser;
import ca.gc.cyber.kangooroo.report.KangoorooResult;
import ca.gc.cyber.kangooroo.report.WebpageReport;
import ca.gc.cyber.kangooroo.utils.log.MessageLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.browserup.harreader.HarReader;
import com.browserup.harreader.HarReaderException;
import com.browserup.harreader.model.Har;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KangoorooStandaloneRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunnerTest.class);
    private final File outputDir = new File("./output");
    private final File tempDir = new File("./tmp");
    private final File testConfigFile = new File("./conf2.yml");
    private final File testOutputDir = new File("./output2");
    private final File testHarFile = new File("./test/data/session.har");
    private Har har;

    @Before
    public void init() throws HarReaderException {

        // if test output/tmp directory exist, we clean it and make a clean one again
        if (outputDir.exists()) {
            FileUtils.deleteQuietly(outputDir);
        }
        outputDir.mkdir();

        if (tempDir.exists()) {
            FileUtils.deleteQuietly(tempDir);
        }
        tempDir.mkdir();

        if (testConfigFile.exists()) {
            FileUtils.deleteQuietly(testConfigFile);
        }

        if (testOutputDir.exists()) {
            FileUtils.deleteQuietly(testOutputDir);
        }

        this.har = new HarReader().readFromFile(testHarFile);
    }

    @After
    public void cleanup() {
        if (outputDir.exists()) {
            FileUtils.deleteQuietly(outputDir);
        }

        if (tempDir.exists()) {
            FileUtils.deleteQuietly(tempDir);
        }

        if (testConfigFile.exists()) {
            FileUtils.deleteQuietly(testConfigFile);
        }

        if (testOutputDir.exists()) {
            FileUtils.deleteQuietly(testOutputDir);
        }
    }

    public void loadingTestHarFile() throws HarReaderException {
        var harFile = new HarReader().readFromFile(testHarFile);

        log.info(harFile.toString());

        log.info("we did the HAR stuff?");
    }


    @Test
    public void runnerShouldReturnExceptionWhenNoURLSpecified() throws Throwable {
        try (MockedStatic<KangoorooStandaloneRunner> runner = Mockito.mockStatic(KangoorooStandaloneRunner.class,
                Mockito.CALLS_REAL_METHODS)) {

            Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
            Logger spyLog = Mockito.spy(log);
            runner.when(KangoorooStandaloneRunner::getLogger).thenReturn(spyLog);

            KangoorooStandaloneRunner.main(new String[] { "test" });

            verify(spyLog).error("--url URL is missing.");
        }
    }

    @Test
    public void runnerShouldOutputHelpMessageWithHelpArgument() throws Throwable {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        KangoorooStandaloneRunner.main(new String[] { "--help" });
        String contentString = outContent.toString(Charset.defaultCharset());

        assertTrue("Kangooroo should print help message.", contentString.contains("usage: kangoorooStandalone.jar"));

        System.setOut(originalOut);
    }

    @Test
    public void runnerShouldReturnExceptionWhenSpecifyInvalidConfFile() throws Throwable {

        try (MockedStatic<KangoorooStandaloneRunner> runner = Mockito.mockStatic(KangoorooStandaloneRunner.class,
                Mockito.CALLS_REAL_METHODS)) {

            Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
            Logger spyLog = Mockito.spy(log);
            runner.when(KangoorooStandaloneRunner::getLogger).thenReturn(spyLog);

            KangoorooStandaloneRunner.main(new String[] { "-u", "http://test.com", "-cf", "not_exist.txt" });

            verify(spyLog).error(matches("does not exist\\.$"));
        }
    }

    @Test
    public void runnerShouldReturnExceptionWhenInvalidUrlType() throws Throwable {

        try (MockedStatic<KangoorooStandaloneRunner> runner = Mockito.mockStatic(KangoorooStandaloneRunner.class,
                Mockito.CALLS_REAL_METHODS)) {

            Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
            Logger spyLog = Mockito.spy(log);
            runner.when(KangoorooStandaloneRunner::getLogger).thenReturn(spyLog);

            KangoorooStandaloneRunner.main(new String[] { "-u", "http://test.com", "-ut", "NOT_EXIST" });

            verify(spyLog).error(matches("^Invalid argument"));
        }
    }

    @Test
    public void runnerShouldReturnExceptionWhenOutputDirectoryNotExist() throws Throwable {

        try (MockedStatic<KangoorooStandaloneRunner> runner = Mockito.mockStatic(KangoorooStandaloneRunner.class,
                Mockito.CALLS_REAL_METHODS)) {

            Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
            Logger spyLog = Mockito.spy(log);
            runner.when(KangoorooStandaloneRunner::getLogger).thenReturn(spyLog);

            FileUtils.deleteQuietly(outputDir);

            KangoorooStandaloneRunner.main(new String[] { "-u", "http://test.com" });

            verify(spyLog).error(matches("^The output directory specified"));
        }

    }

    @Test
    public void runnerShouldReturnExceptionWhenTempDirectoryNotExist() throws Throwable {

        try (MockedStatic<KangoorooStandaloneRunner> runner = Mockito.mockStatic(KangoorooStandaloneRunner.class,
                Mockito.CALLS_REAL_METHODS)) {

            Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
            Logger spyLog = Mockito.spy(log);
            runner.when(KangoorooStandaloneRunner::getLogger).thenReturn(spyLog);

            FileUtils.deleteQuietly(tempDir);

            KangoorooStandaloneRunner.main(new String[] { "-u", "http://test.com" });

            verify(spyLog).error(matches("^The temporary directory specified"));
        }
    }

    @Test
    public void runnerShouldReturnExceptionWhenNoneExistentModuleSpecified() throws Throwable {
        try (MockedStatic<KangoorooStandaloneRunner> runner = Mockito.mockStatic(KangoorooStandaloneRunner.class,
                Mockito.CALLS_REAL_METHODS)) {

            Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
            Logger spyLog = Mockito.spy(log);
            runner.when(KangoorooStandaloneRunner::getLogger).thenReturn(spyLog);

            KangoorooStandaloneRunner.main(new String[] { "-u", "http://test.com", "-mods", "captcha,cat" });

            verify(spyLog).error(matches("is not an allowed module.$"));
        }
    }

    @Test
    public void kangoorooRunnerShouldHaveDefaultBehaviour() throws Throwable {

        List<Object> constructorArgs = new ArrayList<>();
        String urlString = "http://test.com";
        String urlMd5 = DigestUtils.md5Hex(urlString);
        URL url = new URL(urlString);

        try (MockedConstruction<KangoorooChromeBrowser> chromeBrowser = Mockito.mockConstruction(
                // mock KangoorooChromeBroswer and check its constructor arguments
                KangoorooChromeBrowser.class,
                (mock, context) -> {
                    constructorArgs.addAll(context.arguments());
                    when(mock.get(any(), anyString(), anyString()))
                            .thenReturn(new KangoorooResult(har, url));
                    when(mock.getMessageLog())
                            .thenReturn(new MessageLog());
                });

                MockedStatic<KangoorooStandaloneRunner> mockedKangoorooRunner = Mockito
                        .mockStatic(KangoorooStandaloneRunner.class, Mockito.CALLS_REAL_METHODS)) {

            // mock static method of KangoorooStandalone runner
            mockedKangoorooRunner
                    .when(() -> KangoorooStandaloneRunner.generateKangoorooReport(any(), anyLong(),
                            any(), any(), anyString(), anyString(), anyBoolean(), any()))
                    .thenReturn(null);

            KangoorooStandaloneRunner.main(new String[] { "-u", urlString });

            assertTrue("There should be a folder of outputDir/{url_md5}.", (new File(outputDir, urlMd5)).exists());

            // make sure kangooroo chromedriver is set up with the correct arguments
            assertEquals(new File(outputDir, urlMd5), constructorArgs.get(0));
            assertEquals(new File(tempDir, urlMd5), constructorArgs.get(1));

            // default behaviour is no proxy setup
            assertEquals(Optional.empty(), constructorArgs.get(2));
            assertEquals(Optional.empty(), constructorArgs.get(3));
            assertEquals(Optional.empty(), constructorArgs.get(4));

            // check for correct boolean default flags
            assertTrue("Use sandbox is default ON", ((Boolean) constructorArgs.get(5)));
            assertFalse("Use captcha solver is default OFF", ((Boolean) constructorArgs.get(6)));
            assertTrue("Save all files should be default ON", ((Boolean) constructorArgs.get(7)));

        }
    }

    // @Test
    public void kangoorooBrowserArgumentsCanBeOverriden() throws Throwable {

        List<Object> constructorArgs = new ArrayList<>();
        String testConfig = "output_folder: './output2/'";

        String urlString = "http://test.com";
        String urlMd5 = DigestUtils.md5Hex(urlString);
        URL url = new URL(urlString);

        try (FileOutputStream outputStream = new FileOutputStream(testConfigFile)) {
            outputStream.write(testConfig.getBytes(StandardCharsets.UTF_8));
        }
        testOutputDir.mkdir();

        try (MockedConstruction<KangoorooChromeBrowser> chromeBrowser = Mockito.mockConstruction(
                KangoorooChromeBrowser.class,
                (mock, context) -> {
                    constructorArgs.addAll(context.arguments());
                    when(mock.get(any(), anyString(), anyString()))
                            .thenReturn(new KangoorooResult(har, url));
                    when(mock.getMessageLog())
                            .thenReturn(new MessageLog());
                });
                MockedStatic<KangoorooStandaloneRunner> mockedKangoorooRunner = Mockito
                        .mockStatic(KangoorooStandaloneRunner.class, Mockito.CALLS_REAL_METHODS)) {

            // mock static method of KangoorooStandalone runner
            mockedKangoorooRunner
                    .when(() -> KangoorooStandaloneRunner.generateKangoorooReport(any(), anyLong(),
                            any(), any(), anyString(), anyString(), anyBoolean(), any()))
                    .thenReturn(null);

            KangoorooStandaloneRunner.main(new String[] { "-cf",
                    testConfigFile.getPath(), "-u", urlString });
            assertTrue("There should be a folder of outputDir/{url_md5}.", (new File(testOutputDir, urlMd5)).exists());

            // make sure kangooroo chromedriver is set up with the correct arguments
            assertEquals(new File(testOutputDir, urlMd5), constructorArgs.get(0));
            assertEquals(new File(tempDir, urlMd5), constructorArgs.get(1));

            // default behaviour is no proxy setup
            assertEquals(Optional.empty(), constructorArgs.get(2));
            assertEquals(Optional.empty(), constructorArgs.get(3));
            assertEquals(Optional.empty(), constructorArgs.get(4));

            // check for correct boolean default flags
            assertTrue("Use sandbox is default ON", ((Boolean) constructorArgs.get(5)));
            assertFalse("Use captcha solver is default OFF", ((Boolean) constructorArgs.get(6)));
            assertTrue("Save all files should be default ON", ((Boolean) constructorArgs.get(7)));

        }
    }

    // @Test
    public void proxyConfigCannotHaveOnlyIpAndNotPort() throws Throwable {
        String testConfig = "kang-upstream-proxy:\n" +
                " ip: '127.0.0.1'";
        try (FileOutputStream outputStream = new FileOutputStream(testConfigFile)) {
            outputStream.write(testConfig.getBytes(StandardCharsets.UTF_8));
        }
        String urlString = "http://test.com";

        try (MockedStatic<KangoorooStandaloneRunner> runner = Mockito.mockStatic(KangoorooStandaloneRunner.class,
                Mockito.CALLS_REAL_METHODS)) {

            Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
            Logger spyLog = Mockito.spy(log);
            runner.when(KangoorooStandaloneRunner::getLogger).thenReturn(spyLog);

            KangoorooStandaloneRunner.main(new String[] { "-cf",
                    testConfigFile.getPath(), "-u", urlString });

            verify(spyLog).error(matches("^Missing either IP or port in proxy configuration"));
        }

    }

    // @Test
    public void proxyConfigCannotHaveOnlyPortAndNotIp() throws Throwable {
        String testConfig = "kang-upstream-proxy:\n" +
                " port: '12345'";

        try (FileOutputStream outputStream = new FileOutputStream(testConfigFile)) {
            outputStream.write(testConfig.getBytes(StandardCharsets.UTF_8));
        }
        String urlString = "http://test.com";

        try (MockedStatic<KangoorooStandaloneRunner> runner = Mockito.mockStatic(KangoorooStandaloneRunner.class,
                Mockito.CALLS_REAL_METHODS)) {

            Logger log = LoggerFactory.getLogger(KangoorooStandaloneRunner.class);
            Logger spyLog = Mockito.spy(log);
            runner.when(KangoorooStandaloneRunner::getLogger).thenReturn(spyLog);

            KangoorooStandaloneRunner.main(new String[] { "-cf",
                    testConfigFile.getPath(), "-u", urlString });

            verify(spyLog).error(matches("^Missing either IP or port in proxy configuration"));
        }

    }

    // @Test
    public void kangoorooBrowserAllowForProxyWithOnlyIpAndPort() throws Throwable {

        List<Object> constructorArgs = new ArrayList<>();
        String testConfig = "kang-upstream-proxy:\n" +
                " port: '12345'\n" +
                " ip: '127.0.0.1'";

        String urlString = "http://test.com";
        URL url = new URL(urlString);

        try (FileOutputStream outputStream = new FileOutputStream(testConfigFile)) {
            outputStream.write(testConfig.getBytes(StandardCharsets.UTF_8));
        }

        try (MockedConstruction<KangoorooChromeBrowser> chromeBrowser = Mockito.mockConstruction(
                KangoorooChromeBrowser.class,
                (mock, context) -> {
                    constructorArgs.addAll(context.arguments());
                    when(mock.get(any(), anyString(), anyString()))
                            .thenReturn(new KangoorooResult(har, url));
                    when(mock.getMessageLog())
                            .thenReturn(new MessageLog());
                });
                MockedStatic<KangoorooStandaloneRunner> mockedKangoorooRunner = Mockito
                        .mockStatic(KangoorooStandaloneRunner.class, Mockito.CALLS_REAL_METHODS)) {

            // mock static method of KangoorooStandalone runner
            mockedKangoorooRunner
                    .when(() -> KangoorooStandaloneRunner.generateKangoorooReport(any(), anyLong(),
                            any(), any(), anyString(), anyString(), anyBoolean(), any()))
                    .thenReturn(null);

            KangoorooStandaloneRunner.main(new String[] { "-cf",
                    testConfigFile.getPath(), "-u", urlString });

            // make sure kangooroo chromedriver is set up with the correct arguments
            Optional<InetSocketAddress> proxyAddress = (Optional<InetSocketAddress>) constructorArgs.get(2);
            assertEquals(proxyAddress.get().getPort(), 12345);
            assertEquals(proxyAddress.get().getHostString(), "127.0.0.1");
            assertEquals(Optional.empty(), constructorArgs.get(3));
            assertEquals(Optional.empty(), constructorArgs.get(4));

        }
    }

    @Test
    public void resultJsonShouldBeInOutputUrlFolder() throws Throwable {
        String urlString = "http://test.com";
        String urlMd5 = DigestUtils.md5Hex(urlString);
        URL url = new URL(urlString);
        
        try (MockedConstruction<KangoorooChromeBrowser> chromeBrowser = Mockito.mockConstruction(
            KangoorooChromeBrowser.class,
            (mock, context) -> {
                when(mock.get(any(), anyString(), anyString()))
                        .thenReturn(new KangoorooResult(har, url));
                when(mock.getMessageLog())
                        .thenReturn(new MessageLog());
            });
                MockedStatic<KangoorooStandaloneRunner> mockedKangoorooRunner = Mockito
                        .mockStatic(KangoorooStandaloneRunner.class, Mockito.CALLS_REAL_METHODS)) {

            
            
            // mock static method of KangoorooStandalone runner
            mockedKangoorooRunner
                    .when(() -> KangoorooStandaloneRunner.generateKangoorooReport(any(), anyLong(),
                            any(), any(), anyString(), anyString(), anyBoolean(), any()))
                    .thenReturn(null);

            KangoorooStandaloneRunner.main(new String[] { "-u", urlString });

            File jsonOutput = new File(new File(outputDir, urlMd5), "results.json");
            assertTrue("Result.json should be in output url directory.",
                    jsonOutput.exists());

        }
    }

}
