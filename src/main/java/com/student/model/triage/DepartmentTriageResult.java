package com.student.model.triage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * 科室分流结果实体
 * 存储完整的科室分流结果信息
 *
 * @author 系统生成
 * @version 1.0
 * @since 2026-04-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentTriageResult {

    /**
     * 输入的症状描述
     */
    private String symptoms;

    /**
     * 科室推荐列表
     */
    private List<DepartmentRecommendation> recommendations;

    /**
     * 急诊检查结果
     */
    private EmergencyCheckResult emergencyCheck;

    /**
     * 整体置信度
     */
    private Double confidence;

    /**
     * 结果来源（rule_engine, llm_fallback, cache）
     */
    private String source;

    /**
     * 处理时间（毫秒）
     */
    private Long processingTimeMs;

    /**
     * 推荐建议
     */
    private String recommendationAdvice;

    /**
     * 获取主要推荐（置信度最高的推荐）
     *
     * @return 主要推荐项，如果没有推荐则返回null
     */
    public DepartmentRecommendation getPrimaryRecommendation() {
        if (recommendations == null || recommendations.isEmpty()) {
            return null;
        }

        return recommendations.stream()
                .max((r1, r2) -> {
                    double conf1 = r1.getConfidence() != null ? r1.getConfidence() : 0.0;
                    double conf2 = r2.getConfidence() != null ? r2.getConfidence() : 0.0;
                    return Double.compare(conf1, conf2);
                })
                .orElse(null);
    }

    /**
     * 检查是否存在急诊情况
     *
     * @return 如果存在急诊情况返回true，否则返回false
     */
    public boolean hasEmergency() {
        return emergencyCheck != null && emergencyCheck.getIsEmergency() != null && emergencyCheck.getIsEmergency();
    }

    /**
     * 获取急诊级别
     *
     * @return 急诊级别，如果没有急诊情况返回null
     */
    public EmergencyLevel getEmergencyLevel() {
        return hasEmergency() && emergencyCheck.getEmergencyLevel() != null ? emergencyCheck.getEmergencyLevel() : null;
    }

    /**
     * 获取急诊建议
     *
     * @return 急诊建议文本
     */
    public String getEmergencyAdvice() {
        return hasEmergency() ? emergencyCheck.getEmergencyAdvice() : "无急诊症状";
    }

    /**
     * 获取推荐科室名称列表
     *
     * @return 推荐科室名称列表
     */
    public List<String> getRecommendedDepartmentNames() {
        if (recommendations == null) {
            return List.of();
        }
        return recommendations.stream()
                .map(DepartmentRecommendation::getDepartmentName)
                .toList();
    }

    /**
     * 获取急诊科室推荐
     *
     * @return 急诊科室推荐列表
     */
    public List<DepartmentRecommendation> getEmergencyRecommendations() {
        if (recommendations == null) {
            return List.of();
        }
        return recommendations.stream()
                .filter(DepartmentRecommendation::isEmergencyRecommendation)
                .toList();
    }

    /**
     * 获取处理时间描述
     *
     * @return 处理时间描述文本
     */
    public String getProcessingTimeDescription() {
        if (processingTimeMs == null) {
            return "未知";
        }
        if (processingTimeMs < 1000) {
            return processingTimeMs + "ms";
        } else {
            return String.format("%.2fs", processingTimeMs / 1000.0);
        }
    }

    /**
     * 获取结果摘要
     *
     * @return 结果摘要文本
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("症状: ").append(symptoms).append("\n");

        if (hasEmergency()) {
            sb.append("⚠️ 急诊检测: ").append(getEmergencyAdvice()).append("\n");
        }

        DepartmentRecommendation primary = getPrimaryRecommendation();
        if (primary != null) {
            sb.append("主要推荐: ").append(primary.getSimpleRecommendation()).append("\n");
        }

        if (recommendationAdvice != null && !recommendationAdvice.isEmpty()) {
            sb.append("建议: ").append(recommendationAdvice);
        }

        return sb.toString();
    }
}