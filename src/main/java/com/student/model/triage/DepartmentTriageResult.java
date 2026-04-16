package com.student.model.triage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 科室分流结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentTriageResult {
    private String symptoms;
    private List<DepartmentRecommendation> recommendations;
    private EmergencyCheckResult emergencyCheck;
    private double confidence = 0.0;
    private String source = "UNKNOWN";
    private long processingTimeMs = 0L;
    private String advice;
    private Long userId;

    /**
     * 获取主要推荐科室
     */
    public DepartmentRecommendation getPrimaryRecommendation() {
        if (recommendations == null || recommendations.isEmpty()) {
            return null;
        }
        return recommendations.get(0);
    }

    /**
     * 是否包含急诊
     */
    public boolean hasEmergency() {
        return emergencyCheck != null && emergencyCheck.isEmergency();
    }
}