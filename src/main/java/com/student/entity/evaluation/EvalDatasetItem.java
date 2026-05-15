package com.student.entity.evaluation;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评估数据项实体
 * 存储单条标注数据（query + ground_truth + 期望行为）
 *
 * @author 系统
 * @version 1.0
 */
@TableName("eval_dataset_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalDatasetItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("item_index")
    private Integer itemIndex;

    @TableField("query")
    private String query;

    @TableField("ground_truth_chunk_ids")
    private String groundTruthChunkIds;

    @TableField("ground_truth_doc_ids")
    private String groundTruthDocIds;

    @TableField("ground_truth_answer")
    private String groundTruthAnswer;

    @TableField("relevance_labels")
    private String relevanceLabels;

    @TableField("query_type")
    private String queryType;

    @TableField("difficulty")
    private String difficulty;

    @TableField("safety_level")
    private String safetyLevel;

    @TableField("should_trigger_emergency")
    private Boolean shouldTriggerEmergency;

    @TableField("should_trigger_confidence_block")
    private Boolean shouldTriggerConfidenceBlock;

    @TableField("expected_response_type")
    private String expectedResponseType;

    @TableField("expected_keywords")
    private String expectedKeywords;

    @TableField("prohibited_keywords")
    private String prohibitedKeywords;

    @TableField("metadata")
    private String metadata;

    @TableField("annotator")
    private String annotator;

    @TableField("review_status")
    private String reviewStatus;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 难度常量 */
    public static final String DIFFICULTY_SIMPLE = "simple";
    public static final String DIFFICULTY_MEDIUM = "medium";
    public static final String DIFFICULTY_HARD = "hard";

    /** 安全等级常量 */
    public static final String SAFETY_SAFE = "SAFE";
    public static final String SAFETY_NEEDS_DISCLAIMER = "NEEDS_DISCLAIMER";
    public static final String SAFETY_EMERGENCY = "EMERGENCY";
    public static final String SAFETY_BLOCKED = "BLOCKED";
}
