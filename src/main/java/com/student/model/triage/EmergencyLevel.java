package com.student.model.triage;

import lombok.Getter;

import java.util.Arrays;

/**
 * 急诊症状分级枚举
 * 用于标识症状的紧急程度和就医建议
 *
 * @author 系统生成
 * @version 1.0
 * @since 2026-04-14
 */
@Getter
public enum EmergencyLevel {

    /**
     * 危急 - 需要立即就医
     */
    CRITICAL(0, "危急", "立即就医"),

    /**
     * 高危 - 2小时内就医
     */
    HIGH(1, "高危", "2小时内就医"),

    /**
     * 中危 - 24小时内就医
     */
    MEDIUM(2, "中危", "24小时内就医"),

    /**
     * 低危 - 常规门诊
     */
    LOW(3, "低危", "常规门诊");

    private final int priority;
    private final String levelName;
    private final String advice;

    EmergencyLevel(int priority, String levelName, String advice) {
        this.priority = priority;
        this.levelName = levelName;
        this.advice = advice;
    }

    /**
     * 根据优先级获取对应的急诊级别枚举
     *
     * @param priority 优先级（0-3）
     * @return 对应的EmergencyLevel枚举
     * @throws IllegalArgumentException 如果优先级不在有效范围内
     */
    public static EmergencyLevel fromPriority(int priority) {
        return Arrays.stream(values())
                .filter(level -> level.getPriority() == priority)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无效的优先级: " + priority));
    }

    /**
     * 获取急诊级别的显示名称
     *
     * @return 急诊级别名称
     */
    public String getDisplayName() {
        return levelName + " (" + advice + ")";
    }

    /**
     * 判断是否为紧急情况（CRITICAL或HIGH）
     *
     * @return 如果是紧急情况返回true，否则返回false
     */
    public boolean isEmergency() {
        return this == CRITICAL || this == HIGH;
    }
}