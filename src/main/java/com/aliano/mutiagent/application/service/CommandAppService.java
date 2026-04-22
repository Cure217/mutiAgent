package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.command.CommandEnvelope;
import com.aliano.mutiagent.application.command.CommandTypes;
import com.aliano.mutiagent.application.command.RuntimeTargetTypes;
import com.aliano.mutiagent.application.dto.CreateOperationLogRequest;
import com.aliano.mutiagent.application.dto.CreateSessionRequest;
import com.aliano.mutiagent.application.dto.SendInputRequest;
import com.aliano.mutiagent.application.dto.SessionWorkspaceMeta;
import com.aliano.mutiagent.application.dto.UpdateSessionWorkspaceRequest;
import com.aliano.mutiagent.application.dto.WorkspaceDispatchCreateCommandRequest;
import com.aliano.mutiagent.application.dto.WorkspaceDispatchExistingCommandRequest;
import com.aliano.mutiagent.application.dto.WorkspaceSummaryApplyCommandRequest;
import com.aliano.mutiagent.domain.session.AiSession;
import com.aliano.mutiagent.infrastructure.process.StopMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandAppService {

    private static final String ARCHITECT_WINDOW_OPERATOR = "architect-window";

    private final SessionAppService sessionAppService;
    private final OperationLogAppService operationLogAppService;

    public AiSession createSession(CreateSessionRequest request) {
        CommandEnvelope<CreateSessionRequest> command = new CommandEnvelope<>(
                CommandTypes.SESSION_CREATE_START,
                RuntimeTargetTypes.SESSION,
                null,
                null,
                null,
                request
        );
        try {
            AiSession session = sessionAppService.createAndStart(request);
            recordCommand(command.withTargetId(session.getId()), "success", createSessionDetail(request, session, null));
            return session;
        } catch (RuntimeException exception) {
            recordCommand(command, "failed", createSessionDetail(request, null, exception));
            throw exception;
        }
    }

    public void sendSessionInput(String sessionId, SendInputRequest request) {
        boolean appendNewLine = request.appendNewLine() == null || request.appendNewLine();
        boolean recordInput = request.recordInput() == null || request.recordInput();
        CommandEnvelope<SendInputRequest> command = new CommandEnvelope<>(
                CommandTypes.SESSION_INPUT_SEND,
                RuntimeTargetTypes.SESSION,
                sessionId,
                null,
                null,
                request
        );
        try {
            sessionAppService.sendInput(sessionId, request.content(), appendNewLine, recordInput);
            if (recordInput) {
                recordCommand(command, "success", inputDetail(request, null));
            }
        } catch (RuntimeException exception) {
            if (recordInput) {
                recordCommand(command, "failed", inputDetail(request, exception));
            }
            throw exception;
        }
    }

    public void stopSession(String sessionId, StopMode stopMode) {
        CommandEnvelope<StopMode> command = new CommandEnvelope<>(
                CommandTypes.SESSION_STOP,
                RuntimeTargetTypes.SESSION,
                sessionId,
                null,
                null,
                stopMode
        );
        try {
            sessionAppService.stopSession(sessionId, stopMode);
            recordCommand(command, "success", stopDetail(stopMode, null));
        } catch (RuntimeException exception) {
            recordCommand(command, "failed", stopDetail(stopMode, exception));
            throw exception;
        }
    }

    public AiSession dispatchWorkspaceCreate(WorkspaceDispatchCreateCommandRequest request) {
        CommandEnvelope<WorkspaceDispatchCreateCommandRequest> command = new CommandEnvelope<>(
                CommandTypes.WORKSPACE_DISPATCH_CREATE,
                RuntimeTargetTypes.AGENT_WINDOW,
                null,
                null,
                ARCHITECT_WINDOW_OPERATOR,
                request
        );
        try {
            AiSession session = sessionAppService.createAndStart(new CreateSessionRequest(
                    request.appInstanceId(),
                    request.title(),
                    request.projectPath(),
                    request.interactionMode(),
                    request.initInput(),
                    request.tags(),
                    request.workspaceMeta()
            ));
            Map<String, Object> detail = workspaceDispatchCreateDetail(request, session, null);
            recordCommand(command.withTargetId(session.getId()), "success", detail);
            recordOperation(RuntimeTargetTypes.AGENT_WINDOW, session.getId(), "workspace.created", "success", detail, ARCHITECT_WINDOW_OPERATOR);
            return session;
        } catch (RuntimeException exception) {
            recordCommand(command, "failed", workspaceDispatchCreateDetail(request, null, exception));
            throw exception;
        }
    }

    public void dispatchWorkspaceExisting(WorkspaceDispatchExistingCommandRequest request) {
        boolean appendNewLine = request.appendNewLine() == null || request.appendNewLine();
        boolean recordInput = request.recordInput() == null || request.recordInput();
        CommandEnvelope<WorkspaceDispatchExistingCommandRequest> command = new CommandEnvelope<>(
                CommandTypes.WORKSPACE_DISPATCH_EXISTING,
                RuntimeTargetTypes.AGENT_WINDOW,
                request.sessionId(),
                null,
                ARCHITECT_WINDOW_OPERATOR,
                request
        );
        try {
            sessionAppService.sendInput(request.sessionId(), request.content(), appendNewLine, recordInput);
            if (request.workspaceMeta() != null) {
                sessionAppService.updateWorkspaceMeta(request.sessionId(), toUpdateWorkspaceRequest(request.workspaceMeta()));
            }
            Map<String, Object> detail = workspaceDispatchExistingDetail(request, null);
            recordCommand(command, "success", detail);
            recordOperation(RuntimeTargetTypes.AGENT_WINDOW, request.sessionId(), "workspace.dispatched", "success", detail, ARCHITECT_WINDOW_OPERATOR);
        } catch (RuntimeException exception) {
            recordCommand(command, "failed", workspaceDispatchExistingDetail(request, exception));
            throw exception;
        }
    }

    public void recordSummaryApplied(WorkspaceSummaryApplyCommandRequest request) {
        String targetId = StringUtils.hasText(request.targetSessionId()) ? request.targetSessionId().trim() : null;
        String targetType = targetId == null ? RuntimeTargetTypes.ARCHITECT_CONSOLE : RuntimeTargetTypes.AGENT_WINDOW;
        CommandEnvelope<WorkspaceSummaryApplyCommandRequest> command = new CommandEnvelope<>(
                CommandTypes.SUMMARY_APPLY,
                targetType,
                targetId,
                null,
                ARCHITECT_WINDOW_OPERATOR,
                request
        );
        Map<String, Object> detail = summaryApplyDetail(request);
        recordCommand(command, "success", detail);
        recordOperation(targetType, targetId, "summary.applied", "success", detail, ARCHITECT_WINDOW_OPERATOR);
    }

    private void recordCommand(CommandEnvelope<?> command, String result, Map<String, Object> detail) {
        recordOperation(
                command.targetType(),
                command.targetId(),
                command.commandType(),
                result,
                detail,
                command.operatorName()
        );
    }

    private void recordOperation(String targetType,
                                 String targetId,
                                 String action,
                                 String result,
                                 Map<String, Object> detail,
                                 String operatorName) {
        try {
            operationLogAppService.create(new CreateOperationLogRequest(
                    targetType,
                    targetId,
                    action,
                    result,
                    operatorName,
                    detail
            ));
        } catch (RuntimeException exception) {
            log.warn("Failed to record runtime command {}", action, exception);
        }
    }

    private Map<String, Object> createSessionDetail(CreateSessionRequest request,
                                                   AiSession session,
                                                   RuntimeException exception) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("appInstanceId", request.appInstanceId());
        detail.put("title", request.title());
        detail.put("projectPath", request.projectPath());
        detail.put("interactionMode", request.interactionMode());
        detail.put("hasInitInput", request.initInput() != null && !request.initInput().isBlank());
        detail.put("tagCount", request.tags() == null ? 0 : request.tags().size());
        if (request.workspaceMeta() != null) {
            detail.put("workspaceKind", request.workspaceMeta().getWorkspaceKind());
            detail.put("role", request.workspaceMeta().getRole());
            detail.put("coordinationStatus", request.workspaceMeta().getCoordinationStatus());
        }
        if (session != null) {
            detail.put("sessionId", session.getId());
            detail.put("status", session.getStatus());
            detail.put("pid", session.getPid());
        }
        appendError(detail, exception);
        return detail;
    }

    private Map<String, Object> inputDetail(SendInputRequest request, RuntimeException exception) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("contentLength", request.content() == null ? 0 : request.content().length());
        detail.put("appendNewLine", request.appendNewLine() == null || request.appendNewLine());
        detail.put("recordInput", request.recordInput() == null || request.recordInput());
        appendError(detail, exception);
        return detail;
    }

    private Map<String, Object> stopDetail(StopMode stopMode, RuntimeException exception) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stopMode", stopMode == null ? StopMode.GRACEFUL.name() : stopMode.name());
        appendError(detail, exception);
        return detail;
    }

    private Map<String, Object> workspaceDispatchCreateDetail(WorkspaceDispatchCreateCommandRequest request,
                                                              AiSession session,
                                                              RuntimeException exception) {
        Map<String, Object> detail = sanitizeDetail(request.detail());
        detail.putIfAbsent("dispatchMode", "new");
        putIfAbsent(detail, "title", request.title());
        putIfAbsent(detail, "projectPath", request.projectPath());
        putIfAbsent(detail, "targetRole", request.workspaceMeta() == null ? null : request.workspaceMeta().getRole());
        putIfAbsent(detail, "role", request.workspaceMeta() == null ? null : request.workspaceMeta().getRole());
        detail.put("appInstanceId", request.appInstanceId());
        detail.put("hasInitInput", StringUtils.hasText(request.initInput()));
        detail.put("tagCount", request.tags() == null ? 0 : request.tags().size());
        if (session != null) {
            detail.put("sessionId", session.getId());
            detail.put("status", session.getStatus());
            detail.put("pid", session.getPid());
        }
        appendWorkspaceMeta(detail, request.workspaceMeta());
        appendError(detail, exception);
        return detail;
    }

    private Map<String, Object> workspaceDispatchExistingDetail(WorkspaceDispatchExistingCommandRequest request,
                                                                RuntimeException exception) {
        Map<String, Object> detail = sanitizeDetail(request.detail());
        detail.putIfAbsent("dispatchMode", "existing");
        detail.put("sessionId", request.sessionId());
        detail.put("contentLength", request.content() == null ? 0 : request.content().length());
        detail.put("appendNewLine", request.appendNewLine() == null || request.appendNewLine());
        detail.put("recordInput", request.recordInput() == null || request.recordInput());
        appendWorkspaceMeta(detail, request.workspaceMeta());
        appendError(detail, exception);
        return detail;
    }

    private Map<String, Object> summaryApplyDetail(WorkspaceSummaryApplyCommandRequest request) {
        Map<String, Object> detail = sanitizeDetail(request.detail());
        detail.putIfAbsent("dispatchMode", StringUtils.hasText(request.targetSessionId()) ? "existing" : "new");
        if (StringUtils.hasText(request.targetSessionId())) {
            detail.put("targetSessionId", request.targetSessionId().trim());
        }
        return detail;
    }

    private UpdateSessionWorkspaceRequest toUpdateWorkspaceRequest(SessionWorkspaceMeta workspaceMeta) {
        return new UpdateSessionWorkspaceRequest(
                workspaceMeta.getWorkspaceKind(),
                workspaceMeta.getRole(),
                workspaceMeta.getCoordinationStatus(),
                workspaceMeta.getProgressSummary(),
                workspaceMeta.getBlockedReason(),
                workspaceMeta.getDependencySessionIds() == null ? new ArrayList<>() : new ArrayList<>(workspaceMeta.getDependencySessionIds()),
                workspaceMeta.getSharedContextSummary(),
                workspaceMeta.getSharedContextRefs() == null ? new ArrayList<>() : new ArrayList<>(workspaceMeta.getSharedContextRefs()),
                workspaceMeta.getTaskScope(),
                workspaceMeta.getAcceptanceCriteria(),
                workspaceMeta.getDeliverableSpec(),
                workspaceMeta.getSharedContextMode(),
                workspaceMeta.getSharedContextLimit()
        );
    }

    private void appendWorkspaceMeta(Map<String, Object> detail, SessionWorkspaceMeta workspaceMeta) {
        if (workspaceMeta == null) {
            return;
        }
        putIfAbsent(detail, "workspaceKind", workspaceMeta.getWorkspaceKind());
        putIfAbsent(detail, "targetRole", workspaceMeta.getRole());
        putIfAbsent(detail, "role", workspaceMeta.getRole());
        putIfAbsent(detail, "coordinationStatus", workspaceMeta.getCoordinationStatus());
        putIfAbsent(detail, "progressSummary", workspaceMeta.getProgressSummary());
        putIfAbsent(detail, "blockedReason", workspaceMeta.getBlockedReason());
        if (workspaceMeta.getDependencySessionIds() != null && !workspaceMeta.getDependencySessionIds().isEmpty()) {
            detail.putIfAbsent("dependencyIds", workspaceMeta.getDependencySessionIds());
        }
        putIfAbsent(detail, "sharedContextMode", workspaceMeta.getSharedContextMode());
        if (workspaceMeta.getSharedContextLimit() != null) {
            detail.putIfAbsent("sharedContextLimit", workspaceMeta.getSharedContextLimit());
        }
        putIfAbsent(detail, "scopeHint", workspaceMeta.getTaskScope());
        putIfAbsent(detail, "acceptance", workspaceMeta.getAcceptanceCriteria());
        putIfAbsent(detail, "deliverable", workspaceMeta.getDeliverableSpec());
    }

    private Map<String, Object> sanitizeDetail(Map<String, Object> detail) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (detail == null || detail.isEmpty()) {
            return sanitized;
        }
        detail.forEach((key, value) -> {
            if (value != null) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private void putIfAbsent(Map<String, Object> detail, String key, String value) {
        if (detail.containsKey(key) || !StringUtils.hasText(value)) {
            return;
        }
        detail.put(key, value.trim());
    }

    private void appendError(Map<String, Object> detail, RuntimeException exception) {
        if (exception != null) {
            detail.put("errorType", exception.getClass().getSimpleName());
            detail.put("errorMessage", exception.getMessage());
        }
    }

}
