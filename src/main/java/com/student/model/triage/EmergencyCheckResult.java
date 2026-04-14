package com.student.model.triage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 急诊症状检查结果实体
 * 存储急诊症状检查的结果和建议
 *
 * @author 系统生成
 * @version 1.0
 * @since 2026-04-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyCheckResult {

    /**
     * 是否为急诊情况
     */
    private Boolean isEmergency;

    /**
     * 急诊级别
     */
    private EmergencyLevel emergencyLevel;

    /**
     * 识别的急诊症状列表
     */
    private List<String> emergencySymptoms;

    /**
     * 立即行动建议
     */
    private String immediateAction;

    /**
     * 警告消息
     */
    private String warningMessage;

    /**
     * 获取急诊建议
     *
     * @return 急诊建议文本
     */
    public String getEmergencyAdvice() {
        if (!isEmergency) {
            return "未检测到急诊症状，建议常规门诊就诊。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("检测到急诊症状！\n");

        if (emergencyLevel != null) {
            sb.append("急诊级别: ").append(emergencyLevel.getDisplayName()).append("\n");
        }

        if (immediateAction != null && !immediateAction.isEmpty()) {
            sb.append("立即行动: ").append(immediateAction).append("\n");
        }

        if (emergencySymptoms != null && !emergencySymptoms.isEmpty()) {
            sb.append("识别症状: ").append(String.join("、", emergencySymptoms)).append("\n");
        }

        if (warningMessage != null && !warningMessage.isEmpty()) {
            sb.append("警告: ").append(warningMessage);
        }

        return sb.toString();
    }

    /**
     * 获取简化的急诊建议
     *
     * @return 简化急诊建议
     */
    public String getSimpleAdvice() {
        if (!isEmergency) {
            return "常规门诊";
        }

        if (emergencyLevel != null) {
            return emergencyLevel.getAdvice();
        }

        return "立即就医";
    }

    /**
     * 判断是否为危急情况
     *
     * @return 如果是危急情况返回true，否则返回false
     */
    public boolean isCritical() {
        return isEmergency && emergencyLevel == EmergencyLevel.CRITICAL;
    }

    /**
     * 判断是否为高危情况
     *
     * @return 如果是高危情况返回true，否则返回false
     */
    public boolean isHighRisk() {
        return isEmergency && emergencyLevel == EmergencyLevel.HIGH;
    }

    /**
     * 获取急诊级别的优先级数值
     *
     * @return 急诊级别优先级（数值越小越紧急）
     */
    public int getEmergencyPriority() {
        return isEmergency && emergencyLevel != null ? emergencyLevel.getPriority() : Integer.MAX_VALUE;
    }
}