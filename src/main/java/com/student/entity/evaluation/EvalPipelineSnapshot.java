package com.student.entity.evaluation;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管线快照实体
 * 按RAG管线步骤存储每次查询的中间状态和耗时
 *
 * @author 系统
 * @version 1.0
 */
@TableName("eval_pipeline_snapshot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalPipelineSnapshot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("run_id")
    private Long runId;

    @TableField("query_result_id")
    private Long queryResultId;

    @TableField("step_name")
    private String stepName;

    @TableField("step_order")
    private Integer stepOrder;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("input_count")
    private Integer inputCount;

    @TableField("output_count")
    private Integer outputCount;

    @TableField("intermediate_data")
    private String intermediateData;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 步骤状态常量 */
    public static final String STEP_SUCCESS = "SUCCESS";
    public static final String STEP_SKIPPED = "SKIPPED";
    public static final String STEP_FAILED = "FAILED";
    public static final String STEP_BLOCKED = "BLOCKED";

    /** 管线步骤名称常量 */
    public static final String STEP_ENTRY = "入口";
    public static final String STEP_QUERY_REWRITE = "查询预处理";
    public static final String STEP_EMERGENCY_CHECK = "安全兜底①-急症预检";
    public static final String STEP_DUAL_RECALL = "双路召回";
    public static final String STEP_RRF_FUSION = "RRF融合";
    public static final String STEP_QUALITY_FILTER = "质量过滤";
    public static final String STEP_CONTRADICTION = "矛盾检测";
    public static final String STEP_MULTI_RERANK = "多因子重排序";
    public static final String STEP_CONFIDENCE_GATE = "安全兜底②-置信度门控";
    public static final String STEP_SEMANTIC_CACHE = "语义缓存";
    public static final String STEP_LLM_GENERATION = "LLM生成";
    public static final String STEP_ANSWER_SANITIZE = "安全兜底③-答案净化";
    public static final String STEP_RESPONSE = "响应构建";
}
