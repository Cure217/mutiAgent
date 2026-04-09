package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.CreateSessionRequest;
import com.aliano.mutiagent.common.exception.BusinessException;
import com.aliano.mutiagent.common.model.PageResponse;
import com.aliano.mutiagent.common.util.IdGenerator;
import com.aliano.mutiagent.domain.instance.AppInstance;
import com.aliano.mutiagent.domain.message.MessageRecord;
import com.aliano.mutiagent.domain.session.AiSession;
import com.aliano.mutiagent.domain.session.InteractionMode;
import com.aliano.mutiagent.domain.session.SessionStatus;
import com.aliano.mutiagent.infrastructure.adapter.AIAdapter;
import com.aliano.mutiagent.infrastructure.adapter.AdapterRegistry;
import com.aliano.mutiagent.infrastructure.event.SessionEventPublisher;
import com.aliano.mutiagent.infrastructure.persistence.mapper.AppInstanceMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.MessageMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.SessionMapper;
import com.aliano.mutiagent.infrastructure.process.ProcessRuntime;
import com.aliano.mutiagent.infrastructure.process.ProcessSupervisor;
import com.aliano.mutiagent.infrastructure.process.SessionLaunchContext;
import com.aliano.mutiagent.infrastructure.process.StopMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionAppService {

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final AppInstanceMapper appInstanceMapper;
    private final AdapterRegistry adapterRegistry;
    private final ProcessSupervisor processSupervisor;
    private final SessionEventPublisher sessionEventPublisher;
    private final SessionStreamAppService sessionStreamAppService;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public SessionAppService(SessionMapper sessionMapper,
                             MessageMapper messageMapper,
                             AppInstanceMapper appInstanceMapper,
                             AdapterRegistry adapterRegistry,
                             ProcessSupervisor processSupervisor,
                             SessionEventPublisher sessionEventPublisher,
                             SessionStreamAppService sessionStreamAppService,
                             IdGenerator idGenerator,
                             ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.appInstanceMapper = appInstanceMapper;
        this.adapterRegistry = adapterRegistry;
        this.processSupervisor = processSupervisor;
        this.sessionEventPublisher = sessionEventPublisher;
        this.sessionStreamAppService = sessionStreamAppService;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResponse<AiSession> list(String appInstanceId, String status, String keyword, int pageNo, int pageSize) {
        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.max(pageSize, 1);
        int offset = (validPageNo - 1) * validPageSize;
        List<AiSession> items = sessionMapper.findPage(appInstanceId, status, keyword, validPageSize, offset);
        long total = sessionMapper.countPage(appInstanceId, status, keyword);
        return new PageResponse<>(items, validPageNo, validPageSize, total);
    }

    public AiSession get(String id) {
        AiSession session = sessionMapper.findById(id);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

    public List<AiSession> runningSessions() {
        return sessionMapper.findRunning();
    }

    public PageResponse<MessageRecord> messages(String sessionId, int pageNo, int pageSize) {
        get(sessionId);
        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.max(pageSize, 1);
        int offset = (validPageNo - 1) * validPageSize;
        List<MessageRecord> items = messageMapper.findPage(sessionId, validPageSize, offset);
        long total = messageMapper.countBySessionId(sessionId);
        return new PageResponse<>(items, validPageNo, validPageSize, total);
    }

    public AiSession createAndStart(CreateSessionRequest request) {
        AppInstance instance = appInstanceMapper.findById(request.appInstanceId());
        if (instance == null) {
            throw new BusinessException("应用实例不存在");
        }
        if (!Boolean.TRUE.equals(instance.getEnabled())) {
            throw new BusinessException("应用实例已禁用");
        }

        String now = OffsetDateTime.now().toString();
        AiSession session = new AiSession();
        session.setId(idGenerator.next("ses"));
        session.setAppInstanceId(instance.getId());
        session.setTitle(StringUtils.hasText(request.title()) ? request.title() : instance.getName());
        session.setProjectPath(request.projectPath());
        session.setStatus(SessionStatus.STARTING.name());
        session.setInteractionMode(resolveInteractionMode(request.interactionMode()));
        session.setTagsJson(writeJson(request.tags()));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);

        Map<String, Object> createdPayload = new LinkedHashMap<>();
        createdPayload.put("status", session.getStatus());
        createdPayload.put("title", session.getTitle());
        sessionEventPublisher.publish("session.created", session.getId(), createdPayload);

        AIAdapter adapter = adapterRegistry.resolve(instance);
        try {
            ProcessRuntime runtime = processSupervisor.start(new SessionLaunchContext(session, instance, adapter));
            String startedAt = OffsetDateTime.now().toString();
            sessionMapper.updateRuntime(
                    session.getId(),
                    SessionStatus.RUNNING.name(),
                    runtime.pid(),
                    startedAt,
                    runtime.rawLogPath(),
                    startedAt
            );
            appInstanceMapper.updateLastStartAt(instance.getId(), startedAt, startedAt);

            Map<String, Object> runningPayload = new LinkedHashMap<>();
            runningPayload.put("status", SessionStatus.RUNNING.name());
            runningPayload.put("pid", runtime.pid());
            runningPayload.put("rawLogPath", runtime.rawLogPath());
            sessionEventPublisher.publish("session.status.changed", session.getId(), runningPayload);

            if (StringUtils.hasText(request.initInput()) && adapter.supportsInteractiveInput()) {
                sendInput(session.getId(), request.initInput(), true);
            }
            return get(session.getId());
        } catch (IOException exception) {
            String failedAt = OffsetDateTime.now().toString();
            sessionMapper.updateStatus(session.getId(), SessionStatus.FAILED.name(), failedAt, -1, exception.getMessage(), failedAt);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", exception.getMessage());
            sessionEventPublisher.publish("session.error", session.getId(), payload);
            throw new BusinessException("启动会话失败: " + exception.getMessage(), exception);
        }
    }

    public void sendInput(String sessionId, String content, boolean appendNewLine) {
        AiSession session = get(sessionId);
        if (!SessionStatus.RUNNING.name().equals(session.getStatus())) {
            throw new BusinessException("当前会话不可输入");
        }
        try {
            processSupervisor.sendInput(sessionId, content, appendNewLine);
            sessionStreamAppService.recordUserInput(sessionId, content, "manual-input");
        } catch (IOException exception) {
            throw new BusinessException("发送输入失败", exception);
        }
    }

    public void stopSession(String sessionId, StopMode stopMode) {
        AiSession session = get(sessionId);
        if (SessionStatus.COMPLETED.name().equals(session.getStatus()) || SessionStatus.FAILED.name().equals(session.getStatus())) {
            return;
        }
        String now = OffsetDateTime.now().toString();
        sessionMapper.updateStatusOnly(sessionId, SessionStatus.STOPPING.name(), now);
        processSupervisor.stop(sessionId, stopMode);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", SessionStatus.STOPPING.name());
        sessionEventPublisher.publish("session.status.changed", sessionId, payload);
    }

    private String resolveInteractionMode(String interactionMode) {
        if (!StringUtils.hasText(interactionMode)) {
            return InteractionMode.RAW.name();
        }
        return interactionMode.toUpperCase();
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("会话标签序列化失败", exception);
        }
    }
}
