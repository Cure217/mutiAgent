package com.aliano.mutiagent.controller;

import com.aliano.mutiagent.application.dto.CreateSessionRequest;
import com.aliano.mutiagent.application.dto.ResizeTerminalRequest;
import com.aliano.mutiagent.application.dto.SendInputRequest;
import com.aliano.mutiagent.application.dto.SessionTimelineItem;
import com.aliano.mutiagent.application.dto.StopSessionRequest;
import com.aliano.mutiagent.application.service.SessionAppService;
import com.aliano.mutiagent.common.model.ApiResponse;
import com.aliano.mutiagent.common.model.PageResponse;
import com.aliano.mutiagent.domain.message.MessageRecord;
import com.aliano.mutiagent.domain.session.AiSession;
import com.aliano.mutiagent.infrastructure.process.StopMode;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionAppService sessionAppService;

    @GetMapping
    public ApiResponse<PageResponse<AiSession>> list(@RequestParam(required = false) String appInstanceId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") int pageNo,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(sessionAppService.list(appInstanceId, status, keyword, pageNo, pageSize));
    }

    @GetMapping("/running")
    public ApiResponse<List<AiSession>> running() {
        return ApiResponse.success(sessionAppService.runningSessions());
    }

    @GetMapping("/{id}")
    public ApiResponse<AiSession> detail(@PathVariable String id) {
        return ApiResponse.success(sessionAppService.get(id));
    }

    @PostMapping
    public ApiResponse<AiSession> create(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.success(sessionAppService.createAndStart(request));
    }

    @PostMapping("/{id}/input")
    public ApiResponse<Void> input(@PathVariable String id, @Valid @RequestBody SendInputRequest request) {
        sessionAppService.sendInput(
                id,
                request.content(),
                request.appendNewLine() == null || request.appendNewLine(),
                request.recordInput() == null || request.recordInput()
        );
        return ApiResponse.success();
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<Void> stop(@PathVariable String id, @RequestBody(required = false) StopSessionRequest request) {
        StopMode stopMode = StopMode.GRACEFUL;
        if (request != null && request.stopMode() != null) {
            stopMode = StopMode.valueOf(request.stopMode().toUpperCase());
        }
        sessionAppService.stopSession(id, stopMode);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/terminal/resize")
    public ApiResponse<Void> resizeTerminal(@PathVariable String id, @Valid @RequestBody ResizeTerminalRequest request) {
        sessionAppService.resizeTerminal(id, request.cols(), request.rows());
        return ApiResponse.success();
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<PageResponse<MessageRecord>> messages(@PathVariable String id,
                                                             @RequestParam(defaultValue = "1") int pageNo,
                                                             @RequestParam(defaultValue = "50") int pageSize) {
        return ApiResponse.success(sessionAppService.messages(id, pageNo, pageSize));
    }

    @GetMapping("/{id}/messages/around")
    public ApiResponse<List<MessageRecord>> messagesAround(@PathVariable String id,
                                                           @RequestParam String messageId,
                                                           @RequestParam(defaultValue = "40") int before,
                                                           @RequestParam(defaultValue = "40") int after) {
        return ApiResponse.success(sessionAppService.messagesAround(id, messageId, before, after));
    }

    @GetMapping("/{id}/raw-output")
    public ApiResponse<String> rawOutput(@PathVariable String id) {
        return ApiResponse.success(sessionAppService.rawOutput(id));
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<SessionTimelineItem>> timeline(@PathVariable String id,
                                                           @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.success(sessionAppService.timeline(id, limit));
    }
}
