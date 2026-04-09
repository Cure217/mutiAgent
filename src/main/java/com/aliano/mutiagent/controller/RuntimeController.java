package com.aliano.mutiagent.controller;

import com.aliano.mutiagent.application.service.RuntimeAppService;
import com.aliano.mutiagent.common.model.ApiResponse;
import com.aliano.mutiagent.infrastructure.process.ProcessRuntime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runtime")
@RequiredArgsConstructor
public class RuntimeController {

    private final RuntimeAppService runtimeAppService;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(runtimeAppService.health());
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics() {
        return ApiResponse.success(runtimeAppService.statistics());
    }

    @GetMapping("/processes")
    public ApiResponse<List<ProcessRuntime>> processes() {
        return ApiResponse.success(runtimeAppService.processes());
    }
}
