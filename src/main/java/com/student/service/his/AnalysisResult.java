package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分析层输出 —— LLM生成的结构化分析结果
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {

    /** 友好的评估摘要（200字以内） */
    private String summary;

    /** 风险评估 */
    private RiskAssessmentBlock riskAssessment;

    /** 详细分析 */
    private DetailAnalysisBlock detailAnalysis;

    /** 分层建议 */
    private RecommendationsBlock recommendations;

    /** 随访建议 */
    private FollowUpBlock followUp;

    /** 免责声明 */
    private String disclaimer;

    /** 分析耗时（毫秒） */
    private long latencyMs;

    /** 原始LLM响应 */
    private String rawLlmResponse;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessmentBlock {
        private String level;
        private String description;
        private boolean requiresUrgentAttention;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailAnalysisBlock {
        private String overview;
        private List<String> patterns;
        private String conclusion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationsBlock {
        private List<RecItem> immediate;
        private List<RecItem> shortTerm;
        private List<ProfessionalRecItem> professional;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecItem {
        private String title;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfessionalRecItem {
        private String title;
        private String description;
        private String resource;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowUpBlock {
        private String suggestedDate;
        private String rationale;
    }
}
