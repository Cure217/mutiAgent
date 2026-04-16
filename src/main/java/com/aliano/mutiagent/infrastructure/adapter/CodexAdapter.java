package com.aliano.mutiagent.infrastructure.adapter;

import com.aliano.mutiagent.domain.instance.AppInstance;
import com.aliano.mutiagent.domain.message.MessageRole;
import com.aliano.mutiagent.domain.message.MessageType;
import com.aliano.mutiagent.domain.session.AiSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CodexAdapter extends GenericCliAdapter {

    private static final String YOLO_FLAG = "--dangerously-bypass-approvals-and-sandbox";
    private static final String FULL_AUTO_FLAG = "--full-auto";
    private static final String ASK_FOR_APPROVAL_SHORT_FLAG = "-a";
    private static final String ASK_FOR_APPROVAL_LONG_FLAG = "--ask-for-approval";
    private static final Pattern PROMPT_ONLY = Pattern.compile("^[>›]+$");
    private static final Pattern CONTEXT_LINE = Pattern.compile("^\\d+%\\s+context\\s+left$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPINNER_LINE = Pattern.compile("^[⠁-⣿]+\\s+.*$");
    private static final Pattern BOX_DRAWING_ONLY = Pattern.compile("^[╭╮╰╯│─╴╶┌┐└┘├┤┬┴┼_\\s]+$");
    private static final Pattern WORKING_FRAGMENT = Pattern.compile("^(?:[•◦·]\\s*)?(?:\\d+|W|Wo|or|rk|ki|in|ng|Wng|Wog|Working?)$", Pattern.CASE_INSENSITIVE);

    public CodexAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String getType() {
        return "codex-cli";
    }

    @Override
    public ParseResult parseOutput(AppInstance instance, String streamName, String chunk) {
        String sanitizedChunk = sanitizeChunk(chunk);
        if (!StringUtils.hasText(sanitizedChunk)) {
            return new ParseResult(List.of());
        }

        if (!"stdout".equalsIgnoreCase(streamName)) {
            return super.parseOutput(instance, streamName, chunk);
        }

        String filteredText = filterCodexTuiText(sanitizedChunk);
        if (!StringUtils.hasText(filteredText)) {
            return new ParseResult(List.of());
        }

        return new ParseResult(List.of(
                new ParsedMessage(
                        MessageRole.ASSISTANT.name().toLowerCase(),
                        MessageType.RAW.name().toLowerCase(),
                        filteredText,
                        null,
                        false
                )
        ));
    }

    @Override
    public LaunchPlan buildLaunchPlan(AppInstance instance, AiSession session) {
        List<String> launchArgs = resolveCodexArgs(instance);
        if (!"windows".equalsIgnoreCase(instance.getRuntimeEnv())) {
            return super.buildLaunchPlan(instance, session, launchArgs);
        }

        Path wrapperPath = resolveCodexWrapper(instance);
        Path nodePath = resolveNodeExecutable();
        Path scriptPath = wrapperPath == null ? null : wrapperPath.getParent()
                .resolve("node_modules")
                .resolve("@openai")
                .resolve("codex")
                .resolve("bin")
                .resolve("codex.js");

        if (wrapperPath == null || nodePath == null || scriptPath == null || !Files.exists(scriptPath)) {
            return super.buildLaunchPlan(instance, session, launchArgs);
        }

        List<String> command = new ArrayList<>();
        command.add(nodePath.toString());
        command.add(scriptPath.toString());
        command.addAll(launchArgs);
        String workingDirectory = StringUtils.hasText(session.getProjectPath()) ? session.getProjectPath() : instance.getWorkdir();
        return new LaunchPlan(command, readMap(instance.getEnvJson()), workingDirectory);
    }

    private List<String> resolveCodexArgs(AppInstance instance) {
        List<String> args = new ArrayList<>();
        for (String arg : readList(instance.getArgsJson())) {
            if (StringUtils.hasText(arg)) {
                args.add(arg.trim());
            }
        }
        if (hasExplicitApprovalConfiguration(args)) {
            return args;
        }
        args.add(YOLO_FLAG);
        return args;
    }

    private boolean hasExplicitApprovalConfiguration(List<String> args) {
        for (String arg : args) {
            if (!StringUtils.hasText(arg)) {
                continue;
            }
            String normalized = arg.trim();
            if (YOLO_FLAG.equals(normalized)
                    || FULL_AUTO_FLAG.equals(normalized)
                    || ASK_FOR_APPROVAL_SHORT_FLAG.equals(normalized)
                    || ASK_FOR_APPROVAL_LONG_FLAG.equals(normalized)
                    || normalized.startsWith(ASK_FOR_APPROVAL_SHORT_FLAG + "=")
                    || normalized.startsWith(ASK_FOR_APPROVAL_LONG_FLAG + "=")) {
                return true;
            }
        }
        return false;
    }

    private Path resolveCodexWrapper(AppInstance instance) {
        Path configuredExecutable = resolveExecutableFile(instance.getExecutablePath());
        if (configuredExecutable != null) {
            return configuredExecutable;
        }

        Path configuredLaunchCommand = resolveExecutableFile(instance.getLaunchCommand());
        if (configuredLaunchCommand != null) {
            return configuredLaunchCommand;
        }

        List<Path> candidates = List.of(
                Path.of(System.getProperty("user.home"), ".npm-global", "codex.cmd"),
                Path.of(System.getProperty("user.home"), "AppData", "Roaming", "npm", "codex.cmd"),
                Path.of(System.getProperty("user.home"), "codex.cmd")
        );
        return candidates.stream().filter(Files::exists).findFirst().orElse(null);
    }

    private Path resolveNodeExecutable() {
        List<Path> candidates = new ArrayList<>();
        String nvmSymlink = System.getenv("NVM_SYMLINK");
        if (StringUtils.hasText(nvmSymlink)) {
            candidates.add(Path.of(nvmSymlink, "node.exe"));
        }
        candidates.add(Path.of("C:\\nvm4w\\nodejs\\node.exe"));

        String programFiles = System.getenv("ProgramFiles");
        if (StringUtils.hasText(programFiles)) {
            candidates.add(Path.of(programFiles, "nodejs", "node.exe"));
        }
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (StringUtils.hasText(programFilesX86)) {
            candidates.add(Path.of(programFilesX86, "nodejs", "node.exe"));
        }
        return candidates.stream().filter(Files::exists).findFirst().orElse(null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String filterCodexTuiText(String text) {
        List<String> lines = text.lines()
                .map(String::strip)
                .filter(StringUtils::hasText)
                .filter(line -> !shouldSkipLine(line))
                .toList();
        if (lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines).strip();
    }

    private boolean shouldSkipLine(String line) {
        if (!StringUtils.hasText(line)) {
            return true;
        }
        if (PROMPT_ONLY.matcher(line).matches()
                || CONTEXT_LINE.matcher(line).matches()
                || SPINNER_LINE.matcher(line).matches()
                || BOX_DRAWING_ONLY.matcher(line).matches()
                || WORKING_FRAGMENT.matcher(line).matches()) {
            return true;
        }
        String normalized = line.toLowerCase();
        return normalized.contains("tab to queue message")
                || normalized.startsWith("gpt-")
                || normalized.startsWith("model:")
                || normalized.startsWith("directory:")
                || normalized.contains("openai codex")
                || normalized.contains("press enter to continue")
                || normalized.contains("to change")
                || normalized.contains("context left");
    }
}
