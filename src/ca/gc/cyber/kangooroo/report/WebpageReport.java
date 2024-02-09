package ca.gc.cyber.kangooroo.report;

import java.net.URL;
import java.util.Date;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString(callSuper = true)
@Getter
@Setter
public class WebpageReport {

    private static final Logger log = LoggerFactory.getLogger(WebpageReport.class);

    @Expose
    @SerializedName(value = "response_code", alternate = {"code"})
    private int responseCode;
    @Expose
    @SerializedName("connection_success")
    private boolean connectionSuccess;
    @Expose
    @SerializedName("fetch_object_success")
    private boolean fetchSuccess;
    @Expose
    @SerializedName("requested_url")
    private URL requestedURL;
    @Expose
    @SerializedName("requested_url_ip")
    private String requestedURLIP = null;
    @Expose
    @SerializedName("actual_url")
    private URL actualURL = null;
    @Expose
    @SerializedName("actual_url_ip")
    private String actualURLIP = null;
    @Expose
    @SerializedName("server_info")
    private LinkedHashMap<String, String> serverInfo;
    @Expose
    @SerializedName("has_timed_out")
    private boolean hasTimedOut = false;
    @Expose
    @SerializedName("processing_time")
    private long processingTime = 0;

    @Expose
    protected Date creationDate;
    @Expose
    protected String md5;
    @Expose
    protected String engineName;
    @Expose
    protected String engineVersion;


    public WebpageReport(URL requestedURL, String engineName, String engineVersion) {
        this.requestedURL = requestedURL;
        this.engineName = engineName;
        this.engineVersion = engineVersion;
        this.creationDate = new Date();
        this.requestedURL = requestedURL;
    }


    public void setCrawlResult(int responseCode, boolean connectionSuccess, boolean fetchSuccess, URL requestedURL, String requestedURLIP, URL actualURL, String actualURLIP, LinkedHashMap<String, String> serverInfo, boolean hasTimedOut, long processingTime) {
        this.requestedURL = requestedURL;
        this.requestedURLIP = requestedURLIP;
        this.responseCode = responseCode;
        this.connectionSuccess = connectionSuccess;
        this.fetchSuccess = fetchSuccess;
        this.actualURL = actualURL;
        this.actualURLIP = actualURLIP;
        this.serverInfo = serverInfo;
        this.hasTimedOut = hasTimedOut;
        this.processingTime = processingTime;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (actualURL == null ? 0 : actualURL.hashCode());
        result = prime * result + (actualURLIP == null ? 0 : actualURLIP.hashCode());
        result = prime * result + (connectionSuccess ? 1231 : 1237);
        result = prime * result + (fetchSuccess ? 1231 : 1237);
        result = prime * result + (hasTimedOut ? 1231 : 1237);
        result = prime * result + (requestedURL == null ? 0 : requestedURL.hashCode());
        result = prime * result + (requestedURLIP == null ? 0 : requestedURLIP.hashCode());
        result = prime * result + responseCode;
        return result;
    }


    /**
     * Explicitly written so that we can control requestedURLIp and actualURLIp : old reports didn't have the ips, so the equals for those fields is optional.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WebpageReport)) {
            return false;
        }
        WebpageReport other = (WebpageReport) obj;
        if (actualURL == null) {
            if (other.actualURL != null) {
                return false;
            }
        } else if (!actualURL.equals(other.actualURL)) {
            return false;
        }
        if (actualURLIP != null && other.actualURLIP != null && !actualURLIP.equals(other.actualURLIP)) { // Compare only if the other has an actual IP (old reports didn't)
            return false;
        }
        if (connectionSuccess != other.connectionSuccess) {
            return false;
        }
        if (fetchSuccess != other.fetchSuccess) {
            return false;
        }
        if (hasTimedOut != other.hasTimedOut) {
            return false;
        }
        if (requestedURL == null) {
            if (other.requestedURL != null) {
                return false;
            }
        } else if (!requestedURL.equals(other.requestedURL)) {
            return false;
        }
        if (requestedURLIP != null && other.requestedURLIP != null && !requestedURLIP.equals(other.requestedURLIP)) { // Compare only if the other has an requested IP (old reports didn't)
            return false;
        }
        return responseCode == other.responseCode;
    }
}
