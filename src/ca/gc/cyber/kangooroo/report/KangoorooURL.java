package ca.gc.cyber.kangooroo.report;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KangoorooURL {
    String url;
    String urlMd5;
    String host;
    String ip;

}
