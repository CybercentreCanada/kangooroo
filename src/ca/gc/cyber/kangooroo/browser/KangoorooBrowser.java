package ca.gc.cyber.kangooroo.browser;


import ca.gc.cyber.kangooroo.report.KangoorooResult;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.browserup.bup.BrowserUpProxy;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.proxy.CaptureType;
import com.browserup.bup.proxy.auth.AuthType;
import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

public abstract class KangoorooBrowser {

    private static final Logger log = LoggerFactory.getLogger(KangoorooBrowser.class);
    private static final int TIMEOUT_IN_SECONDS = 60;
    private static final Map<String, BrowserUpProxy> PROXY_MAP = Collections.synchronizedMap(new HashMap<>()); // Thread name, Proxy
    protected boolean hasTimedOut = false;

    protected File tempFolder;
    protected File resultFolder;

    protected Optional<InetSocketAddress> upstreamProxy;
    protected Optional<String> username;
    protected Optional<String> password;


    public KangoorooBrowser(File resultFolder, File tempFolder) {
        this(resultFolder, tempFolder, Optional.empty(), Optional.empty(), Optional.empty());
    }


    public KangoorooBrowser(File resultFolder, File tempFolder, Optional<InetSocketAddress> upstreamProxy,
                            Optional<String> username, Optional<String> password) {

        this.resultFolder = resultFolder;
        this.tempFolder = tempFolder;
        this.upstreamProxy = upstreamProxy;
        this.username = username;
        this.password = password;
    }


    public KangoorooResult get(URL initialURL, String windowSize, String userAgent) throws IOException {
        KangoorooResult result = execute(initialURL, windowSize, userAgent);

        displayResources(result.getHar());
        saveHar(result.getHar(), resultFolder);

        return result;
    }


    protected abstract KangoorooResult execute(URL initialURL, String windowSize, String userAgent) throws IOException;


    /**
     * save har data to file.
     *
     * @param har
     * @param resultFolder
     * @return
     * @throws IOException
     */
    protected File saveHar(Har har, File resultFolder) throws IOException {
        File harFile = new File(resultFolder, "session.har");
        log.debug("Saving the HAR file to " + harFile);
        har.writeTo(harFile);
        return harFile;
    }


    /**
     * log all resources in HAR
     *
     * @param har
     */
    protected void displayResources(Har har) {
        log.info("Resources:");
        for (HarEntry entry : har.getLog().getEntries()) {
            int statusCode = entry.getResponse().getStatus();


            log.info("[" + StringUtils.left(statusCode + "", 3) + "] " + entry.getRequest()
                    + entry.getResponse().getContent().getMimeType() + ")");
        }
    }


    protected void saveResources(Har har, File downloadedFolder) throws IOException {
        log.debug("Saving " + har.getLog().getEntries().size() + " resources...");
        int i = 0;
        for (HarEntry entry : har.getLog().getEntries()) {
            File outputFile = new File(downloadedFolder, "resource" + i);
            saveHarEntry(entry, outputFile);
            i++;
        }
    }


    /**
     * write HAR entry to file.
     *
     * @param entry
     * @param outputFile
     * @throws IOException
     */
    protected void saveHarEntry(HarEntry entry, File outputFile) throws IOException {
        if ("base64".equals(entry.getResponse().getContent().getEncoding())) {
            try (InputStream inputStream = Base64.getDecoder()
                    .wrap(IOUtils.toInputStream(entry.getResponse()
                            .getContent()
                            .getText(), StandardCharsets.UTF_8))) {
                FileUtils.copyInputStreamToFile(inputStream, outputFile);
            }
        } else {
            FileUtils.writeStringToFile(outputFile, entry.getResponse().getContent().getText(), StandardCharsets.UTF_8);
        }
    }


    /**
     * Remove downloaded content from the HAR file and download the content to disk.
     *
     * @param har
     * @param downloadMD5
     * @throws IOException
     */
    protected void removeDownloadedContent(Har har, String downloadMD5) throws IOException {
        for (HarEntry entry : har.getLog().getEntries()) {
            if (entry.getResponse().getContent() != null && entry.getResponse().getContent().getText() != null) {
                String md5 = "";
                if (entry.getResponse().getContent().getEncoding() != null && entry.getResponse()
                        .getContent()
                        .getEncoding()
                        .equals("base64")) {
                    try (InputStream inputStream = Base64.getDecoder()
                            .wrap(IOUtils.toInputStream(entry.getResponse()
                                    .getContent()
                                    .getText(), StandardCharsets.UTF_8))) {
                        md5 = DigestUtils.md5Hex(inputStream);
                    }
                } else {
                    md5 = DigestUtils.md5Hex(entry.getResponse().getContent().getText());
                }
                if (md5.equals(downloadMD5)) {
                    entry.getResponse().getContent().setText("");
                    entry.getResponse().getContent().setComment("removed;md5:" + md5);
                    return;
                }
            }
        }
    }


    /**
     * Get the list of information that should be captued by the HAR.
     *
     * @return
     */
    abstract protected CaptureType[] getHarCaptureTypes();


    /**
     * Create a BrowserUpProxy with upstream proxy if present.
     * We also remove the "VIA" header from the HTTP request to remove proxy information
     *
     * @return
     */
    private BrowserUpProxy createProxy() {
        BrowserUpProxy proxy = new BrowserUpProxyServer();

        proxy.enableHarCaptureTypes(getHarCaptureTypes());
        proxy.setIdleConnectionTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        proxy.setRequestTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        proxy.setConnectTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        proxy.setTrustAllServers(true);

        if (upstreamProxy.isPresent()) {
            proxy.setChainedProxy(upstreamProxy.get());
        }

        if (username.isPresent() && password.isPresent()) {
            proxy.chainedProxyAuthorization(username.get(), password.get(), AuthType.BASIC);
        } else if (username.isPresent() || password.isPresent()) {
            throw new IllegalStateException("Should not be able to add proxy authentication with only Username or Password");
        }


        proxy.addLastHttpFilterFactory(new HttpFiltersSourceAdapter() {
            @Override
            public HttpFilters filterRequest(HttpRequest originalRequest) {
                return new HttpFiltersAdapter(originalRequest) {
                    @Override
                    public HttpResponse proxyToServerRequest(HttpObject httpObject) {
                        if (httpObject instanceof HttpRequest) {
                            // Remove header 'VIA' that tells the remote server we are using BrowserMobProxy
                            ((HttpRequest) httpObject).headers().remove(HttpHeaders.Names.VIA);
                        }
                        return super.proxyToServerRequest(httpObject);
                    }

                };
            }
        });

        proxy.start();

        return proxy;
    }


    /**
     * Get the proxy for the thread
     *
     * @return
     */
    public final synchronized BrowserUpProxy getPROXY() {
        String threadName = Thread.currentThread().getName();

        log.debug("A proxy is created for thread name: " + threadName);

        String[] nameSplit = threadName.split("-");
        if (nameSplit.length > 2) {
            threadName = nameSplit[0] + "-" + nameSplit[1];
        }

        if (!PROXY_MAP.containsKey(threadName)) {
            PROXY_MAP.put(threadName, createProxy());
        }

        return PROXY_MAP.get(threadName);
    }


    /**
     * Stop and remove all proxies when we shutdown the browser.
     */
    public void browserShutdown() {
        PROXY_MAP.values().stream().filter(BrowserUpProxy::isStarted).forEach(BrowserUpProxy::stop);
        PROXY_MAP.clear();
    }
}
