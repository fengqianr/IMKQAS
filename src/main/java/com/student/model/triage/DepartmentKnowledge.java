package com.student.model.triage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 科室知识库配置实体
 * 存储科室相关的症状、关键词和优先级信息
 *
 * @author 系统生成
 * @version 1.0
 * @since 2026-04-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentKnowledge {

    /**
     * 科室ID
     */
    private String id;

    /**
     * 科室名称
     */
    private String name;

    /**
     * 科室关联的症状列表
     */
    private List<String> symptoms;

    /**
     * 科室关联的关键词列表
     */
    private List<String> keywords;

    /**
     * 科室优先级（数值越小优先级越高）
     */
    private Integer priority;

    /**
     * 是否为急诊科室
     */
    private Boolean emergency;

    /**
     * 急诊级别
     */
    private EmergencyLevel emergencyLevel;

    /**
     * 科室描述
     */
    private String description;

    /**
     * 检查症状是否匹配本科室
     *
     * @param symptom 症状描述
     * @return 如果症状匹配返回true，否则返回false
     */
    public boolean matchesSymptom(String symptom) {
        if (symptoms == null || symptom == null) {
            return false;
        }
        String normalizedSymptom = symptom.toLowerCase().trim();
        return symptoms.stream()
                .anyMatch(s -> s.toLowerCase().contains(normalizedSymptom) ||
                              normalizedSymptom.contains(s.toLowerCase()));
    }

    /**
     * 检查关键词是否匹配本科室
     *
     * @param keyword 关键词
     * @return 如果关键词匹配返回true，否则返回false
     */
    public boolean matchesKeyword(String keyword) {
        if (keywords == null || keyword == null) {
            return false;
        }
        String normalizedKeyword = keyword.toLowerCase().trim();
        return keywords.stream()
                .anyMatch(k -> k.toLowerCase().contains(normalizedKeyword) ||
                              normalizedKeyword.contains(k.toLowerCase()));
    }

    /**
     * 获取科室的完整显示信息
     *
     * @return 科室显示信息
     */
    public String getDisplayInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (emergency != null && emergency) {
            sb.append(" [急诊]");
        }
        if (emergencyLevel != null) {
            sb.append(" (").append(emergencyLevel.getDisplayName()).append(")");
        }
        if (description != null && !description.isEmpty()) {
            sb.append(" - ").append(description);
        }
        return sb.toString();
    }

    /**
     * 判断是否为急诊科室
     *
     * @return 如果是急诊科室返回true，否则返回false
     */
    public boolean isEmergencyDepartment() {
        return emergency != null && emergency;
    }
}