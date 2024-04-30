package ca.gc.cyber.kangooroo.report;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ServiceReport implements Serializable {

    public String serviceName;

}
