package com.company.contractagent.service;

import com.company.contractagent.domain.ContractDraft;
import com.company.contractagent.domain.ContractStatus;
import com.company.contractagent.dto.CreateContractDraftRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ContractService {
    private final JdbcTemplate jdbcTemplate;
    private final ContractDocumentService contractDocumentService;
    private final ContractNumberService contractNumberService;
    private final String outputRoot;

    public ContractService(
            JdbcTemplate jdbcTemplate,
            ContractDocumentService contractDocumentService,
            ContractNumberService contractNumberService,
            @Value("${contract.output-root:./storage/contracts}") String outputRoot
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.contractDocumentService = contractDocumentService;
        this.contractNumberService = contractNumberService;
        this.outputRoot = outputRoot;
    }

    @Transactional
    public ContractDraft createDraft(CreateContractDraftRequest request) {
        Map<String, Object> fields = request.fields() == null ? Map.of() : request.fields();
        String id = contractNumberService.generateContractNumber(
                contractNumberService.resolveSigningDate(
                        fields.get("contractDate"),
                        fields.get("invoiceDate")
                )
        );
        jdbcTemplate.update(
                """
                        INSERT INTO contract (contract_no, contract_type, template_code, title, status)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                id,
                request.contractType(),
                request.templateCode(),
                request.title(),
                ContractStatus.DRAFT.name()
        );
        upsertFields(id, fields);
        return requireDraft(id);
    }

    public List<ContractDraft> listDrafts() {
        return jdbcTemplate.query(
                """
                        SELECT contract_no, contract_type, template_code, title, status, created_at
                        FROM contract
                        ORDER BY created_at DESC, id DESC
                        """,
                (rs, rowNum) -> mapDraft(rs)
        );
    }

    public Optional<ContractDraft> findById(String id) {
        List<ContractDraft> drafts = jdbcTemplate.query(
                """
                        SELECT contract_no, contract_type, template_code, title, status, created_at
                        FROM contract
                        WHERE contract_no = ?
                        """,
                (rs, rowNum) -> mapDraft(rs),
                id
        );
        return drafts.stream().findFirst();
    }

    @Transactional
    public ContractDraft updateFields(String id, Map<String, Object> fields) {
        requireDraft(id);
        upsertFields(id, fields);
        return requireDraft(id);
    }

    @Transactional
    public Map<String, Object> deleteContract(String id) {
        requireDraft(id);
        jdbcTemplate.update("DELETE FROM contract_file WHERE contract_no = ?", id);
        jdbcTemplate.update("DELETE FROM contract_version WHERE contract_no = ?", id);
        jdbcTemplate.update("DELETE FROM contract_field_value WHERE contract_no = ?", id);
        jdbcTemplate.update("DELETE FROM contract WHERE contract_no = ?", id);
        deleteGeneratedDirectory(id);
        return Map.of(
                "contractId", id,
                "status", "DELETED"
        );
    }

    @Transactional
    public Map<String, Object> generateDocx(String id) {
        ContractDraft draft = requireDraft(id);
        String fileName = draft.id() + ".docx";
        Path filePath = Path.of(outputRoot, draft.id(), fileName);
        contractDocumentService.generateDocx(draft, filePath);
        recordFile(draft.id(), "DOCX", fileName, filePath);
        return Map.of(
                "contractId", draft.id(),
                "fileName", fileName,
                "fileType", "DOCX",
                "status", "GENERATED",
                "downloadUrl", "/api/contracts/" + draft.id() + "/files/DOCX"
        );
    }

    @Transactional
    public Map<String, Object> exportPdf(String id) {
        ContractDraft draft = requireDraft(id);
        String fileName = draft.id() + ".pdf";
        return Map.of(
                "contractId", draft.id(),
                "fileName", fileName,
                "fileType", "PDF",
                "status", "PENDING_CONVERTER",
                "nextStep", "PDF 转换需要云服务器安装 LibreOffice 后再启用。",
                "downloadUrl", "/api/contracts/" + draft.id() + "/files/PDF"
        );
    }

    public Path requireGeneratedFile(String contractNo, String fileType) {
        requireDraft(contractNo);
        List<String> paths = jdbcTemplate.queryForList(
                """
                        SELECT storage_path
                        FROM contract_file
                        WHERE contract_no = ? AND file_type = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                String.class,
                contractNo,
                fileType
        );
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("文件不存在，请先生成：" + fileType);
        }
        return Path.of(paths.get(0));
    }

    public Map<String, Object> reviewRisk(String id) {
        ContractDraft draft = requireDraft(id);
        return reviewRisk(draft.fields());
    }

    public Map<String, Object> reviewRisk(Map<String, Object> fields) {
        List<String> findings = new ArrayList<>();
        String riskLevel = "LOW";

        if (isBlank(fields.get("amount")) && isBlank(fields.get("fee"))) {
            findings.add("合同金额或服务费用未明确。");
            riskLevel = "MEDIUM";
        }
        if (isBlank(fields.get("taxRate"))) {
            findings.add("税率未明确，合同正文必须体现税率和价税合计。");
            riskLevel = "MEDIUM";
        }
        if (isBlank(fields.get("quantity"))) {
            findings.add("数量未明确，合同内容必须包含数量。");
            riskLevel = "MEDIUM";
        }
        if (isBlank(fields.get("specification"))) {
            findings.add("规格型号未明确，合同内容必须包含规格型号。");
            riskLevel = "MEDIUM";
        }
        if (isBlank(fields.get("disputeResolution"))) {
            findings.add("争议解决方式未设置，建议明确管辖法院或仲裁机构。");
            riskLevel = "MEDIUM";
        }
        if (containsBannedText(fields)) {
            findings.add("合同字段中包含禁用字样，生成文件时会自动过滤。");
            riskLevel = "MEDIUM";
        }
        if (isLargeAmount(fields.get("amount")) || isLargeAmount(fields.get("fee"))) {
            findings.add("合同金额较高，建议进入法务复核或高额合同审批。");
            riskLevel = "HIGH";
        }
        if (findings.isEmpty()) {
            findings.add("未发现明显规则风险，仍建议业务负责人确认模板、金额、税率与验收条款。");
        }

        return Map.of(
                "riskLevel", riskLevel,
                "findings", findings,
                "source", "规则审查 + Chroma 知识库 MVP"
        );
    }

    private ContractDraft mapDraft(ResultSet rs) throws SQLException {
        String contractNo = rs.getString("contract_no");
        return new ContractDraft(
                contractNo,
                rs.getString("contract_type"),
                rs.getString("template_code"),
                rs.getString("title"),
                ContractStatus.valueOf(rs.getString("status")),
                loadFields(contractNo),
                toOffsetDateTime(rs.getTimestamp("created_at"))
        );
    }

    private Map<String, Object> loadFields(String contractNo) {
        Map<String, Object> fields = new LinkedHashMap<>();
        jdbcTemplate.queryForList(
                """
                        SELECT field_code, field_value
                        FROM contract_field_value
                        WHERE contract_no = ?
                        ORDER BY id ASC
                        """,
                contractNo
        ).forEach(row -> fields.put(String.valueOf(row.get("field_code")), row.get("field_value")));
        return fields;
    }

    private void upsertFields(String contractNo, Map<String, Object> fields) {
        fields.forEach((code, value) -> jdbcTemplate.update(
                """
                        INSERT INTO contract_field_value (contract_no, field_code, field_value)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE field_value = VALUES(field_value)
                        """,
                contractNo,
                code,
                value == null ? null : String.valueOf(value)
        ));
    }

    private void recordFile(String contractNo, String fileType, String fileName, Path filePath) {
        jdbcTemplate.update(
                """
                        INSERT INTO contract_file (contract_no, file_type, file_name, storage_path)
                        VALUES (?, ?, ?, ?)
                        """,
                contractNo,
                fileType,
                fileName,
                filePath.toString()
        );
    }

    private void deleteGeneratedDirectory(String contractNo) {
        Path root = Path.of(outputRoot).toAbsolutePath().normalize();
        Path directory = root.resolve(contractNo).normalize();
        if (!directory.startsWith(root) || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("删除合同文件失败：" + path, exception);
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("删除合同文件目录失败：" + directory, exception);
        }
    }

    private ContractDraft requireDraft(String id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("合同不存在：" + id));
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private static boolean containsBannedText(Map<String, Object> fields) {
        return fields.values().stream()
                .map(String::valueOf)
                .anyMatch(text -> text.contains("参考")
                        || text.contains("参考文献")
                        || text.contains("通用设备")
                        || text.contains("不含税金额")
                        || text.contains("税额"));
    }

    private static boolean isLargeAmount(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).replace(",", "").trim();
        try {
            if (text.contains("万")) {
                String number = text.substring(0, text.indexOf("万")).replaceAll("[^0-9.]", "");
                return !number.isEmpty() && Double.parseDouble(number) >= 100;
            }
            String number = text.replaceAll("[^0-9.]", "");
            return !number.isEmpty() && Double.parseDouble(number) >= 1_000_000;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
