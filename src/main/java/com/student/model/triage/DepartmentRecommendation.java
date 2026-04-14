package com.student.model.triage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 科室推荐项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRecommendation {
    private String departmentId;
    private String departmentName;
    private double confidence = 0.0;
    private String reasoning;
    private List<String> matchedSymptoms;
    private boolean emergencyDepartment = false;
    private int priority = 1;

    /**
     * 格式化推荐文本
     */
    public String toDisplayText() {
        return String.format("%s (%.0f%%置信度): %s",
            departmentName, confidence * 100, reasoning);
    }
}