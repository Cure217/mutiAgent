package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.config.MutiAgentProperties;
import com.aliano.mutiagent.infrastructure.persistence.mapper.AppInstanceMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.MessageMapper;
import com.aliano.mutiagent.infrastructure.persistence.mapper.SessionMapper;
import com.aliano.mutiagent.infrastructure.process.ProcessRuntime;
import com.aliano.mutiagent.infrastructure.process.ProcessSupervisor;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RuntimeAppService {

    private final AppInstanceMapper appInstanceMapper;
    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final ProcessSupervisor processSupervisor;
    private final MutiAgentProperties properties;

    public RuntimeAppService(AppInstanceMapper appInstanceMapper,
                             SessionMapper sessionMapper,
                             MessageMapper messageMapper,
                             ProcessSupervisor processSupervisor,
                             MutiAgentProperties properties) {
        this.appInstanceMapper = appInstanceMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.processSupervisor = processSupervisor;
        this.properties = properties;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", OffsetDateTime.now().toString());
        result.put("dbPath", properties.resolveDatabasePath().toString());
        result.put("baseDir", properties.resolveBaseDir().toString());
        result.put("runningProcesses", processSupervisor.countRunning());
        return result;
    }

    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceCount", appInstanceMapper.countAll());
        result.put("sessionCount", sessionMapper.countAll());
        result.put("runningSessionCount", sessionMapper.countRunning());
        result.put("messageCount", messageMapper.countAll());
        result.put("runningProcessCount", processSupervisor.countRunning());
        return result;
    }

    public List<ProcessRuntime> processes() {
        return processSupervisor.listRunning();
    }
}
