package com.company.contractagent.controller;

import com.company.contractagent.domain.ContractTemplate;
import com.company.contractagent.service.TemplateCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final TemplateCatalogService templateCatalogService;

    public TemplateController(TemplateCatalogService templateCatalogService) {
        this.templateCatalogService = templateCatalogService;
    }

    @GetMapping
    public List<ContractTemplate> listTemplates() {
        return templateCatalogService.listTemplates();
    }

    @GetMapping("/{code}")
    public ContractTemplate getTemplate(@PathVariable String code) {
        return templateCatalogService.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在：" + code));
    }
}

