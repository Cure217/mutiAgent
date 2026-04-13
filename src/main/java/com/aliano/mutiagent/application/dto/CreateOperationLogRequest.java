package com.aliano.mutiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateOperationLogRequest(
        @NotBlank(message = "目标类型不能为空") String targetType,
        String targetId,
        @NotBlank(message = "操作类型不能为空") String action,
        @NotBlank(message = "结果不能为空") String result,
        String operatorName,
        Map<String, Object> detail) {
}
