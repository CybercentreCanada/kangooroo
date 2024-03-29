package ca.gc.cyber.kangooroo.utils.io.net.http;

import ca.gc.cyber.kangooroo.utils.io.net.url.URLRedirection;
import ca.gc.cyber.kangooroo.utils.io.net.url.URLUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarHeader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class HarUtils {

    private static final Logger log = LoggerFactory.getLogger(HarUtils.class);

    // Use Jackson instead of Gson to serialize a Har because the library behind it already uses Jackson internally
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }


    public static List<URLRedirection> getHTTPRedirections(Har har) throws MalformedURLException {
        List<URLRedirection> redirections = new ArrayList<>();
        URL from = new URL(har.getLog().getEntries().get(0).getRequest().getUrl());

        // Compute redirection as seen on the HAR file based on the "redirectURL" value from a response
        URL lastRequest = null;
        for (HarEntry entry : har.getLog().getEntries()) {
            lastRequest = new URL(entry.getRequest().getUrl());
            if (entry.getResponse().getRedirectURL() != null && !entry.getResponse().getRedirectURL().equals("")) {
                URL to;
                try {
                    to = getAbsoluteUrlFromRedirectedUrl(entry);
                } catch (MalformedURLException e) {
                    log.warn("RedirectedURL is not parsable, using the request.url of the next entry : " + e.getMessage());
                    HarEntry nextEntry = har.getLog().getEntries().get(har.getLog().getEntries().indexOf(entry) + 1);
                    to = new URL(nextEntry.getRequest().getUrl());
                }
                redirections.add(new URLRedirection(from, to, entry.getResponse().getStatus()));
                from = new URL(to.toString());
            } else {
                break;
            }
        }

        // Make sure redirections match the next request made as the browser can translate the URL before generating the request
        // If a change is detected, replace the URL by the one generated by the browser
        List<URLRedirection> finalRedirections = new ArrayList<>();
        for (URLRedirection redirection : redirections) {
            int index = redirections.indexOf(redirection);
            boolean isLast = index == redirections.size() - 1;
            if (!isLast) {
                URLRedirection nextRedirection = redirections.get(index + 1);
                if (!redirection.getTo().equals(nextRedirection.getFrom())) {
                    URL toUrl = redirection.getTo();
                    if (toUrl.equals(nextRedirection.getFrom())) {
                        redirection = new URLRedirection(redirection.getFrom(), nextRedirection.getFrom(), redirection.getStatusCode());
                    } else {
                        log.warn("Normalized redirection URL does not match the next request, using the URL of the next request instead.");
                        log.warn(toUrl + " vs " + nextRedirection.getFrom());
                        redirection = new URLRedirection(redirection.getFrom(), nextRedirection.getFrom(), redirection.getStatusCode());
                    }
                }
            } else if (isLast && !lastRequest.equals(redirection.getTo())) {

                URL toUrl = redirection.getTo();
                if (toUrl.equals(lastRequest)) {
                    redirection = new URLRedirection(redirection.getFrom(), toUrl, redirection.getStatusCode());
                } else if (lastRequest.toExternalForm().equals(redirection.getFrom().toExternalForm())) {
                    log.warn("No request has been made after detecting the last redirection, maybe Chomium did not sent the request?");
                    redirection = new URLRedirection(redirection.getFrom(), toUrl, redirection.getStatusCode());
                } else {
                    log.warn("Normalized redirection URL does not match the next request, using the URL of the next request instead.");
                    log.warn(toUrl + " vs " + lastRequest);
                    redirection = new URLRedirection(redirection.getFrom(), lastRequest, redirection.getStatusCode());
                }
            }
            finalRedirections.add(redirection);
        }

        return finalRedirections;
    }


    private static URL getAbsoluteUrlFromRedirectedUrl(HarEntry entry) throws MalformedURLException {
        if (entry.getResponse().getRedirectURL() == null || entry.getResponse().getRedirectURL().equals("")) {
            return null;
        } else {
            String redirection = entry.getResponse().getRedirectURL();
            if (URLUtils.isUrlAbsolute(redirection)) {
                return new URL(redirection);
            } else if (redirection.startsWith("//")) {
                URL request = new URL(entry.getRequest().getUrl());
                return new URL(request.getProtocol() + ":" + redirection);
            } else {
                return URLUtils.getAbsoluteURL(new URL(entry.getRequest().getUrl()), redirection);
            }
        }
    }


    public static boolean isConnectionSuccess(Har har) {
        if (har.getLog().getEntries().size() > 0) {
            return har.getLog().getEntries().get(0).getResponse().getStatus() != 0;
        }
        return false;
    }


    public static boolean isFetchSuccess(Har har) {
        HarEntry lastHop = getFirstEntryNotRedirected(har);
        return lastHop != null && lastHop.getResponse().getStatus() == 200;
    }


    /**
     * i.e. get the entry that corresponds to requestedURL
     *
     * @param har
     * @return
     */
    public static HarEntry getFirstEntry(Har har) {
        List<HarEntry> entries = har.getLog().getEntries();
        return entries != null && entries.size() > 0 ? entries.get(0) : null;
    }


    /**
     * i.e. get the last entry
     *
     * @param har
     */
    public static HarEntry getLastEntry(Har har) {
        List<HarEntry> entries = har.getLog().getEntries();
        if (entries.size() > 0) {
            return entries.get(entries.size() - 1);
        } else {
            return null;
        }
    }


    /**
     * i.e. get the entry that corresponds to the actualURL
     *
     * @param har
     * @return
     */
    public static HarEntry getFirstEntryNotRedirected(Har har) {
        if (har.getLog().getEntries().isEmpty()) {
            return null;
        }

        for (HarEntry entry : har.getLog().getEntries()) {
            boolean redirect = false;
            if (entry.getResponse().getRedirectURL().equals("")) {
                for (HarHeader x : entry.getResponse().getHeaders()) {
                    if (x.getName().equals("Location")) {
                        redirect = true;
                        break;
                    }
                }
                if (!redirect) {
                    return entry;
                }
            }
        }
        return har.getLog().getEntries().get(har.getLog().getEntries().size() - 1);
    }


    public static HarEntry getFirstEntry200OK(Har har) {
        for (HarEntry entry : har.getLog().getEntries()) {
            if (entry.getResponse().getStatus() == 200) {
                return entry;
            }
        }
        return null;
    }


    public static void toFile(HarEntry entry, File outputFile) throws IOException {
        if ("base64".equals(entry.getResponse().getContent().getEncoding())) {
            try (InputStream inputStream = Base64.getDecoder()
                    .wrap(IOUtils.toInputStream(entry.getResponse()
                            .getContent()
                            .getText(), StandardCharsets.UTF_8))) {
                FileUtils.copyInputStreamToFile(inputStream, outputFile);
            }
        } else if (entry.getResponse().getContent().getText() != null) {
            FileUtils.writeStringToFile(outputFile, entry.getResponse().getContent().getText(), StandardCharsets.UTF_8);
        }
    }


    public static Optional<Matcher> matcher(HarEntry entry, Pattern pattern) throws IOException {
        String content = null;
        if ("base64".equals(entry.getResponse().getContent().getEncoding())) {
            try (InputStream inputStream = Base64.getDecoder()
                    .wrap(IOUtils.toInputStream(entry.getResponse()
                            .getContent()
                            .getText(), StandardCharsets.UTF_8))) {
                StringWriter writer = new StringWriter();
                String encoding = StandardCharsets.UTF_8.name();
                IOUtils.copy(inputStream, writer, encoding);
                content = writer.toString();
            }
        } else if (entry.getResponse().getContent().getText() != null) {
            content = entry.getResponse().getContent().getText();
        }

        if (content != null) {
            return Optional.of(pattern.matcher(content));
        } else {
            return Optional.empty();
        }
    }


    public static boolean hasContent(HarEntry entry) {
        return entry.getResponse().getContent().getText() != null;
    }


    public static boolean isImage(HarEntry entry) {
        if (entry.getResponse().getContent().getMimeType() != null) {
            return entry.getResponse().getContent().getMimeType().toLowerCase().startsWith("image/");
        }
        return false;
    }


    public static boolean isJavascript(HarEntry entry) {
        if (entry.getResponse().getContent().getMimeType() != null) {
            String mimeType = entry.getResponse().getContent().getMimeType().toLowerCase();
            return mimeType.contains("javascript")
                    || mimeType.contains("ecmascript");
        } else return org.apache.commons.io.FilenameUtils.getExtension(entry.getRequest().getUrl()).equals("js");
    }


    public static String getContentMD5(HarEntry entry) {
        if (entry.getResponse().getContent().getComment() != null &&
                entry.getResponse().getContent().getComment().startsWith("removed;md5:")) {
            /*
             * Our internal tools may use the comment field to store the md5 of the content if the content is removed from the HAR file
             */
            String md5 = entry.getResponse().getContent().getComment().substring(12);
            return md5;
        } else if ("base64".equals(entry.getResponse().getContent().getEncoding())) {
            try (InputStream inputStream = Base64.getDecoder()
                    .wrap(IOUtils.toInputStream(entry.getResponse()
                            .getContent()
                            .getText(), StandardCharsets.UTF_8))) {
                return DigestUtils.md5Hex(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (entry.getResponse().getContent().getText() != null) {

            return DigestUtils.md5Hex(entry.getResponse().getContent().getText());

        } else {
            return null;
        }
    }


    public static void writeFile(Har har, File destination) throws IOException {
        MAPPER.writeValue(destination, har);
    }


    /**
     * Strip out all the contents (getResponse().getContent().getText()) of the Har
     *
     * @param har                Har to sanitize
     * @param createCopy         If true, generate a new Har for the sanitized version and return it
     * @param generateContentMD5 If true, compute the MD5 of each content and store it on the comment section of the response
     * @return A sanitized version of the Har without the content of each response
     * @throws IOException
     */
    public static Har removeContent(Har har, boolean createCopy, boolean generateContentMD5) throws IOException {
        Har sanitizedHar = har;
        if (createCopy) {
            sanitizedHar = MAPPER.readValue(MAPPER.writeValueAsString(har), Har.class);
        }
        for (HarEntry entry : sanitizedHar.getLog().getEntries()) {
            if (entry.getResponse().getContent() != null && entry.getResponse().getContent().getText() != null) {
                if (entry.getResponse().getContent().getComment() != null &&
                        entry.getResponse().getContent().getComment().startsWith("removed")) {
                    continue; // already removed
                }
                if (generateContentMD5) {
                    String md5 = "";
                    if (entry.getResponse().getContent().getEncoding() != null &&
                            entry.getResponse().getContent().getEncoding().equals("base64")) {
                        try (InputStream inputStream = Base64.getDecoder()
                                .wrap(IOUtils.toInputStream(entry.getResponse()
                                        .getContent()
                                        .getText(), StandardCharsets.UTF_8))) {
                            md5 = DigestUtils.md5Hex(inputStream);
                        }
                    } else {
                        md5 = DigestUtils.md5Hex(entry.getResponse().getContent().getText());
                    }
                    entry.getResponse().getContent().setComment("removed;md5:" + md5);
                }

                entry.getResponse().getContent().setText("");
                entry.getResponse().getContent().setEncoding("");
            }
        }
        return sanitizedHar;
    }


    public static void displayEntries(Har har) {
        for (HarEntry entry : har.getLog().getEntries()) {
            int statusCode = entry.getResponse().getStatus();
            log.info("[" + StringUtils.left(statusCode + "", 3) + "] " + entry.getRequest()
                    .getUrl() + " (" + entry.getResponse()
                    .getContent()
                    .getMimeType() + ")");
        }
    }
}
