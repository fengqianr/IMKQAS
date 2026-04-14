package com.student.service;

import com.student.entity.Document;

/**
 * 文档处理服务接口
 * 负责文档解析、分块、向量化和存储的完整流水线
 *
 * @author 系统
 * @version 1.0
 */
public interface DocumentProcessorService {

    /**
     * 处理单个文档
     *
     * @param documentId 文档ID
     * @return 处理结果（成功/失败）
     */
    boolean processDocument(Long documentId);

    /**
     * 批量处理文档
     *
     * @param documentIds 文档ID列表
     * @return 成功处理的文档数量
     */
    int processDocuments(java.util.List<Long> documentIds);

    /**
     * 获取文档处理状态
     *
     * @param documentId 文档ID
     * @return 处理状态
     */
    Document.Status getProcessingStatus(Long documentId);

    /**
     * 取消文档处理
     *
     * @param documentId 文档ID
     * @return 是否取消成功
     */
    boolean cancelProcessing(Long documentId);

    /**
     * 重新处理失败的文档
     *
     * @param documentId 文档ID
     * @return 是否重新处理成功
     */
    boolean retryFailedDocument(Long documentId);

    /**
     * 获取文档处理统计信息
     *
     * @return 处理统计（总文档数、成功数、失败数、处理中数）
     */
    java.util.Map<String, Object> getProcessingStats();
}