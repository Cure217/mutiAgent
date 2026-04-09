package com.aliano.mutiagent.domain.instance;

import lombok.Data;

@Data
public class AppInstance {

    private String id;
    private String code;
    private String name;
    private String appType;
    private String adapterType;
    private String runtimeEnv;
    private String launchMode;
    private String executablePath;
    private String launchCommand;
    private String argsJson;
    private String workdir;
    private String envJson;
    private String pathMappingRule;
    private Boolean enabled;
    private Boolean autoRestart;
    private String remark;
    private String lastStartAt;
    private String createdAt;
    private String updatedAt;

}
