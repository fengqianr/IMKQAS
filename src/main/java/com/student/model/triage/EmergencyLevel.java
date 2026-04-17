package com.student.model.triage;

import lombok.Getter;

/**
 * 急诊症状分级枚举
 * 定义急诊症状的严重级别和相应的就医建议
 *
 * @author 系统生成
 * @version 1.0
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

    private EmergencyLevel(int priority, String levelName, String action) {
        this.priority = priority;
        this.levelName = levelName;
        this.action = action;
    }

    /**
     * 获取描述信息（用于显示）
     */
    public String getDescription() {
        return levelName + " - " + action;
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

    /**
     * 根据字符串名称获取枚举（不区分大小写）
     */
    public static EmergencyLevel fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return LOW;
        }
        try {
            return EmergencyLevel.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试匹配 levelName
            for (EmergencyLevel level : values()) {
                if (level.getLevelName().equals(name) || level.name().equalsIgnoreCase(name)) {
                    return level;
                }
            }
            return LOW;
        }
    }
}