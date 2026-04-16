package com.student.service.dataBase;

import io.milvus.client.MilvusClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.SearchResults;
import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ReleaseCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.grpc.MutationResult;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.response.MutationResultWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus向量数据库服务
 * 提供向量插入、搜索、删除等操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MilvusService {

    private final MilvusClient milvusClient;

    @Value("${imkqas.vector-db.milvus.collection:medical_documents}")
    private String collectionName;

    @Value("${imkqas.rag.embedding.dimension:1024}")
    private int dimension;

    /**
     * 插入向量数据
     *
     * @param chunkId 文档分块ID
     * @param documentId 文档ID
     * @param content 文本内容
     * @param embedding 向量数据
     * @param metadata 元数据JSON字符串
     * @return 插入的向量ID
     */
    public Long insertVector(Long chunkId, Long documentId, String content, List<Float> embedding, String metadata) {
        try {
            log.info("插入向量数据: chunkId={}, documentId={}, contentLength={}",
                    chunkId, documentId, content.length());

            // 确保集合已加载
            loadCollection();

            // 构建插入数据
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("chunk_id", List.of(chunkId)));
            fields.add(new InsertParam.Field("document_id", List.of(documentId)));
            fields.add(new InsertParam.Field("content", List.of(content)));
            fields.add(new InsertParam.Field("embedding", List.of(embedding)));
            fields.add(new InsertParam.Field("metadata", List.of(metadata != null ? metadata : "{}")));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            R<MutationResult> insertResponse = milvusClient.insert(insertParam);
            if (insertResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("插入向量数据失败: {}", insertResponse.getMessage());
                throw new RuntimeException("插入向量数据失败: " + String.valueOf(insertResponse.getMessage()));
            }

            // 获取插入的ID
            MutationResultWrapper wrapper = new MutationResultWrapper(insertResponse.getData());
            List<Long> ids = wrapper.getLongIDs();
            if (ids == null || ids.isEmpty()) {
                throw new RuntimeException("插入向量数据失败：未返回ID");
            }

            Long vectorId = ids.get(0);
            log.info("向量数据插入成功: vectorId={}, chunkId={}", vectorId, chunkId);

            return vectorId;
        } catch (Exception e) {
            log.error("插入向量数据异常", e);
            throw new RuntimeException("插入向量数据异常", e);
        }
    }

    /**
     * 批量插入向量数据
     *
     * @param dataList 数据列表
     * @return 插入的向量ID列表
     */
    public List<Long> batchInsertVectors(List<VectorData> dataList) {
        try {
            log.info("批量插入向量数据: count={}", dataList.size());

            // 确保集合已加载
            loadCollection();

            // 构建批量插入数据
            List<Long> chunkIds = new ArrayList<>();
            List<Long> documentIds = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            List<List<Float>> embeddings = new ArrayList<>();
            List<String> metadatas = new ArrayList<>();

            for (VectorData data : dataList) {
                chunkIds.add(data.getChunkId());
                documentIds.add(data.getDocumentId());
                contents.add(data.getContent());
                embeddings.add(data.getEmbedding());
                metadatas.add(data.getMetadata() != null ? data.getMetadata() : "{}");
            }

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("chunk_id", chunkIds));
            fields.add(new InsertParam.Field("document_id", documentIds));
            fields.add(new InsertParam.Field("content", contents));
            fields.add(new InsertParam.Field("embedding", embeddings));
            fields.add(new InsertParam.Field("metadata", metadatas));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            R<MutationResult> insertResponse = milvusClient.insert(insertParam);
            if (insertResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("批量插入向量数据失败: {}", insertResponse.getMessage());
                throw new RuntimeException("批量插入向量数据失败: " + String.valueOf(insertResponse.getMessage()));
            }

            // 获取插入的ID列表
            MutationResultWrapper wrapper = new MutationResultWrapper(insertResponse.getData());
            List<Long> ids = wrapper.getLongIDs();
            log.info("批量向量数据插入成功: count={}", ids.size());

            return ids;
        } catch (Exception e) {
            log.error("批量插入向量数据异常", e);
            throw new RuntimeException("批量插入向量数据异常", e);
        }
    }

    /**
     * 向量相似度搜索
     *
     * @param queryEmbedding 查询向量
     * @param topK 返回结果数量
     * @return 搜索结果列表
     */
    public List<SearchResult> searchSimilarVectors(List<Float> queryEmbedding, int topK) {
        return searchSimilarVectors(queryEmbedding, topK, null);
    }

    /**
     * 向量相似度搜索（带过滤条件）
     *
     * @param queryEmbedding 查询向量
     * @param topK 返回结果数量
     * @param filter 过滤条件表达式
     * @return 搜索结果列表
     */
    public List<SearchResult> searchSimilarVectors(List<Float> queryEmbedding, int topK, String filter) {
        try {
            log.info("向量相似度搜索: topK={}, filter={}", topK, filter);

            // 确保集合已加载
            loadCollection();

            // 构建搜索参数
            List<String> outputFields = List.of("chunk_id", "document_id", "content", "metadata");
            List<List<Float>> searchVectors = List.of(queryEmbedding);

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withVectorFieldName("embedding")
                    .withVectors(searchVectors)
                    .withTopK(topK)
                    .withMetricType(MetricType.IP) // 内积相似度
                    .withOutFields(outputFields)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withExpr(filter)
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);
            if (searchResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("向量搜索失败: {}", searchResponse.getMessage());
                throw new RuntimeException("向量搜索失败: " + String.valueOf(searchResponse.getMessage()));
            }

            // 解析搜索结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            for (int i = 0; i < topK; i++) {
                try {
                    Long chunkId = wrapper.getFieldData("chunk_id", 0).get(i).toString() != null ?
                            Long.parseLong(wrapper.getFieldData("chunk_id", 0).get(i).toString()) : null;
                    Long documentId = wrapper.getFieldData("document_id", 0).get(i).toString() != null ?
                            Long.parseLong(wrapper.getFieldData("document_id", 0).get(i).toString()) : null;
                    String content = wrapper.getFieldData("content", 0).get(i).toString();
                    String metadata = wrapper.getFieldData("metadata", 0).get(i).toString();
                    Float score = wrapper.getIDScore(0).get(i).getScore();

                    SearchResult result = new SearchResult();
                    result.setChunkId(chunkId);
                    result.setDocumentId(documentId);
                    result.setContent(content);
                    result.setMetadata(metadata);
                    result.setScore(score);

                    results.add(result);
                } catch (Exception e) {
                    log.warn("解析搜索结果失败: index={}", i, e);
                }
            }

            log.info("向量搜索完成: 返回结果数量={}", results.size());
            return results;
        } catch (Exception e) {
            log.error("向量搜索异常", e);
            throw new RuntimeException("向量搜索异常", e);
        }
    }

    /**
     * 加载集合到内存
     */
    public void loadCollection() {
        try {
            LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<RpcStatus> loadResponse = milvusClient.loadCollection(loadParam);
            if (loadResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("加载集合失败: {}", loadResponse.getMessage());
                throw new RuntimeException("加载集合失败: " + String.valueOf(loadResponse.getMessage()));
            }

            log.debug("集合加载成功: {}", collectionName);
        } catch (Exception e) {
            log.error("加载集合异常", e);
            throw new RuntimeException("加载集合异常", e);
        }
    }

    /**
     * 释放集合从内存
     */
    public void releaseCollection() {
        try {
            ReleaseCollectionParam releaseParam = ReleaseCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<RpcStatus> releaseResponse = milvusClient.releaseCollection(releaseParam);
            if (releaseResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("释放集合失败: {}", releaseResponse.getMessage());
                throw new RuntimeException("释放集合失败: " + String.valueOf(releaseResponse.getMessage()));
            }

            log.debug("集合释放成功: {}", collectionName);
        } catch (Exception e) {
            log.error("释放集合异常", e);
            throw new RuntimeException("释放集合异常", e);
        }
    }

    /**
     * 获取集合统计信息
     *
     * @return 集合中的实体数量
     */
    public long getCollectionCount() {
        try {
            GetCollectionStatisticsParam param = GetCollectionStatisticsParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();
            R<GetCollectionStatisticsResponse> countResponse = milvusClient.getCollectionStatistics(param);
            if (countResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("获取集合统计信息失败: {}", countResponse.getMessage());
                throw new RuntimeException("获取集合统计信息失败: " + String.valueOf(countResponse.getMessage()));
            }

            long count = 0L;
            // Extract count from GetCollectionStatisticsResponse
            if (countResponse.getData().getStatsList() != null) {
                for (int i = 0; i < countResponse.getData().getStatsList().size(); i++) {
                    var stat = countResponse.getData().getStatsList().get(i);
                    if ("row_count".equals(stat.getKey())) {
                        try {
                            count = Long.parseLong(stat.getValue());
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse row_count: {}", stat.getValue());
                        }
                        break;
                    }
                }
            }
            log.debug("集合实体数量: {}", count);
            return count;
        } catch (Exception e) {
            log.error("获取集合统计信息异常", e);
            throw new RuntimeException("获取集合统计信息异常", e);
        }
    }

    /**
     * 删除向量数据
     *
     * @param chunkId 文档分块ID
     * @return 是否删除成功
     */
    public boolean deleteByChunkId(Long chunkId) {
        try {
            String expr = String.format("chunk_id == %d", chunkId);
            return deleteByExpression(expr);
        } catch (Exception e) {
            log.error("按chunkId删除向量数据异常", e);
            return false;
        }
    }

    /**
     * 按文档ID删除向量数据
     *
     * @param documentId 文档ID
     * @return 是否删除成功
     */
    public boolean deleteByDocumentId(Long documentId) {
        try {
            String expr = String.format("document_id == %d", documentId);
            return deleteByExpression(expr);
        } catch (Exception e) {
            log.error("按documentId删除向量数据异常", e);
            return false;
        }
    }

    /**
     * 按表达式删除向量数据
     *
     * @param expr 删除表达式
     * @return 是否删除成功
     */
    public boolean deleteByExpression(String expr) {
        try {
            log.info("按表达式删除向量数据: expr={}", expr);

            // 构建删除参数
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();

            R<MutationResult> deleteResponse = milvusClient.delete(deleteParam);
            if (deleteResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("删除向量数据失败: {}", deleteResponse.getMessage());
                return false;
            }

            MutationResultWrapper wrapper = new MutationResultWrapper(deleteResponse.getData());
            long deletedCount = wrapper.getDeleteCount();
            log.info("向量数据删除成功: 删除数量={}", deletedCount);
            return deletedCount > 0;
        } catch (Exception e) {
            log.error("删除向量数据异常", e);
            return false;
        }
    }

    /**
     * 向量数据类
     */
    public static class VectorData {
        private Long chunkId;
        private Long documentId;
        private String content;
        private List<Float> embedding;
        private String metadata;

        // Getters and Setters
        public Long getChunkId() { return chunkId; }
        public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<Float> getEmbedding() { return embedding; }
        public void setEmbedding(List<Float> embedding) { this.embedding = embedding; }
        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
    }

    /**
     * 搜索结果类
     */
    public static class SearchResult {
        private Long chunkId;
        private Long documentId;
        private String content;
        private String metadata;
        private Float score;

        // Getters and Setters
        public Long getChunkId() { return chunkId; }
        public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
        public Float getScore() { return score; }
        public void setScore(Float score) { this.score = score; }
    }
}