package com.company.contractagent.dto;

import com.company.contractagent.domain.ContractDraft;
import com.company.contractagent.domain.ContractTemplate;

import java.util.List;
import java.util.Map;

public record AgentChatResponse(
        String sessionId,
        String stage,
        String assistantMessage,
        ContractTemplate selectedTemplate,
        Map<String, Object> collectedFields,
        List<Map<String, Object>> missingFields,
        ContractDraft currentDraft,
        Map<String, Object> riskReport,
        Map<String, Object> generatedFiles,
        List<AgentToolTrace> toolCalls,
        String systemPrompt
) {
}
