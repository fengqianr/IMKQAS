package com.student.entity.evaluation;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 逐查询评估结果实体
 * 存储单个query在各评估环节的详细指标
 *
 * @author 系统
 * @version 1.0
 */
@TableName("eval_query_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalQueryResult {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("run_id")
    private Long runId;

    @TableField("dataset_item_id")
    private Long datasetItemId;

    @TableField("query")
    private String query;

    // 检索结果
    @TableField("retrieved_chunk_ids")
    private String retrievedChunkIds;

    @TableField("vector_chunk_ids")
    private String vectorChunkIds;

    @TableField("keyword_chunk_ids")
    private String keywordChunkIds;

    @TableField("hit_ground_truth")
    private Boolean hitGroundTruth;

    @TableField("first_relevant_rank")
    private Integer firstRelevantRank;

    // 过滤结果
    @TableField("before_filter_count")
    private Integer beforeFilterCount;

    @TableField("after_filter_count")
    private Integer afterFilterCount;

    @TableField("discarded_reasons")
    private String discardedReasons;

    // 生成结果
    @TableField("generated_answer")
    private String generatedAnswer;

    @TableField("faithfulness_score")
    private Double faithfulnessScore;

    @TableField("answer_relevance_score")
    private Double answerRelevanceScore;

    @TableField("context_relevance_score")
    private Double contextRelevanceScore;

    // 安全结果
    @TableField("emergency_triggered")
    private Boolean emergencyTriggered;

    @TableField("confidence_blocked")
    private Boolean confidenceBlocked;

    @TableField("safety_sanitized")
    private Boolean safetySanitized;

    @TableField("final_confidence")
    private Double finalConfidence;

    // 耗时明细
    @TableField("total_time_ms")
    private Long totalTimeMs;

    @TableField("retrieval_time_ms")
    private Long retrievalTimeMs;

    @TableField("rerank_time_ms")
    private Long rerankTimeMs;

    @TableField("llm_time_ms")
    private Long llmTimeMs;

    @TableField("preprocessing_time_ms")
    private Long preprocessingTimeMs;

    // 缓存和同义词
    @TableField("cache_hit")
    private Boolean cacheHit;

    @TableField("entities_recognized")
    private Integer entitiesRecognized;

    @TableField("entities_mapped")
    private Integer entitiesMapped;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
