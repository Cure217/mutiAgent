package com.aliano.mutiagent.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "muti-agent")
public class MutiAgentProperties {

    private final Storage storage = new Storage();
    private final Runtime runtime = new Runtime();

    public Storage getStorage() {
        return storage;
    }

    public Runtime getRuntime() {
        return runtime;
    }

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

    public static class Storage {

        private String baseDir;
        private String databaseRelativePath = "data/muti-agent.db";
        private String sessionLogRelativeDir = "logs/sessions";
        private String appLogRelativeDir = "logs/app";
        private String runtimeRelativeDir = "runtime";

        public String getBaseDir() {
            return baseDir;
        }

        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
        }

        public String getDatabaseRelativePath() {
            return databaseRelativePath;
        }

        public void setDatabaseRelativePath(String databaseRelativePath) {
            this.databaseRelativePath = databaseRelativePath;
        }

        public String getSessionLogRelativeDir() {
            return sessionLogRelativeDir;
        }

        public void setSessionLogRelativeDir(String sessionLogRelativeDir) {
            this.sessionLogRelativeDir = sessionLogRelativeDir;
        }

        public String getAppLogRelativeDir() {
            return appLogRelativeDir;
        }

        public void setAppLogRelativeDir(String appLogRelativeDir) {
            this.appLogRelativeDir = appLogRelativeDir;
        }

        public String getRuntimeRelativeDir() {
            return runtimeRelativeDir;
        }

        public void setRuntimeRelativeDir(String runtimeRelativeDir) {
            this.runtimeRelativeDir = runtimeRelativeDir;
        }
    }

    public static class Runtime {

        private long gracefulStopWaitMs = 1500L;

        public long getGracefulStopWaitMs() {
            return gracefulStopWaitMs;
        }

        public void setGracefulStopWaitMs(long gracefulStopWaitMs) {
            this.gracefulStopWaitMs = gracefulStopWaitMs;
        }
    }
}
