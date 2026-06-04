package com.company.contractagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContractFieldExtractionService {
    private static final String SYSTEM_PROMPT = """
            你是合同字段抽取助手。请从用户自然语言中提取合同生成字段。
            只输出严格 JSON，不要输出 Markdown、解释、前后缀文字。
            缺失字段必须为 null，不要猜测。
            数量可以拆分为 {"value": number, "unit": "string"}。
            税率输出数字，例如 13。
            金额输出人民币元数字，例如 30万 输出 300000。
            日期统一输出 YYYY-MM-DD。
            JSON 格式必须是：
            {
              "甲方名称": "string or null",
              "乙方名称": "string or null",
              "产品名称": "string or null",
              "规格型号": "string or null",
              "数量": { "value": number, "unit": "string" } | null,
              "税率": number | null,
              "价税合计": number | null,
              "签订日期": "YYYY-MM-DD" | null,
              "付款方式": "string" | null
            }
            """;

    private static final Pattern COMPANY_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9（）()·]{2,80}(?:公司|集团|企业|中心|合作社|厂))");
    private static final Pattern PARTY_A_PATTERN = Pattern.compile("(?:甲方\\s*(?:采购公司抬头|采购公司|购买方)?|购买方|买方|采购方)\\s*(?:[:：为是])?\\s*([\\u4e00-\\u9fa5A-Za-z0-9（）()·]{2,80}(?:公司|集团|企业|中心|合作社|厂))");
    private static final Pattern PARTY_B_PATTERN = Pattern.compile("(?:乙方\\s*(?:销售公司|销售方|卖方)?|销售方|销方|卖方|供货方)\\s*(?:[:：为是])?\\s*([\\u4e00-\\u9fa5A-Za-z0-9（）()·]{2,80}(?:公司|集团|企业|中心|合作社|厂))");
    private static final Pattern PRODUCT_PATTERN = Pattern.compile("(?:产品名称|产品|品名|标的物|货物名称)\\s*(?:[:：为是])?\\s*([^，。；;\\n]+)");
    private static final Pattern SPEC_PATTERN = Pattern.compile("(?:规格型号|规格|型号)\\s*(?:[:：为是])?\\s*([A-Za-z0-9][A-Za-z0-9._\\-/]*)");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(?:数量|数目)?\\s*([0-9]+(?:\\.[0-9]+)?\\s*(?:套|台|件|个|项|批|只|箱|组|米|吨|kg|KG))");
    private static final Pattern TAX_RATE_PATTERN = Pattern.compile("(?:税率)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:价税合计|合同金额|总金额|金额)\\s*(?:[:：为是])?\\s*([0-9,]+(?:\\.[0-9]+)?\\s*(?:万元|万|元)?)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[.年\\-/](\\d{1,2})(?:[.月\\-/](\\d{1,2})日?)?");
    private static final Pattern PAYMENT_PATTERN = Pattern.compile("(?:付款方式|付款条件|支付方式)\\s*(?:[:：为是])?\\s*([^，。；;\\n]+)");

    private final KimiClientService kimiClientService;
    private final ObjectMapper objectMapper;

    public ContractFieldExtractionService(KimiClientService kimiClientService, ObjectMapper objectMapper) {
        this.kimiClientService = kimiClientService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> extractContractFields(String userInput) {
        Map<String, Object> chineseFields = null;
        if (kimiClientService.isEnabled()) {
            try {
                chineseFields = extractWithKimi(userInput);
            } catch (RuntimeException ignored) {
                chineseFields = null;
            }
        }
        if (chineseFields == null) {
            chineseFields = fallbackExtract(userInput);
        }
        return toTemplateFields(chineseFields);
    }

    private Map<String, Object> extractWithKimi(String userInput) {
        Map<String, Object> completion = kimiClientService.chatCompletion(List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userInput)
        ));
        String content = assistantContent(completion);
        try {
            Map<String, Object> parsed = objectMapper.readValue(extractJson(content), new TypeReference<>() {
            });
            return normalizeChineseFields(parsed);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Kimi returned invalid JSON", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private String assistantContent(Map<String, Object> completion) {
        Object choicesValue = completion.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalArgumentException("Kimi returned empty choices");
        }
        Object messageValue = ((Map<String, Object>) choices.get(0)).get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new IllegalArgumentException("Kimi returned invalid message");
        }
        Object content = message.get("content");
        if (content == null || String.valueOf(content).isBlank()) {
            throw new IllegalArgumentException("Kimi returned empty content");
        }
        return String.valueOf(content);
    }

    private String extractJson(String content) {
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("No JSON object found");
        }
        return text.substring(start, end + 1);
    }

    private Map<String, Object> fallbackExtract(String text) {
        Map<String, Object> fields = emptyChineseFields();
        put(fields, "甲方名称", firstGroup(PARTY_A_PATTERN, text));
        put(fields, "乙方名称", firstGroup(PARTY_B_PATTERN, text));
        inferPartiesFromCompanyOrder(fields, text);
        put(fields, "产品名称", extractProductName(text));
        put(fields, "规格型号", firstGroup(SPEC_PATTERN, text));
        put(fields, "数量", parseQuantity(firstGroup(QUANTITY_PATTERN, text)));
        put(fields, "税率", parseNumber(firstGroup(TAX_RATE_PATTERN, text)));
        put(fields, "价税合计", parseAmount(firstGroup(AMOUNT_PATTERN, text)));
        put(fields, "签订日期", normalizeDate(text));
        put(fields, "付款方式", cleanPayment(firstGroup(PAYMENT_PATTERN, text)));
        return fields;
    }

    private Map<String, Object> normalizeChineseFields(Map<String, Object> raw) {
        Map<String, Object> fields = emptyChineseFields();
        put(fields, "甲方名称", stringOrNull(raw.get("甲方名称")));
        put(fields, "乙方名称", stringOrNull(raw.get("乙方名称")));
        put(fields, "产品名称", cleanProductName(stringOrNull(raw.get("产品名称"))));
        put(fields, "规格型号", stringOrNull(raw.get("规格型号")));
        put(fields, "数量", normalizeQuantity(raw.get("数量")));
        put(fields, "税率", parseNumber(raw.get("税率")));
        put(fields, "价税合计", parseAmount(raw.get("价税合计")));
        put(fields, "签订日期", normalizeDate(stringOrNull(raw.get("签订日期"))));
        put(fields, "付款方式", stringOrNull(raw.get("付款方式")));
        return fields;
    }

    private Map<String, Object> toTemplateFields(Map<String, Object> chineseFields) {
        Map<String, Object> fields = new LinkedHashMap<>();
        put(fields, "甲方名称", chineseFields.get("甲方名称"));
        put(fields, "乙方名称", chineseFields.get("乙方名称"));
        put(fields, "产品名称", chineseFields.get("产品名称"));
        put(fields, "规格型号", chineseFields.get("规格型号"));
        put(fields, "数量", quantityText(chineseFields.get("数量")));
        put(fields, "税率", percentText(chineseFields.get("税率")));
        put(fields, "价税合计", amountText(chineseFields.get("价税合计")));
        put(fields, "签订日期", chineseFields.get("签订日期"));
        put(fields, "付款方式", chineseFields.get("付款方式"));

        put(fields, "partyA", chineseFields.get("甲方名称"));
        put(fields, "partyB", chineseFields.get("乙方名称"));
        put(fields, "productName", chineseFields.get("产品名称"));
        put(fields, "specification", chineseFields.get("规格型号"));
        put(fields, "quantity", quantityText(chineseFields.get("数量")));
        put(fields, "taxRate", percentText(chineseFields.get("税率")));
        put(fields, "amount", amountText(chineseFields.get("价税合计")));
        put(fields, "contractDate", chineseFields.get("签订日期"));
        put(fields, "invoiceDate", chineseFields.get("签订日期"));
        put(fields, "paymentMethod", chineseFields.get("付款方式"));
        return fields;
    }

    private Map<String, Object> emptyChineseFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("甲方名称", null);
        fields.put("乙方名称", null);
        fields.put("产品名称", null);
        fields.put("规格型号", null);
        fields.put("数量", null);
        fields.put("税率", null);
        fields.put("价税合计", null);
        fields.put("签订日期", null);
        fields.put("付款方式", null);
        return fields;
    }

    private void inferPartiesFromCompanyOrder(Map<String, Object> fields, String text) {
        Matcher matcher = COMPANY_PATTERN.matcher(text);
        if (fields.get("甲方名称") == null && matcher.find()) {
            fields.put("甲方名称", matcher.group(1).trim());
        }
        if (fields.get("乙方名称") == null && matcher.find()) {
            fields.put("乙方名称", matcher.group(1).trim());
        }
    }

    private Object normalizeQuantity(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object number = parseNumber(map.get("value"));
            Object unit = stringOrNull(map.get("unit"));
            if (number == null && unit == null) {
                return null;
            }
            Map<String, Object> quantity = new LinkedHashMap<>();
            quantity.put("value", number);
            quantity.put("unit", unit);
            return quantity;
        }
        return parseQuantity(stringOrNull(value));
    }

    private Object parseQuantity(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(\\D+)").matcher(text.trim());
        if (!matcher.find()) {
            return text.trim();
        }
        Map<String, Object> quantity = new LinkedHashMap<>();
        quantity.put("value", parseNumber(matcher.group(1)));
        quantity.put("unit", matcher.group(2).trim());
        return quantity;
    }

    private String quantityText(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object number = map.get("value");
            Object unit = map.get("unit");
            if (number == null && unit == null) {
                return null;
            }
            return trimNumber(number) + (unit == null ? "" : String.valueOf(unit));
        }
        return stringOrNull(value);
    }

    private String percentText(Object value) {
        Object number = parseNumber(value);
        return number == null ? null : trimNumber(number) + "%";
    }

    private String amountText(Object value) {
        BigDecimal amount = parseAmount(value);
        return amount == null ? null : amount.stripTrailingZeros().toPlainString() + "元";
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).replace(",", "").trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal amount = new BigDecimal(matcher.group(1));
        if (text.contains("万")) {
            amount = amount.multiply(BigDecimal.valueOf(10_000));
        }
        return amount.stripTrailingZeros();
    }

    private Object parseNumber(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).replace(",", "").replace("%", "").trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return new BigDecimal(text).stripTrailingZeros();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String trimNumber(Object value) {
        Object number = parseNumber(value);
        if (number instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return number == null ? "" : String.valueOf(number);
    }

    private String normalizeDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = matcher.group(3) == null ? 1 : Integer.parseInt(matcher.group(3));
        return LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String extractProductName(String text) {
        Matcher matcher = PRODUCT_PATTERN.matcher(text);
        if (matcher.find()) {
            return cleanProductName(matcher.group(1));
        }
        Matcher candidate = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]+(?:配件|设备|软件|系统|算法|站点|服务|产品|无人机)[^\\s，。；;]*)").matcher(text);
        return candidate.find() ? cleanProductName(candidate.group(1)) : null;
    }

    private String cleanProductName(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replace("通用设备", "")
                .replace("*", "")
                .replaceAll("(?:金额|合同金额|价税合计|规格|型号|数量|税率|付款方式|付款条件|签订日期).*$", "")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanPayment(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceAll("\\s*(?:规格型号|规格|型号|数量|税率|价税合计|金额|合同金额|签订日期).*$", "")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private void put(Map<String, Object> fields, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            fields.put(key, value);
        }
    }
}
