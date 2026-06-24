package com.student.dto.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 检索管线步骤DTO
 * 将后端PipelineTraceContext.StepRecord序列化后传给前端可视化
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalStepDto {
    /** 步骤名称（如"查询预处理"、"双路召回+RRF融合"等） */
    private String stepName;
    /** 步骤顺序 */
    private int stepOrder;
    /** 耗时（毫秒） */
    private long durationMs;
    /** 输入数量 */
    private int inputCount;
    /** 输出数量 */
    private int outputCount;
    /** 中间数据（可包含：检索命中数、过滤率、重排序分数等） */
    private Map<String, Object> intermediateData;
    /** 步骤状态：SUCCESS / BLOCKED / ERROR */
    private String status;
    /** 步骤完成时间戳（毫秒） */
    private Long timestamp;
}
