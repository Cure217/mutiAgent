package com.aliano.mutiagent.infrastructure.event;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ClientAttachmentRegistry {

    private final Map<String, ClientAttachment> attachments = new ConcurrentHashMap<>();

    public ClientAttachment attach(String transportSessionId,
                                   String clientId,
                                   String observedTargetType,
                                   String observedTargetId,
                                   String userAgent,
                                   String remoteAddress) {
        ClientAttachment attachment = ClientAttachment.connected(
                transportSessionId,
                clientId,
                observedTargetType,
                observedTargetId,
                userAgent,
                remoteAddress
        );
        attachments.put(transportSessionId, attachment);
        return attachment;
    }

    public ClientAttachment heartbeat(String transportSessionId, String observedTargetType, String observedTargetId) {
        return attachments.computeIfPresent(
                transportSessionId,
                (ignored, attachment) -> attachment.heartbeat(observedTargetType, observedTargetId)
        );
    }

    public void detach(String transportSessionId) {
        attachments.remove(transportSessionId);
    }

    public int countAll() {
        return attachments.size();
    }

    public long countObservingTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return 0;
        }
        return attachments.values().stream()
                .filter(attachment -> targetType.equalsIgnoreCase(attachment.observedTargetType()))
                .count();
    }

    public List<ClientAttachment> snapshot() {
        return attachments.values().stream()
                .sorted(Comparator.comparing(ClientAttachment::connectedAt))
                .toList();
    }
}
