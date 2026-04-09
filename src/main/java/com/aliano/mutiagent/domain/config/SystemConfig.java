package com.aliano.mutiagent.domain.config;

import lombok.Data;

@Data
public class SystemConfig {

    private String id;
    private String configGroup;
    private String configKey;
    private String valueType;
    private String valueText;
    private String valueJson;
    private String secretRef;
    private String remark;
    private String updatedAt;
}
