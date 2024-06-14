package ca.gc.cyber.kangooroo.utils.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileHashes {
    private String md5;
    private String sha1;
    private String sha256;
}
