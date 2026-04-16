package com.aliano.mutiagent.infrastructure.process;

import com.aliano.mutiagent.application.service.SessionStreamAppService;
import com.aliano.mutiagent.bootstrap.StoragePathManager;
import com.aliano.mutiagent.common.exception.BusinessException;
import com.aliano.mutiagent.config.MutiAgentProperties;
import com.aliano.mutiagent.domain.session.InteractionMode;
import com.aliano.mutiagent.infrastructure.adapter.LaunchPlan;
import com.aliano.mutiagent.infrastructure.adapter.ParseResult;
import com.aliano.mutiagent.infrastructure.storage.SessionRawLogWriter;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashMap;
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

    private static final int DEFAULT_TERMINAL_COLS = 120;
    private static final int DEFAULT_TERMINAL_ROWS = 32;

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
        Charset fallbackCharset = resolveFallbackCharset(context, launchPlan);
        boolean ttyEnabled = shouldUsePty(context);
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.putAll(launchPlan.environment());
        if (ttyEnabled) {
            environment.putIfAbsent("TERM", "xterm-256color");
        }

        Process process = ttyEnabled
                ? startPtyProcess(launchPlan, environment)
                : startRegularProcess(launchPlan, environment);
        Path rawLogPath = storagePathManager.createSessionLogPath(context.session().getId());
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), fallbackCharset));
        ProcessRuntime runtime = new ProcessRuntime(
                context.session().getId(),
                process.pid(),
                rawLogPath.toString(),
                OffsetDateTime.now().toString(),
                launchPlan.command()
        );
        ManagedProcess managedProcess = new ManagedProcess(process, writer, runtime, context, new StreamChunkDecoder(fallbackCharset));
        processRegistry.put(context.session().getId(), managedProcess);

        processTaskExecutor.execute(() -> consumeStream(managedProcess, process.getInputStream(), "stdout"));
        if (!ttyEnabled) {
            processTaskExecutor.execute(() -> consumeStream(managedProcess, process.getErrorStream(), "stderr"));
        }
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
    public void resizeTerminal(String sessionId, int cols, int rows) {
        ManagedProcess managedProcess = processRegistry.get(sessionId);
        if (managedProcess == null) {
            return;
        }
        if (managedProcess.process instanceof PtyProcess ptyProcess) {
            try {
                ptyProcess.setWinSize(new WinSize(cols, rows));
            } catch (RuntimeException exception) {
                throw new BusinessException("调整终端尺寸失败", exception);
            }
        }
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
    public boolean hasProcess(String sessionId) {
        return processRegistry.containsKey(sessionId);
    }

    private Process startRegularProcess(LaunchPlan launchPlan, Map<String, String> environment) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command());
        if (StringUtils.hasText(launchPlan.workingDirectory())) {
            processBuilder.directory(Path.of(launchPlan.workingDirectory()).toFile());
        }
        processBuilder.redirectErrorStream(false);
        processBuilder.environment().putAll(environment);
        return processBuilder.start();
    }

    private Process startPtyProcess(LaunchPlan launchPlan, Map<String, String> environment) throws IOException {
        PtyProcessBuilder builder = new PtyProcessBuilder(launchPlan.command().toArray(String[]::new))
                .setEnvironment(environment)
                .setRedirectErrorStream(true)
                .setInitialColumns(DEFAULT_TERMINAL_COLS)
                .setInitialRows(DEFAULT_TERMINAL_ROWS)
                .setConsole(false);

        if (StringUtils.hasText(launchPlan.workingDirectory())) {
            builder.setDirectory(launchPlan.workingDirectory());
        }

        if (isWindows()) {
            builder.setUseWinConPty(true);
        }

        return builder.start();
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
        try (inputStream) {
            byte[] buffer = new byte[2048];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                emitChunk(managedProcess, streamName, managedProcess.decoder.decode(buffer, length));
            }
            emitChunk(managedProcess, streamName, managedProcess.decoder.flush());
        } catch (IOException exception) {
            sessionStreamAppService.handleSupervisorError(managedProcess.context.session().getId(), exception.getMessage());
        }
    }

    private void emitChunk(ManagedProcess managedProcess, String streamName, String chunk) throws IOException {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        sessionRawLogWriter.append(Path.of(managedProcess.runtime.rawLogPath()), streamName, chunk);
        ParseResult parseResult = managedProcess.context.adapter()
                .parseOutput(managedProcess.context.instance(), streamName, chunk);
        sessionStreamAppService.handleProcessOutput(
                managedProcess.context.session().getId(),
                managedProcess.context.instance().getAdapterType(),
                streamName,
                chunk,
                parseResult
        );
    }

    private Charset resolveFallbackCharset(SessionLaunchContext context, LaunchPlan launchPlan) {
        String configuredCharset = firstNonBlank(
                launchPlan.environment().get("MUTI_AGENT_CHARSET"),
                launchPlan.environment().get("APP_CHARSET"),
                launchPlan.environment().get("PYTHONIOENCODING")
        );
        if (StringUtils.hasText(configuredCharset)) {
            return parseCharset(configuredCharset);
        }

        String locale = firstNonBlank(
                launchPlan.environment().get("LC_ALL"),
                launchPlan.environment().get("LC_CTYPE"),
                launchPlan.environment().get("LANG")
        );
        if (containsUtf8(locale)) {
            return StandardCharsets.UTF_8;
        }

        if ("wsl".equalsIgnoreCase(context.instance().getRuntimeEnv())) {
            return StandardCharsets.UTF_8;
        }

        return Charset.defaultCharset();
    }

    private Charset parseCharset(String charsetName) {
        try {
            return Charset.forName(charsetName.trim());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException exception) {
            throw new BusinessException("不支持的字符集: " + charsetName, exception);
        }
    }

    private boolean containsUtf8(String value) {
        return StringUtils.hasText(value) && value.toLowerCase().contains("utf-8");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean shouldUsePty(SessionLaunchContext context) {
        return "embedded".equalsIgnoreCase(context.instance().getLaunchMode())
                || InteractionMode.RAW.name().equalsIgnoreCase(context.session().getInteractionMode());
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private void watchExit(ManagedProcess managedProcess) {
        try {
            int exitCode = managedProcess.process.waitFor();
            sessionStreamAppService.handleProcessExit(managedProcess.context.session().getId(), exitCode);
            processRegistry.remove(managedProcess.context.session().getId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            sessionStreamAppService.handleSupervisorError(managedProcess.context.session().getId(), exception.getMessage());
            processRegistry.remove(managedProcess.context.session().getId());
        }
    }

    private static final class ManagedProcess {

        private final Process process;
        private final BufferedWriter writer;
        private final ProcessRuntime runtime;
        private final SessionLaunchContext context;
        private final StreamChunkDecoder decoder;

        private ManagedProcess(Process process,
                               BufferedWriter writer,
                               ProcessRuntime runtime,
                               SessionLaunchContext context,
                               StreamChunkDecoder decoder) {
            this.process = process;
            this.writer = writer;
            this.runtime = runtime;
            this.context = context;
            this.decoder = decoder;
        }
    }

    private static final class StreamChunkDecoder {

        private final Charset fallbackCharset;
        private Charset detectedCharset;
        private byte[] remainder = new byte[0];

        private StreamChunkDecoder(Charset fallbackCharset) {
            this.fallbackCharset = fallbackCharset;
        }

        private String decode(byte[] chunk, int length) {
            byte[] merged = merge(chunk, length);
            if (merged.length == 0) {
                return "";
            }
            if (detectedCharset == null) {
                detectedCharset = detectCharset(merged, fallbackCharset);
            }
            return decodeAvailable(merged, false);
        }

        private String flush() {
            if (remainder.length == 0) {
                return "";
            }
            if (detectedCharset == null) {
                detectedCharset = detectCharset(remainder, fallbackCharset);
            }
            String result = decodeAvailable(remainder, true);
            remainder = new byte[0];
            return result;
        }

        private byte[] merge(byte[] chunk, int length) {
            byte[] merged = Arrays.copyOf(remainder, remainder.length + length);
            System.arraycopy(chunk, 0, merged, remainder.length, length);
            return merged;
        }

        private String decodeAvailable(byte[] bytes, boolean flush) {
            if (StandardCharsets.UTF_16LE.equals(detectedCharset) || StandardCharsets.UTF_16BE.equals(detectedCharset)) {
                int decodeLength = flush ? bytes.length : bytes.length - (bytes.length % 2);
                remainder = Arrays.copyOfRange(bytes, decodeLength, bytes.length);
                return new String(bytes, 0, decodeLength, detectedCharset);
            }

            int maxTail = Math.min(4, bytes.length);
            for (int tail = 0; tail <= maxTail; tail++) {
                int decodeLength = bytes.length - tail;
                if (decodeLength <= 0) {
                    continue;
                }
                try {
                    CharBuffer buffer = detectedCharset.newDecoder().decode(ByteBuffer.wrap(bytes, 0, decodeLength));
                    remainder = flush ? new byte[0] : Arrays.copyOfRange(bytes, decodeLength, bytes.length);
                    return buffer.toString();
                } catch (CharacterCodingException ignored) {
                }
            }

            remainder = new byte[0];
            return new String(bytes, detectedCharset);
        }

        private Charset detectCharset(byte[] bytes, Charset fallbackCharset) {
            if (bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xEF
                    && (bytes[1] & 0xFF) == 0xBB
                    && (bytes[2] & 0xFF) == 0xBF) {
                return StandardCharsets.UTF_8;
            }
            if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
            if (looksLikeUtf16WithNullPattern(bytes, true)) {
                return StandardCharsets.UTF_16LE;
            }
            if (looksLikeUtf16WithNullPattern(bytes, false)) {
                return StandardCharsets.UTF_16BE;
            }
            if (isValidUtf8(bytes)) {
                return StandardCharsets.UTF_8;
            }
            if (looksLikeUtf16Printable(bytes, true)) {
                return StandardCharsets.UTF_16LE;
            }
            if (looksLikeUtf16Printable(bytes, false)) {
                return StandardCharsets.UTF_16BE;
            }
            return fallbackCharset;
        }

        private boolean looksLikeUtf16WithNullPattern(byte[] bytes, boolean littleEndian) {
            int pairCount = Math.min(bytes.length / 2, 128);
            if (pairCount < 8) {
                return false;
            }
            int highZeroCount = 0;
            int lowZeroCount = 0;
            for (int index = 0; index < pairCount * 2; index += 2) {
                if (bytes[index] == 0) {
                    highZeroCount++;
                }
                if (bytes[index + 1] == 0) {
                    lowZeroCount++;
                }
            }
            if (littleEndian) {
                return lowZeroCount >= pairCount * 0.35 && lowZeroCount > highZeroCount * 2;
            }
            return highZeroCount >= pairCount * 0.35 && highZeroCount > lowZeroCount * 2;
        }

        private boolean looksLikeUtf16Printable(byte[] bytes, boolean littleEndian) {
            int pairCount = Math.min(bytes.length / 2, 128);
            if (pairCount < 8) {
                return false;
            }
            int printable = 0;
            int control = 0;
            for (int index = 0; index < pairCount * 2; index += 2) {
                int value = littleEndian
                        ? (bytes[index] & 0xFF) | ((bytes[index + 1] & 0xFF) << 8)
                        : ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
                char ch = (char) value;
                if (Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                    control++;
                    continue;
                }
                if (Character.isWhitespace(ch)
                        || Character.isLetterOrDigit(ch)
                        || (ch >= 0x4E00 && ch <= 0x9FFF)
                        || (ch >= 0x3000 && ch <= 0x303F)
                        || (ch >= 0xFF00 && ch <= 0xFFEF)) {
                    printable++;
                }
            }
            return printable >= pairCount * 0.7 && control <= pairCount * 0.1;
        }

        private boolean isValidUtf8(byte[] bytes) {
            try {
                StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
                return true;
            } catch (CharacterCodingException ignored) {
                return false;
            }
        }
    }
}
