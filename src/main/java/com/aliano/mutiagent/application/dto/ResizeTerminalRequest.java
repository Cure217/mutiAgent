package com.aliano.mutiagent.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ResizeTerminalRequest(
        @Min(value = 20, message = "终端列数不能小于 20")
        @Max(value = 500, message = "终端列数不能大于 500")
        Integer cols,
        @Min(value = 5, message = "终端行数不能小于 5")
        @Max(value = 300, message = "终端行数不能大于 300")
        Integer rows) {
}
