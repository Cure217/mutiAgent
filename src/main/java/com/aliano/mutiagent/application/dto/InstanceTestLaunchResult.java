package com.aliano.mutiagent.application.dto;

import java.util.List;

public record InstanceTestLaunchResult(
        boolean valid,
        String adapterType,
        List<String> command,
        String executable,
        String resolvedExecutable,
        String workingDirectory,
        List<String> environmentKeys,
        List<String> warnings) {
}
