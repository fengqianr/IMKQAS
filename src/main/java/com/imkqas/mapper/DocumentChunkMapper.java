package com.imkqas.mapper;

import com.imkqas.entity.DocumentChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文档分块Mapper接口
 * 提供文档分块数据的数据库操作
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 物理删除文档的所有分块（绕过@TableLogic逻辑删除）
     * 用于重新处理文档时彻底清理旧分块，避免 uk_document_chunk 唯一约束冲突
     */
    @Delete("DELETE FROM document_chunks WHERE document_id = #{documentId}")
    int physicalDeleteByDocumentId(@Param("documentId") Long documentId);
}