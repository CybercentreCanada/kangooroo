package ca.gc.cyber.kangooroo;


import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KangoorooRunnerConf {

    private String version;
    @SerializedName(value = "temporary_folder")
    private String temporaryFolder;
    @SerializedName(value = "output_folder")
    private String outputFolder;

    @SerializedName(value = "browser_settings")
    private Map<String, BrowserSetting> browserSettings;

    @SerializedName(value = "kang-upstream-proxy")
    private ProxyConfiguration upstreamProxy;


    public Optional<InetSocketAddress> getOptionalUpstreamProxyAddress() {
        return Optional.ofNullable(this.getUpstreamProxy()).map(proxy ->
                InetSocketAddress.createUnresolved(proxy.getIp(), proxy.getPort())
        );
    }


    public Optional<String> getOptionalUpstreamProxyUsername() {
        return Optional.ofNullable(this.getUpstreamProxy()).map(ProxyConfiguration::getUsername);
    }


    public Optional<String> getOptionalUpstreamProxyPassword() {
        return Optional.ofNullable(this.getUpstreamProxy()).map(ProxyConfiguration::getPassword);
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BrowserSetting {
        @SerializedName(value = "user_agent")
        private String userAgent;
        @SerializedName(value = "window_size")
        private String windowSize;
        @SerializedName(value = "request_headers")
        private Map<String, String> requestHeaders;
        @SerializedName(value="time_zone")
        private String timeZone;

    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProxyConfiguration {
        private String ip;
        private Integer port;
        private String username;
        private String password;
    }
}
