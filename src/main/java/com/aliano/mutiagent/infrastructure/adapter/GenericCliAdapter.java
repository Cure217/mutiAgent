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
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class GenericCliAdapter implements AIAdapter {

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B\\[[0-9;?]*[ -/]*[@-~]");
    private static final Pattern OSC_ESCAPE = Pattern.compile("\\u001B\\][^\\u0007]*(\\u0007|\\u001B\\\\)");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "generic-cli";
    }

    @Override
    public LaunchPlan buildLaunchPlan(AppInstance instance, AiSession session) {
        if (shouldLaunchByWsl(instance)) {
            return buildWslLaunchPlan(instance, session);
        }
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
        String sanitizedChunk = sanitizeChunk(chunk);
        if (!StringUtils.hasText(sanitizedChunk)) {
            return new ParseResult(Collections.emptyList());
        }
        String role = "stdout".equalsIgnoreCase(streamName)
                ? MessageRole.ASSISTANT.name().toLowerCase()
                : MessageRole.SYSTEM.name().toLowerCase();
        String messageType = "stdout".equalsIgnoreCase(streamName)
                ? MessageType.RAW.name().toLowerCase()
                : MessageType.ERROR.name().toLowerCase();
        return new ParseResult(List.of(new ParsedMessage(role, messageType, sanitizedChunk, null, false)));
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

    protected String sanitizeChunk(String chunk) {
        if (!StringUtils.hasText(chunk)) {
            return "";
        }
        String withoutOsc = OSC_ESCAPE.matcher(chunk).replaceAll("");
        String withoutAnsi = ANSI_ESCAPE.matcher(withoutOsc).replaceAll("");
        String normalized = CONTROL_CHARS.matcher(withoutAnsi).replaceAll("")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "")
                .replaceAll("\\n{3,}", "\n\n");
        if (!StringUtils.hasText(normalized.replaceAll("\\s+", ""))) {
            return "";
        }
        return normalized.strip();
    }

    private LaunchPlan buildWslLaunchPlan(AppInstance instance, AiSession session) {
        List<String> command = new ArrayList<>();
        command.add(StringUtils.hasText(instance.getExecutablePath()) ? instance.getExecutablePath() : "wsl.exe");

        String linuxWorkdir = resolveLinuxWorkdir(instance, session);
        if (StringUtils.hasText(linuxWorkdir)) {
            command.add("--cd");
            command.add(linuxWorkdir);
        }

        command.add("--exec");
        command.add(instance.getLaunchCommand());
        command.addAll(readList(instance.getArgsJson()));

        String windowsWorkingDirectory = StringUtils.hasText(session.getProjectPath()) ? session.getProjectPath() : instance.getWorkdir();
        return new LaunchPlan(command, readMap(instance.getEnvJson()), windowsWorkingDirectory);
    }

    private boolean shouldLaunchByWsl(AppInstance instance) {
        String executable = firstNonBlank(instance.getExecutablePath(), instance.getLaunchCommand());
        return "wsl".equalsIgnoreCase(instance.getRuntimeEnv())
                || (StringUtils.hasText(executable) && executable.toLowerCase(Locale.ROOT).endsWith("wsl.exe"));
    }

    private String resolveLinuxWorkdir(AppInstance instance, AiSession session) {
        String explicitLinuxPath = session.getProjectPathLinux();
        if (StringUtils.hasText(explicitLinuxPath)) {
            return explicitLinuxPath;
        }

        String windowsPath = firstNonBlank(session.getProjectPath(), instance.getWorkdir());
        if (!StringUtils.hasText(windowsPath)) {
            return null;
        }
        return toWslPath(windowsPath);
    }

    private String toWslPath(String windowsPath) {
        String normalized = windowsPath.replace('\\', '/');
        if (normalized.length() >= 3
                && Character.isLetter(normalized.charAt(0))
                && normalized.charAt(1) == ':'
                && normalized.charAt(2) == '/') {
            char drive = Character.toLowerCase(normalized.charAt(0));
            return "/mnt/" + drive + normalized.substring(2);
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
