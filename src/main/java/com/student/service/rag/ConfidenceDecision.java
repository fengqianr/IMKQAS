package com.student.service.rag;

import lombok.Getter;

/**
 * 置信度判定结果
 * 表示检索结果置信度的评估结果：放行 / 警告（需添加免责声明） / 阻断
 *
 * @author 系统
 * @version 1.0
 */
@Getter
public class ConfidenceDecision {

    private final boolean blocked;
    private final boolean warning;
    private final double maxScore;
    private final String message;

    private ConfidenceDecision(boolean blocked, boolean warning, double maxScore, String message) {
        this.blocked = blocked;
        this.warning = warning;
        this.maxScore = maxScore;
        this.message = message;
    }

    /** 放行：置信度足够高，正常执行 LLM 生成 */
    public static ConfidenceDecision pass(double maxScore) {
        return new ConfidenceDecision(false, false, maxScore, null);
    }

    /** 警告：置信度偏低，LLM 正常生成但需添加免责声明 */
    public static ConfidenceDecision warning(double maxScore, String disclaimer) {
        return new ConfidenceDecision(false, true, maxScore, disclaimer);
    }

    /** 阻断：置信度过低，不调用 LLM */
    public static ConfidenceDecision block(double maxScore, String response) {
        return new ConfidenceDecision(true, false, maxScore, response);
    }
}
