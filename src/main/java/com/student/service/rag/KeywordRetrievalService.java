package com.student.service.rag;

import com.student.entity.DocumentChunk;

import java.util.List;
import java.util.Map;

/**
 * 关键词检索服务接口
 * 基于BM25算法实现全文检索，支持医学术语同义词扩展
 *
 * @author 系统
 * @version 1.0
 */
public interface KeywordRetrievalService {

    /**
     * 构建全文索引（对所有文档分块）
     *
     * @return 索引构建是否成功
     */
    boolean buildIndex();

    /**
     * 添加文档分块到索引
     *
     * @param chunk 文档分块
     * @return 是否添加成功
     */
    boolean addToIndex(DocumentChunk chunk);

    /**
     * 批量添加文档分块到索引
     *
     * @param chunks 文档分块列表
     * @return 成功添加的数量
     */
    int addBatchToIndex(List<DocumentChunk> chunks);

    /**
     * 从索引中删除文档分块
     *
     * @param chunkId 分块ID
     * @return 是否删除成功
     */
    boolean removeFromIndex(Long chunkId);

    /**
     * 更新索引中的文档分块
     *
     * @param chunk 文档分块
     * @return 是否更新成功
     */
    boolean updateInIndex(DocumentChunk chunk);

    /**
     * 关键词检索
     *
     * @param query 查询关键词
     * @param topK 返回结果数量
     * @return 检索结果列表，每个元素包含分块ID和相关性分数
     */
    List<SearchResult> search(String query, int topK);

    /**
     * 多字段关键词检索
     *
     * @param query 查询关键词
     * @param fields 检索字段列表（如：content, title等）
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    List<SearchResult> search(String query, List<String> fields, int topK);

    /**
     * 带同义词扩展的关键词检索
     *
     * @param query 查询关键词
     * @param expandSynonyms 是否扩展同义词
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    List<SearchResult> searchWithSynonyms(String query, boolean expandSynonyms, int topK);

    /**
     * 获取索引统计信息
     *
     * @return 索引统计信息
     */
    Map<String, Object> getIndexStats();

    /**
     * 清除所有索引数据
     *
     * @return 是否清除成功
     */
    boolean clearIndex();

    /**
     * 检索结果类
     */
    class SearchResult {
        private final Long chunkId;
        private final Long documentId;
        private final Double score;
        private final String content;
        private final Map<String, Object> metadata;

        public SearchResult(Long chunkId, Long documentId, Double score, String content, Map<String, Object> metadata) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.score = score;
            this.content = content;
            this.metadata = metadata;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public Double getScore() {
            return score;
        }

        public String getContent() {
            return content;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}