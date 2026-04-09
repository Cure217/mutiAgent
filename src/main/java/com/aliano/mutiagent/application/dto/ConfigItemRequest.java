package com.aliano.mutiagent.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfigItemRequest(
        @NotBlank(message = "配置分组不能为空") String configGroup,
        @NotBlank(message = "配置键不能为空") String configKey,
        @NotBlank(message = "配置值类型不能为空") String valueType,
        String valueText,
        String valueJson,
        String secretRef,
        String remark) {
}
