package com.company.contractagent.domain;

public record TemplateField(
        String code,
        String label,
        String type,
        boolean required,
        String placeholder
) {
}

