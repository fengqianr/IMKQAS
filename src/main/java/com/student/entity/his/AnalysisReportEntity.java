package com.student.entity.his;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI分析报告持久化实体
 * 对应数据库表: analysis_reports
 *
 * @author 系统
 * @version 1.0
 */
@TableName("analysis_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReportEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("analysis_id")
    private String analysisId;

    @TableField("session_id")
    private String sessionId;

    @TableField("questionnaire_id")
    private String questionnaireId;

    @TableField("questionnaire_title")
    private String questionnaireTitle;

    @TableField("user_id")
    private Long userId;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("total_score")
    private Integer totalScore;

    @TableField("max_score")
    private Integer maxScore;

    @TableField("severity")
    private String severity;

    @TableField("interpretation")
    private String interpretation;

    @TableField("summary")
    private String summary;

    @TableField("risk_assessment")
    private String riskAssessment;

    @TableField("detail_analysis")
    private String detailAnalysis;

    @TableField("recommendations")
    private String recommendations;

    @TableField("follow_up")
    private String followUp;

    @TableField("disclaimer")
    private String disclaimer;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField("raw_llm_response")
    private String rawLlmResponse;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
