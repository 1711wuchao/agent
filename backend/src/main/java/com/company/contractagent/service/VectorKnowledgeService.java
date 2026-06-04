package com.company.contractagent.service;

import com.company.contractagent.dto.KnowledgeChunkRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VectorKnowledgeService {
    private static final int EMBEDDING_DIMENSION = 64;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient chromaClient;
    private final String collectionName;

    public VectorKnowledgeService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${contract.chroma.url:http://localhost:8000}") String chromaUrl,
            @Value("${contract.chroma.collection:contract_knowledge_v1}") String collectionName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.chromaClient = RestClient.builder().baseUrl(chromaUrl).build();
        this.collectionName = collectionName;
    }

    @Transactional
    public Map<String, Object> addChunk(KnowledgeChunkRequest request) {
        long documentId = ensureDocument(request);
        int chunkIndex = nextChunkIndex(documentId);
        String vectorId = "knowledge-" + documentId + "-" + chunkIndex + "-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> metadata = metadata(request, documentId, chunkIndex);

        jdbcTemplate.update(
                """
                        INSERT INTO knowledge_chunk (document_id, chunk_index, content, metadata, vector_id)
                        VALUES (?, ?, ?, CAST(? AS JSON), ?)
                        """,
                documentId,
                chunkIndex,
                request.content(),
                toJson(metadata),
                vectorId
        );

        upsertToChroma(vectorId, request.content(), metadata);

        return Map.of(
                "documentId", documentId,
                "chunkIndex", chunkIndex,
                "vectorId", vectorId,
                "collection", collectionName
        );
    }

    public Map<String, Object> search(String query, String contractType, int limit) {
        String collectionId = ensureCollection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_embeddings", List.of(embed(query)));
        body.put("n_results", Math.max(1, Math.min(limit, 10)));

        Map<String, Object> response = chromaClient.post()
                .uri(chromaCollectionPath(collectionId) + "/query")
                .body(body)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> matches = flattenMatches(response, contractType);
        return Map.of(
                "query", query,
                "collection", collectionName,
                "matches", matches
        );
    }

    @Transactional
    public List<Map<String, Object>> seedDefaults() {
        List<KnowledgeChunkRequest> chunks = List.of(
                new KnowledgeChunkRequest("销售合同标准付款条款", "销售合同付款方式应明确付款节点、验收条件、发票要求和逾期付款责任。", "SALES", "PAYMENT", "MEDIUM", "legal-standard-v1"),
                new KnowledgeChunkRequest("高额合同复核规则", "销售合同金额超过 100 万元时，建议提交法务复核，并设置违约责任上限。", "SALES", "RISK_REVIEW", "HIGH", "legal-standard-v1"),
                new KnowledgeChunkRequest("争议解决条款", "合同应明确争议解决方式，优先约定甲方所在地法院或双方确认的仲裁机构。", "COMMON", "DISPUTE", "MEDIUM", "legal-standard-v1"),
                new KnowledgeChunkRequest("保密协议期限", "保密协议应明确保密信息范围、保密期限、例外情形和违约责任。", "NDA", "CONFIDENTIALITY", "MEDIUM", "legal-standard-v1"),
                new KnowledgeChunkRequest("服务合同验收", "服务合同应写明服务范围、验收标准、交付物、验收周期和整改机制。", "SERVICE", "ACCEPTANCE", "MEDIUM", "legal-standard-v1")
        );
        return chunks.stream().map(this::addChunk).toList();
    }

    private long ensureDocument(KnowledgeChunkRequest request) {
        List<Long> existing = jdbcTemplate.query(
                """
                        SELECT id FROM knowledge_document
                        WHERE title = ? AND COALESCE(contract_type, '') = COALESCE(?, '') AND COALESCE(source, '') = COALESCE(?, '')
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                request.title(),
                request.contractType(),
                request.source()
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbcTemplate.update(
                "INSERT INTO knowledge_document (title, contract_type, source) VALUES (?, ?, ?)",
                request.title(),
                request.contractType(),
                request.source()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private int nextChunkIndex(long documentId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(chunk_index), -1) + 1 FROM knowledge_chunk WHERE document_id = ?",
                Integer.class,
                documentId
        );
        return value == null ? 0 : value;
    }

    private void upsertToChroma(String vectorId, String content, Map<String, Object> metadata) {
        String collectionId = ensureCollection();
        Map<String, Object> body = Map.of(
                "ids", List.of(vectorId),
                "documents", List.of(content),
                "embeddings", List.of(embed(content)),
                "metadatas", List.of(metadata)
        );
        chromaClient.post()
                .uri(chromaCollectionPath(collectionId) + "/upsert")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String ensureCollection() {
        Object collections = chromaClient.get()
                .uri("/api/v2/tenants/default_tenant/databases/default_database/collections")
                .retrieve()
                .body(Object.class);
        String existingId = findCollectionId(collections);
        if (existingId != null) {
            return existingId;
        }
        Map<String, Object> created = chromaClient.post()
                .uri("/api/v2/tenants/default_tenant/databases/default_database/collections")
                .body(Map.of(
                        "name", collectionName,
                        "metadata", Map.of("description", "Contract Agent legal knowledge"),
                        "get_or_create", true
                ))
                .retrieve()
                .body(Map.class);
        return String.valueOf(created.get("id"));
    }

    @SuppressWarnings("unchecked")
    private String findCollectionId(Object collections) {
        Object value = collections;
        if (collections instanceof Map<?, ?> map) {
            value = map.get("value");
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> collection && collectionName.equals(collection.get("name"))) {
                    return String.valueOf(collection.get("id"));
                }
            }
        }
        return null;
    }

    private String chromaCollectionPath(String collectionId) {
        return "/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionId;
    }

    private Map<String, Object> metadata(KnowledgeChunkRequest request, long documentId, int chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", documentId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("title", valueOrDefault(request.title(), ""));
        metadata.put("contractType", valueOrDefault(request.contractType(), "COMMON"));
        metadata.put("clauseType", valueOrDefault(request.clauseType(), "GENERAL"));
        metadata.put("riskLevel", valueOrDefault(request.riskLevel(), "LOW"));
        metadata.put("source", valueOrDefault(request.source(), "manual"));
        return metadata;
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> flattenMatches(Map<String, Object> response, String contractType) {
        List<Map<String, Object>> matches = new ArrayList<>();
        List<List<String>> ids = (List<List<String>>) response.getOrDefault("ids", List.of(List.of()));
        List<List<String>> documents = (List<List<String>>) response.getOrDefault("documents", List.of(List.of()));
        List<List<Map<String, Object>>> metadatas = (List<List<Map<String, Object>>>) response.getOrDefault("metadatas", List.of(List.of()));
        List<List<Number>> distances = (List<List<Number>>) response.getOrDefault("distances", List.of(List.of()));

        if (ids.isEmpty()) {
            return matches;
        }
        for (int i = 0; i < ids.get(0).size(); i++) {
            Map<String, Object> metadata = metadatas.get(0).get(i);
            String matchContractType = String.valueOf(metadata.getOrDefault("contractType", "COMMON"));
            if (contractType != null && !contractType.isBlank() && !"COMMON".equals(matchContractType) && !contractType.equalsIgnoreCase(matchContractType)) {
                continue;
            }
            double distance = distances.get(0).get(i).doubleValue();
            matches.add(Map.of(
                    "id", ids.get(0).get(i),
                    "content", documents.get(0).get(i),
                    "score", Math.max(0, 1 - distance),
                    "distance", distance,
                    "metadata", metadata
            ));
        }
        return matches;
    }

    private static List<Double> embed(String text) {
        double[] vector = new double[EMBEDDING_DIMENSION];
        String normalized = text == null ? "" : text.toLowerCase();
        for (int i = 0; i < normalized.length(); i++) {
            int bucket = Math.floorMod(normalized.charAt(i), EMBEDDING_DIMENSION);
            vector[bucket] += 1.0;
        }
        for (String token : normalized.split("[\\s，,。；;：:]+")) {
            if (!token.isBlank()) {
                int bucket = Math.floorMod(hash(token), EMBEDDING_DIMENSION);
                vector[bucket] += 3.0;
            }
        }
        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        List<Double> result = new ArrayList<>(EMBEDDING_DIMENSION);
        for (double value : vector) {
            result.add(norm == 0 ? 0.0 : value / norm);
        }
        return result;
    }

    private static int hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16) | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
        } catch (NoSuchAlgorithmException e) {
            return value.hashCode();
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
