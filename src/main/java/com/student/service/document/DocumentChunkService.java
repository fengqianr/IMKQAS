package com.student.service.document;

import com.student.entity.DocumentChunk;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 文档分块服务接口
 * 提供文档分块相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
public interface DocumentChunkService extends IService<DocumentChunk> {

    /**
     * 清除所有文档分块数据
     * 同时删除 MySQL 中的分块记录、Milvus 中的向量数据，并重置文档的分块计数
     *
     * @return 删除的分块数量
     */
    int clearAllChunks();
}