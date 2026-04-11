package com.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * 文档分块实体类
 * 对应数据库表: document_chunks
 * 存储医学文档的分块内容，用于向量检索
 *
 * @author 系统
 * @version 1.0
 */
@TableName("document_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("document_id")
    private Long documentId; // 文档ID

    @TableField("chunk_index")
    private Integer chunkIndex; // 分块序号

    @TableField("content")
    private String content; // 分块文本内容

    @TableField("metadata")
    private String metadata; // 元数据: {page: 页码, section: 章节, start_char: 起始字符, end_char: 结束字符}

    @TableField("vector_id")
    private String vectorId; // Milvus向量ID

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted = 0;

    /**
     * 获取分块标识符
     * @return 分块标识符 (documentId-chunkIndex)
     */
    public String getChunkIdentifier() {
        return documentId + "-" + chunkIndex;
    }

    /**
     * 获取页码（从元数据中提取）
     * @return 页码，如果不存在则返回null
     */
    public Integer getPageNumber() {
        // 简化实现，实际应从metadata JSON中解析
        if (metadata != null && metadata.contains("\"page\":")) {
            try {
                // 简单解析JSON，实际应使用JSON库
                int start = metadata.indexOf("\"page\":") + 7;
                int end = metadata.indexOf(",", start);
                if (end == -1) end = metadata.indexOf("}", start);
                String pageStr = metadata.substring(start, end).trim();
                return Integer.parseInt(pageStr);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 获取章节信息（从元数据中提取）
     * @return 章节信息，如果不存在则返回null
     */
    public String getSection() {
        // 简化实现，实际应从metadata JSON中解析
        if (metadata != null && metadata.contains("\"section\":")) {
            try {
                int start = metadata.indexOf("\"section\":\"") + 11;
                int end = metadata.indexOf("\"", start);
                return metadata.substring(start, end);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 判断是否已向量化
     * @return 是否已向量化
     */
    public boolean isVectorized() {
        return vectorId != null && !vectorId.trim().isEmpty();
    }

    /**
     * 更新向量ID
     * @param vectorId 新的向量ID
     */
    public void updateVectorId(String vectorId) {
        this.vectorId = vectorId;
    }
}