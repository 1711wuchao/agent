package com.company.contractagent.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeChunkRequest(
        @NotBlank String title,
        @NotBlank String content,
        String contractType,
        String clauseType,
        String riskLevel,
        String source
) {
}
