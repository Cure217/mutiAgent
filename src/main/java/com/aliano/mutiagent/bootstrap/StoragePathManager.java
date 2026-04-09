package com.aliano.mutiagent.bootstrap;

import com.aliano.mutiagent.config.MutiAgentProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class StoragePathManager {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MutiAgentProperties properties;

    public StoragePathManager(MutiAgentProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(properties.resolveBaseDir());
        Files.createDirectories(properties.resolveDatabasePath().getParent());
        Files.createDirectories(properties.resolveSessionLogDir());
        Files.createDirectories(properties.resolveAppLogDir());
        Files.createDirectories(properties.resolveRuntimeDir());
    }

    public Path createSessionLogPath(String sessionId) throws IOException {
        Path dailyDir = properties.resolveSessionLogDir().resolve(LocalDate.now().format(DATE_FORMATTER));
        Files.createDirectories(dailyDir);
        Path logPath = dailyDir.resolve(sessionId + ".log");
        if (!Files.exists(logPath)) {
            Files.createFile(logPath);
        }
        return logPath;
    }
}
