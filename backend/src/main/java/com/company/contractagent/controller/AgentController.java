package com.company.contractagent.controller;

import com.company.contractagent.dto.AgentChatRequest;
import com.company.contractagent.dto.AgentChatResponse;
import com.company.contractagent.service.AgentOrchestratorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/agent")
public class AgentController {
    private final AgentOrchestratorService agentOrchestratorService;

    public AgentController(AgentOrchestratorService agentOrchestratorService) {
        this.agentOrchestratorService = agentOrchestratorService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return agentOrchestratorService.chat(request);
    }
}
