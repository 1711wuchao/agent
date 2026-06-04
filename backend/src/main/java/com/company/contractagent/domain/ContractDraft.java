package com.company.contractagent.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record ContractDraft(
        String id,
        String contractType,
        String templateCode,
        String title,
        ContractStatus status,
        Map<String, Object> fields,
        OffsetDateTime createdAt
) {
}

