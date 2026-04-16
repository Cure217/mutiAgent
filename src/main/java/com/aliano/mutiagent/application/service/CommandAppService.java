package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.command.CommandEnvelope;
import com.aliano.mutiagent.application.command.CommandTypes;
import com.aliano.mutiagent.application.command.RuntimeTargetTypes;
import com.aliano.mutiagent.application.dto.CreateOperationLogRequest;
import com.aliano.mutiagent.application.dto.CreateSessionRequest;
import com.aliano.mutiagent.application.dto.SendInputRequest;
import com.aliano.mutiagent.domain.session.AiSession;
import com.aliano.mutiagent.infrastructure.process.StopMode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandAppService {

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

    private void recordCommand(CommandEnvelope<?> command, String result, Map<String, Object> detail) {
        try {
            operationLogAppService.create(new CreateOperationLogRequest(
                    command.targetType(),
                    command.targetId(),
                    command.commandType(),
                    result,
                    command.operatorName(),
                    detail
            ));
        } catch (RuntimeException exception) {
            log.warn("Failed to record runtime command {}", command.commandType(), exception);
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

    private void appendError(Map<String, Object> detail, RuntimeException exception) {
        if (exception != null) {
            detail.put("errorType", exception.getClass().getSimpleName());
            detail.put("errorMessage", exception.getMessage());
        }
    }

}
