package com.company.contractagent.dto;

public record AgentToolTrace(
        String toolName,
        Object arguments,
        boolean success,
        Object data,
        String message
) {
}
