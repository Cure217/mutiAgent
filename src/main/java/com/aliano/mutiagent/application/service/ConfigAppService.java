package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.ConfigItemRequest;
import com.aliano.mutiagent.application.dto.UpdateConfigsRequest;
import com.aliano.mutiagent.common.exception.BusinessException;
import com.aliano.mutiagent.common.util.IdGenerator;
import com.aliano.mutiagent.domain.config.SystemConfig;
import com.aliano.mutiagent.infrastructure.persistence.mapper.ConfigMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfigAppService {

    private final ConfigMapper configMapper;
    private final IdGenerator idGenerator;

    public List<SystemConfig> list() {
        return configMapper.findAll();
    }

    public List<SystemConfig> save(UpdateConfigsRequest request) {
        for (ConfigItemRequest item : request.items()) {
            configMapper.upsert(toSystemConfig(item));
        }
        return list();
    }

    private SystemConfig toSystemConfig(ConfigItemRequest item) {
        SystemConfig existed = configMapper.findByGroupAndKey(item.configGroup(), item.configKey());

        SystemConfig config = new SystemConfig();
        config.setId(existed != null ? existed.getId() : idGenerator.next("cfg"));
        config.setConfigGroup(item.configGroup().trim());
        config.setConfigKey(item.configKey().trim());
        config.setValueType(normalizeValueType(item.valueType()));
        config.setValueText(item.valueText());
        config.setValueJson(item.valueJson());
        config.setSecretRef(item.secretRef());
        config.setRemark(item.remark());
        config.setUpdatedAt(OffsetDateTime.now().toString());
        return config;
    }

    private String normalizeValueType(String valueType) {
        String normalized = valueType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "string", "number", "boolean", "json" -> normalized;
            default -> throw new BusinessException("不支持的配置值类型: " + valueType);
        };
    }
}
