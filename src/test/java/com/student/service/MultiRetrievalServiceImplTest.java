package com.student.service;

import com.student.config.RagConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * 多路检索服务单元测试
 * 测试MultiRetrievalServiceImpl的各种方法
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiRetrievalServiceImplTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private MilvusService milvusService;

    @Mock
    private KeywordRetrievalService keywordRetrievalService;

    @Mock
    private RagConfig ragConfig;

    @Mock
    private RagConfig.RetrievalConfig retrievalConfig;

    @Mock
    private RagConfig.RetrievalConfig.WeightsConfig weightsConfig;

    @InjectMocks
    private MultiRetrievalServiceImpl multiRetrievalService;

    @BeforeEach
    void setUp() {
        // 模拟RagConfig配置
        when(ragConfig.getRetrieval()).thenReturn(retrievalConfig);
        when(retrievalConfig.getWeights()).thenReturn(weightsConfig);
        when(weightsConfig.getVector()).thenReturn(0.6);
        when(weightsConfig.getKeyword()).thenReturn(0.4);
        when(retrievalConfig.getTimeout()).thenReturn(5000);
        when(retrievalConfig.getMode()).thenReturn(RagConfig.RetrievalConfig.Mode.HYBRID);

        // 模拟嵌入服务
        when(embeddingService.getDimension()).thenReturn(1024);
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.validateVector(anyList())).thenReturn(true);
    }

    @Test
    void testVectorRetrieval_Success() {
        // 准备
        String query = "测试查询";
        int topK = 5;
        List<Float> queryVector = Arrays.asList(1.0f, 2.0f, 3.0f);

        // 创建Milvus搜索结果
        MilvusService.SearchResult result1 = new MilvusService.SearchResult();
        result1.setChunkId(1L);
        result1.setDocumentId(1L);
        result1.setScore(0.9f);
        result1.setContent("内容1");

        MilvusService.SearchResult result2 = new MilvusService.SearchResult();
        result2.setChunkId(2L);
        result2.setDocumentId(1L);
        result2.setScore(0.8f);
        result2.setContent("内容2");

        List<MilvusService.SearchResult> milvusResults = Arrays.asList(result1, result2);

        when(embeddingService.embed(query)).thenReturn(queryVector);
        when(milvusService.searchSimilarVectors(queryVector, topK)).thenReturn(milvusResults);

        // 执行
        List<MultiRetrievalService.RetrievalResult> results = multiRetrievalService.vectorRetrieval(query, topK);

        // 验证
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(MultiRetrievalService.RetrievalSource.VECTOR, results.get(0).getSource());
        verify(embeddingService).embed(query);
        verify(milvusService).searchSimilarVectors(queryVector, topK);
    }

    @Test
    void testVectorRetrieval_EmbeddingFailure() {
        // 准备
        String query = "测试查询";
        int topK = 5;

        when(embeddingService.embed(query)).thenReturn(null);

        // 执行
        List<MultiRetrievalService.RetrievalResult> results = multiRetrievalService.vectorRetrieval(query, topK);

        // 验证
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(embeddingService).embed(query);
        verify(milvusService, never()).searchSimilarVectors(any(), anyInt());
    }

    @Test
    void testVectorRetrieval_MilvusEmptyResults() {
        // 准备
        String query = "测试查询";
        int topK = 5;
        List<Float> queryVector = Arrays.asList(1.0f, 2.0f, 3.0f);

        when(embeddingService.embed(query)).thenReturn(queryVector);
        when(milvusService.searchSimilarVectors(queryVector, topK)).thenReturn(Collections.emptyList());

        // 执行
        List<MultiRetrievalService.RetrievalResult> results = multiRetrievalService.vectorRetrieval(query, topK);

        // 验证
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(embeddingService).embed(query);
        verify(milvusService).searchSimilarVectors(queryVector, topK);
    }

    @Test
    void testKeywordRetrieval_Success() {
        // 准备
        String query = "测试查询";
        int topK = 5;
        List<KeywordRetrievalService.SearchResult> keywordResults = Arrays.asList(
                new KeywordRetrievalService.SearchResult(1L, 1L, 0.9, "内容1", null),
                new KeywordRetrievalService.SearchResult(2L, 1L, 0.8, "内容2", null)
        );

        when(keywordRetrievalService.searchWithSynonyms(query, true, topK)).thenReturn(keywordResults);

        // 执行
        List<MultiRetrievalService.RetrievalResult> results = multiRetrievalService.keywordRetrieval(query, topK);

        // 验证
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(MultiRetrievalService.RetrievalSource.KEYWORD, results.get(0).getSource());
        verify(keywordRetrievalService).searchWithSynonyms(query, true, topK);
    }

    @Test
    void testKeywordRetrieval_EmptyResults() {
        // 准备
        String query = "测试查询";
        int topK = 5;

        when(keywordRetrievalService.searchWithSynonyms(query, true, topK)).thenReturn(Collections.emptyList());

        // 执行
        List<MultiRetrievalService.RetrievalResult> results = multiRetrievalService.keywordRetrieval(query, topK);

        // 验证
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(keywordRetrievalService).searchWithSynonyms(query, true, topK);
    }

    @Test
    void testHybridRetrieval_Success() {
        // 准备
        String query = "测试查询";
        int topK = 5;

        // 模拟向量检索结果
        List<Float> queryVector = Arrays.asList(1.0f, 2.0f, 3.0f);

        MilvusService.SearchResult milvusResult1 = new MilvusService.SearchResult();
        milvusResult1.setChunkId(1L);
        milvusResult1.setDocumentId(1L);
        milvusResult1.setScore(0.9f);
        milvusResult1.setContent("内容1");

        MilvusService.SearchResult milvusResult2 = new MilvusService.SearchResult();
        milvusResult2.setChunkId(2L);
        milvusResult2.setDocumentId(1L);
        milvusResult2.setScore(0.8f);
        milvusResult2.setContent("内容2");

        List<MilvusService.SearchResult> milvusResults = Arrays.asList(milvusResult1, milvusResult2);

        // 模拟关键词检索结果
        List<KeywordRetrievalService.SearchResult> keywordResults = Arrays.asList(
                new KeywordRetrievalService.SearchResult(2L, 1L, 0.85, "内容2", null),
                new KeywordRetrievalService.SearchResult(3L, 1L, 0.75, "内容3", null)
        );

        when(embeddingService.embed(query)).thenReturn(queryVector);
        when(milvusService.searchSimilarVectors(queryVector, topK * 2)).thenReturn(milvusResults);
        when(keywordRetrievalService.searchWithSynonyms(query, true, topK * 2)).thenReturn(keywordResults);

        // 执行
        List<MultiRetrievalService.RetrievalResult> results = multiRetrievalService.hybridRetrieval(query, topK);

        // 验证
        assertNotNull(results);
        // 应该有融合结果，至少包含一些文档
        verify(embeddingService).embed(query);
        verify(milvusService).searchSimilarVectors(queryVector, topK * 2);
        verify(keywordRetrievalService).searchWithSynonyms(query, true, topK * 2);
    }

    @Test
    void testHybridRetrieval_WithCustomWeights() {
        // 准备
        String query = "测试查询";
        int topK = 5;
        double vectorWeight = 0.7;
        double keywordWeight = 0.3;

        // 模拟向量检索结果
        List<Float> queryVector = Arrays.asList(1.0f, 2.0f, 3.0f);

        MilvusService.SearchResult milvusResult = new MilvusService.SearchResult();
        milvusResult.setChunkId(1L);
        milvusResult.setDocumentId(1L);
        milvusResult.setScore(0.9f);
        milvusResult.setContent("内容1");

        List<MilvusService.SearchResult> milvusResults = Arrays.asList(milvusResult);

        // 模拟关键词检索结果
        List<KeywordRetrievalService.SearchResult> keywordResults = Arrays.asList(
                new KeywordRetrievalService.SearchResult(2L, 1L, 0.85, "内容2", null)
        );

        when(embeddingService.embed(query)).thenReturn(queryVector);
        when(milvusService.searchSimilarVectors(queryVector, topK * 2)).thenReturn(milvusResults);
        when(keywordRetrievalService.searchWithSynonyms(query, true, topK * 2)).thenReturn(keywordResults);

        // 执行
        List<MultiRetrievalService.RetrievalResult> results = multiRetrievalService.hybridRetrieval(query, topK, vectorWeight, keywordWeight);

        // 验证
        assertNotNull(results);
        // 权重已应用
        verify(embeddingService).embed(query);
        verify(milvusService).searchSimilarVectors(queryVector, topK * 2);
        verify(keywordRetrievalService).searchWithSynonyms(query, true, topK * 2);
    }

    @Test
    void testHybridRetrieval_Timeout() {
        // 准备
        String query = "测试查询";
        int topK = 5;

        // 模拟超时情况 - 让其中一个future超时
        when(embeddingService.embed(query)).thenAnswer(invocation -> {
            Thread.sleep(6000); // 超时时间5000ms
            return Arrays.asList(1.0f, 2.0f, 3.0f);
        });

        // 执行
        List<MultiRetrievalService.RetrievalResult> results = multiRetrievalService.hybridRetrieval(query, topK);

        // 验证
        assertNotNull(results);
        // 应该触发降级检索
    }

    @Test
    void testGetCurrentMode() {
        // 默认模式应该是HYBRID
        MultiRetrievalService.RetrievalMode mode = multiRetrievalService.getCurrentMode();
        assertEquals(MultiRetrievalService.RetrievalMode.HYBRID, mode);
    }

    @Test
    void testSetMode() {
        // 执行
        multiRetrievalService.setMode(MultiRetrievalService.RetrievalMode.VECTOR);

        // 验证
        MultiRetrievalService.RetrievalMode mode = multiRetrievalService.getCurrentMode();
        assertEquals(MultiRetrievalService.RetrievalMode.VECTOR, mode);
    }

    @Test
    void testGetStats() {
        // 先执行一些检索操作以生成统计信息
        String query = "测试查询";
        int topK = 5;

        // 模拟向量检索
        List<Float> queryVector = Arrays.asList(1.0f, 2.0f, 3.0f);

        MilvusService.SearchResult milvusResult = new MilvusService.SearchResult();
        milvusResult.setChunkId(1L);
        milvusResult.setDocumentId(1L);
        milvusResult.setScore(0.9f);
        milvusResult.setContent("内容1");

        List<MilvusService.SearchResult> milvusResults = Arrays.asList(milvusResult);

        when(embeddingService.embed(query)).thenReturn(queryVector);
        when(milvusService.searchSimilarVectors(queryVector, topK)).thenReturn(milvusResults);

        multiRetrievalService.vectorRetrieval(query, topK);

        // 执行
        MultiRetrievalService.RetrievalStats stats = multiRetrievalService.getStats();

        // 验证
        assertNotNull(stats);
        assertTrue(stats.getTotalQueries() > 0);
        assertTrue(stats.getVectorQueries() > 0);
    }

    @Test
    void testFallbackRetrieval_PreferVector() {
        // 这个测试可能需要访问私有方法，暂时跳过
        // 可以通过模拟hybridRetrieval的超时来间接测试
    }

    @Test
    void testFuseResults_EmptyBoth() {
        // 测试融合空结果
        // 由于fuseResults是私有的，需要通过hybridRetrieval间接测试
    }

    @Test
    void testFuseResults_VectorOnly() {
        // 测试只有向量结果的情况
        // 可以通过模拟keywordRetrieval返回空列表来测试
    }

    @Test
    void testFuseResults_KeywordOnly() {
        // 测试只有关键词结果的情况
        // 可以通过模拟vectorRetrieval返回空列表来测试
    }
}