package com.student.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索K值调优测试响应DTO
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalTestResponse {
    /** 是否成功 */
    private boolean success;
    /** 错误信息（失败时） */
    private String error;
    /** 检索通路 */
    private String pathway;
    /** 查询文本 */
    private String query;
    /** 请求的K值 */
    private int requestedTopK;
    /** 实际返回数量 */
    private int actualCount;
    /** 总延迟（毫秒） */
    private double latencyMs;
    /** 向量嵌入耗时（毫秒，关键词通路为0） */
    private double embeddingLatencyMs;
    /** 检索搜索耗时（毫秒） */
    private double searchLatencyMs;
    /** 检索结果列表 */
    private List<RetrievalResultItem> results;
}
