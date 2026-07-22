package com.imkqas.service.rag;

import lombok.Getter;

/**
 * 置信度判定结果
 * 表示 LLM 原生置信度的评估结果：放行 / 警告（需添加免责声明） / 阻断
 *
 * @author 系统
 * @version 2.0
 */
@Getter
public class ConfidenceDecision {

    private final boolean blocked;
    private final boolean warning;
    private final double confidence;
    private final String message;

    private ConfidenceDecision(boolean blocked, boolean warning, double confidence, String message) {
        this.blocked = blocked;
        this.warning = warning;
        this.confidence = confidence;
        this.message = message;
    }

    /** 放行：置信度足够高 */
    public static ConfidenceDecision pass(double confidence) {
        return new ConfidenceDecision(false, false, confidence, null);
    }

    /** 警告：置信度偏低，需添加免责声明 */
    public static ConfidenceDecision warning(double confidence, String disclaimer) {
        return new ConfidenceDecision(false, true, confidence, disclaimer);
    }

    /** 阻断：置信度过低，不返回 LLM 回答 */
    public static ConfidenceDecision block(double confidence, String response) {
        return new ConfidenceDecision(true, false, confidence, response);
    }
}
