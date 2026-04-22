package com.aliano.mutiagent.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeDiagnosticsTracker {

    private static final int MAX_RECENT_EVENTS = 12;

    private final Instant startedInstant = Instant.now();
    private final String startedAt = OffsetDateTime.now().toString();
    private final Deque<Map<String, Object>> recentLifecycleEvents = new ArrayDeque<>();

    private volatile String readyAt;

    @EventListener(ApplicationReadyEvent.class)
    public void markReady() {
        readyAt = OffsetDateTime.now().toString();
    }

    public String startedAt() {
        return startedAt;
    }

    public String readyAt() {
        return readyAt;
    }

    public long uptimeMs() {
        return Duration.between(startedInstant, Instant.now()).toMillis();
    }

    public synchronized List<Map<String, Object>> recentLifecycleEvents() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> event : recentLifecycleEvents) {
            result.add(new LinkedHashMap<>(event));
        }
        return result;
    }

    public synchronized void recordSupervisorError(String sessionId, String errorMessage) {
        LinkedHashMap<String, Object> event = new LinkedHashMap<>();
        event.put("type", "supervisor_error");
        event.put("observedAt", OffsetDateTime.now().toString());
        event.put("sessionId", sessionId);
        event.put("message", trim(errorMessage, 300));
        push(event);
    }

    public synchronized void recordProcessExit(String sessionId,
                                               String status,
                                               int exitCode,
                                               String exitReason,
                                               String observedAt) {
        LinkedHashMap<String, Object> event = new LinkedHashMap<>();
        event.put("type", "process_exit");
        event.put("observedAt", StringUtils.hasText(observedAt) ? observedAt : OffsetDateTime.now().toString());
        event.put("sessionId", sessionId);
        event.put("status", status);
        event.put("exitCode", exitCode);
        event.put("exitReason", trim(exitReason, 300));
        push(event);
    }

    private void push(Map<String, Object> event) {
        recentLifecycleEvents.addFirst(new LinkedHashMap<>(event));
        while (recentLifecycleEvents.size() > MAX_RECENT_EVENTS) {
            recentLifecycleEvents.removeLast();
        }
    }

    private String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
