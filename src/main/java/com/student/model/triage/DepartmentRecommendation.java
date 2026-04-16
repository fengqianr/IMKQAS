package com.student.model.triage;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 科室推荐项
 * 用于存储科室推荐的相关信息，包括置信度、匹配症状等
 *
 * @author 系统生成
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRecommendation {
    private String departmentId;
    private String departmentName;
    private double confidence = 0.0;
    private String reason;
    private List<String> matchedSymptoms;
    private boolean emergency = false;
    private int priority = 1;

    /**
     * 格式化推荐文本
     */
    public String toDisplayText() {
        return String.format("%s (%.0f%%置信度): %s",
            departmentName, confidence * 100, reason);
    }
}