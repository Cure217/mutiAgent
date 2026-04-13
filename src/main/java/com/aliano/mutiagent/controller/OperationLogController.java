package com.aliano.mutiagent.controller;

import com.aliano.mutiagent.application.dto.CreateOperationLogRequest;
import com.aliano.mutiagent.application.service.OperationLogAppService;
import com.aliano.mutiagent.common.model.ApiResponse;
import com.aliano.mutiagent.common.model.PageResponse;
import com.aliano.mutiagent.domain.log.OperationLogRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogAppService operationLogAppService;

    @GetMapping
    public ApiResponse<PageResponse<OperationLogRecord>> list(@RequestParam(required = false) String targetType,
                                                              @RequestParam(required = false) String targetId,
                                                              @RequestParam(required = false) String action,
                                                              @RequestParam(required = false) String operatorName,
                                                              @RequestParam(defaultValue = "1") int pageNo,
                                                              @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(operationLogAppService.list(targetType, targetId, action, operatorName, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<OperationLogRecord> create(@Valid @RequestBody CreateOperationLogRequest request) {
        return ApiResponse.success(operationLogAppService.create(request));
    }
}
