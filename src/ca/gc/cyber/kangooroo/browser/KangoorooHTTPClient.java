package ca.gc.cyber.kangooroo.browser;

import ca.gc.cyber.kangooroo.KangoorooRunnerConf.BrowserSetting;
import ca.gc.cyber.kangooroo.report.KangoorooResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.browserup.bup.proxy.CaptureType;
import com.browserup.harreader.model.Har;

public class KangoorooHTTPClient extends KangoorooBrowser {

    private static final String PROXY_DOMAIN = "localhost";
    private static final Logger log = LoggerFactory.getLogger(KangoorooHTTPClient.class);
    private static final int CONNECTION_TIMEOUT_MS = 30_000;

    private static final CaptureType[] CAPTURE_TYPES = new CaptureType[]{CaptureType.REQUEST_HEADERS,
            CaptureType.RESPONSE_HEADERS, CaptureType.RESPONSE_COOKIES};


    public KangoorooHTTPClient(File resultFolder, File tempFolder) {
        super(resultFolder, tempFolder);
    }


    private static HttpClientBuilder createBuilder() {
        HttpClientBuilder builder = null;
        // building an insecure HTTPS client
        log.debug("SSLHttpClient: Trust all certificates.");
        // Ignore all certificate
        try {
            // Ignore all certificate
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(new TrustSelfSignedStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            }).build();
            var sslConnectionFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            builder = HttpClients.custom().setSSLSocketFactory(sslConnectionFactory);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new RuntimeException(e);
        }

        builder.setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
                .setConnectTimeout(CONNECTION_TIMEOUT_MS)
                .setSocketTimeout(CONNECTION_TIMEOUT_MS)
                .build());

        return builder;
    }


    @Override
    protected KangoorooResult execute(URL initialURL, BrowserSetting browserSetting) throws IOException {
        String initialURLMd5 = DigestUtils.md5Hex(initialURL.toExternalForm());
        log.info("Downloading using HTTPClient: " + initialURL + " [" + initialURLMd5 + "]");

        Pair<Har, URL> pair = downloadFileWithHar(initialURL, browserSetting.getUserAgent(), tempFolder);

        KangoorooResult result = new KangoorooResult(pair);
        processResult(result, tempFolder, resultFolder);
        return result;
    }


    protected void processResult(KangoorooResult result, File downloadFile, File resultFolder) throws IOException {
        log.info("Fetch done, processing the results..");
        if (result.isConnectionSuccess() && downloadFile.exists()) {

            String fileMD5;
            Har har = result.getHar();

            try (var is = FileUtils.openInputStream(downloadFile)) {
                fileMD5 = DigestUtils.md5Hex(is);
            }

            File destFile = new File(resultFolder, "downloadFile");
            FileUtils.moveFile(downloadFile, destFile);

            // Keep a trace of the MD5 on the har file via the comment section
            // Get the last entry as it is the request that ended up downloading the file
            if (har.getLog().getEntries().size() > 0) {
                har.getLog()
                        .getEntries()
                        .get(har.getLog().getEntries().size() - 1)
                        .getResponse()
                        .getContent()
                        .setText("");
                har.getLog()
                        .getEntries()
                        .get(har.getLog().getEntries().size() - 1)
                        .getResponse()
                        .getContent()
                        .setComment("removed;md5:" + fileMD5);
            }
        }
    }


    private Pair<Har, URL> downloadFileWithHar(URL url, String userAgent, File downloadFile) throws IOException {
        Har har;
        URL actualURL;
        getPROXY().newHar();
        try {
            actualURL = downloadFile(url, downloadFile, userAgent, true);
        } finally {
            har = getPROXY().endHar();
        }

        return Pair.of(har, actualURL);
    }


    public URL downloadFile(URL url, File outputFile, String userAgent, boolean withBrowserMobProxy) throws IOException {
        URL finalURL = null;
        HttpClientBuilder builder = KangoorooHTTPClient.createBuilder();

        // make this download goes through the proxy
        if (withBrowserMobProxy) {
            builder.setProxy(new HttpHost(PROXY_DOMAIN, getPROXY().getPort()));
        }

        builder.setRedirectStrategy(new LaxRedirectStrategy() {
            @Override
            public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext ctx) throws ProtocolException {
                Header locationHeader = response.getFirstHeader("location");
                if (request instanceof HttpUriRequest) {
                    URI uri = ((HttpUriRequest) request).getURI();

                    // support for relative URI and single backslash "\"
                    try {
                        if (locationHeader != null && locationHeader.getValue().equals("\\")) {
                            URI redirectionURI = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), "/", null, null);
                            log.warn("Location field with a backslash detected, new location: " + redirectionURI);
                            return redirectionURI;
                        }
                    } catch (URISyntaxException e) {
                        log.error("Unable to generate the redirection URI", e);
                    }
                }
                // Default
                return super.getLocationURI(request, response, ctx);
            }
        });


        // create request and request context
        HttpClientContext context = HttpClientContext.create();
        HttpGet request;
        try {
            log.debug("Trying to download from URI: " + url.toURI());
            request = new HttpGet(url.toURI());
        } catch (URISyntaxException e1) {
            throw new IllegalArgumentException("Unable to convert the URL into an URI: " + url);
        }
        request.setHeader("User-Agent", userAgent);

        BasicCookieStore cookieStore = new BasicCookieStore();
        context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);


        try (CloseableHttpClient client = builder.build()) {
            try (CloseableHttpResponse response = client.execute(request, context)) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    try (ReadableByteChannel readableByteChannel = Channels.newChannel(response.getEntity()
                            .getContent());
                         FileOutputStream fileOutputStream = new FileOutputStream(outputFile.toString())) {
                        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                    }
                } else {

                    log.warn("Download failed, not a 200 OK response. We got response code: " +
                            response.getStatusLine().getStatusCode());
                }
            } catch (SocketTimeoutException e) {
                log.warn("Download Failed. Connection timed out.");
            } catch (Exception e) {
                log.warn("Unexpected exception. Failed to download.");
                log.debug(e.getMessage());
            }

            finalURL = new URL(request.getURI().toString());
            List<URI> locations = context.getRedirectLocations();
            if (locations != null) {
                finalURL = new URL(locations.get(locations.size() - 1).toString());
            }

            // these error shouldn't happen. Throw runtime exception and exit
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw e;
        }

        return finalURL;
    }

    @Override
    protected CaptureType[] getHarCaptureTypes() {
        // we don't store the content on the HAR file so we don't include:
        // CaptureType.RESPONSE_CONTENT and  CaptureType.RESPONSE_BINARY_CONTENT
        return CAPTURE_TYPES;
    }
}
