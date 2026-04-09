package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.CreateInstanceRequest;
import com.aliano.mutiagent.application.dto.UpdateInstanceRequest;
import com.aliano.mutiagent.common.exception.BusinessException;
import com.aliano.mutiagent.common.util.IdGenerator;
import com.aliano.mutiagent.domain.instance.AppInstance;
import com.aliano.mutiagent.infrastructure.persistence.mapper.AppInstanceMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InstanceAppService {

    private final AppInstanceMapper appInstanceMapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public InstanceAppService(AppInstanceMapper appInstanceMapper, IdGenerator idGenerator, ObjectMapper objectMapper) {
        this.appInstanceMapper = appInstanceMapper;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public List<AppInstance> list(String appType, Boolean enabled, String keyword) {
        Integer enabledFlag = enabled == null ? null : Boolean.TRUE.equals(enabled) ? 1 : 0;
        return appInstanceMapper.findAll(appType, enabledFlag, keyword);
    }

    public AppInstance get(String id) {
        AppInstance instance = appInstanceMapper.findById(id);
        if (instance == null) {
            throw new BusinessException("应用实例不存在");
        }
        return instance;
    }

    public AppInstance create(CreateInstanceRequest request) {
        String now = OffsetDateTime.now().toString();
        AppInstance instance = new AppInstance();
        instance.setId(idGenerator.next("ins"));
        instance.setCode(buildCode(request.name()));
        instance.setName(request.name());
        instance.setAppType(request.appType());
        instance.setAdapterType(StringUtils.hasText(request.adapterType()) ? request.adapterType() : "generic-cli");
        instance.setRuntimeEnv(request.runtimeEnv());
        instance.setLaunchMode(request.launchMode());
        instance.setExecutablePath(request.executablePath());
        instance.setLaunchCommand(request.launchCommand());
        instance.setArgsJson(writeJson(request.args()));
        instance.setWorkdir(request.workdir());
        instance.setEnvJson(writeJson(request.env()));
        instance.setEnabled(request.enabled() == null || request.enabled());
        instance.setAutoRestart(Boolean.TRUE.equals(request.autoRestart()));
        instance.setRemark(request.remark());
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        appInstanceMapper.insert(instance);
        return get(instance.getId());
    }

    public AppInstance update(String id, UpdateInstanceRequest request) {
        AppInstance instance = get(id);
        instance.setName(request.name());
        instance.setAppType(request.appType());
        instance.setAdapterType(StringUtils.hasText(request.adapterType()) ? request.adapterType() : instance.getAdapterType());
        instance.setRuntimeEnv(request.runtimeEnv());
        instance.setLaunchMode(request.launchMode());
        instance.setExecutablePath(request.executablePath());
        instance.setLaunchCommand(request.launchCommand());
        instance.setArgsJson(writeJson(request.args()));
        instance.setWorkdir(request.workdir());
        instance.setEnvJson(writeJson(request.env()));
        instance.setEnabled(request.enabled() == null ? instance.getEnabled() : request.enabled());
        instance.setAutoRestart(request.autoRestart() == null ? instance.getAutoRestart() : request.autoRestart());
        instance.setRemark(request.remark());
        instance.setUpdatedAt(OffsetDateTime.now().toString());
        appInstanceMapper.update(instance);
        return get(id);
    }

    public void setEnabled(String id, boolean enabled) {
        get(id);
        appInstanceMapper.updateEnabled(id, enabled ? 1 : 0, OffsetDateTime.now().toString());
    }

    private String buildCode(String name) {
        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "instance";
        }
        return normalized + "-" + idGenerator.next("code").substring(5);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("实例配置序列化失败", exception);
        }
    }
}
