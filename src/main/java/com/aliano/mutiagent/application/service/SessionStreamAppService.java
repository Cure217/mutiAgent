package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.common.util.IdGenerator;
import com.aliano.mutiagent.domain.message.MessageRecord;
import com.aliano.mutiagent.infrastructure.adapter.ParseResult;
import com.aliano.mutiagent.infrastructure.adapter.ParsedMessage;
import com.aliano.mutiagent.infrastructure.event.SessionEventPublisher;
import com.aliano.mutiagent.infrastructure.persistence.mapper.MessageMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.SessionMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionStreamAppService {

    private final MessageMapper messageMapper;
    private final SessionMapper sessionMapper;
    private final SessionEventPublisher sessionEventPublisher;
    private final IdGenerator idGenerator;
    private final ConcurrentMap<String, Object> messageLocks = new ConcurrentHashMap<>();

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
        String status = exitCode == 0 ? "COMPLETED" : "FAILED";
        String exitReason = resolveExitReason(sessionId, exitCode);
        sessionMapper.updateStatus(sessionId, status, now, exitCode, exitReason, now);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("exitCode", exitCode);
        payload.put("exitReason", exitReason);
        payload.put("endedAt", now);
        sessionEventPublisher.publish("session.closed", sessionId, payload);
    }

    public void handleSupervisorError(String sessionId, String errorMessage) {
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

    private String resolveExitReason(String sessionId, int exitCode) {
        if (exitCode == 0) {
            return "进程正常退出";
        }
        String text = messageMapper.findLatestForTimeline(sessionId, 5)
                .stream()
                .map(message -> (message.getContentText() == null ? "" : message.getContentText())
                        + "\n"
                        + (message.getRawChunk() == null ? "" : message.getRawChunk()))
                .reduce("", (left, right) -> left + "\n" + right);
        if (!org.springframework.util.StringUtils.hasText(text)) {
            return "进程退出";
        }
        if (text.contains("wsl.exe [参数]")
                || text.contains("适用于 Linux 的 Windows 子系统功能")
                || text.contains("wsl --install")) {
            return "WSL 未安装或未配置默认发行版，无法启动该会话";
        }
        return "进程退出";
    }
}
