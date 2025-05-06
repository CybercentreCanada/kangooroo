package ca.gc.cyber.kangooroo.browser;

import ca.gc.cyber.kangooroo.KangoorooRunnerConf.BrowserSetting;
import ca.gc.cyber.kangooroo.report.KangoorooResult;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;
import java.util.Optional;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.browserup.bup.proxy.CaptureType;

import static org.junit.Assert.assertEquals;

public class KangoorooBrowserTest {

    private static final Logger log = LoggerFactory.getLogger(KangoorooBrowserTest.class);


    @Rule
    public ExpectedException exception = ExpectedException.none();

    ConcreteTestKangoorooBrowser testBrowser;

    @After
    public void cleanup() {

        if (testBrowser != null) {
            try {
                testBrowser.browserShutdown();
            } catch (IllegalStateException e) {
            }

            testBrowser = null;
        }


    }

    @Test
    public void nullUpstreamProxyIsDefault() {
        testBrowser = new ConcreteTestKangoorooBrowser(
                null, null, Optional.empty(), Optional.empty(), Optional.empty());

        var proxy = testBrowser.getPROXY();

        assertEquals(null, proxy.getChainedProxy());
    }

    @Test
    public void allowUnauthenticatedProxy() {
        testBrowser = new ConcreteTestKangoorooBrowser(
                null, null, Optional.of(InetSocketAddress.
                createUnresolved("127.0.0.1", 12345)),
                Optional.empty(), Optional.empty());

        var proxy = testBrowser.getPROXY();

        assertEquals(12345, proxy.getChainedProxy().getPort());
        assertEquals("127.0.0.1", proxy.getChainedProxy().getHostString());

    }

    @Test
    public void shouldThrowExceptionWhenAuthenticatedProxyNoPassword() {
        ConcreteTestKangoorooBrowser testBrowser = new ConcreteTestKangoorooBrowser(
                null, null, Optional.empty(),
                Optional.of("username"), Optional.empty());

        exception.expect(IllegalStateException.class);
        testBrowser.getPROXY();

    }

    @Test
    public void shouldThrowExceptionWhenAuthenticatedProxyNotUsername() {
        // given proxy with no username
        testBrowser = new ConcreteTestKangoorooBrowser(
                null, null, Optional.empty(),
                Optional.empty(), Optional.of("password"));

        exception.expect(IllegalStateException.class);
        testBrowser.getPROXY();


    }

    @Test
    public void authenticatedProxyCanBeConfigured() {
        testBrowser = new ConcreteTestKangoorooBrowser(
                null, null, Optional.of(InetSocketAddress.
                createUnresolved("127.0.0.1", 12345)),
                Optional.of("username"), Optional.of("password"));


        var proxy = testBrowser.getPROXY();

        assertEquals("127.0.0.1", proxy.getChainedProxy().getHostString());
        assertEquals(12345, proxy.getChainedProxy().getPort());

    }

    @Test
    public void requestHeaderCanBeConfigured() {
        testBrowser = new ConcreteTestKangoorooBrowser(
                null, null, Optional.empty(), Optional.empty(), Optional.empty());

        Map<String, String> headers = Map.of("Accept-Encoding", "test", "sec-ch-ua-platform", "Windows");
            
        var proxy = testBrowser.getPROXY(headers);

        var proxyHeaders = proxy.getAllHeaders();

        for (var key : headers.keySet()) {
            assertEquals(headers.get(key), proxyHeaders.get(key));
        }



    }

    private class ConcreteTestKangoorooBrowser extends KangoorooBrowser {

        public ConcreteTestKangoorooBrowser(File resultFolder, File tempFolder, Optional<InetSocketAddress> upstreamProxy,
                                            Optional<String> username, Optional<String> password) {
            super(resultFolder, tempFolder, upstreamProxy, username, password);
        }

        @Override
        protected KangoorooResult execute(URL initialURL, BrowserSetting browserSetting) throws IOException {
            return null;
        }

        @Override
        protected CaptureType[] getHarCaptureTypes() {
            return new CaptureType[]{
                    CaptureType.REQUEST_HEADERS,
                    CaptureType.RESPONSE_CONTENT,
                    CaptureType.RESPONSE_HEADERS,
                    CaptureType.RESPONSE_COOKIES,
                    CaptureType.RESPONSE_BINARY_CONTENT
            };
        }


    }


}
