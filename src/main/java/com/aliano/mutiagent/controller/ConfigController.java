package com.aliano.mutiagent.controller;

import com.aliano.mutiagent.application.dto.UpdateConfigsRequest;
import com.aliano.mutiagent.application.service.ConfigAppService;
import com.aliano.mutiagent.common.model.ApiResponse;
import com.aliano.mutiagent.domain.config.SystemConfig;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigAppService configAppService;

    @GetMapping
    public ApiResponse<List<SystemConfig>> list() {
        return ApiResponse.success(configAppService.list());
    }

    @PutMapping
    public ApiResponse<List<SystemConfig>> update(@Valid @RequestBody UpdateConfigsRequest request) {
        return ApiResponse.success(configAppService.save(request));
    }
}
