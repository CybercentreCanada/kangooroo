package ca.gc.cyber.kangooroo.report;


import ca.gc.cyber.kangooroo.report.KangoorooResult.CaptchaResult;
import ca.gc.cyber.kangooroo.utils.data.FileHashes;
import ca.gc.cyber.kangooroo.utils.io.net.http.HarUtils;
import ca.gc.cyber.kangooroo.utils.io.net.url.URLRedirection;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.browserup.harreader.model.Har;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString(callSuper = true)
@Getter
@Setter
public class KangoorooURLReport {

    private Har report;
    private Experimentation experiment;
    private Summary summary;
    private Map<String, FileHashes> fileHashes;
    private Captcha captcha;

    public enum CaptchaService {
        RECAPTCHA, H_CAPTCHA
    }

    public enum DownloadStatus {
        COMPLETED_DOWNLOAD, INCOMPLETE_DOWNLOAD, NO_DOWNLOAD
    }


    


    public KangoorooURLReport(Har report) {
        this.report = report;
    }

    public void setExperiment(Map<String, String> engineInfo, 
    String status, List<String> messages, String startTime, 
    Long processTime,String url, String urlType, String windowSize, String userAgent, List<String> modules,
    DownloadStatus downloadStatus) {
        this.experiment = new Experimentation(engineInfo);
        this.experiment.setExecution(status, messages, startTime, processTime, downloadStatus);
        this.experiment.setParams(url, urlType, windowSize, userAgent, modules);
    }


    public void setCaptcha(CaptchaResult captchaResult) {
        

        if (KangoorooResult.CaptchaResult.CAPTCHA_BLOCKED.equals(captchaResult) || KangoorooResult.CaptchaResult.CAPTCHA_FAILED.equals(captchaResult)) {
            this.captcha = new Captcha(true, CaptchaService.RECAPTCHA.name(), false);
        }
        else if (KangoorooResult.CaptchaResult.IS_HCAPTCHA.equals(captchaResult)) {
            this.captcha = new Captcha(true, CaptchaService.H_CAPTCHA.name(), false);
        }
        else if (KangoorooResult.CaptchaResult.CAPTCHA_SOLVED.equals(captchaResult)) {
            this.captcha = new Captcha(true, CaptchaService.RECAPTCHA.name(), true);
        }
        else {
            this.captcha = new Captcha(false, null, false);
        }
    }

    public void setFileHashes(Har rawHar) throws IOException {
        this.fileHashes = HarUtils.getEntryFileHashes(rawHar);
    }


    public void setSummary(Map<String, Object> fetchResult, KangoorooURL requestedUrl, KangoorooURL actualUrl, 
    List<URLRedirection> urlRedirects, Map<String, String> serverInfo, Map<String, String> requestHeaders, Map<String, String> sessionCookies) {
        this.summary = new Summary(fetchResult, requestedUrl, actualUrl, urlRedirects, serverInfo, requestHeaders, sessionCookies);
    }

    @Data 
    class Experimentation {
        Map<String, String> engineInfo;
        Parameters params;
        Execution execution;


        public Experimentation(Map<String, String> engineInfo) {
            this.engineInfo = engineInfo;

        }

        public void setExecution(String status, List<String> messages, String startTime, Long processTime, DownloadStatus downloadStatus) {
            this.execution = new Execution(status, messages, startTime, processTime, downloadStatus);
        }

        public void setParams(String url, String urlType, String windowSize, String userAgent, List<String> modules) {
            this.params = new Parameters(url, urlType, windowSize, userAgent, modules);
        }

        @Data
        @AllArgsConstructor
        class Parameters {
            String url;
            String urlType;
            String windowSize;
            String userAgent;
            List<String> modules;

            
        }
        
        @Data 
        @AllArgsConstructor
        class Execution {
            String status;
            List<String> messages;
            String startTime;
            Long processTime;
            DownloadStatus downloadStatus;
            
        }
    }

    @Data
    @AllArgsConstructor
    class Summary {

        Map<String, Object> fetchResult;
        KangoorooURL requestedUrl;
        KangoorooURL actualUrl;
        List<URLRedirection> urlRedirects;
        Map<String, String> serverInfo;
        Map<String, String> requestHeaders;
        Map<String, String> sessionCookies;

        
    }

    @Data
    @AllArgsConstructor
    class Captcha {
        Boolean isPresent;
        String service;
        Boolean solved;
    }



}
