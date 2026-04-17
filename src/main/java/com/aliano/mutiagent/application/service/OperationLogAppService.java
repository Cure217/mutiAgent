package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.CreateOperationLogRequest;
import com.aliano.mutiagent.common.model.PageResponse;
import com.aliano.mutiagent.common.util.IdGenerator;
import com.aliano.mutiagent.domain.log.OperationLogRecord;
import com.aliano.mutiagent.infrastructure.persistence.mapper.OperationLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperationLogAppService {

    private final OperationLogMapper operationLogMapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public PageResponse<OperationLogRecord> list(String targetType,
                                                 String targetId,
                                                 String action,
                                                 String operatorName,
                                                 int pageNo,
                                                 int pageSize) {
        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (validPageNo - 1) * validPageSize;
        List<String> targetTypes = splitFilterValues(targetType);
        return new PageResponse<>(
                operationLogMapper.findPage(targetTypes, normalize(targetId), normalize(action), normalize(operatorName), validPageSize, offset),
                validPageNo,
                validPageSize,
                operationLogMapper.countPage(targetTypes, normalize(targetId), normalize(action), normalize(operatorName))
        );
    }

    public OperationLogRecord create(CreateOperationLogRequest request) {
        OperationLogRecord record = new OperationLogRecord();
        record.setId(idGenerator.next("op"));
        record.setTargetType(normalize(request.targetType()));
        record.setTargetId(normalize(request.targetId()));
        record.setAction(normalize(request.action()));
        record.setResult(normalize(request.result()));
        record.setOperatorName(StringUtils.hasText(request.operatorName()) ? request.operatorName().trim() : "local-user");
        record.setDetailJson(writeJson(request.detail()));
        record.setCreatedAt(OffsetDateTime.now().toString());
        operationLogMapper.insert(record);
        return record;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> splitFilterValues(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        List<String> values = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return values.isEmpty() ? null : values;
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
