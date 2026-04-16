package com.aliano.mutiagent.infrastructure.event;

import com.aliano.mutiagent.application.service.ClientAttachmentAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class SessionWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ClientAttachmentRegistry clientAttachmentRegistry;
    private final ClientAttachmentAppService clientAttachmentAppService;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        AttachmentHandshake handshake = resolveHandshake(session);
        ClientAttachment attachment = clientAttachmentRegistry.attach(
                session.getId(),
                handshake.clientId(),
                handshake.observedTargetType(),
                handshake.observedTargetId(),
                session.getHandshakeHeaders().getFirst("User-Agent"),
                resolveRemoteAddress(session)
        );
        clientAttachmentAppService.recordAttach(attachment);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        ClientAttachment attachment = clientAttachmentRegistry.detach(session.getId());
        if (attachment != null) {
            clientAttachmentAppService.recordDetach(attachment);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        ClientAttachment attachment = clientAttachmentRegistry.detach(session.getId());
        if (attachment != null) {
            clientAttachmentAppService.recordDetach(attachment);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientHeartbeatSignal signal = parseHeartbeat(message.getPayload());
        if (signal == null) {
            return;
        }
        ClientAttachment previousAttachment = clientAttachmentRegistry.get(session.getId());
        ClientAttachment nextAttachment = clientAttachmentRegistry.heartbeat(
                session.getId(),
                signal.observedTargetType(),
                signal.observedTargetId()
        );
        clientAttachmentAppService.recordObserve(previousAttachment, nextAttachment);
    }

    public void broadcast(String payload) {
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                ClientAttachment attachment = clientAttachmentRegistry.detach(session.getId());
                if (attachment != null) {
                    clientAttachmentAppService.recordDetach(attachment);
                }
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException exception) {
                sessions.remove(session);
                ClientAttachment attachment = clientAttachmentRegistry.detach(session.getId());
                if (attachment != null) {
                    clientAttachmentAppService.recordDetach(attachment);
                }
            }
        }
    }

    private ClientHeartbeatSignal parseHeartbeat(String payload) {
        try {
            ClientHeartbeatSignal signal = objectMapper.readValue(payload, ClientHeartbeatSignal.class);
            if (!"client.heartbeat".equalsIgnoreCase(signal.type())) {
                return null;
            }
            return signal;
        } catch (IOException exception) {
            return null;
        }
    }

    private AttachmentHandshake resolveHandshake(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return new AttachmentHandshake("cli_" + compactUuid(), null, null);
        }
        Map<String, String> queryParams = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .toSingleValueMap();
        String clientId = hasText(queryParams.get("clientId"))
                ? queryParams.get("clientId").trim()
                : "cli_" + compactUuid();
        String observedTargetId = hasText(queryParams.get("targetId"))
                ? queryParams.get("targetId").trim()
                : null;
        String observedTargetType = observedTargetId == null
                ? null
                : (hasText(queryParams.get("targetType")) ? queryParams.get("targetType").trim() : "session");
        return new AttachmentHandshake(clientId, observedTargetType, observedTargetId);
    }

    private String resolveRemoteAddress(WebSocketSession session) {
        InetSocketAddress remoteAddress = session.getRemoteAddress();
        if (remoteAddress == null) {
            return null;
        }
        return remoteAddress.getHostString() + ":" + remoteAddress.getPort();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record AttachmentHandshake(String clientId, String observedTargetType, String observedTargetId) {
    }

    private record ClientHeartbeatSignal(String type, String observedTargetType, String observedTargetId) {
    }
}
