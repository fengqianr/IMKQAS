package com.student.dto.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索路径DTO
 * 封装完整RAG管线追踪数据，包含步骤列表和汇总元数据
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalPathDto {
    /** 管线步骤列表（按stepOrder排序） */
    private List<RetrievalStepDto> steps;
    /** 管线总耗时（毫秒） */
    private long totalDurationMs;
    /** 是否命中语义缓存 */
    private boolean cacheHit;
    /** 意图分类类型 */
    private String intentType;
}
