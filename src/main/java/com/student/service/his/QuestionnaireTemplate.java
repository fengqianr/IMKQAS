package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 问卷模板数据结构
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireTemplate {

    /** 问卷唯一标识（如PHQ-9） */
    private String id;

    /** 问卷标题 */
    private String title;

    /** 问卷描述 */
    private String description;

    /** 触发关键词（用于匹配用户输入） */
    private List<String> triggerKeywords;

    /** 分类：mental_health/diabetes/sleep/pain/general */
    private String category;

    /** 题目列表 */
    private List<QuestionItem> items;

    /** 评分规则 */
    private ScoringRule scoringRule;

    /**
     * 单个问题
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionItem {
        /** 题号（从1开始） */
        private int index;

        /** 问题链接ID（如/q1） */
        private String linkId;

        /** 问题文本 */
        private String text;

        /** 选项列表 */
        private List<AnswerOption> options;
    }

    /**
     * 答案选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerOption {
        /** 选项编码（FHIR Coding.code） */
        private String code;

        /** 选项显示文本 */
        private String display;

        /** 分数 */
        private int score;
    }

    /**
     * 评分规则
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoringRule {
        /** 最低可能总分 */
        private int minScore;

        /** 最高可能总分 */
        private int maxScore;

        /** 分数区间解释 */
        private List<ScoreRange> ranges;
    }

    /**
     * 分数区间解释
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreRange {
        /** 最低分（含） */
        private int minScore;

        /** 最高分（含） */
        private int maxScore;

        /** 严重程度 */
        private String severity;

        /** 解释文本 */
        private String interpretation;
    }
}
