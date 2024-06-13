package ca.gc.cyber.kangooroo.report;


import ca.gc.cyber.kangooroo.utils.io.net.url.URLRedirection;

import java.util.List;
import java.util.Map;

import com.browserup.harreader.model.Har;

import lombok.Data;

@Data
public class KangoorooURLReport {

    Har report;
    Experimentation experiment;
    Summary summary;
    Map<String, HashTypes> fileHashes;
    Captcha captcha;

    @Data
    public class Experimentation {
        Map<String, String> engineInfo;
        Map<String, String> params;
        Execution execution;
        public class Execution {
            String status;
            List<String> message;
            String startTime;
            Long processTime;
        }
    }

    @Data
    public class Summary {

        Map<String, String> fetchResult;
        KangoorooURL requestedUrl;
        KangoorooURL actualUrl;
        List<URLRedirection> urlRedirects;
        Map<String, String> serverInfo;
    }


    public class HashTypes {
        String md5;
        String sha1;
        String sha256;
    }

    @Data
    public class Captcha {
        Boolean isPresent;
        String service;
        Boolean solved;
    }


}
