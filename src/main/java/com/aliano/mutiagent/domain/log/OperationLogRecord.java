package com.aliano.mutiagent.domain.log;

import lombok.Data;

@Data
public class OperationLogRecord {

    private String id;
    private String targetType;
    private String targetId;
    private String action;
    private String result;
    private String operatorName;
    private String detailJson;
    private String createdAt;
}
