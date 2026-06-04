package com.company.contractagent.service;

import com.company.contractagent.domain.ContractTemplate;
import com.company.contractagent.domain.TemplateField;
import com.company.contractagent.dto.CreateContractDraftRequest;
import com.company.contractagent.dto.ToolCallRequest;
import com.company.contractagent.dto.ToolCallResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KimiToolService {
    private final TemplateCatalogService templateCatalogService;
    private final ContractService contractService;
    private final VectorKnowledgeService vectorKnowledgeService;

    public KimiToolService(TemplateCatalogService templateCatalogService, ContractService contractService, VectorKnowledgeService vectorKnowledgeService) {
        this.templateCatalogService = templateCatalogService;
        this.contractService = contractService;
        this.vectorKnowledgeService = vectorKnowledgeService;
    }

    public ToolCallResponse call(ToolCallRequest request) {
        return switch (request.toolName()) {
            case "search_contract_template" -> searchContractTemplate(request);
            case "get_template_required_fields" -> getTemplateRequiredFields(request);
            case "detect_missing_required_fields" -> detectMissingRequiredFields(request);
            case "create_contract_draft" -> createContractDraft(request);
            case "update_contract_fields" -> updateContractFields(request);
            case "generate_contract_docx" -> generateDocx(request);
            case "export_contract_pdf" -> exportPdf(request);
            case "search_clause_knowledge" -> searchClauseKnowledge(request);
            case "review_contract_risk" -> reviewContractRisk(request);
            default -> new ToolCallResponse(request.toolName(), false, null, "未知 Tool：" + request.toolName());
        };
    }

    private ToolCallResponse searchContractTemplate(ToolCallRequest request) {
        Object contractType = request.arguments().get("contractType");
        if (contractType == null || String.valueOf(contractType).isBlank()) {
            return ok(request.toolName(), templateCatalogService.listTemplates(), "已返回可用合同模板");
        }
        return templateCatalogService.findByName(String.valueOf(contractType))
                .map(template -> ok(request.toolName(), List.of(template), "已匹配合同模板"))
                .orElseGet(() -> ok(request.toolName(), templateCatalogService.listTemplates(), "未精确匹配，已返回全部模板"));
    }

    private ToolCallResponse getTemplateRequiredFields(ToolCallRequest request) {
        String templateCode = String.valueOf(request.arguments().get("templateCode"));
        return templateCatalogService.findByCode(templateCode)
                .map(template -> ok(request.toolName(), template.fields(), "已返回模板字段"))
                .orElseGet(() -> new ToolCallResponse(request.toolName(), false, null, "模板不存在：" + templateCode));
    }

    @SuppressWarnings("unchecked")
    private ToolCallResponse detectMissingRequiredFields(ToolCallRequest request) {
        String templateCode = String.valueOf(request.arguments().get("templateCode"));
        Map<String, Object> fields = (Map<String, Object>) request.arguments().getOrDefault("fields", Map.of());
        return templateCatalogService.findByCode(templateCode)
                .map(template -> ok(request.toolName(), missingRequiredFields(template, fields), "已检测缺失字段"))
                .orElseGet(() -> new ToolCallResponse(request.toolName(), false, null, "模板不存在：" + templateCode));
    }

    @SuppressWarnings("unchecked")
    private ToolCallResponse createContractDraft(ToolCallRequest request) {
        Map<String, Object> args = request.arguments();
        CreateContractDraftRequest draftRequest = new CreateContractDraftRequest(
                String.valueOf(args.get("contractType")),
                String.valueOf(args.get("templateCode")),
                String.valueOf(args.getOrDefault("title", "AI 创建的合同草稿")),
                (Map<String, Object>) args.getOrDefault("fields", Map.of())
        );
        return ok(request.toolName(), contractService.createDraft(draftRequest), "合同草稿创建成功");
    }

    @SuppressWarnings("unchecked")
    private ToolCallResponse updateContractFields(ToolCallRequest request) {
        String contractId = String.valueOf(request.arguments().get("contractId"));
        Map<String, Object> fields = (Map<String, Object>) request.arguments().getOrDefault("fields", Map.of());
        return ok(request.toolName(), contractService.updateFields(contractId, fields), "合同字段已更新");
    }

    private ToolCallResponse generateDocx(ToolCallRequest request) {
        String contractId = String.valueOf(request.arguments().get("contractId"));
        return ok(request.toolName(), contractService.generateDocx(contractId), "Word 生成任务已完成");
    }

    private ToolCallResponse exportPdf(ToolCallRequest request) {
        String contractId = String.valueOf(request.arguments().get("contractId"));
        return ok(request.toolName(), contractService.exportPdf(contractId), "PDF 导出任务已完成");
    }

    @SuppressWarnings("unchecked")
    private ToolCallResponse reviewContractRisk(ToolCallRequest request) {
        if (request.arguments().containsKey("contractId")) {
            String contractId = String.valueOf(request.arguments().get("contractId"));
            return ok(request.toolName(), contractService.reviewRisk(contractId), "已返回规则风险审查结果");
        }
        Map<String, Object> fields = (Map<String, Object>) request.arguments().getOrDefault("fields", Map.of());
        return ok(request.toolName(), contractService.reviewRisk(fields), "已返回规则风险审查结果");
    }

    private ToolCallResponse searchClauseKnowledge(ToolCallRequest request) {
        String query = String.valueOf(request.arguments().getOrDefault("query", ""));
        String contractType = String.valueOf(request.arguments().getOrDefault("contractType", ""));
        int limit = 5;
        Object limitValue = request.arguments().get("limit");
        if (limitValue instanceof Number number) {
            limit = number.intValue();
        }
        return ok(request.toolName(), vectorKnowledgeService.search(query, contractType, limit), "已返回 Chroma 知识库检索结果");
    }

    private static ToolCallResponse ok(String toolName, Object data, String message) {
        return new ToolCallResponse(toolName, true, data, message);
    }

    public static List<Map<String, Object>> missingRequiredFields(ContractTemplate template, Map<String, Object> fields) {
        return template.fields().stream()
                .filter(TemplateField::required)
                .filter(field -> isBlank(fields.get(field.code())))
                .map(field -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", field.code());
                    item.put("label", field.label());
                    item.put("type", field.type());
                    item.put("required", field.required());
                    item.put("placeholder", field.placeholder());
                    return item;
                })
                .toList();
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

}
