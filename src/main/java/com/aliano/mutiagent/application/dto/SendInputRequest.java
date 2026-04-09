package com.aliano.mutiagent.application.dto;

import jakarta.validation.constraints.NotBlank;

public record SendInputRequest(
        @NotBlank(message = "输入内容不能为空") String content,
        Boolean appendNewLine) {
}
