package com.company.contractagent.controller;

import com.company.contractagent.service.ExcelBatchContractService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts/batch")
public class ExcelBatchContractController {
    private final ExcelBatchContractService excelBatchContractService;

    public ExcelBatchContractController(ExcelBatchContractService excelBatchContractService) {
        this.excelBatchContractService = excelBatchContractService;
    }

    @PostMapping("/excel")
    public Map<String, Object> generateFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "requirements", required = false) String requirements
    ) {
        return excelBatchContractService.generateFromExcel(file, requirements);
    }

    @PostMapping("/download-docx")
    public ResponseEntity<ByteArrayResource> downloadDocxZip(@RequestBody BatchDownloadRequest request) {
        byte[] zipBytes = excelBatchContractService.downloadDocxZip(request.contractIds());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"batch-contracts.zip\"")
                .contentLength(zipBytes.length)
                .body(new ByteArrayResource(zipBytes));
    }

    public record BatchDownloadRequest(List<String> contractIds) {
    }
}
