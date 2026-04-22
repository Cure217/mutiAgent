package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.config.MutiAgentProperties;
import com.aliano.mutiagent.infrastructure.event.ClientAttachment;
import com.aliano.mutiagent.infrastructure.event.ClientAttachmentRegistry;
import com.aliano.mutiagent.infrastructure.persistence.mapper.AppInstanceMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.MessageMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.SessionMapper;
import com.aliano.mutiagent.infrastructure.process.ProcessRuntime;
import com.aliano.mutiagent.infrastructure.process.ProcessSupervisor;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RuntimeAppService {

    private final AppInstanceMapper appInstanceMapper;
    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final ProcessSupervisor processSupervisor;
    private final ClientAttachmentRegistry clientAttachmentRegistry;
    private final MutiAgentProperties properties;
    private final RuntimeDiagnosticsTracker runtimeDiagnosticsTracker;
    private final Environment environment;

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", OffsetDateTime.now().toString());
        result.put("startedAt", runtimeDiagnosticsTracker.startedAt());
        result.put("readyAt", runtimeDiagnosticsTracker.readyAt());
        result.put("uptimeMs", runtimeDiagnosticsTracker.uptimeMs());
        result.put("dbPath", properties.resolveDatabasePath().toString());
        result.put("baseDir", properties.resolveBaseDir().toString());
        result.put("runtimeDir", properties.resolveRuntimeDir().toString());
        result.put("appLogPath", resolveAppLogPath());
        result.put("runningProcesses", processSupervisor.countRunning());
        result.put("attachedClientCount", clientAttachmentRegistry.countAll());
        result.put("observingSessionAttachmentCount", clientAttachmentRegistry.countObservingTargetType("session"));
        result.put("recentLifecycleEvents", runtimeDiagnosticsTracker.recentLifecycleEvents());
        return result;
    }

    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceCount", appInstanceMapper.countAll());
        result.put("sessionCount", sessionMapper.countAll());
        result.put("runningSessionCount", sessionMapper.countRunning());
        result.put("messageCount", messageMapper.countAll());
        result.put("runningProcessCount", processSupervisor.countRunning());
        result.put("attachedClientCount", clientAttachmentRegistry.countAll());
        result.put("observingSessionAttachmentCount", clientAttachmentRegistry.countObservingTargetType("session"));
        return result;
    }

    public List<ProcessRuntime> processes() {
        return processSupervisor.listRunning();
    }

    public List<ClientAttachment> attachments() {
        return clientAttachmentRegistry.snapshot();
    }

    private String resolveAppLogPath() {
        String configuredLogFile = environment.getProperty("logging.file.name");
        if (StringUtils.hasText(configuredLogFile)) {
            return Paths.get(configuredLogFile).toAbsolutePath().normalize().toString();
        }
        return properties.resolveAppLogDir().resolve("muti-agent.log").normalize().toString();
    }
}
