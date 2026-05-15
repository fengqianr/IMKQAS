package com.student.entity.evaluation;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评估运行记录实体
 * 存储每次评估运行的汇总指标
 *
 * @author 系统
 * @version 1.0
 */
@TableName("eval_run")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalRun {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("run_name")
    private String runName;

    @TableField("status")
    private String status;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("total_queries")
    private Integer totalQueries;

    @TableField("evaluated_queries")
    private Integer evaluatedQueries;

    @TableField("eval_dimensions")
    private String evalDimensions;

    // 检索指标
    @TableField("recall_at_1")
    private Double recallAt1;
    @TableField("recall_at_5")
    private Double recallAt5;
    @TableField("recall_at_10")
    private Double recallAt10;
    @TableField("recall_at_20")
    private Double recallAt20;
    @TableField("precision_at_5")
    private Double precisionAt5;
    @TableField("precision_at_10")
    private Double precisionAt10;
    @TableField("mrr")
    private Double mrr;
    @TableField("ndcg_at_10")
    private Double ndcgAt10;
    @TableField("hit_rate_at_5")
    private Double hitRateAt5;

    // 融合指标
    @TableField("mrr_vector_only")
    private Double mrrVectorOnly;
    @TableField("mrr_keyword_only")
    private Double mrrKeywordOnly;
    @TableField("mrr_fused")
    private Double mrrFused;
    @TableField("complementarity_score")
    private Double complementarityScore;

    // 过滤指标
    @TableField("filter_precision")
    private Double filterPrecision;
    @TableField("filter_recall")
    private Double filterRecall;
    @TableField("blacklist_hit_rate")
    private Double blacklistHitRate;

    // 重排序指标
    @TableField("mrr_before_rerank")
    private Double mrrBeforeRerank;
    @TableField("mrr_after_rerank")
    private Double mrrAfterRerank;

    // 生成质量指标
    @TableField("avg_faithfulness")
    private Double avgFaithfulness;
    @TableField("avg_answer_relevance")
    private Double avgAnswerRelevance;
    @TableField("avg_context_relevance")
    private Double avgContextRelevance;

    // 安全质量指标
    @TableField("emergency_detection_accuracy")
    private Double emergencyDetectionAccuracy;
    @TableField("safety_block_rate")
    private Double safetyBlockRate;

    // 管线耗时
    @TableField("avg_total_time_ms")
    private Double avgTotalTimeMs;
    @TableField("avg_retrieval_time_ms")
    private Double avgRetrievalTimeMs;
    @TableField("avg_llm_time_ms")
    private Double avgLlmTimeMs;
    @TableField("cache_hit_rate")
    private Double cacheHitRate;

    @TableField("config_snapshot")
    private String configSnapshot;

    @TableField("error_log")
    private String errorLog;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 运行状态常量 */
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
