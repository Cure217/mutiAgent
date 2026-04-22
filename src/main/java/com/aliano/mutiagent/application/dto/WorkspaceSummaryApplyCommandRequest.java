package com.aliano.mutiagent.application.dto;

import java.util.Map;

public record WorkspaceSummaryApplyCommandRequest(
        String targetSessionId,
        Map<String, Object> detail) {
}
