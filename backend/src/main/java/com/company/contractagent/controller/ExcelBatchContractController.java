package com.company.contractagent.controller;

import com.company.contractagent.service.ExcelBatchContractService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts/batch")
public class ExcelBatchContractController {
    private final ExcelBatchContractService excelBatchContractService;

    public ExcelBatchContractController(ExcelBatchContractService excelBatchContractService) {
        this.excelBatchContractService = excelBatchContractService;
    }

    @PostMapping("/excel")
    public Map<String, Object> generateFromExcel(@RequestParam("file") MultipartFile file) {
        return excelBatchContractService.generateFromExcel(file);
    }
}
