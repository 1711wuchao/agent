package com.company.contractagent.controller;

import com.company.contractagent.dto.ToolCallRequest;
import com.company.contractagent.dto.ToolCallResponse;
import com.company.contractagent.service.KimiToolService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/tools")
public class KimiToolController {
    private final KimiToolService kimiToolService;
    private final String agentToolToken;

    public KimiToolController(KimiToolService kimiToolService, @Value("${agent.tool-token:}") String agentToolToken) {
        this.kimiToolService = kimiToolService;
        this.agentToolToken = agentToolToken;
    }

    @PostMapping("/call")
    public ToolCallResponse callTool(
            @RequestHeader(value = "X-Agent-Token", required = false) String token,
            @Valid @RequestBody ToolCallRequest request
    ) {
        if (agentToolToken != null && !agentToolToken.isBlank() && !agentToolToken.equals(token)) {
            throw new InvalidAgentTokenException();
        }
        return kimiToolService.call(request);
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class InvalidAgentTokenException extends RuntimeException {
    }
}
