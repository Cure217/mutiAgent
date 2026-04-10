package com.aliano.mutiagent.application.dto;

import lombok.Data;

@Data
public class HistorySearchSessionHit {

    private String sessionId;
    private String title;
    private String appInstanceId;
    private String appType;
    private String instanceName;
    private String projectPath;
    private String status;
    private String interactionMode;
    private String createdAt;
    private String lastMessageAt;
    private String summary;
    private String matchedMessageText;
    private String matchedText;
    private String matchedSource;
}
