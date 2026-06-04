package com.company.contractagent.service;

import com.company.contractagent.domain.ContractDraft;
import com.company.contractagent.dto.CreateContractDraftRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ExcelBatchContractService {
    private static final Logger log = LoggerFactory.getLogger(ExcelBatchContractService.class);
    private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ContractService contractService;
    private final ObjectMapper objectMapper;

    public ExcelBatchContractService(ContractService contractService, ObjectMapper objectMapper) {
        this.contractService = contractService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> generateFromExcel(MultipartFile file) {
        List<InvoiceLine> lines = readInvoiceLines(file);
        Map<String, List<InvoiceLine>> groups = lines.stream()
                .collect(Collectors.groupingBy(InvoiceLine::groupKey, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> results = new ArrayList<>();
        for (List<InvoiceLine> groupLines : groups.values()) {
            Map<String, Object> fields = buildFields(groupLines);
            boolean tech = groupLines.stream().anyMatch(line -> isTechProduct(line.productName()));
            String templateCode = tech ? "TECH_DEVELOPMENT" : "PURCHASE";
            String contractType = tech ? "技术开发合同" : "产品购销合同";
            String title = groupLines.get(0).buyer() + "与" + groupLines.get(0).seller() + contractType;

            ContractDraft draft = contractService.createDraft(new CreateContractDraftRequest(
                    contractType,
                    templateCode,
                    title,
                    fields
            ));
            Map<String, Object> docx = contractService.generateDocx(draft.id());
            results.add(Map.of(
                    "contractId", draft.id(),
                    "contractType", contractType,
                    "seller", groupLines.get(0).seller(),
                    "buyer", groupLines.get(0).buyer(),
                    "invoiceDate", groupLines.get(0).invoiceDate().toString(),
                    "lineCount", groupLines.size(),
                    "amount", fields.get("amount"),
                    "fileName", docx.get("fileName"),
                    "downloadUrl", docx.get("downloadUrl")
            ));
        }

        return Map.of(
                "sourceFile", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                "rowCount", lines.size(),
                "contractCount", results.size(),
                "contracts", results
        );
    }

    private List<InvoiceLine> readInvoiceLines(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("Excel 第一行必须是表头");
            }
            Map<String, Integer> columns = headerColumns(header);
            List<InvoiceLine> lines = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String seller = cell(row, columns, formatter, evaluator, "销方名称");
                String buyer = cell(row, columns, formatter, evaluator, "购买方名称");
                String invoiceDate = cell(row, columns, formatter, evaluator, "开票日期");
                String productName = cell(row, columns, formatter, evaluator, "货物或应税劳务名称");
                if (seller.isBlank() || buyer.isBlank() || invoiceDate.isBlank() || productName.isBlank()) {
                    continue;
                }
                lines.add(new InvoiceLine(
                        seller,
                        buyer,
                        parseInvoiceDate(invoiceDate),
                        productName,
                        cell(row, columns, formatter, evaluator, "规格型号"),
                        cell(row, columns, formatter, evaluator, "单位"),
                        cell(row, columns, formatter, evaluator, "数量"),
                        cell(row, columns, formatter, evaluator, "税率"),
                        money(cell(row, columns, formatter, evaluator, "价税合计"))
                ));
            }
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("未读取到有效发票行");
            }
            return lines;
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取 Excel 失败：" + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> buildFields(List<InvoiceLine> lines) {
        InvoiceLine first = lines.get(0);
        BigDecimal total = lines.stream()
                .map(InvoiceLine::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        List<Map<String, Object>> rawItems = lines.stream()
                .map(this::rawItem)
                .toList();
        List<Map<String, Object>> items = mergeItems(lines);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("partyA", first.buyer());
        fields.put("partyB", first.seller());
        fields.put("productName", items.size() == 1 ? items.get(0).get("productName") : "多项合同标的");
        fields.put("specification", items.size() == 1 ? items.get(0).get("specification") : "详见合同标的表");
        fields.put("quantity", items.size() == 1 ? items.get(0).get("quantity") : "详见合同标的表");
        fields.put("taxRate", commonTaxRate(lines));
        fields.put("amount", totalText(total));
        fields.put("invoiceDate", first.invoiceDate().toString());
        fields.put("rawItemsJson", toJson(rawItems));
        fields.put("itemsJson", toJson(items));
        fields.put("technicalGoal", TechContractDefaults.technicalGoal(null));
        fields.put("implementationPath", TechContractDefaults.DEFAULT_IMPLEMENTATION_PATH);
        fields.put("acceptanceMilestones", TechContractDefaults.DEFAULT_ACCEPTANCE_MILESTONES);
        fields.put("acceptanceStandard", TechContractDefaults.DEFAULT_ACCEPTANCE_STANDARD);
        fields.put("disputeResolution", "因本合同产生的争议，提交甲方所在地有管辖权的人民法院处理。");
        return fields;
    }

    private Map<String, Integer> headerColumns(Row header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        for (Cell cell : header) {
            columns.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
        }
        List<String> required = List.of("销方名称", "购买方名称", "开票日期", "货物或应税劳务名称", "规格型号", "数量", "税率", "价税合计");
        for (String name : required) {
            if (!columns.containsKey(name)) {
                throw new IllegalArgumentException("Excel 缺少表头：" + name);
            }
        }
        return columns;
    }

    private static String cell(Row row, Map<String, Integer> columns, DataFormatter formatter, FormulaEvaluator evaluator, String name) {
        Integer index = columns.get(name);
        if (index == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    private static LocalDate parseInvoiceDate(String value) {
        String normalized = value.trim();
        if (normalized.length() >= 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
        }
    }

    private static BigDecimal money(String value) {
        String text = value == null ? "" : value.replace(",", "").replace("¥", "").trim();
        if (text.isBlank()) {
            return BigDecimal.ZERO;
        }
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if (!matcher.find()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal amount = new BigDecimal(matcher.group(1));
            if (text.contains("万")) {
                amount = amount.multiply(BigDecimal.valueOf(10_000));
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String totalText(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "元";
    }

    private Map<String, Object> rawItem(InvoiceLine line) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productName", cleanProductName(line.productName()));
        item.put("specification", blankDefault(line.specification(), "按双方确认规格执行"));
        item.put("unit", blankDefault(line.unit(), ""));
        item.put("quantity", line.quantity());
        item.put("taxRate", blankDefault(line.taxRate(), "13%"));
        item.put("amount", totalText(line.amount()));
        return item;
    }

    private List<Map<String, Object>> mergeItems(List<InvoiceLine> lines) {
        Map<String, MergedItem> merged = new LinkedHashMap<>();
        for (InvoiceLine line : lines) {
            String productName = cleanProductName(line.productName());
            String specification = blankDefault(line.specification(), "按双方确认规格执行");
            String key = productName + "|" + specification;
            MergedItem item = merged.computeIfAbsent(key, ignored -> new MergedItem(
                    productName,
                    specification,
                    blankDefault(line.unit(), ""),
                    blankDefault(line.taxRate(), "13%")
            ));
            item.add(line);
        }

        return merged.values().stream()
                .filter(item -> item.quantity.compareTo(BigDecimal.ZERO) > 0)
                .filter(item -> item.amount.compareTo(BigDecimal.ZERO) > 0)
                .map(MergedItem::toContractItem)
                .toList();
    }

    private static boolean isTechProduct(String productName) {
        return productName.contains("站点") || productName.contains("软件") || productName.contains("算法");
    }

    private static String cleanProductName(String value) {
        String result = value == null ? "" : value.trim();
        result = result.replaceAll("\\*[^*]+\\*", "");
        result = result.replace("通用设备", "");
        return result.isBlank() ? "合同产品" : result;
    }

    private static String commonTaxRate(List<InvoiceLine> lines) {
        return lines.stream()
                .map(InvoiceLine::taxRate)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(2)
                .toList()
                .size() == 1 ? lines.get(0).taxRate() : "详见合同标的表";
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("生成合同明细 JSON 失败", exception);
        }
    }

    private record InvoiceLine(
            String seller,
            String buyer,
            LocalDate invoiceDate,
            String productName,
            String specification,
            String unit,
            String quantity,
            String taxRate,
            BigDecimal amount
    ) {
        private String groupKey() {
            return seller + "|" + buyer + "|" + invoiceDate;
        }
    }

    private static class MergedItem {
        private final String productName;
        private final String specification;
        private final String unit;
        private final String taxRate;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;

        private MergedItem(String productName, String specification, String unit, String taxRate) {
            this.productName = productName;
            this.specification = specification;
            this.unit = unit;
            this.taxRate = taxRate;
        }

        private void add(InvoiceLine line) {
            String currentUnit = blankDefault(line.unit(), "");
            String currentTaxRate = blankDefault(line.taxRate(), "13%");
            if (!unit.equals(currentUnit)) {
                log.warn("同一产品规格的单位不一致，按首次单位处理。产品：{}，规格：{}，首次单位：{}，当前单位：{}", productName, specification, unit, currentUnit);
            }
            if (!taxRate.equals(currentTaxRate)) {
                log.warn("同一产品规格的税率不一致，按首次税率处理，并按金额汇总。产品：{}，规格：{}，首次税率：{}，当前税率：{}", productName, specification, taxRate, currentTaxRate);
            }
            quantity = quantity.add(quantity(line.quantity()));
            amount = amount.add(line.amount() == null ? BigDecimal.ZERO : line.amount());
        }

        private Map<String, Object> toContractItem() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productName", productName);
            item.put("specification", specification);
            item.put("unit", unit);
            item.put("quantity", quantityText(quantity, unit));
            item.put("taxRate", taxRate);
            item.put("amount", totalText(amount));
            if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                item.put("unitPrice", amount.divide(quantity, 2, RoundingMode.HALF_UP).toPlainString());
            }
            return item;
        }
    }

    private static BigDecimal quantity(String value) {
        String number = value == null ? "" : value.replace(",", "").replaceAll("[^0-9.\\-]", "").trim();
        if (number.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(number).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            log.warn("数量格式无法解析，按 0 处理：{}", value);
            return BigDecimal.ZERO;
        }
    }

    private static String quantityText(BigDecimal quantity, String unit) {
        String text = quantity.stripTrailingZeros().toPlainString();
        return text + blankDefault(unit, "");
    }
}
