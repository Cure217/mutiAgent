package com.aliano.mutiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record WorkspaceDispatchExistingCommandRequest(
        @NotBlank(message = "目标子窗口不能为空") String sessionId,
        @NotBlank(message = "派单内容不能为空") String content,
        Boolean appendNewLine,
        Boolean recordInput,
        SessionWorkspaceMeta workspaceMeta,
        Map<String, Object> detail) {
}
