package com.student.entity.his;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 访谈会话持久化实体
 * 对应数据库表: interview_sessions
 *
 * @author 系统
 * @version 1.0
 */
@TableName("interview_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

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

    @TableField("current_question_index")
    private Integer currentQuestionIndex;

    @TableField("total_questions")
    private Integer totalQuestions;

    @TableField("answers")
    private String answers;

    @TableField("current_score")
    private Integer currentScore;

    @TableField("completed")
    private Integer completed;

    @TableField("collection_mode")
    private String collectionMode;

    @TableField("degradation_level")
    private String degradationLevel;

    @TableField("raw_inputs")
    private String rawInputs;

    @TableField("safety_flags")
    private String safetyFlags;

    @TableField("context_summary")
    private String contextSummary;

    @TableField("provenance")
    private String provenance;

    @TableField("consecutive_failures")
    private Integer consecutiveFailures;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
