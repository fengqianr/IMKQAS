package com.student.service.rag;

import lombok.Getter;

/**
 * 安全决策结果
 * 表示急症预检的判定结果：放行或阻断
 *
 * @author 系统
 * @version 1.0
 */
@Getter
public class SafetyDecision {

    private final boolean blocked;
    private final String reasonCode;
    private final String adviceMessage;
    private final String severityLevel;

    private SafetyDecision(boolean blocked, String reasonCode, String adviceMessage, String severityLevel) {
        this.blocked = blocked;
        this.reasonCode = reasonCode;
        this.adviceMessage = adviceMessage;
        this.severityLevel = severityLevel;
    }

    /** 放行（非急症） */
    public static SafetyDecision pass() {
        return new SafetyDecision(false, null, null, null);
    }

    /** 阻断（急症命中） */
    public static SafetyDecision block(String reasonCode, String adviceMessage, String severityLevel) {
        return new SafetyDecision(true, reasonCode, adviceMessage, severityLevel);
    }
}
