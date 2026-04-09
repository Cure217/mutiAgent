package com.aliano.mutiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateSessionRequest(
        @NotBlank(message = "应用实例不能为空") String appInstanceId,
        String title,
        String projectPath,
        String interactionMode,
        String initInput,
        List<String> tags) {
}
