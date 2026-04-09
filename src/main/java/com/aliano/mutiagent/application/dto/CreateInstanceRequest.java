package com.aliano.mutiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record CreateInstanceRequest(
        @NotBlank(message = "实例名称不能为空") String name,
        @NotBlank(message = "应用类型不能为空") String appType,
        String adapterType,
        @NotBlank(message = "运行环境不能为空") String runtimeEnv,
        @NotBlank(message = "启动模式不能为空") String launchMode,
        String executablePath,
        @NotBlank(message = "启动命令不能为空") String launchCommand,
        List<String> args,
        String workdir,
        Map<String, String> env,
        Boolean enabled,
        Boolean autoRestart,
        String remark) {
}
