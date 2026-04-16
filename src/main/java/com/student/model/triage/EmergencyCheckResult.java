package com.student.model.triage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 急诊症状检查结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyCheckResult {
    private boolean isEmergency = false;
    private EmergencyLevel emergencyLevel = EmergencyLevel.LOW;
    private List<String> emergencySymptoms;
    private String immediateAction;
    private String warningMessage;
    private String advice;

    /**
     * 获取急诊建议
     */
    public String getEmergencyAdvice() {
        if (!isEmergency) {
            return "症状无需急诊，请预约常规门诊。";
        }
        return String.format("急诊%s: %s。建议：%s",
            emergencyLevel.getLevelName(),
            warningMessage,
            immediateAction);
    }
}