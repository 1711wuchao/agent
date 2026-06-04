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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        return generateFromExcel(file, null);
    }

    public Map<String, Object> generateFromExcel(MultipartFile file, String requirements) {
        List<InvoiceLine> lines = readInvoiceLines(file);
        String direction = inferContractDirection(file.getOriginalFilename(), requirements);
        Map<String, List<InvoiceLine>> groups = lines.stream()
                .collect(Collectors.groupingBy(InvoiceLine::groupKey, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> results = new ArrayList<>();
        for (List<InvoiceLine> groupLines : groups.values()) {
            Map<String, Object> fields = buildFields(groupLines, requirements, direction);
            boolean zhongcheng = isZhongchengGroup(groupLines);
            boolean tech = direction.isBlank() && hasTechProduct(groupLines);
            String templateCode = tech ? "TECH_DEVELOPMENT" : "PURCHASE";
            String contractType = tech ? "技术开发合同" : "产品购销合同";
            String title = contractTitle(groupLines.get(0), contractType, direction);

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
                    "invoiceDate", invoiceDateText(groupLines),
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

    public byte[] downloadDocxZip(List<String> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            throw new IllegalArgumentException("请选择需要批量下载的合同");
        }
        String folderName = "contracts-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Set<String> usedNames = new HashSet<>();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (String contractId : contractIds) {
                if (contractId == null || contractId.isBlank()) {
                    continue;
                }
                Path docx = contractService.requireGeneratedFile(contractId.trim(), "DOCX");
                String entryName = uniqueZipName(usedNames, folderName + "/" + safeZipFileName(docx.getFileName().toString()));
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                Files.copy(docx, zipOutputStream);
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("批量打包 Word 失败：" + exception.getMessage(), exception);
        }
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

    private Map<String, Object> buildFields(List<InvoiceLine> lines, String requirements, String direction) {
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
        fields.put("buyer", first.buyer());
        fields.put("seller", first.seller());
        if (!direction.isBlank()) {
            fields.put("contractDirection", direction);
            fields.put("contractDirectionLabel", direction + "合同");
        }
        fields.put("productName", items.size() == 1 ? items.get(0).get("productName") : "多项合同标的");
        fields.put("specification", items.size() == 1 ? items.get(0).get("specification") : "详见合同标的表");
        fields.put("quantity", items.size() == 1 ? items.get(0).get("quantity") : "详见合同标的表");
        fields.put("taxRate", commonTaxRate(lines));
        fields.put("amount", totalText(total));
        fields.put("invoiceDate", first.invoiceDate().toString());
        fields.put("invoiceMonth", first.invoiceDate().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        fields.put("invoiceDateRange", invoiceDateText(lines));
        fields.put("containsTechnicalProduct", hasTechProduct(lines));
        if (requirements != null && !requirements.isBlank()) {
            fields.put("customRequirements", requirements.trim());
        }
        if (!direction.isBlank() || (isZhongchengGroup(lines) && !hasTechProduct(lines))) {
            fields.put("contractDate", ContractDocumentService.zhongchengSigningDate(first.invoiceDate()).toString());
            fields.put("signingPlace", "苏州市吴江区");
        }
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
        item.put("unit", normalizeOptionalCell(line.unit()));
        item.put("quantity", quantityDisplayText(line.quantity(), line.unit()));
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
                .map(MergedItem::toContractItem)
                .toList();
    }

    private static boolean isTechProduct(String productName) {
        return productName.contains("站点") || productName.contains("软件") || productName.contains("算法");
    }

    private static String inferContractDirection(String fileName, String requirements) {
        String text = (fileName == null ? "" : fileName) + " " + (requirements == null ? "" : requirements);
        if (text.contains("销项")) {
            return "销项";
        }
        if (text.contains("进项")) {
            return "进项";
        }
        return "";
    }

    private static String contractTitle(InvoiceLine first, String contractType, String direction) {
        if (!direction.isBlank()) {
            return direction + "合同_" + first.seller() + "_" + first.buyer();
        }
        return first.buyer() + "与" + first.seller() + contractType;
    }

    private static boolean hasTechProduct(List<InvoiceLine> lines) {
        return lines.stream().anyMatch(line -> isTechProduct(line.productName()));
    }

    private static String invoiceDateText(List<InvoiceLine> lines) {
        List<LocalDate> dates = lines.stream()
                .map(InvoiceLine::invoiceDate)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (dates.isEmpty()) {
            return "";
        }
        if (dates.size() == 1) {
            return dates.get(0).toString();
        }
        return dates.get(0) + " 至 " + dates.get(dates.size() - 1);
    }

    private static boolean isZhongchengGroup(List<InvoiceLine> lines) {
        return lines.stream().anyMatch(line -> containsZhongcheng(line.seller()) || containsZhongcheng(line.buyer()));
    }

    private static boolean containsZhongcheng(String value) {
        return value != null && value.contains("中城");
    }

    private static String cleanProductName(String value) {
        String result = value == null ? "" : value.trim();
        result = result.replaceAll("\\*[^*]+\\*", "");
        result = result.replace("通用设备", "");
        return result.isBlank() ? "合同产品" : result;
    }

    private static String commonTaxRate(List<InvoiceLine> lines) {
        List<String> taxRates = lines.stream()
                .map(InvoiceLine::taxRate)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(2)
                .toList();
        return taxRates.size() == 1 ? taxRates.get(0) : "详见合同标的表";
    }

    private static String blankDefault(String value, String fallback) {
        String normalized = normalizeOptionalCell(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String normalizeOptionalCell(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return "/".equals(text) || "-".equals(text) || "--".equals(text) ? "" : text;
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
            return seller + "|" + buyer + "|" + invoiceDate.getYear() + "-" + String.format("%02d", invoiceDate.getMonthValue());
        }
    }

    private static class MergedItem {
        private final String productName;
        private final String specification;
        private final String unit;
        private final String taxRate;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        private boolean hasPositiveQuantity;

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
            BigDecimal currentQuantity = quantity(line.quantity());
            if (currentQuantity.compareTo(BigDecimal.ZERO) > 0) {
                quantity = quantity.add(currentQuantity);
                hasPositiveQuantity = true;
            }
            amount = amount.add(line.amount() == null ? BigDecimal.ZERO : line.amount());
        }

        private Map<String, Object> toContractItem() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productName", productName);
            item.put("specification", specification);
            item.put("unit", unit);
            item.put("quantity", quantityText(hasPositiveQuantity ? quantity : BigDecimal.ONE, unit));
            item.put("taxRate", taxRate);
            item.put("amount", totalText(amount));
            if (hasPositiveQuantity && quantity.compareTo(BigDecimal.ZERO) > 0) {
                item.put("unitPrice", amount.divide(quantity, 2, RoundingMode.HALF_UP).toPlainString());
            }
            return item;
        }
    }

    private static BigDecimal quantity(String value) {
        String text = normalizeOptionalCell(value);
        String number = text.replace(",", "").replaceAll("[^0-9.\\-]", "").trim();
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

    private static String quantityDisplayText(String quantity, String unit) {
        BigDecimal value = quantity(quantity);
        return quantityText(value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ONE, unit);
    }

    private static String safeZipFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String uniqueZipName(Set<String> usedNames, String name) {
        if (usedNames.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String prefix = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            String candidate = prefix + "-" + i + suffix;
            if (usedNames.add(candidate)) {
                return candidate;
            }
        }
    }
}
