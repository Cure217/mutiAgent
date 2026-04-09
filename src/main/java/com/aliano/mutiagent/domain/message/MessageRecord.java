package com.aliano.mutiagent.domain.message;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public String getRawChunk() {
        return rawChunk;
    }

    public void setRawChunk(String rawChunk) {
        this.rawChunk = rawChunk;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Boolean getIsStructured() {
        return isStructured;
    }

    public void setIsStructured(Boolean structured) {
        isStructured = structured;
    }

    public String getSourceAdapter() {
        return sourceAdapter;
    }

    public void setSourceAdapter(String sourceAdapter) {
        this.sourceAdapter = sourceAdapter;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
