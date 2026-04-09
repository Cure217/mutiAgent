package com.aliano.mutiagent.infrastructure.process;

import java.util.List;

public record ProcessRuntime(
        String sessionId,
        Long pid,
        String rawLogPath,
        String startedAt,
        List<String> command) {
}
