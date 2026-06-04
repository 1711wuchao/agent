package com.company.contractagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ToolCallRequest(
        @NotBlank String toolName,
        @NotNull Map<String, Object> arguments
) {
}

