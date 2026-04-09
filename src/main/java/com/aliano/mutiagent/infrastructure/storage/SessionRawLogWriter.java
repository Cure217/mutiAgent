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

    private final Map<String, Object> lockMap = new ConcurrentHashMap<>();

    public void append(Path logPath, String streamName, String chunk) throws IOException {
        Object lock = lockMap.computeIfAbsent(logPath.toString(), key -> new Object());
        synchronized (lock) {
            String line = "[" + OffsetDateTime.now() + "][" + streamName + "] " + chunk + System.lineSeparator();
            Files.writeString(logPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
