package com.student.service.triage.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.student.model.triage.DepartmentRecommendation;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.EmergencyLevel;
import com.student.service.LlmService;
import com.student.service.triage.config.TriageConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM分流适配器
 * 负责LLM提示词构建、响应解析和结果转换
 *
 * @author 系统生成
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class LlmTriageAdapter {

    private final LlmService llmService;
    private final TriageConfig config;
    private final ExecutorService executorService;

    // LLM响应解析正则表达式
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile("主要推荐科室[:：]\\s*([^\\n]+)");
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("置信度[:：]\\s*([0-9]*\\.?[0-9]+)");
    private static final Pattern REASON_PATTERN = Pattern.compile("理由[:：]\\s*([^\\n]+)");
    private static final Pattern ALTERNATIVE_PATTERN = Pattern.compile("备选科室[:：]\\s*([^\\n]+)");
    private static final Pattern EMERGENCY_PATTERN = Pattern.compile("紧急程度[:：]\\s*(CRITICAL|HIGH|MEDIUM|LOW)", Pattern.CASE_INSENSITIVE);

    // 缓存键前缀
    private static final String CACHE_KEY_PREFIX = "triage:llm:";

    /**
     * 使用LLM分析症状
     *
     * @param symptoms 症状描述
     * @return 分流结果
     */
    public DepartmentTriageResult analyze(String symptoms) {
        return analyze(symptoms, false);
    }

    /**
     * 使用LLM分析症状（支持异步）
     *
     * @param symptoms 症状描述
     * @param async 是否异步执行
     * @return 分流结果
     */
    public DepartmentTriageResult analyze(String symptoms, boolean async) {
        if (!isAvailable()) {
            log.warn("LLM服务不可用，跳过LLM分析");
            return null;
        }

        long startTime = System.currentTimeMillis();

        try {
            if (async && executorService != null) {
                CompletableFuture<DepartmentTriageResult> future = CompletableFuture.supplyAsync(
                    () -> performLlmAnalysis(symptoms),
                    executorService
                );

                return future.get(config.getLlmTimeout(), TimeUnit.MILLISECONDS);
            } else {
                return performLlmAnalysis(symptoms);
            }

        } catch (Exception e) {
            log.error("LLM分析异常: symptoms={}", symptoms, e);
            return null;
        } finally {
            long processingTime = System.currentTimeMillis() - startTime;
            log.debug("LLM分析完成: 处理时间={}ms", processingTime);
        }
    }

    /**
     * 执行LLM分析
     */
    private DepartmentTriageResult performLlmAnalysis(String symptoms) {
        try {
            // 1. 构建提示词
            String prompt = buildTriagePrompt(symptoms);
            log.debug("LLM提示词构建完成: {}", prompt.substring(0, Math.min(100, prompt.length())) + "...");

            // 2. 调用LLM服务
            String llmResponse = llmService.generateAnswer(prompt, Collections.emptyList());
            if (llmResponse == null || llmResponse.trim().isEmpty()) {
                log.warn("LLM返回空响应");
                return createFallbackResult(symptoms, "LLM返回空响应");
            }

            log.debug("LLM响应接收完成: {}", llmResponse.substring(0, Math.min(200, llmResponse.length())) + "...");

            // 3. 解析LLM响应
            return parseLlmResponse(llmResponse, symptoms);

        } catch (Exception e) {
            log.error("LLM分析执行失败: symptoms={}", symptoms, e);
            return createFallbackResult(symptoms, "LLM分析失败: " + e.getMessage());
        }
    }

    /**
     * 构建分流提示词
     */
    private String buildTriagePrompt(String symptoms) {
        String template = config.getLlmTriagePromptTemplate();

        // 替换模板中的占位符
        String prompt = template.replace("{symptoms}", symptoms);

        // 添加额外的指令
        prompt += "\n\n注意：请确保回复格式严格遵循上述要求，不要添加额外说明。";

        return prompt;
    }

    /**
     * 解析LLM响应
     */
    private DepartmentTriageResult parseLlmResponse(String llmResponse, String originalSymptoms) {
        try {
            // 1. 解析主要推荐科室
            String departmentName = extractDepartment(llmResponse);
            if (departmentName == null || departmentName.trim().isEmpty()) {
                log.warn("LLM响应中未找到科室推荐");
                return createFallbackResult(originalSymptoms, "LLM响应中未找到科室推荐");
            }

            // 2. 解析置信度
            double confidence = extractConfidence(llmResponse);

            // 3. 解析推荐理由
            String reason = extractReason(llmResponse);

            // 4. 解析备选科室
            List<String> alternativeDepartments = extractAlternativeDepartments(llmResponse);

            // 5. 解析紧急程度
            EmergencyLevel emergencyLevel = extractEmergencyLevel(llmResponse);

            // 6. 构建推荐列表
            List<DepartmentRecommendation> recommendations = buildRecommendations(
                departmentName, confidence, reason, alternativeDepartments);

            // 7. 构建分流结果
            DepartmentTriageResult result = new DepartmentTriageResult();
            result.setSymptoms(originalSymptoms);
            result.setRecommendations(recommendations);
            result.setConfidence(confidence);
            result.setSource("LLM");
            result.setAdvice(generateLlmAdvice(departmentName, confidence, reason, emergencyLevel));
            result.setProcessingTimeMs(0L); // 实际处理时间在外部记录

            log.info("LLM响应解析成功: 科室={}, 置信度={}, 紧急程度={}",
                departmentName, confidence, emergencyLevel);

            return result;

        } catch (Exception e) {
            log.error("LLM响应解析失败: response={}", llmResponse, e);
            return createFallbackResult(originalSymptoms, "LLM响应解析失败: " + e.getMessage());
        }
    }

    /**
     * 提取科室名称
     */
    private String extractDepartment(String llmResponse) {
        Matcher matcher = DEPARTMENT_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 备用匹配模式
        Pattern[] alternativePatterns = {
            Pattern.compile("推荐科室[:：]\\s*([^\\n]+)"),
            Pattern.compile("建议就诊[:：]\\s*([^\\n]+)"),
            Pattern.compile("科室[:：]\\s*([^\\n]+)")
        };

        for (Pattern pattern : alternativePatterns) {
            Matcher altMatcher = pattern.matcher(llmResponse);
            if (altMatcher.find()) {
                return altMatcher.group(1).trim();
            }
        }

        return null;
    }

    /**
     * 提取置信度
     */
    private double extractConfidence(String llmResponse) {
        Matcher matcher = CONFIDENCE_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            try {
                double confidence = Double.parseDouble(matcher.group(1));
                // 确保置信度在0-1之间
                return Math.max(0.0, Math.min(1.0, confidence));
            } catch (NumberFormatException e) {
                log.warn("置信度解析失败: {}", matcher.group(1));
            }
        }

        // 默认置信度
        return 0.7;
    }

    /**
     * 提取推荐理由
     */
    private String extractReason(String llmResponse) {
        Matcher matcher = REASON_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 查找包含"理由"的行
        String[] lines = llmResponse.split("\\n");
        for (String line : lines) {
            if (line.contains("理由") || line.contains("原因") || line.contains("依据")) {
                return line.replaceAll(".*[:：]", "").trim();
            }
        }

        return "基于症状分析和医学知识推荐";
    }

    /**
     * 提取备选科室
     */
    private List<String> extractAlternativeDepartments(String llmResponse) {
        Matcher matcher = ALTERNATIVE_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            String alternatives = matcher.group(1).trim();
            // 分割备选科室
            return List.of(alternatives.split("[,，、\\s]+"));
        }

        return Collections.emptyList();
    }

    /**
     * 提取紧急程度
     */
    private EmergencyLevel extractEmergencyLevel(String llmResponse) {
        Matcher matcher = EMERGENCY_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            try {
                return EmergencyLevel.valueOf(matcher.group(1).toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("紧急程度解析失败: {}", matcher.group(1));
            }
        }

        return EmergencyLevel.LOW;
    }

    /**
     * 构建推荐列表
     */
    private List<DepartmentRecommendation> buildRecommendations(String primaryDepartment,
                                                               double confidence,
                                                               String reason,
                                                               List<String> alternativeDepartments) {
        List<DepartmentRecommendation> recommendations = new ArrayList<>();

        // 主要推荐
        DepartmentRecommendation primary = new DepartmentRecommendation();
        primary.setDepartmentId(generateDepartmentId(primaryDepartment));
        primary.setDepartmentName(primaryDepartment);
        primary.setConfidence(confidence);
        primary.setReason(reason);
        primary.setEmergency(false); // LLM通常不判断急诊
        recommendations.add(primary);

        // 备选推荐
        for (String altDept : alternativeDepartments) {
            if (altDept != null && !altDept.trim().isEmpty() && !altDept.equals(primaryDepartment)) {
                DepartmentRecommendation alternative = new DepartmentRecommendation();
                alternative.setDepartmentId(generateDepartmentId(altDept));
                alternative.setDepartmentName(altDept.trim());
                alternative.setConfidence(confidence * 0.7); // 备选科室置信度较低
                alternative.setReason("备选推荐，症状部分匹配");
                alternative.setEmergency(false);
                recommendations.add(alternative);
            }
        }

        return recommendations;
    }

    /**
     * 生成科室ID
     */
    private String generateDepartmentId(String departmentName) {
        // 简单的ID生成逻辑：将科室名称转换为小写并用下划线连接
        return "llm_" + departmentName.toLowerCase()
                .replaceAll("[\\s\\-]", "_")
                .replaceAll("[^a-z0-9_]", "");
    }

    /**
     * 生成LLM建议
     */
    private String generateLlmAdvice(String department, double confidence, String reason, EmergencyLevel emergencyLevel) {
        StringBuilder advice = new StringBuilder();

        advice.append("基于AI分析，建议就诊").append(department);

        if (confidence >= 0.8) {
            advice.append("（高度匹配）");
        } else if (confidence >= 0.6) {
            advice.append("（匹配度良好）");
        } else {
            advice.append("（建议进一步确认）");
        }

        advice.append("。").append(reason);

        if (emergencyLevel != EmergencyLevel.LOW) {
            advice.append("。注意：检测到").append(emergencyLevel.getDescription())
                  .append("，请及时就医。");
        }

        return advice.toString();
    }

    /**
     * 创建降级结果
     */
    private DepartmentTriageResult createFallbackResult(String symptoms, String errorMessage) {
        DepartmentTriageResult result = new DepartmentTriageResult();
        result.setSymptoms(symptoms);
        result.setRecommendations(Collections.emptyList());
        result.setConfidence(0.0);
        result.setSource("LLM_FALLBACK");
        result.setAdvice("LLM分析失败，建议咨询导诊台。错误：" + errorMessage);
        result.setProcessingTimeMs(0L);
        return result;
    }

    /**
     * 检查LLM服务是否可用
     */
    public boolean isAvailable() {
        return llmService != null && llmService.isAvailable();
    }

    /**
     * 获取LLM服务信息
     */
    public String getLlmServiceInfo() {
        if (llmService == null) {
            return "LLM服务未配置";
        }
        return llmService.getModelInfo().getName();
    }

    /**
     * 批量分析症状
     *
     * @param symptomsList 症状列表
     * @return 分流结果列表
     */
    public List<DepartmentTriageResult> batchAnalyze(List<String> symptomsList) {
        List<DepartmentTriageResult> results = new ArrayList<>();

        for (String symptoms : symptomsList) {
            DepartmentTriageResult result = analyze(symptoms, true);
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    /**
     * 清除缓存（如果启用）
     */
    public void clearCache() {
        // 这里可以添加缓存清理逻辑
        log.info("LLM适配器缓存已清除");
    }
}