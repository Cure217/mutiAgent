package com.aliano.mutiagent.domain.session;

import lombok.Data;

@Data
public class AiSession {

    private String id;
    private String appInstanceId;
    private String title;
    private String projectPath;
    private String projectPathLinux;
    private String status;
    private String interactionMode;
    private Long pid;
    private String startedAt;
    private String endedAt;
    private String lastMessageAt;
    private Integer exitCode;
    private String exitReason;
    private String rawLogPath;
    private String summary;
    private String tagsJson;
    private String extraJson;
    private String createdAt;
    private String updatedAt;

}
