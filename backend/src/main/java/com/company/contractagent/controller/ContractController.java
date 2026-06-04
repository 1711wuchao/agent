package com.company.contractagent.controller;

import com.company.contractagent.domain.ContractDraft;
import com.company.contractagent.dto.CreateContractDraftRequest;
import com.company.contractagent.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public List<ContractDraft> listDrafts() {
        return contractService.listDrafts();
    }

    @PostMapping
    public ContractDraft createDraft(@Valid @RequestBody CreateContractDraftRequest request) {
        return contractService.createDraft(request);
    }

    @GetMapping("/{id}")
    public ContractDraft getDraft(@PathVariable String id) {
        return contractService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在：" + id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteContract(@PathVariable String id) {
        return contractService.deleteContract(id);
    }

    @PostMapping("/{id}/generate-docx")
    public Map<String, Object> generateDocx(@PathVariable String id) {
        return contractService.generateDocx(id);
    }

    @PostMapping("/{id}/export-pdf")
    public Map<String, Object> exportPdf(@PathVariable String id) {
        return contractService.exportPdf(id);
    }

    @GetMapping("/{id}/files/{fileType}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String id, @PathVariable String fileType) throws MalformedURLException {
        Path filePath;
        try {
            filePath = contractService.requireGeneratedFile(id, fileType.toUpperCase()).toAbsolutePath().normalize();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在，请先生成：" + fileType);
        }
        Resource resource = new UrlResource(filePath.toUri());
        String contentType = "DOCX".equalsIgnoreCase(fileType)
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath.getFileName() + "\"")
                .body(resource);
    }
}
