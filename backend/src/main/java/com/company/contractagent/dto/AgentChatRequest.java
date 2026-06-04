package com.company.contractagent.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record AgentChatRequest(
        String sessionId,
        @NotBlank String message,
        Map<String, Object> fields
) {
}
