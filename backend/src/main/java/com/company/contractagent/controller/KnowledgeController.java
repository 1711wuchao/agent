package com.company.contractagent.controller;

import com.company.contractagent.dto.KnowledgeChunkRequest;
import com.company.contractagent.service.VectorKnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final VectorKnowledgeService vectorKnowledgeService;

    public KnowledgeController(VectorKnowledgeService vectorKnowledgeService) {
        this.vectorKnowledgeService = vectorKnowledgeService;
    }

    @PostMapping("/chunks")
    public Map<String, Object> addChunk(@Valid @RequestBody KnowledgeChunkRequest request) {
        return vectorKnowledgeService.addChunk(request);
    }

    @PostMapping("/seed")
    public List<Map<String, Object>> seedDefaults() {
        return vectorKnowledgeService.seedDefaults();
    }

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String query,
            @RequestParam(required = false) String contractType,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return vectorKnowledgeService.search(query, contractType, limit);
    }
}
