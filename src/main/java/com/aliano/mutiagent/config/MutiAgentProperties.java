package com.aliano.mutiagent.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "muti-agent")
@Getter
public class MutiAgentProperties {

    private final Storage storage = new Storage();
    private final Runtime runtime = new Runtime();

    public Path resolveBaseDir() {
        if (StringUtils.hasText(storage.baseDir)) {
            return Paths.get(storage.baseDir).toAbsolutePath().normalize();
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (StringUtils.hasText(localAppData)) {
            return Paths.get(localAppData, "mutiAgent").toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".mutiAgent").toAbsolutePath().normalize();
    }

    public Path resolveDatabasePath() {
        return resolveBaseDir().resolve(storage.databaseRelativePath).normalize();
    }

    public Path resolveSessionLogDir() {
        return resolveBaseDir().resolve(storage.sessionLogRelativeDir).normalize();
    }

    public Path resolveAppLogDir() {
        return resolveBaseDir().resolve(storage.appLogRelativeDir).normalize();
    }

    public Path resolveRuntimeDir() {
        return resolveBaseDir().resolve(storage.runtimeRelativeDir).normalize();
    }

    @Getter
    @Setter
    public static class Storage {

        private String baseDir;
        private String databaseRelativePath = "data/muti-agent.db";
        private String sessionLogRelativeDir = "logs/sessions";
        private String appLogRelativeDir = "logs/app";
        private String runtimeRelativeDir = "runtime";

    }

    @Getter
    @Setter
    public static class Runtime {

        private long gracefulStopWaitMs = 1500L;
    }
}
