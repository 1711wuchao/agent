package com.company.contractagent.service;

import com.company.contractagent.domain.ParsedContractNumber;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ContractNumberService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern CONTRACT_NUMBER_PATTERN = Pattern.compile("^\\d{8}-\\d{4}$");

    private final JdbcTemplate jdbcTemplate;

    public ContractNumberService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 生成合同编号。
     *
     * 存储方式：使用 MySQL 数据库中 contract 表的 contract_no 字段作为流水号来源。
     * 查询同一签订日期下已存在的最大流水号，然后递增生成下一号。
     *
     * @param date 签订日期，支持 java.time.LocalDate、java.util.Date、YYYYMMDD 字符串、yyyy-MM-dd 字符串
     * @return 格式为 YYYYMMDD-XXXX 的合同编号，例如 20260604-0001
     */
    public String generateContractNumber(Object date) {
        String dateText = normalizeDate(date);
        List<String> numbers = getContractsByDate(dateText);
        int nextSerial = numbers.stream()
                .map(this::parseContractNumber)
                .map(ParsedContractNumber::serial)
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
        return dateText + "-" + String.format("%04d", nextSerial);
    }

    /**
     * 解析合同编号。
     *
     * @param contractNumber 合同编号，例如 20260604-0003
     * @return date 为日期部分，serial 为 4 位流水号
     */
    public ParsedContractNumber parseContractNumber(String contractNumber) {
        if (!validateContractNumber(contractNumber)) {
            throw new IllegalArgumentException("合同编号格式不正确：" + contractNumber);
        }
        String[] parts = contractNumber.split("-", 2);
        return new ParsedContractNumber(parts[0], parts[1]);
    }

    /**
     * 校验合同编号格式。
     *
     * @param contractNumber 合同编号
     * @return true 表示符合 YYYYMMDD-XXXX 格式，且日期合法
     */
    public boolean validateContractNumber(String contractNumber) {
        if (contractNumber == null || !CONTRACT_NUMBER_PATTERN.matcher(contractNumber).matches()) {
            return false;
        }
        try {
            LocalDate.parse(contractNumber.substring(0, 8), DATE_FORMATTER);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    /**
     * 查询指定日期下的所有合同编号。
     *
     * @param date 日期字符串，格式为 YYYYMMDD
     * @return 该日期下已落库的合同编号列表
     */
    public List<String> getContractsByDate(String date) {
        String dateText = normalizeDate(date);
        return jdbcTemplate.queryForList(
                """
                        SELECT contract_no
                        FROM contract
                        WHERE contract_no LIKE ?
                        ORDER BY contract_no ASC
                        """,
                String.class,
                dateText + "-%"
        );
    }

    /**
     * 根据业务字段推导签订日期。
     * 优先使用 contractDate；没有 contractDate 时，沿用当前业务规则：invoiceDate 向前推 3 个月。
     */
    public LocalDate resolveSigningDate(Object contractDate, Object invoiceDate) {
        LocalDate explicitDate = tryParseDate(contractDate);
        if (explicitDate != null) {
            return explicitDate;
        }
        LocalDate parsedInvoiceDate = tryParseDate(invoiceDate);
        if (parsedInvoiceDate != null) {
            return parsedInvoiceDate.minusMonths(3);
        }
        return LocalDate.now();
    }

    private String normalizeDate(Object date) {
        LocalDate localDate = tryParseDate(date);
        if (localDate == null) {
            throw new IllegalArgumentException("日期格式不正确，请使用 YYYYMMDD 或 yyyy-MM-dd");
        }
        return DATE_FORMATTER.format(localDate);
    }

    private LocalDate tryParseDate(Object date) {
        if (date == null || String.valueOf(date).isBlank()) {
            return null;
        }
        if (date instanceof LocalDate localDate) {
            return localDate;
        }
        if (date instanceof Date legacyDate) {
            return legacyDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = String.valueOf(date).trim();
        if (text.length() >= 10 && text.charAt(4) == '-') {
            text = text.substring(0, 10);
        }
        try {
            if (text.matches("^\\d{8}$")) {
                return LocalDate.parse(text, DATE_FORMATTER);
            }
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
