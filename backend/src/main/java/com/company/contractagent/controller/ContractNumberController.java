package com.company.contractagent.controller;

import com.company.contractagent.domain.ParsedContractNumber;
import com.company.contractagent.service.ContractNumberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contract-numbers")
public class ContractNumberController {
    private final ContractNumberService contractNumberService;

    public ContractNumberController(ContractNumberService contractNumberService) {
        this.contractNumberService = contractNumberService;
    }

    @GetMapping("/generate")
    public Map<String, String> generateContractNumber(@RequestParam String date) {
        return Map.of("contractNumber", contractNumberService.generateContractNumber(date));
    }

    @GetMapping("/parse")
    public ParsedContractNumber parseContractNumber(@RequestParam String contractNumber) {
        return contractNumberService.parseContractNumber(contractNumber);
    }

    @GetMapping("/validate")
    public Map<String, Boolean> validateContractNumber(@RequestParam String contractNumber) {
        return Map.of("valid", contractNumberService.validateContractNumber(contractNumber));
    }

    @GetMapping("/date/{date}")
    public List<String> getContractsByDate(@PathVariable String date) {
        return contractNumberService.getContractsByDate(date);
    }
}
