package com.aliano.mutiagent.application.dto;

import lombok.Data;

@Data
public class SessionTimelineItem {

    private String itemId;
    private String sessionId;
    private String messageId;
    private Integer seqNo;
    private String itemType;
    private String eventType;
    private String title;
    private String role;
    private String messageType;
    private String content;
    private String createdAt;
}
