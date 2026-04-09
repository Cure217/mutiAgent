package com.aliano.mutiagent.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class SessionEventPublisher {

    private final SessionWebSocketHandler sessionWebSocketHandler;
    private final ObjectMapper objectMapper;

    public SessionEventPublisher(SessionWebSocketHandler sessionWebSocketHandler, ObjectMapper objectMapper) {
        this.sessionWebSocketHandler = sessionWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    public void publish(String event, String sessionId, Object payload) {
        try {
            SessionEvent sessionEvent = new SessionEvent(event, sessionId, OffsetDateTime.now().toString(), payload);
            sessionWebSocketHandler.broadcast(objectMapper.writeValueAsString(sessionEvent));
        } catch (JsonProcessingException ignored) {
        }
    }
}
