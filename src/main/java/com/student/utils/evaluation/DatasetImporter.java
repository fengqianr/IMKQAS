package com.student.utils.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.entity.evaluation.EvalDatasetItem;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 评估数据集导入工具
 * 支持从现有test_scenarios.json格式转换为评估数据项
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
public final class DatasetImporter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private DatasetImporter() {
    }

    /**
     * 从 test_scenarios.json 格式解析评估数据项
     * 输入格式: [{"id": "SCENARIO_001", "name": "...", "userQuery": "...", ...}]
     *
     * @param jsonContent JSON内容
     * @return 评估数据项列表
     */
    public static List<EvalDatasetItem> parseFromTestScenarios(String jsonContent) {
        List<EvalDatasetItem> items = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    items.add(convertScenarioToItem(node));
                }
            }
            log.info("从test_scenarios.json解析了 {} 条数据项", items.size());
        } catch (Exception e) {
            log.error("解析test_scenarios.json失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析test_scenarios.json失败: " + e.getMessage(), e);
        }
        return items;
    }

    /**
     * 从安全场景JSON解析
     */
    public static List<EvalDatasetItem> parseFromSafetyScenarios(String jsonContent) {
        List<EvalDatasetItem> items = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    items.add(convertSafetyScenarioToItem(node));
                }
            }
            log.info("从safety_scenarios.json解析了 {} 条数据项", items.size());
        } catch (Exception e) {
            log.error("解析safety_scenarios.json失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析safety_scenarios.json失败: " + e.getMessage(), e);
        }
        return items;
    }

    /**
     * 从JSON数组直接解析（标准格式）
     */
    public static List<EvalDatasetItem> parseFromJsonArray(String jsonContent) {
        try {
            return objectMapper.readValue(jsonContent,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, EvalDatasetItem.class));
        } catch (Exception e) {
            log.error("解析JSON数组失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析JSON数组失败: " + e.getMessage(), e);
        }
    }

    private static EvalDatasetItem convertScenarioToItem(JsonNode node) {
        String queryType = inferQueryType(node);
        String safetyLevel = node.has("safetyCheck") ? "SAFE" : "SAFE";

        EvalDatasetItem item = EvalDatasetItem.builder()
                .query(node.path("userQuery").asText())
                .expectedKeywords(toJsonArray(node.path("expectedKeywords")))
                .expectedResponseType(node.path("expectedResponseType").asText())
                .queryType(queryType)
                .difficulty(node.path("difficulty").asText("medium"))
                .safetyLevel(safetyLevel)
                .shouldTriggerEmergency(false)
                .shouldTriggerConfidenceBlock(false)
                .reviewStatus("APPROVED")
                .build();

        return item;
    }

    private static EvalDatasetItem convertSafetyScenarioToItem(JsonNode node) {
        EvalDatasetItem item = EvalDatasetItem.builder()
                .query(node.path("userQuery").asText())
                .expectedKeywords(toJsonArray(node.path("requiredResponses")))
                .prohibitedKeywords(toJsonArray(node.path("prohibitedKeywords")))
                .expectedResponseType("SAFETY_FALLBACK")
                .queryType("SAFETY")
                .difficulty("hard")
                .safetyLevel("BLOCKED")
                .shouldTriggerEmergency(true)
                .shouldTriggerConfidenceBlock(true)
                .reviewStatus("APPROVED")
                .build();

        return item;
    }

    private static String inferQueryType(JsonNode node) {
        String desc = node.path("description").asText("").toLowerCase();
        String name = node.path("name").asText("").toLowerCase();

        if (desc.contains("药") || desc.contains("drug") || name.contains("药")) {
            return "DRUG_QUERY";
        }
        if (desc.contains("急诊") || desc.contains("emergency") || name.contains("急诊")
                || name.contains("emergency")) {
            return "EMERGENCY";
        }
        if (desc.contains("科室") || desc.contains("导诊") || desc.contains("department")
                || name.contains("科室")) {
            return "DEPARTMENT_TRIAGE";
        }
        if (desc.contains("安全") || desc.contains("safety") || desc.contains("fallback")
                || name.contains("安全") || name.contains("safety")) {
            return "SAFETY_FALLBACK";
        }
        if (desc.contains("中医") || name.contains("中医")) {
            return "TCM_CONSULTATION";
        }
        if (desc.contains("禁忌") || desc.contains("contraindication") || name.contains("禁忌")) {
            return "CONTRAINDICATION_CHECK";
        }
        return "SYMPTOM_INQUIRY";
    }

    private static String toJsonArray(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "[]";
        }
    }
}
