package com.imkqas.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索结果项DTO（精简版，用于K值调优测试响应）
 *
 * @author 系统
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResultItem {
    /** 文本块ID */
    private Long chunkId;
    /** 文档ID */
    private Long documentId;
    /** 检索得分 */
    private Double score;
    /** 文本内容（trimmed=true时可能截断） */
    private String content;
    /** 向量检索分数（关键词通路为0） */
    private Double vectorScore;
    /** 关键词检索分数（向量通路为0） */
    private Double keywordScore;
    /** 来源: VECTOR / KEYWORD / HYBRID */
    private String source;
}
