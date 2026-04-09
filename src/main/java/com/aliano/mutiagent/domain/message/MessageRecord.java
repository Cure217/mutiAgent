package com.aliano.mutiagent.domain.message;

import lombok.Data;

@Data
public class MessageRecord {

    private String id;
    private String sessionId;
    private Integer seqNo;
    private String role;
    private String messageType;
    private String contentText;
    private String contentJson;
    private String rawChunk;
    private String parentId;
    private Boolean isStructured;
    private String sourceAdapter;
    private String createdAt;

}
