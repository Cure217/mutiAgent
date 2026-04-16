package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.ConfigItemRequest;
import com.aliano.mutiagent.application.dto.UpdateConfigsRequest;
import com.aliano.mutiagent.common.exception.BusinessException;
import com.aliano.mutiagent.common.util.IdGenerator;
import com.aliano.mutiagent.config.MutiAgentProperties;
import com.aliano.mutiagent.domain.config.SystemConfig;
import com.aliano.mutiagent.infrastructure.persistence.mapper.ConfigMapper;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ConfigAppService {

    private static final String RUNTIME_CONFIG_GROUP = "runtime";
    private static final String DEFAULT_PROJECT_PATH_KEY = "defaultProjectPath";
    private static final String LEGACY_DEFAULT_PROJECT_PATH = "D:\\Project\\ali\\260409";

    private final ConfigMapper configMapper;
    private final IdGenerator idGenerator;
    private final MutiAgentProperties properties;

    public List<SystemConfig> list() {
        ensureDefaultProjectPathConfig();
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

    private void ensureDefaultProjectPathConfig() {
        SystemConfig existed = configMapper.findByGroupAndKey(RUNTIME_CONFIG_GROUP, DEFAULT_PROJECT_PATH_KEY);
        if (existed == null) {
            configMapper.upsert(buildDefaultProjectPathConfig(null, resolveSuggestedProjectPath()));
            return;
        }

        if (!shouldHealDefaultProjectPath(existed.getValueText())) {
            return;
        }

        configMapper.upsert(buildDefaultProjectPathConfig(existed, resolveSuggestedProjectPath()));
    }

    private SystemConfig buildDefaultProjectPathConfig(SystemConfig existed, String valueText) {
        SystemConfig config = new SystemConfig();
        config.setId(existed != null ? existed.getId() : idGenerator.next("cfg"));
        config.setConfigGroup(RUNTIME_CONFIG_GROUP);
        config.setConfigKey(DEFAULT_PROJECT_PATH_KEY);
        config.setValueType("string");
        config.setValueText(valueText);
        config.setValueJson(existed != null ? existed.getValueJson() : null);
        config.setSecretRef(existed != null ? existed.getSecretRef() : null);
        config.setRemark(existed != null ? existed.getRemark() : null);
        config.setUpdatedAt(OffsetDateTime.now().toString());
        return config;
    }

    private boolean shouldHealDefaultProjectPath(String valueText) {
        if (!StringUtils.hasText(valueText)) {
            return true;
        }

        Path configuredPath = normalizePath(valueText);
        Path legacyPath = normalizePath(LEGACY_DEFAULT_PROJECT_PATH);
        return configuredPath != null
                && legacyPath != null
                && legacyPath.equals(configuredPath)
                && !Files.isDirectory(configuredPath);
    }

    private String resolveSuggestedProjectPath() {
        Path userHomePath = normalizePath(System.getProperty("user.home"));
        if (userHomePath != null) {
            return userHomePath.toString();
        }
        return properties.resolveBaseDir().toString();
    }

    private Path normalizePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }
        try {
            return Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private String normalizeValueType(String valueType) {
        String normalized = valueType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "string", "number", "boolean", "json" -> normalized;
            default -> throw new BusinessException("不支持的配置值类型: " + valueType);
        };
    }
}
