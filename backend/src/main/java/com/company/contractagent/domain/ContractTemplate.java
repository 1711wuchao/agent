package com.company.contractagent.domain;

import java.util.List;

public record ContractTemplate(
        String code,
        String name,
        String fileName,
        String version,
        boolean enabled,
        List<TemplateField> fields
) {
}

