package com.aliano.mutiagent.controller;

import com.aliano.mutiagent.application.dto.WorkspaceDispatchCreateCommandRequest;
import com.aliano.mutiagent.application.dto.WorkspaceDispatchExistingCommandRequest;
import com.aliano.mutiagent.application.dto.WorkspaceSummaryApplyCommandRequest;
import com.aliano.mutiagent.application.service.CommandAppService;
import com.aliano.mutiagent.common.model.ApiResponse;
import com.aliano.mutiagent.domain.session.AiSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspace-commands")
@RequiredArgsConstructor
public class WorkspaceCommandController {

    private final CommandAppService commandAppService;

    @PostMapping("/dispatch/create")
    public ApiResponse<AiSession> dispatchCreate(@Valid @RequestBody WorkspaceDispatchCreateCommandRequest request) {
        return ApiResponse.success(commandAppService.dispatchWorkspaceCreate(request));
    }

    @PostMapping("/dispatch/existing")
    public ApiResponse<Void> dispatchExisting(@Valid @RequestBody WorkspaceDispatchExistingCommandRequest request) {
        commandAppService.dispatchWorkspaceExisting(request);
        return ApiResponse.success();
    }

    @PostMapping("/summary/apply")
    public ApiResponse<Void> applySummary(@RequestBody WorkspaceSummaryApplyCommandRequest request) {
        commandAppService.recordSummaryApplied(request);
        return ApiResponse.success();
    }
}
