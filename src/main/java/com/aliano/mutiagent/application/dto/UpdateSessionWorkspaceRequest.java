package com.aliano.mutiagent.application.dto;

import java.util.List;

public record UpdateSessionWorkspaceRequest(
        String workspaceKind,
        String role,
        String coordinationStatus,
        String progressSummary,
        String blockedReason,
        List<String> dependencySessionIds,
        String sharedContextSummary,
        List<SessionSharedContextRef> sharedContextRefs,
        String taskScope,
        String acceptanceCriteria,
        String deliverableSpec,
        String sharedContextMode,
        Integer sharedContextLimit) {
}
