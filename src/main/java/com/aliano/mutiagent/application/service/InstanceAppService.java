package com.aliano.mutiagent.application.service;

import com.aliano.mutiagent.application.dto.CreateInstanceRequest;
import com.aliano.mutiagent.application.dto.InstanceTestLaunchResult;
import com.aliano.mutiagent.application.dto.UpdateInstanceRequest;
import com.aliano.mutiagent.common.exception.BusinessException;
import com.aliano.mutiagent.common.util.IdGenerator;
import com.aliano.mutiagent.domain.instance.AppInstance;
import com.aliano.mutiagent.domain.session.AiSession;
import com.aliano.mutiagent.infrastructure.adapter.AIAdapter;
import com.aliano.mutiagent.infrastructure.adapter.AdapterRegistry;
import com.aliano.mutiagent.infrastructure.adapter.LaunchPlan;
import com.aliano.mutiagent.infrastructure.persistence.mapper.AppInstanceMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InstanceAppService {

    private final AppInstanceMapper appInstanceMapper;
    private final AdapterRegistry adapterRegistry;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

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

    public InstanceTestLaunchResult testLaunch(String id) {
        AppInstance instance = get(id);
        AIAdapter adapter = adapterRegistry.resolve(instance);

        LaunchPlan launchPlan;
        try {
            launchPlan = adapter.buildLaunchPlan(instance, buildDryRunSession(instance));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("测试启动校验失败: " + exception.getMessage(), exception);
        }

        if (launchPlan.command() == null || launchPlan.command().isEmpty()) {
            throw new BusinessException("测试启动校验失败: 未生成有效的启动命令");
        }

        List<String> warnings = new ArrayList<>();
        String executable = launchPlan.command().get(0);
        String resolvedExecutable = resolveExecutable(executable, warnings);
        String workingDirectory = normalizeWorkingDirectory(launchPlan.workingDirectory());
        Set<String> environmentKeys = new LinkedHashSet<>(launchPlan.environment().keySet());

        return new InstanceTestLaunchResult(
                true,
                adapter.getType(),
                launchPlan.command(),
                executable,
                resolvedExecutable,
                workingDirectory,
                new ArrayList<>(environmentKeys),
                warnings
        );
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

    private AiSession buildDryRunSession(AppInstance instance) {
        AiSession session = new AiSession();
        session.setId(idGenerator.next("dry"));
        session.setAppInstanceId(instance.getId());
        session.setTitle(instance.getName());
        session.setProjectPath(instance.getWorkdir());
        return session;
    }

    private String normalizeWorkingDirectory(String workingDirectory) {
        if (!StringUtils.hasText(workingDirectory)) {
            return null;
        }
        try {
            Path path = Path.of(workingDirectory).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                throw new BusinessException("工作目录不存在: " + path);
            }
            if (!Files.isDirectory(path)) {
                throw new BusinessException("工作目录不是目录: " + path);
            }
            return path.toString();
        } catch (InvalidPathException exception) {
            throw new BusinessException("工作目录非法: " + workingDirectory, exception);
        }
    }

    private String resolveExecutable(String executable, List<String> warnings) {
        if (!StringUtils.hasText(executable)) {
            throw new BusinessException("测试启动校验失败: 缺少可执行程序");
        }

        if (looksLikePath(executable)) {
            try {
                Path path = Path.of(executable).toAbsolutePath().normalize();
                if (!Files.exists(path)) {
                    throw new BusinessException("可执行程序不存在: " + path);
                }
                if (!Files.isRegularFile(path)) {
                    throw new BusinessException("可执行程序不是有效文件: " + path);
                }
                return path.toString();
            } catch (InvalidPathException exception) {
                throw new BusinessException("可执行程序路径非法: " + executable, exception);
            }
        }

        String resolved = resolveFromPath(executable);
        if (resolved != null) {
            return resolved;
        }

        warnings.add("未在当前 PATH 中解析到首个可执行程序，可能依赖外部 shell 或 WSL 环境。");
        return null;
    }

    private boolean looksLikePath(String value) {
        return value.contains("\\") || value.contains("/") || value.contains(":");
    }

    private String resolveFromPath(String executable) {
        String pathEnv = System.getenv("PATH");
        if (!StringUtils.hasText(pathEnv)) {
            return null;
        }

        List<String> candidates = buildExecutableCandidates(executable);
        for (String directory : pathEnv.split(Pattern.quote(File.pathSeparator))) {
            if (!StringUtils.hasText(directory)) {
                continue;
            }
            for (String candidate : candidates) {
                try {
                    Path path = Path.of(directory, candidate).normalize();
                    if (Files.isRegularFile(path)) {
                        return path.toAbsolutePath().toString();
                    }
                } catch (InvalidPathException ignored) {
                }
            }
        }
        return null;
    }

    private List<String> buildExecutableCandidates(String executable) {
        List<String> candidates = new ArrayList<>();
        candidates.add(executable);

        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        if (!isWindows || hasExecutableExtension(executable)) {
            return candidates;
        }

        String pathExt = System.getenv("PATHEXT");
        String extensions = StringUtils.hasText(pathExt) ? pathExt : ".EXE;.CMD;.BAT;.COM";
        for (String extension : extensions.split(";")) {
            if (!StringUtils.hasText(extension)) {
                continue;
            }
            candidates.add(executable + extension.toLowerCase(Locale.ROOT));
            candidates.add(executable + extension.toUpperCase(Locale.ROOT));
        }
        return candidates;
    }

    private boolean hasExecutableExtension(String executable) {
        String lower = executable.toLowerCase(Locale.ROOT);
        return lower.endsWith(".exe") || lower.endsWith(".cmd") || lower.endsWith(".bat") || lower.endsWith(".com");
    }
}
