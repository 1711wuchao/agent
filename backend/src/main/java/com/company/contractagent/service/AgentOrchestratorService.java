package com.company.contractagent.service;

import com.company.contractagent.domain.ContractDraft;
import com.company.contractagent.domain.ContractTemplate;
import com.company.contractagent.dto.AgentChatRequest;
import com.company.contractagent.dto.AgentChatResponse;
import com.company.contractagent.dto.AgentToolTrace;
import com.company.contractagent.dto.ToolCallRequest;
import com.company.contractagent.dto.ToolCallResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AgentOrchestratorService {
    public static final String SYSTEM_PROMPT = """
            你是企业合同 Agent。

            你的任务是围绕用户的合同目标，自动规划、补全信息、调用工具并生成合同结果。

            规则：
            1. 不允许直接编造合同正文，必须优先使用合同模板工具。
            2. 创建合同前，必须先查询模板和必填字段。
            3. 如果必填字段缺失，必须向用户追问，不得继续生成。
            4. 生成 Word 前，必须先创建合同草稿。
            5. 导出 PDF 前，必须先生成 Word。
            6. 合同生成后，必须调用风险审查工具。
            7. 风险审查结果中如有高风险，必须提示用户确认。
            8. 不确定的信息必须追问，不允许猜测。
            9. 工具调用参数必须是 JSON。
            10. 根据 Excel 或用户输入中的销售方、购买方、开票日期、品名、规格型号、数量、税率和价税合计生成合同。
            11. 同一销售方、同一购买方、同一开票月份归并生成一份合同；不同开票月份分别生成合同；合同日期按开票日期向前推算三个月。
            12. 合同编号不得出现汉字。
            13. 购销合同页数目标为 3-5 页；技术开发合同页数目标为 10-15 页。
            14. 合同付款必须分 2 期：合同签订后支付合同总金额 70%，项目验收通过后 90-100 天内结清未付款项。
            15. 硬件产品类合同必须补充国家验收标准等内容。
            16. 涉及“中城”的合同不得无条件走固定购销模板，必须先根据发票品名判断合同类型。
            17. 品名包含软件、算法、站点的，属于技术开发范畴；涉及中城时同样适用该规则，并使用技术开发合同。
            18. 涉及中城但品名不属于技术开发范畴的硬件或产品类记录，使用中城固定购销模板。
            19. 技术目标、技术实现路径、验收节点和验收标准如用户未提供，由系统使用通用兜底条款补齐。
            20. 合同内不得出现“参考”“参考文献”“通用设备”“不含税金额”“税额”等字样。
            21. 合同内容必须体现税率、价税合计、数量、规格型号；送货地址仅标注“甲方工厂内指定地点”。
            22. 不需要法人签字页；盖章页不得包含表格；不得体现企业地址和法人信息。
            """;

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?\\s*(?:万元|万|元)?)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}(?:[-年/]\\d{1,2})?(?:[-月/]\\d{1,2}日?)?)");
    private static final int MAX_KIMI_TOOL_ROUNDS = 4;

    private final Map<String, AgentSessionState> sessions = new ConcurrentHashMap<>();
    private final TemplateCatalogService templateCatalogService;
    private final KimiToolService kimiToolService;
    private final KimiClientService kimiClientService;
    private final ContractFieldExtractionService contractFieldExtractionService;
    private final ObjectMapper objectMapper;

    public AgentOrchestratorService(
            TemplateCatalogService templateCatalogService,
            KimiToolService kimiToolService,
            KimiClientService kimiClientService,
            ContractFieldExtractionService contractFieldExtractionService,
            ObjectMapper objectMapper
    ) {
        this.templateCatalogService = templateCatalogService;
        this.kimiToolService = kimiToolService;
        this.kimiClientService = kimiClientService;
        this.contractFieldExtractionService = contractFieldExtractionService;
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "AGT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : request.sessionId();
        AgentSessionState state = sessions.computeIfAbsent(sessionId, ignored -> new AgentSessionState());

        if (isContractWorkflow(request.message()) || state.templateCode != null || state.draft != null) {
            return chatWithRules(sessionId, state, request);
        }

        if (kimiClientService.isEnabled()) {
            try {
                return chatWithKimi(sessionId, state, request);
            } catch (RuntimeException exception) {
                return chatWithRules(sessionId, state, request);
            }
        }
        return chatWithRules(sessionId, state, request);
    }

    private AgentChatResponse chatWithKimi(String sessionId, AgentSessionState state, AgentChatRequest request) {
        List<AgentToolTrace> traces = new ArrayList<>();
        state.fields.putAll(extractContractFields(request.message()));
        if (request.fields() != null) {
            state.fields.putAll(request.fields());
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(state.kimiMessages);
        messages.add(Map.of("role", "user", "content", request.message()));

        String finalMessage = null;
        toolLoop:
        for (int round = 0; round < MAX_KIMI_TOOL_ROUNDS; round++) {
            Map<String, Object> completion = kimiClientService.chatCompletion(messages, toolSchemas());
            Map<String, Object> assistantMessage = firstAssistantMessage(completion);
            List<Map<String, Object>> toolCalls = toolCalls(assistantMessage);

            if (toolCalls.isEmpty()) {
                finalMessage = String.valueOf(assistantMessage.getOrDefault("content", "已完成。"));
                messages.add(Map.of("role", "assistant", "content", finalMessage));
                break;
            }

            messages.add(assistantMessage);
            for (Map<String, Object> toolCall : toolCalls) {
                String toolCallId = String.valueOf(toolCall.get("id"));
                Map<String, Object> function = asMap(toolCall.get("function"));
                String toolName = String.valueOf(function.get("name"));
                Map<String, Object> arguments = parseArguments(String.valueOf(function.getOrDefault("arguments", "{}")));

                ToolCallResponse toolResponse = callTool(toolName, arguments, traces);
                applyToolResultToState(state, toolResponse);
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCallId,
                        "name", toolName,
                        "content", toJson(toolResponse)
                ));

                if ("detect_missing_required_fields".equals(toolName)) {
                    List<Map<String, Object>> missingFields = castMissingFields(toolResponse.data());
                    if (!missingFields.isEmpty()) {
                        finalMessage = buildMissingFieldMessage(missingFields);
                        messages.add(Map.of("role", "assistant", "content", finalMessage));
                        break toolLoop;
                    }
                }
            }
        }

        if (finalMessage == null) {
            finalMessage = "工具调用轮次已达到上限，请补充信息或稍后重试。";
        }

        state.kimiMessages = keepRecentConversation(messages);
        List<Map<String, Object>> missingFields = detectCurrentMissingFields(state, traces);
        String stage = decideStage(state, missingFields, finalMessage);

        return response(sessionId, stage, finalMessage, state, currentTemplate(state), missingFields, traces);
    }

    private AgentChatResponse chatWithRules(String sessionId, AgentSessionState state, AgentChatRequest request) {
        List<AgentToolTrace> traces = new ArrayList<>();

        state.fields.putAll(extractContractFields(request.message()));
        if (request.fields() != null) {
            state.fields.putAll(request.fields());
        }

        Optional<ContractTemplate> selected = selectTemplate(request.message(), state.templateCode);
        if (selected.isEmpty()) {
            callTool("search_contract_template", Map.of(), traces);
            return response(sessionId, "NEED_CONTRACT_TYPE", "请先告诉我要生成哪类合同，例如产品购销合同、技术开发合同、服务合同、保密协议或补充协议。", state, null, List.of(), traces);
        }

        ContractTemplate template = selected.get();
        state.templateCode = template.code();
        state.contractType = template.name();
        callTool("search_contract_template", Map.of("contractType", template.name()), traces);
        callTool("get_template_required_fields", Map.of("templateCode", template.code()), traces);

        ToolCallResponse missingResponse = callTool("detect_missing_required_fields", Map.of(
                "templateCode", template.code(),
                "fields", state.fields
        ), traces);
        List<Map<String, Object>> missingFields = castMissingFields(missingResponse.data());
        if (!missingFields.isEmpty()) {
            return response(sessionId, "NEED_FIELDS", buildMissingFieldMessage(missingFields), state, template, missingFields, traces);
        }

        if (state.draft == null) {
            ToolCallResponse draftResponse = callTool("create_contract_draft", Map.of(
                    "contractType", template.name(),
                    "templateCode", template.code(),
                    "title", buildTitle(template, state.fields),
                    "fields", state.fields
            ), traces);
            state.draft = (ContractDraft) draftResponse.data();
        } else {
            ToolCallResponse updateResponse = callTool("update_contract_fields", Map.of(
                    "contractId", state.draft.id(),
                    "fields", state.fields
            ), traces);
            state.draft = (ContractDraft) updateResponse.data();
        }

        callTool("search_clause_knowledge", Map.of(
                "query", template.name() + " 风险条款 付款 违约",
                "vectorStore", "Chroma MVP"
        ), traces);

        ToolCallResponse riskResponse = callTool("review_contract_risk", Map.of("contractId", state.draft.id()), traces);
        state.riskReport = asMap(riskResponse.data());

        if (isHighRisk(state.riskReport) && !isConfirmation(request.message())) {
            return response(sessionId, "RISK_CONFIRMATION", "这份合同存在高风险项，已暂停生成文件。请确认是否继续生成，或先调整字段。", state, template, List.of(), traces);
        }

        ToolCallResponse docxResponse = callTool("generate_contract_docx", Map.of("contractId", state.draft.id()), traces);
        ToolCallResponse pdfResponse = callTool("export_contract_pdf", Map.of("contractId", state.draft.id()), traces);
        state.generatedFiles.clear();
        state.generatedFiles.put("docx", docxResponse.data());
        state.generatedFiles.put("pdf", pdfResponse.data());

        return response(sessionId, "DONE", "合同草稿、风险审查和 Word 已完成。PDF 当前为待转换状态，云端启用 LibreOffice 后可生成真实 PDF。", state, template, List.of(), traces);
    }

    private ToolCallResponse callTool(String toolName, Map<String, Object> arguments, List<AgentToolTrace> traces) {
        ToolCallResponse response = kimiToolService.call(new ToolCallRequest(toolName, arguments));
        traces.add(new AgentToolTrace(toolName, arguments, response.success(), response.data(), response.message()));
        return response;
    }

    private Map<String, Object> extractContractFields(String message) {
        return contractFieldExtractionService.extractContractFields(message);
    }

    private void applyToolResultToState(AgentSessionState state, ToolCallResponse response) {
        if (!response.success()) {
            return;
        }
        switch (response.toolName()) {
            case "search_contract_template" -> {
                List<?> templates = response.data() instanceof List<?> list ? list : List.of();
                if (!templates.isEmpty() && templates.get(0) instanceof ContractTemplate template) {
                    state.templateCode = template.code();
                    state.contractType = template.name();
                }
            }
            case "create_contract_draft", "update_contract_fields" -> {
                if (response.data() instanceof ContractDraft draft) {
                    state.draft = draft;
                    state.templateCode = draft.templateCode();
                    state.contractType = draft.contractType();
                    state.fields.clear();
                    state.fields.putAll(draft.fields());
                }
            }
            case "review_contract_risk" -> state.riskReport = asMap(response.data());
            case "generate_contract_docx" -> state.generatedFiles.put("docx", response.data());
            case "export_contract_pdf" -> state.generatedFiles.put("pdf", response.data());
            default -> {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstAssistantMessage(Map<String, Object> completion) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) completion.getOrDefault("choices", List.of());
        if (choices.isEmpty()) {
            return Map.of("role", "assistant", "content", "Kimi 没有返回有效结果。");
        }
        return (Map<String, Object>) choices.get(0).get("message");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toolCalls(Map<String, Object> assistantMessage) {
        Object value = assistantMessage.get("tool_calls");
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private Map<String, Object> parseArguments(String arguments) {
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return String.valueOf(value);
        }
    }

    private List<Map<String, Object>> keepRecentConversation(List<Map<String, Object>> messages) {
        return messages.stream()
                .filter(message -> !"system".equals(message.get("role")))
                .skip(Math.max(0, messages.size() - 12))
                .toList();
    }

    private String decideStage(AgentSessionState state, List<Map<String, Object>> missingFields, String finalMessage) {
        if (!missingFields.isEmpty()) {
            return "NEED_FIELDS";
        }
        if (state.draft == null) {
            return "PLANNING";
        }
        if (isHighRisk(state.riskReport) && !finalMessage.contains("已完成")) {
            return "RISK_CONFIRMATION";
        }
        if (!state.generatedFiles.isEmpty()) {
            return "DONE";
        }
        return "DRAFT_CREATED";
    }

    private List<Map<String, Object>> detectCurrentMissingFields(AgentSessionState state, List<AgentToolTrace> traces) {
        if (state.templateCode == null || state.fields.isEmpty()) {
            return List.of();
        }
        ToolCallResponse missingResponse = callTool("detect_missing_required_fields", Map.of(
                "templateCode", state.templateCode,
                "fields", state.fields
        ), traces);
        return castMissingFields(missingResponse.data());
    }

    private ContractTemplate currentTemplate(AgentSessionState state) {
        if (state.templateCode == null) {
            return null;
        }
        return templateCatalogService.findByCode(state.templateCode).orElse(null);
    }

    private Optional<ContractTemplate> selectTemplate(String message, String currentTemplateCode) {
        String templateCode = inferTemplateCode(message);
        if (templateCode == null) {
            templateCode = currentTemplateCode;
        }
        if (templateCode == null) {
            return Optional.empty();
        }
        return templateCatalogService.findByCode(templateCode);
    }

    private static boolean isContractWorkflow(String message) {
        return message.contains("合同")
                || message.contains("甲方")
                || message.contains("乙方")
                || message.contains("购买方")
                || message.contains("销售方")
                || message.contains("开票日期")
                || message.contains("价税合计");
    }

    private static String inferTemplateCode(String message) {
        if (message.contains("站点") || message.contains("软件") || message.contains("算法") || message.contains("技术开发")) {
            return "TECH_DEVELOPMENT";
        }
        if (message.contains("采购") || message.contains("购销") || message.contains("产品")) {
            return "PURCHASE";
        }
        if (message.contains("服务") || message.contains("技术服务")) {
            return "SERVICE";
        }
        if (message.contains("保密") || message.toUpperCase().contains("NDA")) {
            return "NDA";
        }
        if (message.contains("补充") || message.contains("变更")) {
            return "SUPPLEMENT";
        }
        if (message.contains("销售") || message.contains("买卖")) {
            return "SALES";
        }
        return null;
    }

    private static Map<String, Object> extractFields(String message, String templateCode) {
        Map<String, Object> fields = new LinkedHashMap<>();
        putIfPresent(fields, "partyA", extractAfterAnyLabel(message, List.of("甲方", "购买方", "买方", "委托方")));
        putIfPresent(fields, "partyB", extractAfterAnyLabel(message, List.of("乙方", "销售方", "卖方", "开发方")));
        putIfPresent(fields, "client", extractAfterLabel(message, "委托方"));
        putIfPresent(fields, "provider", extractAfterLabel(message, "服务方"));
        putIfPresent(fields, "disclosingParty", extractAfterLabel(message, "披露方"));
        putIfPresent(fields, "receivingParty", extractAfterLabel(message, "接收方"));
        putIfPresent(fields, "originalContractNo", extractAfterLabel(message, "原合同编号"));
        putIfPresent(fields, "changeItems", extractAfterLabel(message, "变更事项"));
        putIfPresent(fields, "productName", extractAfterAnyLabel(message, List.of("品名", "产品名称", "产品", "货物名称", "货物")));
        putIfPresent(fields, "specification", extractAfterAnyLabel(message, List.of("规格型号", "规格", "型号")));
        putIfPresent(fields, "quantity", extractAfterAnyLabel(message, List.of("数量", "数目")));
        putIfPresent(fields, "taxRate", extractAfterAnyLabel(message, List.of("税率")));
        putIfPresent(fields, "amount", extractAfterAnyLabel(message, List.of("价税合计", "合同金额", "金额", "总金额")));
        putIfPresent(fields, "invoiceDate", normalizeDate(extractAfterAnyLabel(message, List.of("开票日期", "发票日期", "开票日"))));
        putIfPresent(fields, "paymentMethod", extractPayment(message));
        putIfPresent(fields, "serviceScope", extractAfterLabel(message, "服务内容"));
        putIfPresent(fields, "servicePeriod", extractAfterLabel(message, "服务周期"));
        putIfPresent(fields, "confidentialScope", extractAfterLabel(message, "保密范围"));
        putIfPresent(fields, "term", extractAfterLabel(message, "保密期限"));

        String amount = fields.containsKey("amount") ? null : findFirst(AMOUNT_PATTERN, message);
        if (amount != null) {
            if ("SERVICE".equals(templateCode)) {
                fields.put("fee", amount);
            } else {
                fields.put("amount", amount);
            }
        }

        if (!fields.containsKey("taxRate")) {
            putIfPresent(fields, "taxRate", findFirst(Pattern.compile("(\\d+(?:\\.\\d+)?%)"), message));
        }

        String date = fields.containsKey("invoiceDate") ? null : normalizeDate(findFirst(DATE_PATTERN, message));
        if (date != null) {
            if ("SUPPLEMENT".equals(templateCode)) {
                fields.put("effectiveDate", date);
            } else {
                fields.put("invoiceDate", date);
            }
        }
        return fields;
    }

    private static void putIfPresent(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value.trim());
        }
    }

    private static String extractAfterLabel(String message, String label) {
        Pattern pattern = Pattern.compile(label + "\\s*(?:是|为|：|:|=)?\\s*([^，,；;。\\n]+)");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String extractAfterAnyLabel(String message, List<String> labels) {
        for (String label : labels) {
            String value = extractAfterLabel(message, label);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String extractPayment(String message) {
        if (!message.contains("付款")) {
            return null;
        }
        Pattern pattern = Pattern.compile("([^，,；;。\\n]*付款[^，,；;。\\n]*)");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String findFirst(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String normalizeDate(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("年", "-").replace("月", "-").replace("日", "");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMissingFields(Object data) {
        if (data instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static boolean isHighRisk(Map<String, Object> riskReport) {
        return "HIGH".equals(String.valueOf(riskReport.get("riskLevel")));
    }

    private static boolean isConfirmation(String message) {
        return message.contains("确认") || message.contains("继续") || message.contains("同意") || message.contains("生成");
    }

    private static String buildMissingFieldMessage(ContractTemplate template, List<Map<String, Object>> missingFields) {
        String labels = missingFields.stream()
                .map(item -> String.valueOf(item.get("label")))
                .collect(Collectors.joining("、"));
        return "我已匹配到" + template.name() + "模板，但创建合同前还缺少：" + labels + "。请补充这些字段。";
    }

    private static String buildMissingFieldMessage(List<Map<String, Object>> missingFields) {
        String labels = missingFields.stream()
                .map(item -> String.valueOf(item.get("label")))
                .collect(Collectors.joining("、"));
        return "创建合同前还缺少：" + labels + "。这些信息需要由用户或 Excel 提供，请补充后我再继续生成。";
    }

    private static String buildTitle(ContractTemplate template, Map<String, Object> fields) {
        Object counterparty = fields.getOrDefault("partyB", fields.getOrDefault("client", fields.getOrDefault("receivingParty", "")));
        if (counterparty == null || String.valueOf(counterparty).isBlank()) {
            return template.name() + "草稿";
        }
        return "与" + counterparty + "的" + template.name();
    }

    private AgentChatResponse response(String sessionId, String stage, String message, AgentSessionState state, ContractTemplate template, List<Map<String, Object>> missingFields, List<AgentToolTrace> traces) {
        return new AgentChatResponse(
                sessionId,
                stage,
                message,
                template,
                state.fields,
                missingFields,
                state.draft,
                state.riskReport,
                state.generatedFiles,
                traces,
                SYSTEM_PROMPT
        );
    }

    private static List<Map<String, Object>> toolSchemas() {
        return List.of(
                functionTool("search_contract_template", "查询可用合同模板。参数 contractType 可选。", Map.of(
                        "contractType", stringSchema("合同类型，例如销售合同、采购合同、服务合同")
                ), List.of()),
                functionTool("get_template_required_fields", "查询模板字段和必填项。", Map.of(
                        "templateCode", stringSchema("模板编码，例如 SALES")
                ), List.of("templateCode")),
                functionTool("detect_missing_required_fields", "检测创建合同前缺失的必填字段。", Map.of(
                        "templateCode", stringSchema("模板编码"),
                        "fields", objectSchema("已收集的合同字段")
                ), List.of("templateCode", "fields")),
                functionTool("create_contract_draft", "创建合同草稿。", Map.of(
                        "contractType", stringSchema("合同类型"),
                        "templateCode", stringSchema("模板编码"),
                        "title", stringSchema("合同标题"),
                        "fields", objectSchema("合同字段")
                ), List.of("contractType", "templateCode", "title", "fields")),
                functionTool("update_contract_fields", "更新合同草稿字段。", Map.of(
                        "contractId", stringSchema("合同编号"),
                        "fields", objectSchema("要更新的字段")
                ), List.of("contractId", "fields")),
                functionTool("search_clause_knowledge", "检索合同条款知识库。", Map.of(
                        "query", stringSchema("检索问题"),
                        "contractType", stringSchema("合同类型")
                ), List.of("query")),
                functionTool("review_contract_risk", "审查合同风险。", Map.of(
                        "contractId", stringSchema("合同编号"),
                        "fields", objectSchema("未创建合同时可传字段")
                ), List.of()),
                functionTool("generate_contract_docx", "生成 Word 文件。", Map.of(
                        "contractId", stringSchema("合同编号")
                ), List.of("contractId")),
                functionTool("export_contract_pdf", "导出 PDF 文件。", Map.of(
                        "contractId", stringSchema("合同编号")
                ), List.of("contractId"))
        );
    }

    private static Map<String, Object> functionTool(String name, String description, Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", properties,
                                "required", required
                        )
                )
        );
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> objectSchema(String description) {
        return Map.of("type", "object", "description", description);
    }

    private static class AgentSessionState {
        private String templateCode;
        private String contractType;
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private ContractDraft draft;
        private Map<String, Object> riskReport = Map.of();
        private final Map<String, Object> generatedFiles = new LinkedHashMap<>();
        private List<Map<String, Object>> kimiMessages = new ArrayList<>();
    }
}
