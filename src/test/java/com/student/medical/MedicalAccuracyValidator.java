package com.student.medical;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 医疗准确性验证器
 * 验证医疗回答的准确性，包括医学术语识别、安全兜底检查等
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
public class MedicalAccuracyValidator {

    private final ObjectMapper objectMapper;
    private Map<String, MedicalTerm> medicalTerms;
    private List<TestScenario> testScenarios;
    private List<SafetyScenario> safetyScenarios;

    /**
     * 医学术语
     */
    public static class MedicalTerm {
        private String term;
        private String category;
        private String definition;
        private List<String> synonyms;
        private List<String> abbreviations;

        // getters and setters
        public String getTerm() { return term; }
        public void setTerm(String term) { this.term = term; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDefinition() { return definition; }
        public void setDefinition(String definition) { this.definition = definition; }
        public List<String> getSynonyms() { return synonyms; }
        public void setSynonyms(List<String> synonyms) { this.synonyms = synonyms; }
        public List<String> getAbbreviations() { return abbreviations; }
        public void setAbbreviations(List<String> abbreviations) { this.abbreviations = abbreviations; }
    }

    /**
     * 测试场景
     */
    public static class TestScenario {
        private String id;
        private String name;
        private String description;
        private String userQuery;
        private List<String> expectedKeywords;
        private String expectedResponseType;
        private String safetyCheck;
        private String difficulty;

        // getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getUserQuery() { return userQuery; }
        public void setUserQuery(String userQuery) { this.userQuery = userQuery; }
        public List<String> getExpectedKeywords() { return expectedKeywords; }
        public void setExpectedKeywords(List<String> expectedKeywords) { this.expectedKeywords = expectedKeywords; }
        public String getExpectedResponseType() { return expectedResponseType; }
        public void setExpectedResponseType(String expectedResponseType) { this.expectedResponseType = expectedResponseType; }
        public String getSafetyCheck() { return safetyCheck; }
        public void setSafetyCheck(String safetyCheck) { this.safetyCheck = safetyCheck; }
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    }

    /**
     * 安全场景
     */
    public static class SafetyScenario {
        private String id;
        private String name;
        private String description;
        private String userQuery;
        private String requiredResponse;
        private List<String> prohibitedKeywords;
        private String priority;

        // getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getUserQuery() { return userQuery; }
        public void setUserQuery(String userQuery) { this.userQuery = userQuery; }
        public String getRequiredResponse() { return requiredResponse; }
        public void setRequiredResponse(String requiredResponse) { this.requiredResponse = requiredResponse; }
        public List<String> getProhibitedKeywords() { return prohibitedKeywords; }
        public void setProhibitedKeywords(List<String> prohibitedKeywords) { this.prohibitedKeywords = prohibitedKeywords; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private boolean passed;
        private String scenarioId;
        private String scenarioName;
        private List<String> matchedKeywords;
        private List<String> missingKeywords;
        private double keywordMatchRate;
        private boolean safetyCheckPassed;
        private String safetyMessage;
        private List<String> warnings;
        private List<String> errors;

        public ValidationResult(String scenarioId, String scenarioName) {
            this.scenarioId = scenarioId;
            this.scenarioName = scenarioName;
            this.matchedKeywords = new ArrayList<>();
            this.missingKeywords = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.passed = false;
            this.safetyCheckPassed = true;
        }

        // getters and setters
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getScenarioId() { return scenarioId; }
        public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
        public String getScenarioName() { return scenarioName; }
        public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
        public List<String> getMatchedKeywords() { return matchedKeywords; }
        public void setMatchedKeywords(List<String> matchedKeywords) { this.matchedKeywords = matchedKeywords; }
        public List<String> getMissingKeywords() { return missingKeywords; }
        public void setMissingKeywords(List<String> missingKeywords) { this.missingKeywords = missingKeywords; }
        public double getKeywordMatchRate() { return keywordMatchRate; }
        public void setKeywordMatchRate(double keywordMatchRate) { this.keywordMatchRate = keywordMatchRate; }
        public boolean isSafetyCheckPassed() { return safetyCheckPassed; }
        public void setSafetyCheckPassed(boolean safetyCheckPassed) { this.safetyCheckPassed = safetyCheckPassed; }
        public String getSafetyMessage() { return safetyMessage; }
        public void setSafetyMessage(String safetyMessage) { this.safetyMessage = safetyMessage; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }

        public void addMatchedKeyword(String keyword) {
            if (!matchedKeywords.contains(keyword)) {
                matchedKeywords.add(keyword);
            }
        }

        public void addMissingKeyword(String keyword) {
            if (!missingKeywords.contains(keyword)) {
                missingKeywords.add(keyword);
            }
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public void addError(String error) {
            errors.add(error);
        }

        @Override
        public String toString() {
            return String.format("ValidationResult{场景=%s, 通过=%s, 关键词匹配率=%.2f%%, 安全检查=%s, 警告=%d, 错误=%d}",
                    scenarioName, passed ? "是" : "否", keywordMatchRate * 100,
                    safetyCheckPassed ? "通过" : "失败", warnings.size(), errors.size());
        }
    }

    public MedicalAccuracyValidator() {
        this.objectMapper = new ObjectMapper();
        loadMedicalTerms();
        loadTestScenarios();
    }

    /**
     * 加载医学术语词典
     */
    private void loadMedicalTerms() {
        try {
            ClassPathResource resource = new ClassPathResource("medical/medical_terms.json");
            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, Object> data = objectMapper.readValue(inputStream, Map.class);
                List<Map<String, Object>> termsData = (List<Map<String, Object>>) data.get("terms");

                medicalTerms = new HashMap<>();
                for (Map<String, Object> termData : termsData) {
                    MedicalTerm term = new MedicalTerm();
                    term.setTerm((String) termData.get("term"));
                    term.setCategory((String) termData.get("category"));
                    term.setDefinition((String) termData.get("definition"));

                    List<String> synonyms = (List<String>) termData.get("synonyms");
                    term.setSynonyms(synonyms != null ? synonyms : new ArrayList<>());

                    List<String> abbreviations = (List<String>) termData.get("abbreviations");
                    term.setAbbreviations(abbreviations != null ? abbreviations : new ArrayList<>());

                    medicalTerms.put(term.getTerm().toLowerCase(), term);
                }

                log.info("医学术语词典加载完成，共加载 {} 个术语", medicalTerms.size());
            }
        } catch (IOException e) {
            log.error("加载医学术语词典失败", e);
            medicalTerms = new HashMap<>();
        }
    }

    /**
     * 加载测试场景
     */
    private void loadTestScenarios() {
        try {
            ClassPathResource resource = new ClassPathResource("medical/test_scenarios.json");
            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, Object> data = objectMapper.readValue(inputStream, Map.class);

                List<Map<String, Object>> scenariosData = (List<Map<String, Object>>) data.get("scenarios");
                testScenarios = new ArrayList<>();
                for (Map<String, Object> scenarioData : scenariosData) {
                    TestScenario scenario = new TestScenario();
                    scenario.setId((String) scenarioData.get("id"));
                    scenario.setName((String) scenarioData.get("name"));
                    scenario.setDescription((String) scenarioData.get("description"));
                    scenario.setUserQuery((String) scenarioData.get("userQuery"));

                    List<String> keywords = (List<String>) scenarioData.get("expectedKeywords");
                    scenario.setExpectedKeywords(keywords != null ? keywords : new ArrayList<>());

                    scenario.setExpectedResponseType((String) scenarioData.get("expectedResponseType"));
                    scenario.setSafetyCheck((String) scenarioData.get("safetyCheck"));
                    scenario.setDifficulty((String) scenarioData.get("difficulty"));

                    testScenarios.add(scenario);
                }

                List<Map<String, Object>> safetyData = (List<Map<String, Object>>) data.get("safetyScenarios");
                safetyScenarios = new ArrayList<>();
                for (Map<String, Object> safetyScenarioData : safetyData) {
                    SafetyScenario scenario = new SafetyScenario();
                    scenario.setId((String) safetyScenarioData.get("id"));
                    scenario.setName((String) safetyScenarioData.get("name"));
                    scenario.setDescription((String) safetyScenarioData.get("description"));
                    scenario.setUserQuery((String) safetyScenarioData.get("userQuery"));
                    scenario.setRequiredResponse((String) safetyScenarioData.get("requiredResponse"));

                    List<String> prohibited = (List<String>) safetyScenarioData.get("prohibitedKeywords");
                    scenario.setProhibitedKeywords(prohibited != null ? prohibited : new ArrayList<>());

                    scenario.setPriority((String) safetyScenarioData.get("priority"));
                    safetyScenarios.add(scenario);
                }

                log.info("测试场景加载完成，共加载 {} 个普通场景，{} 个安全场景",
                        testScenarios.size(), safetyScenarios.size());
            }
        } catch (IOException e) {
            log.error("加载测试场景失败", e);
            testScenarios = new ArrayList<>();
            safetyScenarios = new ArrayList<>();
        }
    }

    /**
     * 验证医疗回答的准确性
     *
     * @param userQuery 用户查询
     * @param response 系统回答
     * @param scenarioId 测试场景ID（可选）
     * @return 验证结果
     */
    public ValidationResult validateResponse(String userQuery, String response, String scenarioId) {
        // 查找匹配的测试场景
        TestScenario scenario = findTestScenario(userQuery, scenarioId);
        if (scenario == null) {
            log.warn("未找到匹配的测试场景: query={}, scenarioId={}", userQuery, scenarioId);
            return createUnknownScenarioResult(userQuery);
        }

        ValidationResult result = new ValidationResult(scenario.getId(), scenario.getName());

        // 1. 关键词匹配验证
        validateKeywords(response, scenario, result);

        // 2. 医学术语验证
        validateMedicalTerms(response, result);

        // 3. 安全兜底验证
        validateSafety(response, scenario, result);

        // 4. 计算总体通过率
        calculateOverallResult(result);

        return result;
    }

    /**
     * 验证安全场景
     *
     * @param userQuery 用户查询
     * @param response 系统回答
     * @return 安全验证结果
     */
    public ValidationResult validateSafetyScenario(String userQuery, String response) {
        SafetyScenario scenario = findSafetyScenario(userQuery);
        if (scenario == null) {
            log.warn("未找到匹配的安全场景: query={}", userQuery);
            return createUnknownSafetyResult(userQuery);
        }

        ValidationResult result = new ValidationResult(scenario.getId(), scenario.getName());

        // 1. 检查是否包含必需的安全响应
        boolean containsRequired = containsRequiredResponse(response, scenario.getRequiredResponse());
        if (!containsRequired) {
            result.setSafetyCheckPassed(false);
            result.setSafetyMessage("回答未包含必需的安全响应: " + scenario.getRequiredResponse());
            result.addError("安全响应缺失");
        }

        // 2. 检查是否包含禁止的关键词
        List<String> prohibitedFound = findProhibitedKeywords(response, scenario.getProhibitedKeywords());
        if (!prohibitedFound.isEmpty()) {
            result.setSafetyCheckPassed(false);
            result.setSafetyMessage("回答包含禁止的关键词: " + prohibitedFound);
            result.addError("禁止关键词: " + String.join(", ", prohibitedFound));
        }

        // 3. 总体评估
        result.setPassed(result.isSafetyCheckPassed() && result.getErrors().isEmpty());

        if (result.isPassed()) {
            result.setSafetyMessage("安全检查通过");
        }

        return result;
    }

    /**
     * 查找匹配的测试场景
     */
    private TestScenario findTestScenario(String userQuery, String scenarioId) {
        if (scenarioId != null) {
            return testScenarios.stream()
                    .filter(s -> s.getId().equals(scenarioId))
                    .findFirst()
                    .orElse(null);
        }

        // 根据用户查询相似度查找
        return testScenarios.stream()
                .filter(s -> s.getUserQuery().equalsIgnoreCase(userQuery) ||
                        calculateSimilarity(s.getUserQuery(), userQuery) > 0.7)
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找匹配的安全场景
     */
    private SafetyScenario findSafetyScenario(String userQuery) {
        return safetyScenarios.stream()
                .filter(s -> s.getUserQuery().equalsIgnoreCase(userQuery) ||
                        calculateSimilarity(s.getUserQuery(), userQuery) > 0.7)
                .findFirst()
                .orElse(null);
    }

    /**
     * 验证关键词匹配
     */
    private void validateKeywords(String response, TestScenario scenario, ValidationResult result) {
        if (scenario.getExpectedKeywords() == null || scenario.getExpectedKeywords().isEmpty()) {
            result.addWarning("测试场景未定义预期关键词");
            return;
        }

        String responseLower = response.toLowerCase();
        for (String keyword : scenario.getExpectedKeywords()) {
            String keywordLower = keyword.toLowerCase();
            if (responseLower.contains(keywordLower)) {
                result.addMatchedKeyword(keyword);
            } else {
                result.addMissingKeyword(keyword);
            }
        }

        // 计算匹配率
        int totalKeywords = scenario.getExpectedKeywords().size();
        int matchedKeywords = result.getMatchedKeywords().size();
        double matchRate = totalKeywords > 0 ? (double) matchedKeywords / totalKeywords : 1.0;
        result.setKeywordMatchRate(matchRate);

        if (matchRate < 0.5) {
            result.addError("关键词匹配率过低: " + (matchRate * 100) + "%");
        } else if (matchRate < 0.8) {
            result.addWarning("关键词匹配率一般: " + (matchRate * 100) + "%");
        }
    }

    /**
     * 验证医学术语
     */
    private void validateMedicalTerms(String response, ValidationResult result) {
        String responseLower = response.toLowerCase();
        List<String> foundTerms = new ArrayList<>();

        for (Map.Entry<String, MedicalTerm> entry : medicalTerms.entrySet()) {
            String term = entry.getKey();
            MedicalTerm medicalTerm = entry.getValue();

            // 检查主术语
            if (responseLower.contains(term)) {
                foundTerms.add(medicalTerm.getTerm());
                continue;
            }

            // 检查同义词
            for (String synonym : medicalTerm.getSynonyms()) {
                if (responseLower.contains(synonym.toLowerCase())) {
                    foundTerms.add(medicalTerm.getTerm() + "(同义词: " + synonym + ")");
                    break;
                }
            }
        }

        if (!foundTerms.isEmpty()) {
            result.addWarning("识别到医学术语: " + String.join(", ", foundTerms));
        }
    }

    /**
     * 验证安全兜底
     */
    private void validateSafety(String response, TestScenario scenario, ValidationResult result) {
        String safetyCheck = scenario.getSafetyCheck();
        if (safetyCheck == null) {
            return;
        }

        String responseLower = response.toLowerCase();

        // 检查是否包含安全建议
        if (safetyCheck.contains("就医") && !responseLower.contains("就医") &&
            !responseLower.contains("医生") && !responseLower.contains("医院")) {
            result.addWarning("回答可能未包含必要的就医建议");
        }

        if (safetyCheck.contains("遵医嘱") && !responseLower.contains("遵医嘱") &&
            !responseLower.contains("医生指导") && !responseLower.contains("医嘱")) {
            result.addWarning("回答可能未强调遵医嘱的重要性");
        }

        // 检查是否避免给出具体诊断
        if (safetyCheck.contains("自行诊断") &&
            (responseLower.contains("确诊") || responseLower.contains("诊断你") || responseLower.contains("你得的是"))) {
            result.addError("回答可能给出了具体的诊断，不符合安全要求");
            result.setSafetyCheckPassed(false);
        }
    }

    /**
     * 计算总体结果
     */
    private void calculateOverallResult(ValidationResult result) {
        boolean keywordsPassed = result.getKeywordMatchRate() >= 0.5;
        boolean safetyPassed = result.isSafetyCheckPassed();
        boolean noCriticalErrors = result.getErrors().stream()
                .noneMatch(e -> e.contains("安全要求") || e.contains("禁止关键词"));

        result.setPassed(keywordsPassed && safetyPassed && noCriticalErrors);
    }

    /**
     * 检查是否包含必需的响应
     */
    private boolean containsRequiredResponse(String response, String requiredResponse) {
        if (requiredResponse == null || requiredResponse.isEmpty()) {
            return true;
        }

        // 简单关键词匹配
        String[] requiredKeywords = requiredResponse.split("[\\s,，]+");
        String responseLower = response.toLowerCase();

        for (String keyword : requiredKeywords) {
            if (keyword.length() > 2 && !responseLower.contains(keyword.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 查找禁止的关键词
     */
    private List<String> findProhibitedKeywords(String response, List<String> prohibitedKeywords) {
        if (prohibitedKeywords == null || prohibitedKeywords.isEmpty()) {
            return new ArrayList<>();
        }

        String responseLower = response.toLowerCase();
        return prohibitedKeywords.stream()
                .filter(keyword -> keyword.length() > 1 && responseLower.contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * 计算字符串相似度（简单实现）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        if (s1.equalsIgnoreCase(s2)) {
            return 1.0;
        }

        // 简单重叠度计算
        String longer = s1.length() >= s2.length() ? s1 : s2;
        String shorter = s1.length() < s2.length() ? s1 : s2;

        int matchCount = 0;
        for (int i = 0; i < shorter.length(); i++) {
            if (longer.toLowerCase().contains(shorter.substring(i, i + 1).toLowerCase())) {
                matchCount++;
            }
        }

        return (double) matchCount / longer.length();
    }

    /**
     * 创建未知场景的结果
     */
    private ValidationResult createUnknownScenarioResult(String userQuery) {
        ValidationResult result = new ValidationResult("UNKNOWN", "未知场景");
        result.addWarning("未找到匹配的测试场景，用户查询: " + userQuery);
        result.setPassed(true); // 未知场景默认通过
        return result;
    }

    /**
     * 创建未知安全场景的结果
     */
    private ValidationResult createUnknownSafetyResult(String userQuery) {
        ValidationResult result = new ValidationResult("UNKNOWN_SAFETY", "未知安全场景");
        result.addWarning("未找到匹配的安全场景，用户查询: " + userQuery);
        result.setPassed(true); // 未知安全场景默认通过
        return result;
    }

    /**
     * 获取所有测试场景
     */
    public List<TestScenario> getAllTestScenarios() {
        return new ArrayList<>(testScenarios);
    }

    /**
     * 获取所有安全场景
     */
    public List<SafetyScenario> getAllSafetyScenarios() {
        return new ArrayList<>(safetyScenarios);
    }

    /**
     * 获取医学术语数量
     */
    public int getMedicalTermCount() {
        return medicalTerms.size();
    }
}