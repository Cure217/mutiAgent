package com.aliano.mutiagent.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public final class BackendInstanceLock {

    private static FileChannel lockChannel;
    private static FileLock lock;

    private BackendInstanceLock() {
    }

    public static synchronized void acquire(Environment environment) {
        if (lock != null && lock.isValid()) {
            return;
        }

        try {
            Path runtimeDir = resolveRuntimeDir(environment);
            Files.createDirectories(runtimeDir);
            Path lockFile = runtimeDir.resolve("backend.lock");
            lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            if (lock == null) {
                closeQuietly();
                throw new IllegalStateException("检测到已有后端实例正在运行，请先关闭已有服务，再重新启动。");
            }
        } catch (IOException exception) {
            closeQuietly();
            throw new IllegalStateException("创建后端实例锁失败: " + exception.getMessage(), exception);
        }
    }

    public static synchronized void release() {
        closeQuietly();
    }

    private static Path resolveRuntimeDir(Environment environment) {
        String configuredBaseDir = environment.getProperty("muti-agent.storage.base-dir");
        Path baseDir;
        if (StringUtils.hasText(configuredBaseDir)) {
            baseDir = Paths.get(configuredBaseDir).toAbsolutePath().normalize();
        } else {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (StringUtils.hasText(localAppData)) {
                baseDir = Paths.get(localAppData, "mutiAgent").toAbsolutePath().normalize();
            } else {
                baseDir = Paths.get(System.getProperty("user.home"), ".mutiAgent").toAbsolutePath().normalize();
            }
        }

        String runtimeRelativeDir = environment.getProperty("muti-agent.storage.runtime-relative-dir", "runtime");
        return baseDir.resolve(runtimeRelativeDir).normalize();
    }

    private static void closeQuietly() {
        if (lock != null) {
            try {
                lock.release();
            } catch (IOException ignored) {
            }
            lock = null;
        }
        if (lockChannel != null) {
            try {
                lockChannel.close();
            } catch (IOException ignored) {
            }
            lockChannel = null;
        }
    }
}
