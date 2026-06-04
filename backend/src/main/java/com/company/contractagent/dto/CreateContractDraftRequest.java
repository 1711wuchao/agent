package com.company.contractagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateContractDraftRequest(
        @NotBlank String contractType,
        @NotBlank String templateCode,
        @NotBlank String title,
        @NotNull Map<String, Object> fields
) {
}

