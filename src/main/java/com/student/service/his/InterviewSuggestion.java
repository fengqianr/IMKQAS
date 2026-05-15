package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问卷匹配建议
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSuggestion {

    /** 是否匹配到问卷 */
    private boolean matched;

    /** 匹配到的问卷模板 */
    private QuestionnaireTemplate questionnaire;

    /** AI建议文案（如"您描述的情况可能适合PHQ-9..."） */
    private String suggestionText;

    /** 匹配置信度 0-1 */
    private double confidence;

    public static InterviewSuggestion noMatch() {
        return InterviewSuggestion.builder().matched(false).confidence(0).build();
    }

    public static InterviewSuggestion of(QuestionnaireTemplate template, String text, double confidence) {
        return InterviewSuggestion.builder()
                .matched(true)
                .questionnaire(template)
                .suggestionText(text)
                .confidence(confidence)
                .build();
    }
}
