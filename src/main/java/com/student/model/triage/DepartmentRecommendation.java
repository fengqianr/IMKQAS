package com.student.model.triage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 科室推荐项实体
 * 表示对特定科室的推荐信息，包含置信度和匹配原因
 *
 * @author 系统生成
 * @version 1.0
 * @since 2026-04-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRecommendation {

    /**
     * 科室ID
     */
    private String departmentId;

    /**
     * 科室名称
     */
    private String departmentName;

    /**
     * 推荐置信度（0.0-1.0）
     */
    private Double confidence;

    /**
     * 推荐理由说明
     */
    private String reasoning;

    /**
     * 匹配的症状列表
     */
    private List<String> matchedSymptoms;

    /**
     * 是否为急诊科室
     */
    private Boolean emergencyDepartment;

    /**
     * 科室优先级
     */
    private Integer priority;

    /**
     * 转换为显示文本
     *
     * @return 格式化的显示文本
     */
    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("科室: ").append(departmentName);

        if (confidence != null) {
            sb.append(" (置信度: ").append(String.format("%.1f%%", confidence * 100)).append(")");
        }

        if (emergencyDepartment != null && emergencyDepartment) {
            sb.append(" [急诊]");
        }

        if (reasoning != null && !reasoning.isEmpty()) {
            sb.append("\n理由: ").append(reasoning);
        }

        if (matchedSymptoms != null && !matchedSymptoms.isEmpty()) {
            sb.append("\n匹配症状: ").append(String.join("、", matchedSymptoms));
        }

        return sb.toString();
    }

    /**
     * 获取简化的推荐信息
     *
     * @return 简化推荐信息
     */
    public String getSimpleRecommendation() {
        StringBuilder sb = new StringBuilder();
        sb.append(departmentName);

        if (emergencyDepartment != null && emergencyDepartment) {
            sb.append("（急诊）");
        }

        if (confidence != null) {
            sb.append(" - ").append(String.format("%.0f%%", confidence * 100)).append("匹配");
        }

        return sb.toString();
    }

    /**
     * 判断是否为急诊科室推荐
     *
     * @return 如果是急诊科室推荐返回true，否则返回false
     */
    public boolean isEmergencyRecommendation() {
        return emergencyDepartment != null && emergencyDepartment;
    }

    /**
     * 获取置信度百分比
     *
     * @return 置信度百分比（0-100）
     */
    public int getConfidencePercentage() {
        return confidence != null ? (int) Math.round(confidence * 100) : 0;
    }
}