package com.aliano.mutiagent.infrastructure.adapter;

import java.util.List;
import java.util.Map;

public record LaunchPlan(List<String> command, Map<String, String> environment, String workingDirectory) {
}
