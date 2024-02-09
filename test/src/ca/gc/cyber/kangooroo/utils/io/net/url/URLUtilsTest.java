package ca.gc.cyber.kangooroo.utils.io.net.url;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class URLUtilsTest {

    @Test
    public void absoluteUrlOfBackslashShouldBeAppendToBaseUrl() throws MalformedURLException {
        URL baseUrl = new URL("http://www.domain.com/path/file.txt");
        String relativePath = "/test.html";

        URL absUrl = URLUtils.getAbsoluteURL(baseUrl, relativePath);

        assertEquals("http://www.domain.com/test.html", absUrl.toExternalForm());
    }

    @Test
    public void absoluteUrlOfDoubleDotShouldAppendToDirectoryBefore() throws MalformedURLException {
        URL baseUrl = new URL("http://www.domain.com/to/path/file.txt");
        String relativePath = "../test.html";

        URL absUrl = URLUtils.getAbsoluteURL(baseUrl, relativePath);

        assertEquals("http://www.domain.com/to/test.html", absUrl.toExternalForm());
    }

    @Test
    public void absoluteUrlofDotSlashShouldAppendToCurrentDirectory() throws MalformedURLException {
        URL baseUrl = new URL("http://www.domain.com/to/path/file.txt");
        String relativePath = "./test.html";

        URL absUrl = URLUtils.getAbsoluteURL(baseUrl, relativePath);

        assertEquals("http://www.domain.com/to/path/test.html", absUrl.toExternalForm());
    }

    @Test
    public void asoluteUrlOfArticleShouldReplaceCurrentArticle() throws MalformedURLException {
        URL baseUrl = new URL("http://www.domain.com/to/path/file.txt");
        String relativePath = "test.html";

        URL absUrl = URLUtils.getAbsoluteURL(baseUrl, relativePath);

        assertEquals("http://www.domain.com/to/path/test.html", absUrl.toExternalForm());
    }


}
