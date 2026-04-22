package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.SessionWorkspaceMeta;
import com.aliano.mutiagent.common.util.IdGenerator;
import com.aliano.mutiagent.domain.message.MessageRecord;
import com.aliano.mutiagent.domain.session.AiSession;
import com.aliano.mutiagent.infrastructure.adapter.ParseResult;
import com.aliano.mutiagent.infrastructure.adapter.ParsedMessage;
import com.aliano.mutiagent.infrastructure.event.SessionEventPublisher;
import com.aliano.mutiagent.infrastructure.persistence.mapper.MessageMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.SessionMapper;
import com.aliano.mutiagent.infrastructure.process.StopMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SessionStreamAppService {

    private final MessageMapper messageMapper;
    private final SessionMapper sessionMapper;
    private final SessionEventPublisher sessionEventPublisher;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final RuntimeDiagnosticsTracker runtimeDiagnosticsTracker;
    private final ConcurrentMap<String, Object> messageLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StopMode> stopRequests = new ConcurrentHashMap<>();

    public void markStopRequested(String sessionId, StopMode stopMode) {
        stopRequests.put(sessionId, stopMode == null ? StopMode.GRACEFUL : stopMode);
    }

    public void cancelStopRequested(String sessionId) {
        stopRequests.remove(sessionId);
    }

    public void recordUserInput(String sessionId, String content, String sourceAdapter) {
        appendMessage(sessionId, "user", "text", content, null, null, true, sourceAdapter);
    }

    public void handleProcessOutput(String sessionId,
                                    String sourceAdapter,
                                    String streamName,
                                    String chunk,
                                    ParseResult parseResult) {
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("stream", streamName);
        rawPayload.put("chunk", chunk);
        sessionEventPublisher.publish("session.output.raw", sessionId, rawPayload);

        if (parseResult != null && parseResult.messages() != null) {
            for (ParsedMessage message : parseResult.messages()) {
                appendMessage(
                        sessionId,
                        message.role(),
                        message.messageType(),
                        message.contentText(),
                        message.contentJson(),
                        chunk,
                        message.structured(),
                        sourceAdapter
                );
            }
        }
    }

    public void handleProcessExit(String sessionId, int exitCode) {
        String now = OffsetDateTime.now().toString();
        StopMode requestedStopMode = stopRequests.remove(sessionId);
        boolean stoppedByUser = requestedStopMode != null;
        String status = stoppedByUser || exitCode == 0 ? "COMPLETED" : "FAILED";
        String exitReason = resolveExitReason(sessionId, exitCode, requestedStopMode);
        sessionMapper.updateStatus(sessionId, status, now, exitCode, exitReason, now);
        syncWorkspaceStateAfterExit(sessionId, status, exitReason, now, stoppedByUser);
        runtimeDiagnosticsTracker.recordProcessExit(sessionId, status, exitCode, exitReason, now);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("exitCode", exitCode);
        payload.put("exitReason", exitReason);
        payload.put("endedAt", now);
        sessionEventPublisher.publish("session.closed", sessionId, payload);
    }

    public void handleSupervisorError(String sessionId, String errorMessage) {
        runtimeDiagnosticsTracker.recordSupervisorError(sessionId, errorMessage);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", errorMessage);
        sessionEventPublisher.publish("session.error", sessionId, payload);
    }

    private void appendMessage(String sessionId,
                               String role,
                               String messageType,
                               String contentText,
                               String contentJson,
                               String rawChunk,
                               boolean structured,
                               String sourceAdapter) {
        Object lock = messageLocks.computeIfAbsent(sessionId, key -> new Object());
        String now = OffsetDateTime.now().toString();
        MessageRecord record = new MessageRecord();
        synchronized (lock) {
            record.setId(idGenerator.next("msg"));
            record.setSessionId(sessionId);
            record.setSeqNo(messageMapper.nextSeqNo(sessionId));
            record.setRole(role);
            record.setMessageType(messageType);
            record.setContentText(contentText);
            record.setContentJson(contentJson);
            record.setRawChunk(rawChunk);
            record.setIsStructured(structured);
            record.setSourceAdapter(sourceAdapter);
            record.setCreatedAt(now);
            messageMapper.insert(record);
            messageMapper.syncFtsByMessageId(record.getId());
        }

        sessionMapper.touchLastMessage(sessionId, now, now);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", record.getId());
        payload.put("seqNo", record.getSeqNo());
        payload.put("role", record.getRole());
        payload.put("messageType", record.getMessageType());
        payload.put("contentText", record.getContentText());
        payload.put("isStructured", record.getIsStructured());
        payload.put("createdAt", record.getCreatedAt());
        sessionEventPublisher.publish("session.message.created", sessionId, payload);
    }

    private String resolveExitReason(String sessionId, int exitCode, StopMode requestedStopMode) {
        if (requestedStopMode != null) {
            return requestedStopMode == StopMode.FORCE ? "已强制停止" : "已手动停止";
        }
        if (exitCode == 0) {
            return "进程正常退出";
        }

        String text = messageMapper.findLatestForTimeline(sessionId, 5)
                .stream()
                .map(message -> (message.getContentText() == null ? "" : message.getContentText())
                        + "\n"
                        + (message.getRawChunk() == null ? "" : message.getRawChunk()))
                .reduce("", (left, right) -> left + "\n" + right);
        if (!StringUtils.hasText(text)) {
            return "进程退出";
        }

        String normalizedText = text.toLowerCase();
        if (normalizedText.contains("stdin is not a terminal")) {
            return "当前实例需要终端 TTY，不能以 STRUCTURED 或非终端方式启动，请改用 RAW 模式";
        }
        if (text.contains("wsl.exe [参数]")
                || text.contains("适用于 Linux 的 Windows 子系统功能")
                || text.contains("wsl --install")) {
            return "WSL 未安装或未配置默认发行版，无法启动该会话";
        }
        return "进程退出";
    }

    private void syncWorkspaceStateAfterExit(String sessionId,
                                             String status,
                                             String exitReason,
                                             String updatedAt,
                                             boolean stoppedByUser) {
        AiSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            return;
        }

        SessionWorkspaceMeta workspaceMeta = readWorkspaceMeta(session.getExtraJson());
        boolean failed = "FAILED".equals(status);
        workspaceMeta.setCoordinationStatus(failed ? "blocked" : (stoppedByUser ? "closed" : "completed"));
        workspaceMeta.setBlockedReason(failed ? trimToNull(exitReason) : null);
        workspaceMeta.setProgressSummary(failed
                ? firstNonBlank(trimToNull(exitReason), workspaceMeta.getProgressSummary(), session.getSummary())
                : firstNonBlank(workspaceMeta.getProgressSummary(), session.getSummary(), trimToNull(exitReason)));
        workspaceMeta.setUpdatedAt(updatedAt);

        List<String> tags = replaceCoordinationTag(parseTags(session.getTagsJson()), workspaceMeta.getCoordinationStatus());
        String tagsJson = writeJson(tags);
        String extraJson = writeJson(workspaceMeta);
        String summary = failed
                ? firstNonBlank(trimToNull(exitReason), workspaceMeta.getProgressSummary(), session.getSummary())
                : firstNonBlank(workspaceMeta.getProgressSummary(), session.getSummary(), trimToNull(exitReason));
        sessionMapper.updateWorkspaceMetadata(sessionId, summary, tagsJson, extraJson, updatedAt);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("tagsJson", tagsJson);
        payload.put("extraJson", extraJson);
        payload.put("workspaceMeta", workspaceMeta);
        sessionEventPublisher.publish("session.workspace.updated", sessionId, payload);
    }

    private SessionWorkspaceMeta readWorkspaceMeta(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return new SessionWorkspaceMeta();
        }
        try {
            SessionWorkspaceMeta parsed = objectMapper.readValue(extraJson, SessionWorkspaceMeta.class);
            return parsed == null ? new SessionWorkspaceMeta() : parsed;
        } catch (JsonProcessingException ignored) {
            return new SessionWorkspaceMeta();
        }
    }

    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(tagsJson);
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private List<String> replaceCoordinationTag(List<String> tags, String coordinationStatus) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags == null ? List.<String>of() : tags) {
            if (!StringUtils.hasText(tag) || tag.startsWith("coordination:")) {
                continue;
            }
            normalized.add(tag.trim());
        }
        if (StringUtils.hasText(coordinationStatus)) {
            normalized.add("coordination:" + coordinationStatus);
        }
        return new ArrayList<>(normalized);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("写入会话元信息失败", exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
