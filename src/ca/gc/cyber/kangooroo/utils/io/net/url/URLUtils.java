package ca.gc.cyber.kangooroo.utils.io.net.url;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class URLUtils {
    private static final Logger log = LoggerFactory.getLogger(URLUtils.class);


    /**
     * For URL: http://www.domain.com/path/file.txt<br>
     * - 'relativePath = '/test.html'   -> http://www.domain.com/test.html<br>
     * - 'relativePath = '../test.html' -> http://www.domain.com/test.html<br>
     * - 'relativePath = './test.html'  -> http://www.domain.com/path/test.html<br>
     * - 'relativePath = 'test.html'    -> http://www.domain.com/path/test.html<br>
     *
     * @return an absolute URL based on the relative path you specify
     */
    public static URL getAbsoluteURL(URL originalURL, String relativeURL) throws MalformedURLException {
        String path = originalURL.getPath();
        if (path == null) {
            path = "/";
        }
        List<String> segments;


        //base case
        if (!relativeURL.startsWith("./") && !relativeURL.startsWith("../") && !relativeURL.startsWith("/")) {
            String url = originalURL.getProtocol() + "://" + originalURL.getAuthority() + path;
            if (path.contains("/")) {
                url = url.substring(0, url.lastIndexOf("/"));
            }
            return new URL(url + "/" + relativeURL);
        }


        // recursion case
        // if path is empty, just append relative URL
        if (path.replaceAll("/+", "/").isEmpty()) {
            segments = new ArrayList<>();
            segments.add("");
        } else {
            segments = new ArrayList<>(Arrays.asList(path.replaceAll("/+", "/").substring(1).split("/")));
            if (path.length() > 1 && path.endsWith("/")) {
                segments.add("");
            }
        }
        relativeURL = relativeURL.replaceAll("/+", "/");

        if (relativeURL.startsWith("./")) {
            return getAbsoluteURL(originalURL, relativeURL.substring(2));
        } else if (relativeURL.startsWith("../")) {
            segments.remove(segments.size() - 1);
            URL newURL = new URL(originalURL.getProtocol() + "://" + originalURL.getAuthority() + "/" + StringUtils.join(segments, "/"));
            return getAbsoluteURL(newURL, relativeURL.substring(3));
        } else if (relativeURL.startsWith("/")) {
            URL newURL = new URL(originalURL.getProtocol() + "://" + originalURL.getAuthority() + "/");
            return getAbsoluteURL(newURL, relativeURL.substring(1));
        } else {
            return getAbsoluteURL(originalURL, relativeURL);
        }
    }

    public static boolean isUrlAbsolute(String url) {
        return url.matches("^[a-zA-Z]{3,}://.+");
    }

}
