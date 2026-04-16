package com.aliano.mutiagent.infrastructure.event;

import java.time.OffsetDateTime;

public record ClientAttachment(
        String transportSessionId,
        String clientId,
        String connectedAt,
        String lastHeartbeatAt,
        String observedTargetType,
        String observedTargetId,
        String userAgent,
        String remoteAddress) {

    public static ClientAttachment connected(String transportSessionId,
                                             String clientId,
                                             String observedTargetType,
                                             String observedTargetId,
                                             String userAgent,
                                             String remoteAddress) {
        String now = OffsetDateTime.now().toString();
        return new ClientAttachment(
                transportSessionId,
                clientId,
                now,
                now,
                normalizeTargetType(observedTargetType, observedTargetId),
                normalizeTargetId(observedTargetId),
                userAgent,
                remoteAddress
        );
    }

    public ClientAttachment heartbeat(String observedTargetType, String observedTargetId) {
        return new ClientAttachment(
                transportSessionId,
                clientId,
                connectedAt,
                OffsetDateTime.now().toString(),
                normalizeTargetType(observedTargetType, observedTargetId),
                normalizeTargetId(observedTargetId),
                userAgent,
                remoteAddress
        );
    }

    private static String normalizeTargetType(String observedTargetType, String observedTargetId) {
        if (normalizeTargetId(observedTargetId) == null) {
            return null;
        }
        if (observedTargetType == null || observedTargetType.isBlank()) {
            return "session";
        }
        return observedTargetType.trim();
    }

    private static String normalizeTargetId(String observedTargetId) {
        if (observedTargetId == null || observedTargetId.isBlank()) {
            return null;
        }
        return observedTargetId.trim();
    }
}
