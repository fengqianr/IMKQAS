package com.student.model.triage;

import lombok.Getter;

/**
 * 急诊症状分级枚举
 */
@Getter
public enum EmergencyLevel {
    CRITICAL(0, "危急", "立即就医"),
    HIGH(1, "高危", "2小时内就医"),
    MEDIUM(2, "中危", "24小时内就医"),
    LOW(3, "低危", "常规门诊");

    private final int priority;
    private final String levelName;
    private final String action;

    EmergencyLevel(int priority, String levelName, String action) {
        this.priority = priority;
        this.levelName = levelName;
        this.action = action;
    }

    /**
     * 根据优先级获取枚举
     */
    public static EmergencyLevel fromPriority(int priority) {
        for (EmergencyLevel level : values()) {
            if (level.getPriority() == priority) {
                return level;
            }
        }
        return LOW;
    }
}