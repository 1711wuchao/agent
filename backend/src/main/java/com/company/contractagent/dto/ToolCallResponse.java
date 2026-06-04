package com.company.contractagent.dto;

public record ToolCallResponse(
        String toolName,
        boolean success,
        Object data,
        String message
) {
}

