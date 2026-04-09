package com.aliano.mutiagent.infrastructure.adapter;

import com.aliano.mutiagent.domain.instance.AppInstance;
import com.aliano.mutiagent.domain.message.MessageRole;
import com.aliano.mutiagent.domain.message.MessageType;
import com.aliano.mutiagent.domain.session.AiSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class GenericCliAdapter implements AIAdapter {

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "generic-cli";
    }

    @Override
    public LaunchPlan buildLaunchPlan(AppInstance instance, AiSession session) {
        List<String> command = new ArrayList<>();
        if (StringUtils.hasText(instance.getExecutablePath())) {
            command.add(instance.getExecutablePath());
        }
        if (StringUtils.hasText(instance.getLaunchCommand())) {
            if (command.isEmpty() || !instance.getLaunchCommand().equals(command.get(0))) {
                command.add(instance.getLaunchCommand());
            }
        }
        command.addAll(readList(instance.getArgsJson()));
        if (command.isEmpty()) {
            throw new IllegalArgumentException("未配置有效的启动命令");
        }
        String workingDirectory = StringUtils.hasText(session.getProjectPath()) ? session.getProjectPath() : instance.getWorkdir();
        return new LaunchPlan(command, readMap(instance.getEnvJson()), workingDirectory);
    }

    @Override
    public ParseResult parseOutput(AppInstance instance, String streamName, String chunk) {
        String role = "stdout".equalsIgnoreCase(streamName)
                ? MessageRole.ASSISTANT.name().toLowerCase()
                : MessageRole.SYSTEM.name().toLowerCase();
        String messageType = "stdout".equalsIgnoreCase(streamName)
                ? MessageType.RAW.name().toLowerCase()
                : MessageType.ERROR.name().toLowerCase();
        return new ParseResult(List.of(new ParsedMessage(role, messageType, chunk, null, false)));
    }

    @Override
    public boolean supportsStructuredMessage() {
        return false;
    }

    @Override
    public boolean supportsInteractiveInput() {
        return true;
    }

    protected List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new IllegalArgumentException("启动参数解析失败", exception);
        }
    }

    protected Map<String, String> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new IllegalArgumentException("环境变量解析失败", exception);
        }
    }
}
