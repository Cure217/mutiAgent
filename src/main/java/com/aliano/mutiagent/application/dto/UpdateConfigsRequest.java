package com.aliano.mutiagent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateConfigsRequest(
        @NotEmpty(message = "配置项不能为空") List<@Valid ConfigItemRequest> items) {
}
