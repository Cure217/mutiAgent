package com.aliano.mutiagent.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SessionRawLogWriter {

    private static final String REPLAY_SUFFIX = ".replay";
    private final Map<String, Object> lockMap = new ConcurrentHashMap<>();

    public void append(Path logPath, String streamName, String chunk) throws IOException {
        Object lock = lockMap.computeIfAbsent(logPath.toString(), key -> new Object());
        synchronized (lock) {
            appendHumanReadableLog(logPath, streamName, chunk);
            appendReplayTranscript(logPath, chunk);
        }
    }

    public static Path resolveReplayPath(Path logPath) {
        return logPath.resolveSibling(logPath.getFileName().toString() + REPLAY_SUFFIX);
    }

    private void appendHumanReadableLog(Path logPath, String streamName, String chunk) throws IOException {
        String line = "[" + OffsetDateTime.now() + "][" + streamName + "] " + chunk + System.lineSeparator();
        Files.writeString(logPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void appendReplayTranscript(Path logPath, String chunk) throws IOException {
        Files.writeString(resolveReplayPath(logPath), chunk, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
