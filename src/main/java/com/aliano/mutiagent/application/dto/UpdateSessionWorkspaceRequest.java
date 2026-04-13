package com.aliano.mutiagent.application.dto;

import java.util.List;

public record UpdateSessionWorkspaceRequest(
        String workspaceKind,
        String role,
        String coordinationStatus,
        String progressSummary,
        String blockedReason,
        List<String> dependencySessionIds,
        String sharedContextSummary) {
}
