package com.aliano.mutiagent.infrastructure.process;

import com.aliano.mutiagent.application.service.SessionStreamAppService;
import com.aliano.mutiagent.bootstrap.StoragePathManager;
import com.aliano.mutiagent.common.exception.BusinessException;
import com.aliano.mutiagent.config.MutiAgentProperties;
import com.aliano.mutiagent.infrastructure.adapter.LaunchPlan;
import com.aliano.mutiagent.infrastructure.adapter.ParseResult;
import com.aliano.mutiagent.infrastructure.storage.SessionRawLogWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalProcessSupervisor implements ProcessSupervisor {

    private final SessionStreamAppService sessionStreamAppService;
    private final StoragePathManager storagePathManager;
    private final SessionRawLogWriter sessionRawLogWriter;
    private final MutiAgentProperties properties;
    private final Executor processTaskExecutor;
    private final Map<String, ManagedProcess> processRegistry = new ConcurrentHashMap<>();

    public LocalProcessSupervisor(SessionStreamAppService sessionStreamAppService,
                                  StoragePathManager storagePathManager,
                                  SessionRawLogWriter sessionRawLogWriter,
                                  MutiAgentProperties properties,
                                  @Qualifier("processTaskExecutor") Executor processTaskExecutor) {
        this.sessionStreamAppService = sessionStreamAppService;
        this.storagePathManager = storagePathManager;
        this.sessionRawLogWriter = sessionRawLogWriter;
        this.properties = properties;
        this.processTaskExecutor = processTaskExecutor;
    }

    @Override
    public ProcessRuntime start(SessionLaunchContext context) throws IOException {
        LaunchPlan launchPlan = context.adapter().buildLaunchPlan(context.instance(), context.session());
        ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command());
        if (StringUtils.hasText(launchPlan.workingDirectory())) {
            processBuilder.directory(Path.of(launchPlan.workingDirectory()).toFile());
        }
        processBuilder.redirectErrorStream(false);
        processBuilder.environment().putAll(launchPlan.environment());

        Process process = processBuilder.start();
        Path rawLogPath = storagePathManager.createSessionLogPath(context.session().getId());
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        ProcessRuntime runtime = new ProcessRuntime(
                context.session().getId(),
                process.pid(),
                rawLogPath.toString(),
                OffsetDateTime.now().toString(),
                launchPlan.command()
        );
        ManagedProcess managedProcess = new ManagedProcess(process, writer, runtime, context);
        processRegistry.put(context.session().getId(), managedProcess);

        processTaskExecutor.execute(() -> consumeStream(managedProcess, process.getInputStream(), "stdout"));
        processTaskExecutor.execute(() -> consumeStream(managedProcess, process.getErrorStream(), "stderr"));
        processTaskExecutor.execute(() -> watchExit(managedProcess));
        return runtime;
    }

    @Override
    public void sendInput(String sessionId, String input, boolean appendNewLine) throws IOException {
        ManagedProcess managedProcess = requireProcess(sessionId);
        managedProcess.writer.write(input);
        if (appendNewLine) {
            managedProcess.writer.newLine();
        }
        managedProcess.writer.flush();
    }

    @Override
    public void stop(String sessionId, StopMode stopMode) {
        ManagedProcess managedProcess = requireProcess(sessionId);
        if (stopMode == StopMode.FORCE) {
            managedProcess.process.destroyForcibly();
            return;
        }
        managedProcess.process.destroy();
        try {
            boolean finished = managedProcess.process.waitFor(properties.getRuntime().getGracefulStopWaitMs(),
                    TimeUnit.MILLISECONDS);
            if (!finished) {
                managedProcess.process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            managedProcess.process.destroyForcibly();
        }
    }

    @Override
    public List<ProcessRuntime> listRunning() {
        return processRegistry.values().stream().map(managedProcess -> managedProcess.runtime).toList();
    }

    @Override
    public long countRunning() {
        return processRegistry.size();
    }

    private ManagedProcess requireProcess(String sessionId) {
        ManagedProcess managedProcess = processRegistry.get(sessionId);
        if (managedProcess == null) {
            throw new BusinessException("会话进程不存在或已结束");
        }
        return managedProcess;
    }

    private void consumeStream(ManagedProcess managedProcess, InputStream inputStream, String streamName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sessionRawLogWriter.append(Path.of(managedProcess.runtime.rawLogPath()), streamName, line);
                ParseResult parseResult = managedProcess.context.adapter()
                        .parseOutput(managedProcess.context.instance(), streamName, line);
                sessionStreamAppService.handleProcessOutput(
                        managedProcess.context.session().getId(),
                        managedProcess.context.instance().getAdapterType(),
                        streamName,
                        line,
                        parseResult
                );
            }
        } catch (IOException exception) {
            sessionStreamAppService.handleSupervisorError(managedProcess.context.session().getId(), exception.getMessage());
        }
    }

    private void watchExit(ManagedProcess managedProcess) {
        try {
            int exitCode = managedProcess.process.waitFor();
            processRegistry.remove(managedProcess.context.session().getId());
            sessionStreamAppService.handleProcessExit(managedProcess.context.session().getId(), exitCode);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            processRegistry.remove(managedProcess.context.session().getId());
            sessionStreamAppService.handleSupervisorError(managedProcess.context.session().getId(), exception.getMessage());
        }
    }

    private static final class ManagedProcess {

        private final Process process;
        private final BufferedWriter writer;
        private final ProcessRuntime runtime;
        private final SessionLaunchContext context;

        private ManagedProcess(Process process, BufferedWriter writer, ProcessRuntime runtime, SessionLaunchContext context) {
            this.process = process;
            this.writer = writer;
            this.runtime = runtime;
            this.context = context;
        }
    }
}
