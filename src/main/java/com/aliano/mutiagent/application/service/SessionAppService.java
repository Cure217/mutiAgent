package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.CreateSessionRequest;
import com.aliano.mutiagent.application.dto.SessionSharedContextRef;
import com.aliano.mutiagent.application.dto.SessionTimelineItem;
import com.aliano.mutiagent.application.dto.SessionWorkspaceMeta;
import com.aliano.mutiagent.application.dto.UpdateSessionWorkspaceRequest;
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
import com.aliano.mutiagent.infrastructure.storage.SessionRawLogWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
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

    public List<MessageRecord> messagesAround(String sessionId, String messageId, int before, int after) {
        get(sessionId);
        if (!StringUtils.hasText(messageId)) {
            return List.of();
        }
        MessageRecord target = messageMapper.findById(sessionId, messageId);
        if (target == null) {
            throw new BusinessException("目标消息不存在");
        }
        return messageMapper.findWindowByMessageId(
                sessionId,
                messageId,
                Math.max(before, 0),
                Math.max(after, 0)
        );
    }

    public List<SessionTimelineItem> timeline(String sessionId, int limit) {
        AiSession session = get(sessionId);
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<SessionTimelineItem> items = new ArrayList<>();
        items.add(buildSessionEvent(
                session,
                "session-created",
                "created",
                "会话已创建",
                firstNonBlank(session.getProjectPath(), session.getTitle()),
                session.getCreatedAt()
        ));
        if (StringUtils.hasText(session.getStartedAt())) {
            items.add(buildSessionEvent(
                    session,
                    "session-started",
                    "started",
                    "会话已启动",
                    session.getPid() == null ? null : "PID: " + session.getPid(),
                    session.getStartedAt()
            ));
        }

        messageMapper.findLatestForTimeline(sessionId, safeLimit).stream()
                .sorted(Comparator.comparing(MessageRecord::getSeqNo))
                .map(this::buildMessageEvent)
                .forEach(items::add);

        if (StringUtils.hasText(session.getEndedAt())) {
            items.add(buildSessionEvent(
                    session,
                    "session-ended",
                    "ended",
                    "会话已结束",
                    firstNonBlank(session.getExitReason(), session.getStatus()),
                    session.getEndedAt()
            ));
        }

        return items.stream()
                .sorted(Comparator.comparing(SessionTimelineItem::getCreatedAt, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public String rawOutput(String sessionId) {
        AiSession session = get(sessionId);
        if (!StringUtils.hasText(session.getRawLogPath())) {
            return "";
        }
        try {
            Path rawLogPath = Path.of(session.getRawLogPath());
            Path replayPath = SessionRawLogWriter.resolveReplayPath(rawLogPath);
            if (Files.exists(replayPath)) {
                return Files.readString(replayPath);
            }
            if (!Files.exists(rawLogPath)) {
                return "";
            }
            return Files.readString(rawLogPath);
        } catch (IOException exception) {
            throw new BusinessException("读取原始日志失败", exception);
        }
    }

    public SessionWorkspaceMeta getWorkspaceMeta(String sessionId) {
        return readWorkspaceMeta(get(sessionId));
    }

    public AiSession createAndStart(CreateSessionRequest request) {
        AppInstance instance = appInstanceMapper.findById(request.appInstanceId());
        if (instance == null) {
            throw new BusinessException("应用实例不存在");
        }
        if (!Boolean.TRUE.equals(instance.getEnabled())) {
            throw new BusinessException("应用实例已禁用");
        }

        AIAdapter adapter = adapterRegistry.resolve(instance);
        String now = OffsetDateTime.now().toString();
        AiSession session = new AiSession();
        session.setId(idGenerator.next("ses"));
        session.setAppInstanceId(instance.getId());
        session.setTitle(StringUtils.hasText(request.title()) ? request.title() : instance.getName());
        session.setProjectPath(request.projectPath());
        session.setStatus(SessionStatus.STARTING.name());
        session.setInteractionMode(resolveInteractionMode(request.interactionMode(), adapter));
        SessionWorkspaceMeta workspaceMeta = normalizeWorkspaceMeta(request.workspaceMeta(), null, request.tags(), now);
        session.setTagsJson(writeJson(buildWorkspaceTags(request.tags(), workspaceMeta)));
        session.setExtraJson(writeJson(workspaceMeta));
        session.setSummary(firstNonBlank(workspaceMeta.getProgressSummary(), workspaceMeta.getBlockedReason()));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);

        Map<String, Object> createdPayload = new LinkedHashMap<>();
        createdPayload.put("status", session.getStatus());
        createdPayload.put("title", session.getTitle());
        sessionEventPublisher.publish("session.created", session.getId(), createdPayload);

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
            syncWorkspaceRuntimeMetadata(session, "running", null, "", startedAt, true);

            if (StringUtils.hasText(request.initInput()) && adapter.supportsInteractiveInput()) {
                sendInput(session.getId(), request.initInput(), true, true);
            }
            return get(session.getId());
        } catch (IOException exception) {
            String failedAt = OffsetDateTime.now().toString();
            String exitReason = trimToNull(exception.getMessage());
            sessionMapper.updateStatus(session.getId(), SessionStatus.FAILED.name(), failedAt, -1, exitReason, failedAt);
            Map<String, Object> failedPayload = new LinkedHashMap<>();
            failedPayload.put("status", SessionStatus.FAILED.name());
            failedPayload.put("exitCode", -1);
            failedPayload.put("exitReason", exitReason);
            failedPayload.put("endedAt", failedAt);
            sessionEventPublisher.publish("session.status.changed", session.getId(), failedPayload);
            syncFailedWorkspaceMetadata(session, exitReason, failedAt, true);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", exitReason);
            sessionEventPublisher.publish("session.error", session.getId(), payload);
            throw new BusinessException("启动会话失败: " + exitReason, exception);
        }
    }

    public SessionWorkspaceMeta updateWorkspaceMeta(String sessionId, UpdateSessionWorkspaceRequest request) {
        AiSession session = get(sessionId);
        SessionWorkspaceMeta current = readWorkspaceMeta(session);
        SessionWorkspaceMeta patch = new SessionWorkspaceMeta();
        patch.setWorkspaceKind(request.workspaceKind());
        patch.setRole(request.role());
        patch.setCoordinationStatus(request.coordinationStatus());
        patch.setProgressSummary(request.progressSummary());
        patch.setBlockedReason(request.blockedReason());
        patch.setSharedContextSummary(request.sharedContextSummary());
        patch.setSharedContextRefs(request.sharedContextRefs());
        patch.setTaskScope(request.taskScope());
        patch.setAcceptanceCriteria(request.acceptanceCriteria());
        patch.setDeliverableSpec(request.deliverableSpec());
        patch.setSharedContextMode(request.sharedContextMode());
        patch.setSharedContextLimit(request.sharedContextLimit());
        if (request.dependencySessionIds() != null) {
            patch.setDependencySessionIds(request.dependencySessionIds());
        }

        String now = OffsetDateTime.now().toString();
        return syncWorkspaceMetadata(session, patch, now, true);
    }

    public void sendInput(String sessionId, String content, boolean appendNewLine, boolean recordInput) {
        AiSession session = get(sessionId);
        if (!SessionStatus.RUNNING.name().equals(session.getStatus())) {
            throw new BusinessException("当前会话状态为 " + session.getStatus() + "，不可输入");
        }
        try {
            processSupervisor.sendInput(sessionId, content, appendNewLine);
            if (recordInput) {
                sessionStreamAppService.recordUserInput(sessionId, content, "manual-input");
            }
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
        sessionStreamAppService.markStopRequested(sessionId, stopMode);
        try {
            processSupervisor.stop(sessionId, stopMode);
        } catch (RuntimeException exception) {
            sessionStreamAppService.cancelStopRequested(sessionId);
            throw exception;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", SessionStatus.STOPPING.name());
        sessionEventPublisher.publish("session.status.changed", sessionId, payload);
    }

    public void resizeTerminal(String sessionId, int cols, int rows) {
        AiSession session = get(sessionId);
        if (!SessionStatus.RUNNING.name().equals(session.getStatus())) {
            return;
        }
        processSupervisor.resizeTerminal(sessionId, cols, rows);
    }

    private String resolveInteractionMode(String interactionMode, AIAdapter adapter) {
        if (!StringUtils.hasText(interactionMode)) {
            return InteractionMode.RAW.name();
        }
        String normalized = interactionMode.trim().toUpperCase();
        if (InteractionMode.STRUCTURED.name().equals(normalized) && !adapter.supportsStructuredMessage()) {
            throw new BusinessException("当前应用实例暂不支持 STRUCTURED 模式，请改用 RAW 终端协作模式");
        }
        return normalized;
    }

    private void syncFailedWorkspaceMetadata(AiSession session, String exitReason, String updatedAt, boolean publishEvent) {
        SessionWorkspaceMeta current = readWorkspaceMeta(session);
        SessionWorkspaceMeta patch = new SessionWorkspaceMeta();
        patch.setWorkspaceKind(current.getWorkspaceKind());
        patch.setRole(current.getRole());
        patch.setCoordinationStatus("blocked");
        patch.setProgressSummary(firstNonBlank(trimToNull(exitReason), current.getProgressSummary()));
        patch.setBlockedReason(exitReason);
        patch.setSharedContextSummary(current.getSharedContextSummary());
        patch.setSharedContextRefs(current.getSharedContextRefs());
        patch.setTaskScope(current.getTaskScope());
        patch.setAcceptanceCriteria(current.getAcceptanceCriteria());
        patch.setDeliverableSpec(current.getDeliverableSpec());
        patch.setSharedContextMode(current.getSharedContextMode());
        patch.setSharedContextLimit(current.getSharedContextLimit());
        patch.setDependencySessionIds(current.getDependencySessionIds());
        patch.setUpdatedAt(updatedAt);
        syncWorkspaceMetadata(session, patch, updatedAt, publishEvent);
    }

    private void syncWorkspaceRuntimeMetadata(AiSession session,
                                              String coordinationStatus,
                                              String progressSummary,
                                              String blockedReason,
                                              String updatedAt,
                                              boolean publishEvent) {
        SessionWorkspaceMeta current = readWorkspaceMeta(session);
        SessionWorkspaceMeta patch = new SessionWorkspaceMeta();
        patch.setWorkspaceKind(current.getWorkspaceKind());
        patch.setRole(current.getRole());
        patch.setCoordinationStatus(coordinationStatus);
        patch.setProgressSummary(progressSummary);
        patch.setBlockedReason(blockedReason);
        patch.setSharedContextSummary(current.getSharedContextSummary());
        patch.setSharedContextRefs(current.getSharedContextRefs());
        patch.setTaskScope(current.getTaskScope());
        patch.setAcceptanceCriteria(current.getAcceptanceCriteria());
        patch.setDeliverableSpec(current.getDeliverableSpec());
        patch.setSharedContextMode(current.getSharedContextMode());
        patch.setSharedContextLimit(current.getSharedContextLimit());
        patch.setDependencySessionIds(current.getDependencySessionIds());
        patch.setUpdatedAt(updatedAt);
        syncWorkspaceMetadata(session, patch, updatedAt, publishEvent);
    }

    private SessionWorkspaceMeta syncWorkspaceMetadata(AiSession session,
                                                       SessionWorkspaceMeta patch,
                                                       String updatedAt,
                                                       boolean publishEvent) {
        SessionWorkspaceMeta current = readWorkspaceMeta(session);
        SessionWorkspaceMeta merged = normalizeWorkspaceMeta(patch, current, parseTags(session.getTagsJson()), updatedAt);
        String tagsJson = writeJson(buildWorkspaceTags(parseTags(session.getTagsJson()), merged));
        String extraJson = writeJson(merged);
        String summary = resolveWorkspaceSummary(session, merged, patch);
        sessionMapper.updateWorkspaceMetadata(session.getId(), summary, tagsJson, extraJson, updatedAt);
        if (publishEvent) {
            publishWorkspaceUpdated(session.getId(), summary, tagsJson, extraJson, merged);
        }
        return merged;
    }

    private SessionWorkspaceMeta readWorkspaceMeta(AiSession session) {
        SessionWorkspaceMeta current = readJson(session.getExtraJson(), new ParameterizedTypeReference<>() {
        });
        return normalizeWorkspaceMeta(current, null, parseTags(session.getTagsJson()), session.getUpdatedAt());
    }

    private SessionWorkspaceMeta normalizeWorkspaceMeta(SessionWorkspaceMeta incoming,
                                                        SessionWorkspaceMeta current,
                                                        List<String> tags,
                                                        String updatedAt) {
        List<String> safeTags = tags == null ? List.of() : tags;
        SessionWorkspaceMeta meta = new SessionWorkspaceMeta();
        meta.setWorkspaceKind(firstNonBlank(
                trimToNull(incoming == null ? null : incoming.getWorkspaceKind()),
                trimToNull(current == null ? null : current.getWorkspaceKind()),
                safeTags.contains("workspace:child") ? "child" : null,
                "standard"
        ));
        meta.setRole(firstNonBlank(
                trimToNull(incoming == null ? null : incoming.getRole()),
                trimToNull(current == null ? null : current.getRole()),
                extractTagValue(safeTags, "role:"),
                "general"
        ));
        meta.setCoordinationStatus(firstNonBlank(
                trimToNull(incoming == null ? null : incoming.getCoordinationStatus()),
                trimToNull(current == null ? null : current.getCoordinationStatus()),
                extractTagValue(safeTags, "coordination:"),
                "assigned"
        ));
        meta.setProgressSummary(mergeNullableText(
                incoming == null ? null : incoming.getProgressSummary(),
                current == null ? null : current.getProgressSummary()
        ));
        meta.setBlockedReason(mergeNullableText(
                incoming == null ? null : incoming.getBlockedReason(),
                current == null ? null : current.getBlockedReason()
        ));
        meta.setSharedContextSummary(mergeNullableText(
                incoming == null ? null : incoming.getSharedContextSummary(),
                current == null ? null : current.getSharedContextSummary()
        ));
        meta.setSharedContextRefs(resolveSharedContextRefs(incoming, current));
        meta.setTaskScope(mergeNullableText(
                incoming == null ? null : incoming.getTaskScope(),
                current == null ? null : current.getTaskScope()
        ));
        meta.setAcceptanceCriteria(mergeNullableText(
                incoming == null ? null : incoming.getAcceptanceCriteria(),
                current == null ? null : current.getAcceptanceCriteria()
        ));
        meta.setDeliverableSpec(mergeNullableText(
                incoming == null ? null : incoming.getDeliverableSpec(),
                current == null ? null : current.getDeliverableSpec()
        ));
        meta.setSharedContextMode(mergeNullableText(
                incoming == null ? null : incoming.getSharedContextMode(),
                current == null ? null : current.getSharedContextMode()
        ));
        meta.setSharedContextLimit(resolveNullableInteger(
                incoming == null ? null : incoming.getSharedContextLimit(),
                current == null ? null : current.getSharedContextLimit()
        ));
        meta.setDependencySessionIds(resolveDependencyIds(incoming, current, safeTags));
        meta.setUpdatedAt(firstNonBlank(
                trimToNull(incoming == null ? null : incoming.getUpdatedAt()),
                trimToNull(updatedAt),
                OffsetDateTime.now().toString()
        ));
        return meta;
    }

    private List<String> resolveDependencyIds(SessionWorkspaceMeta incoming,
                                              SessionWorkspaceMeta current,
                                              List<String> tags) {
        if (incoming != null && incoming.getDependencySessionIds() != null) {
            return incoming.getDependencySessionIds().stream()
                    .map(this::trimToNull)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        if (current != null && current.getDependencySessionIds() != null && !current.getDependencySessionIds().isEmpty()) {
            return current.getDependencySessionIds().stream()
                    .map(this::trimToNull)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        return tags.stream()
                .filter(tag -> tag.startsWith("depends:"))
                .map(tag -> trimToNull(tag.substring("depends:".length())))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<SessionSharedContextRef> resolveSharedContextRefs(SessionWorkspaceMeta incoming,
                                                                   SessionWorkspaceMeta current) {
        if (incoming != null && incoming.getSharedContextRefs() != null) {
            return normalizeSharedContextRefs(incoming.getSharedContextRefs());
        }
        if (current != null && current.getSharedContextRefs() != null && !current.getSharedContextRefs().isEmpty()) {
            return normalizeSharedContextRefs(current.getSharedContextRefs());
        }
        return List.of();
    }

    private List<SessionSharedContextRef> normalizeSharedContextRefs(List<SessionSharedContextRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<SessionSharedContextRef> normalized = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (SessionSharedContextRef ref : refs) {
            SessionSharedContextRef candidate = normalizeSharedContextRef(ref);
            if (candidate == null || !seen.add(candidate.getSessionId())) {
                continue;
            }
            normalized.add(candidate);
        }
        return List.copyOf(normalized);
    }

    private SessionSharedContextRef normalizeSharedContextRef(SessionSharedContextRef ref) {
        if (ref == null) {
            return null;
        }
        String sessionId = trimToNull(ref.getSessionId());
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        SessionSharedContextRef normalized = new SessionSharedContextRef();
        normalized.setSessionId(sessionId);
        normalized.setRoleKey(trimToNull(ref.getRoleKey()));
        normalized.setRoleLabel(trimToNull(ref.getRoleLabel()));
        normalized.setTitle(trimToNull(ref.getTitle()));
        normalized.setCoordinationState(trimToNull(ref.getCoordinationState()));
        normalized.setCoordinationLabel(trimToNull(ref.getCoordinationLabel()));
        normalized.setProgressHint(trimToNull(ref.getProgressHint()));
        normalized.setIncludedReason(trimToNull(ref.getIncludedReason()));
        normalized.setLastActiveAt(trimToNull(ref.getLastActiveAt()));
        normalized.setLastActiveText(trimToNull(ref.getLastActiveText()));
        return normalized;
    }

    private List<String> buildWorkspaceTags(List<String> originalTags, SessionWorkspaceMeta workspaceMeta) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        List<String> safeOriginalTags = originalTags == null ? List.of() : originalTags;
        safeOriginalTags.stream()
                .map(this::trimToNull)
                .filter(StringUtils::hasText)
                .filter(tag -> !tag.startsWith("workspace:"))
                .filter(tag -> !tag.startsWith("role:"))
                .filter(tag -> !tag.startsWith("depends:"))
                .filter(tag -> !tag.startsWith("coordination:"))
                .forEach(tags::add);

        if ("child".equalsIgnoreCase(workspaceMeta.getWorkspaceKind())) {
            tags.add("workspace:child");
        }
        if (StringUtils.hasText(workspaceMeta.getRole())) {
            tags.add("role:" + workspaceMeta.getRole());
        }
        if (StringUtils.hasText(workspaceMeta.getCoordinationStatus())) {
            tags.add("coordination:" + workspaceMeta.getCoordinationStatus());
        }
        if (workspaceMeta.getDependencySessionIds() != null) {
            workspaceMeta.getDependencySessionIds().stream()
                    .map(this::trimToNull)
                    .filter(StringUtils::hasText)
                    .forEach(dependencyId -> tags.add("depends:" + dependencyId));
        }
        return List.copyOf(tags);
    }

    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        List<String> tags = readJson(tagsJson, new ParameterizedTypeReference<>() {
        });
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(this::trimToNull)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String extractTagValue(List<String> tags, String prefix) {
        return tags.stream()
                .filter(tag -> tag.startsWith(prefix))
                .findFirst()
                .map(tag -> trimToNull(tag.substring(prefix.length())))
                .orElse(null);
    }

    private String resolveWorkspaceSummary(AiSession session, SessionWorkspaceMeta merged, SessionWorkspaceMeta patch) {
        if (StringUtils.hasText(merged.getProgressSummary())) {
            return merged.getProgressSummary();
        }
        if (StringUtils.hasText(merged.getBlockedReason())) {
            return merged.getBlockedReason();
        }
        if (hasExplicitSummaryUpdate(patch)) {
            return null;
        }
        return trimToNull(session.getSummary());
    }

    private boolean hasExplicitSummaryUpdate(SessionWorkspaceMeta patch) {
        return patch != null
                && (patch.getProgressSummary() != null || patch.getBlockedReason() != null);
    }

    private String mergeNullableText(String incomingValue, String currentValue) {
        if (incomingValue != null) {
            return trimToNull(incomingValue);
        }
        return trimToNull(currentValue);
    }

    private Integer resolveNullableInteger(Integer incomingValue, Integer currentValue) {
        return incomingValue != null ? incomingValue : currentValue;
    }

    private void publishWorkspaceUpdated(String sessionId,
                                         String summary,
                                         String tagsJson,
                                         String extraJson,
                                         SessionWorkspaceMeta workspaceMeta) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("tagsJson", tagsJson);
        payload.put("extraJson", extraJson);
        payload.put("workspaceMeta", workspaceMeta);
        sessionEventPublisher.publish("session.workspace.updated", sessionId, payload);
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

    private <T> T readJson(String value, ParameterizedTypeReference<T> typeReference) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructType(typeReference.getType()));
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private SessionTimelineItem buildSessionEvent(AiSession session,
                                                  String itemId,
                                                  String eventType,
                                                  String title,
                                                  String content,
                                                  String createdAt) {
        SessionTimelineItem item = new SessionTimelineItem();
        item.setItemId(itemId);
        item.setSessionId(session.getId());
        item.setItemType("session-event");
        item.setEventType(eventType);
        item.setTitle(title);
        item.setContent(content);
        item.setCreatedAt(createdAt);
        return item;
    }

    private SessionTimelineItem buildMessageEvent(MessageRecord message) {
        SessionTimelineItem item = new SessionTimelineItem();
        item.setItemId("message-" + message.getId());
        item.setSessionId(message.getSessionId());
        item.setMessageId(message.getId());
        item.setSeqNo(message.getSeqNo());
        item.setItemType("message");
        item.setEventType("message");
        item.setTitle("消息 #" + message.getSeqNo());
        item.setRole(message.getRole());
        item.setMessageType(message.getMessageType());
        item.setContent(firstNonBlank(message.getContentText(), message.getRawChunk()));
        item.setCreatedAt(message.getCreatedAt());
        return item;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
