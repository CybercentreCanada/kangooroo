package ca.gc.cyber.kangooroo.utils.io.net.url;


import java.net.URL;

public class URLRedirection {

    private URL from;
    private URL to;
    private int statusCode;


    public URLRedirection(URL from, URL to, int statusCode) {
        this.from = from;
        this.to = to;
        this.statusCode = statusCode;
    }


    public URL getFrom() {
        return from;
    }


    public URL getTo() {
        return to;
    }

    public int getStatusCode() {
        return statusCode;
    }


    @Override
    public String toString() {
        return "RedirectedURL [statusCode=" + statusCode + ", from=" + from + ", to=" + to + "]";
    }
}
