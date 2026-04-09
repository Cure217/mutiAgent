package com.aliano.mutiagent.controller;

import com.aliano.mutiagent.application.dto.CreateInstanceRequest;
import com.aliano.mutiagent.application.dto.InstanceTestLaunchResult;
import com.aliano.mutiagent.application.dto.UpdateInstanceRequest;
import com.aliano.mutiagent.application.service.InstanceAppService;
import com.aliano.mutiagent.common.model.ApiResponse;
import com.aliano.mutiagent.domain.instance.AppInstance;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceAppService instanceAppService;

    @GetMapping
    public ApiResponse<List<AppInstance>> list(@RequestParam(required = false) String appType,
                                               @RequestParam(required = false) Boolean enabled,
                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.success(instanceAppService.list(appType, enabled, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<AppInstance> detail(@PathVariable String id) {
        return ApiResponse.success(instanceAppService.get(id));
    }

    @PostMapping
    public ApiResponse<AppInstance> create(@Valid @RequestBody CreateInstanceRequest request) {
        return ApiResponse.success(instanceAppService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppInstance> update(@PathVariable String id, @Valid @RequestBody UpdateInstanceRequest request) {
        return ApiResponse.success(instanceAppService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable String id) {
        instanceAppService.setEnabled(id, true);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable String id) {
        instanceAppService.setEnabled(id, false);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/test-launch")
    public ApiResponse<InstanceTestLaunchResult> testLaunch(@PathVariable String id) {
        return ApiResponse.success(instanceAppService.testLaunch(id));
    }
}
