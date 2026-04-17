package com.student.service.triage.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.student.dto.triage.DepartmentKnowledgeBase;
import com.student.model.triage.DepartmentKnowledge;
import com.student.model.triage.DepartmentRecommendation;
import com.student.model.triage.DepartmentTriageResult;
import com.student.model.triage.EmergencyLevel;
import com.student.service.triage.config.TriageConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则引擎分流器
 * 基于配置规则的科室匹配和置信度计算
 *
 * @author 系统生成
 * @version 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class RuleBasedTriageEngine {

    private final DepartmentKnowledgeBase knowledgeBase;
    private final SymptomNormalizer symptomNormalizer;
    private final TriageConfig config;

    // 症状权重配置：不同症状可能具有不同重要性
    private static final double CRITICAL_SYMPTOM_WEIGHT = 2.0;
    private static final double MAJOR_SYMPTOM_WEIGHT = 1.5;
    private static final double MINOR_SYMPTOM_WEIGHT = 1.0;

    /**
     * 分析症状并推荐科室
     *
     * @param symptoms 症状描述
     * @return 分流结果
     */
    public DepartmentTriageResult analyze(String symptoms) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 标准化症状
            String normalizedSymptoms = symptomNormalizer.normalize(symptoms);
            log.debug("规则引擎分析开始: 原始症状='{}', 标准化症状='{}'", symptoms, normalizedSymptoms);

            // 2. 匹配科室
            List<DepartmentRecommendation> recommendations = matchDepartments(normalizedSymptoms);

            // 3. 计算总体置信度
            double overallConfidence = calculateOverallConfidence(recommendations);

            // 4. 生成建议
            String advice = generateAdvice(recommendations, overallConfidence);

            // 5. 构建结果
            DepartmentTriageResult result = new DepartmentTriageResult();
            result.setSymptoms(normalizedSymptoms);
            result.setRecommendations(recommendations);
            result.setConfidence(overallConfidence);
            result.setSource("RULE_ENGINE");
            result.setAdvice(advice);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            log.info("规则引擎分析完成: 症状='{}', 推荐科室数={}, 置信度={}, 处理时间={}ms",
                symptoms, recommendations.size(), overallConfidence, result.getProcessingTimeMs());

            return result;

        } catch (Exception e) {
            log.error("规则引擎分析异常: symptoms={}", symptoms, e);
            return createFallbackResult(symptoms, "规则引擎分析失败: " + e.getMessage());
        }
    }

    /**
     * 匹配科室
     * 根据症状描述匹配最合适的科室
     */
    private List<DepartmentRecommendation> matchDepartments(String normalizedSymptoms) {
        List<DepartmentRecommendation> recommendations = new ArrayList<>();

        if (knowledgeBase.getDepartments() == null || knowledgeBase.getDepartments().isEmpty()) {
            log.warn("科室知识库为空，无法进行匹配");
            return recommendations;
        }

        // 对每个科室计算匹配分数
        for (DepartmentKnowledge dept : knowledgeBase.getDepartments()) {
            double matchScore = calculateDepartmentMatchScore(normalizedSymptoms, dept);

            if (matchScore >= config.getMinMatchScore()) {
                DepartmentRecommendation recommendation = createRecommendation(dept, matchScore);
                recommendations.add(recommendation);
                log.debug("科室匹配: {} (匹配分数: {})", dept.getName(), matchScore);
            }
        }

        // 按匹配分数降序排序
        recommendations.sort(Comparator.comparing(DepartmentRecommendation::getConfidence).reversed());

        // 限制推荐数量
        if (recommendations.size() > config.getMaxRecommendations()) {
            recommendations = recommendations.subList(0, config.getMaxRecommendations());
        }

        return recommendations;
    }

    /**
     * 计算科室匹配分数
     */
    private double calculateDepartmentMatchScore(String symptoms, DepartmentKnowledge dept) {
        if (dept.getSymptoms() == null || dept.getSymptoms().isEmpty()) {
            return 0.0;
        }

        // 将症状拆分为独立症状
        List<String> symptomList = symptomNormalizer.splitSymptoms(symptoms);
        if (symptomList.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        int matchedSymptoms = 0;

        // 检查每个症状是否匹配科室的症状列表
        for (String symptom : symptomList) {
            double symptomScore = calculateSymptomMatchScore(symptom, dept.getSymptoms());
            if (symptomScore > 0) {
                totalScore += symptomScore;
                matchedSymptoms++;
            }
        }

        if (matchedSymptoms == 0) {
            return 0.0;
        }

        // 计算平均分数，并考虑匹配症状比例
        double averageScore = totalScore / matchedSymptoms;
        double coverageRatio = (double) matchedSymptoms / symptomList.size();

        // 综合分数 = 平均分数 * 覆盖率 * 症状权重
        double finalScore = averageScore * coverageRatio * calculateDepartmentWeight(dept);

        // 确保分数在0-1之间
        return Math.min(1.0, Math.max(0.0, finalScore));
    }

    /**
     * 计算症状匹配分数
     */
    private double calculateSymptomMatchScore(String symptom, List<String> departmentSymptoms) {
        double maxScore = 0.0;

        for (String deptSymptom : departmentSymptoms) {
            double score = calculateSymptomSimilarity(symptom, deptSymptom);
            if (score > maxScore) {
                maxScore = score;
            }
        }

        return maxScore;
    }

    /**
     * 计算症状相似度
     */
    private double calculateSymptomSimilarity(String symptom1, String symptom2) {
        if (symptom1.equals(symptom2)) {
            return 1.0;
        }

        // 检查是否包含关系
        if (symptom1.contains(symptom2) || symptom2.contains(symptom1)) {
            double lengthRatio = (double) Math.min(symptom1.length(), symptom2.length())
                               / Math.max(symptom1.length(), symptom2.length());
            return 0.7 + 0.3 * lengthRatio; // 基础分0.7，根据长度比例调整
        }

        // 使用编辑距离计算相似度
        int editDistance = calculateEditDistance(symptom1, symptom2);
        int maxLength = Math.max(symptom1.length(), symptom2.length());

        if (maxLength == 0) {
            return 1.0;
        }

        double similarity = 1.0 - (double) editDistance / maxLength;

        // 应用阈值过滤
        return similarity >= config.getFuzzyMatchThreshold() ? similarity : 0.0;
    }

    /**
     * 计算编辑距离（Levenshtein距离）
     */
    private int calculateEditDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * 计算科室权重
     * 急诊科室和关键科室具有更高权重
     */
    private double calculateDepartmentWeight(DepartmentKnowledge dept) {
        double weight = 1.0;

        if (dept.isEmergency()) {
            weight *= CRITICAL_SYMPTOM_WEIGHT;
        }

        // 根据优先级调整权重
        if (dept.getPriority() > 0) {
            weight *= (1.0 + dept.getPriority() * 0.1);
        }

        return weight;
    }

    /**
     * 创建推荐对象
     */
    private DepartmentRecommendation createRecommendation(DepartmentKnowledge dept, double matchScore) {
        DepartmentRecommendation recommendation = new DepartmentRecommendation();
        recommendation.setDepartmentId(dept.getId());
        recommendation.setDepartmentName(dept.getName());
        recommendation.setConfidence(matchScore);
        recommendation.setEmergency(dept.isEmergency());
        recommendation.setReason(generateRecommendationReason(dept, matchScore));
        recommendation.setPriority(dept.getPriority());
        return recommendation;
    }

    /**
     * 生成推荐理由
     */
    private String generateRecommendationReason(DepartmentKnowledge dept, double confidence) {
        if (confidence >= 0.8) {
            return String.format("症状高度匹配%s的典型症状", dept.getName());
        } else if (confidence >= 0.6) {
            return String.format("症状与%s的常见症状相符", dept.getName());
        } else {
            return String.format("症状部分匹配%s的症状特征", dept.getName());
        }
    }

    /**
     * 计算总体置信度
     */
    private double calculateOverallConfidence(List<DepartmentRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            return 0.0;
        }

        // 使用最高置信度作为总体置信度，但根据推荐数量进行衰减
        double maxConfidence = recommendations.get(0).getConfidence();

        if (recommendations.size() == 1) {
            return maxConfidence;
        } else {
            // 多个推荐时，置信度适当衰减
            double decayFactor = 0.9 / recommendations.size();
            return maxConfidence * (1.0 - decayFactor);
        }
    }

    /**
     * 生成建议文本
     */
    private String generateAdvice(List<DepartmentRecommendation> recommendations, double overallConfidence) {
        if (recommendations.isEmpty()) {
            return "未找到匹配的科室，建议咨询导诊台";
        }

        if (overallConfidence >= 0.7) {
            DepartmentRecommendation primary = recommendations.get(0);
            return String.format("建议优先就诊%s。%s", primary.getDepartmentName(), primary.getReason());
        } else if (overallConfidence >= 0.5) {
            StringBuilder advice = new StringBuilder("建议考虑以下科室：");
            for (int i = 0; i < Math.min(2, recommendations.size()); i++) {
                advice.append(recommendations.get(i).getDepartmentName());
                if (i < Math.min(2, recommendations.size()) - 1) {
                    advice.append("或");
                }
            }
            advice.append("。请结合其他症状判断。");
            return advice.toString();
        } else {
            return "症状匹配度较低，建议咨询全科医学科或导诊台进一步评估";
        }
    }

    /**
     * 创建降级结果
     */
    private DepartmentTriageResult createFallbackResult(String symptoms, String errorMessage) {
        DepartmentTriageResult result = new DepartmentTriageResult();
        result.setSymptoms(symptoms);
        result.setRecommendations(new ArrayList<>());
        result.setConfidence(0.0);
        result.setSource("RULE_ENGINE_FALLBACK");
        result.setAdvice("规则引擎处理失败，建议咨询导诊台。错误：" + errorMessage);
        result.setProcessingTimeMs(0L);
        return result;
    }

    /**
     * 获取知识库信息（用于调试）
     */
    public DepartmentKnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    /**
     * 获取配置信息（用于调试）
     */
    public TriageConfig getConfig() {
        return config;
    }
}