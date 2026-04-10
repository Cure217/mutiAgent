package com.aliano.mutiagent.application.dto;

import lombok.Data;

@Data
public class HistorySearchMessageHit {

    private String messageId;
    private String sessionId;
    private String sessionTitle;
    private String appInstanceId;
    private String appType;
    private String instanceName;
    private String projectPath;
    private Integer seqNo;
    private String role;
    private String messageType;
    private String snippet;
    private String createdAt;
}
