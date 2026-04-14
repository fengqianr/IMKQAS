package com.student.model.triage;

import lombok.Data;

import java.util.List;

/**
 * 科室知识库配置实体
 */
@Data
public class DepartmentKnowledge {
    private String id;
    private String name;
    private List<String> symptoms;
    private List<String> keywords;
    private int priority = 1;
    private boolean emergency = false;
    private String emergencyLevel;
    private String description;

    /**
     * 检查症状是否匹配
     */
    public boolean matchesSymptom(String symptom) {
        if (symptoms == null) return false;
        return symptoms.contains(symptom);
    }

    /**
     * 检查关键词是否匹配
     */
    public boolean matchesKeyword(String keyword) {
        if (keywords == null) return false;
        return keywords.contains(keyword);
    }
}