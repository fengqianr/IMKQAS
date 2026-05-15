package com.student.entity.evaluation;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 在线指标快照实体
 * 定时采集的运行时管线健康度指标
 *
 * @author 系统
 * @version 1.0
 */
@TableName("online_metrics_snapshot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineMetricsSnapshot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("snapshot_time")
    private LocalDateTime snapshotTime;

    @TableField("window_seconds")
    private Integer windowSeconds;

    // 请求量
    @TableField("total_requests")
    private Integer totalRequests;

    @TableField("successful_requests")
    private Integer successfulRequests;

    @TableField("failed_requests")
    private Integer failedRequests;

    @TableField("success_rate")
    private Double successRate;

    // 延迟
    @TableField("avg_total_ms")
    private Double avgTotalMs;

    @TableField("p50_total_ms")
    private Double p50TotalMs;

    @TableField("p95_total_ms")
    private Double p95TotalMs;

    @TableField("p99_total_ms")
    private Double p99TotalMs;

    @TableField("avg_retrieval_ms")
    private Double avgRetrievalMs;

    @TableField("avg_llm_ms")
    private Double avgLlmMs;

    // 缓存
    @TableField("semantic_cache_hits")
    private Integer semanticCacheHits;

    @TableField("semantic_cache_misses")
    private Integer semanticCacheMisses;

    @TableField("cache_hit_rate")
    private Double cacheHitRate;

    // 同义词
    @TableField("avg_entities_per_query")
    private Double avgEntitiesPerQuery;

    @TableField("synonym_coverage_rate")
    private Double synonymCoverageRate;

    @TableField("unmapped_term_rate")
    private Double unmappedTermRate;

    // 安全
    @TableField("emergency_block_count")
    private Integer emergencyBlockCount;

    @TableField("confidence_block_count")
    private Integer confidenceBlockCount;

    @TableField("safety_check_count")
    private Integer safetyCheckCount;

    @TableField("safety_block_rate")
    private Double safetyBlockRate;

    // 置信度分布
    @TableField("conf_bucket_0_35")
    private Integer confBucket035;

    @TableField("conf_bucket_35_60")
    private Integer confBucket3560;

    @TableField("conf_bucket_60_80")
    private Integer confBucket6080;

    @TableField("conf_bucket_80_100")
    private Integer confBucket80100;

    @TableField("avg_confidence")
    private Double avgConfidence;

    // 检索模式分布
    @TableField("vector_retrieval_count")
    private Integer vectorRetrievalCount;

    @TableField("keyword_retrieval_count")
    private Integer keywordRetrievalCount;

    @TableField("hybrid_retrieval_count")
    private Integer hybridRetrievalCount;

    // 质量过滤
    @TableField("avg_input_docs")
    private Double avgInputDocs;

    @TableField("avg_output_docs")
    private Double avgOutputDocs;

    @TableField("avg_discard_rate")
    private Double avgDiscardRate;

    // LLM
    @TableField("total_tokens_generated")
    private Long totalTokensGenerated;

    @TableField("avg_tokens_per_response")
    private Double avgTokensPerResponse;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
