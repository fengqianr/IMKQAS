package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 填表回答响应
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResponse {

    /** 会话ID */
    private String sessionId;

    /** 是否已完成 */
    private boolean completed;

    /** 当前进度: 已答N/总M */
    private String progress;

    /** 下一题信息（未完成时） */
    private QuestionInfo nextQuestion;

    /** 完成摘要（已完成时） */
    private CompletionSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionInfo {
        private int index;
        private String linkId;
        private String text;
        private java.util.List<QuestionnaireTemplate.AnswerOption> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletionSummary {
        private int totalScore;
        private int maxScore;
        private String severity;
        private String interpretation;
        private boolean hasHistory;
        private String trendDescription;
        /** 异步分析结果的摘要文本 */
        private String analysisSummary;
        /** 关联分析报告ID（用于后续查询完整报告） */
        private String analysisId;
    }
}
