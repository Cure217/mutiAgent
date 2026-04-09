package com.aliano.mutiagent.infrastructure.event;

public record SessionEvent(String event, String sessionId, String timestamp, Object payload) {
}
