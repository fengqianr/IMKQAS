package com.student.service;

import com.student.config.RagConfig;
import com.student.service.DocumentProcessorService;
import com.student.entity.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * 问答服务集成测试
 * 测试完整的RAG问答流程，使用模拟外部服务
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootTest
@TestPropertySource(properties = {
        "imkqas.rag.embedding.deployment=local",
        "imkqas.rag.llm.deployment=local",
        "imkqas.rag.reranker.deployment=local"
})
@MockitoSettings(strictness = Strictness.LENIENT)
class QaServiceIntegrationTest {

    @Autowired
    private QaService qaService;

    @MockBean
    private MultiRetrievalService multiRetrievalService;

    @MockBean
    private RrfFusionService rrfFusionService;

    @MockBean
    private EmbeddingService embeddingService;

    @MockBean
    private com.student.service.MilvusService milvusService;

    @MockBean
    private KeywordRetrievalService keywordRetrievalService;

    @MockBean
    private CrossEncoderRerankService crossEncoderRerankService;

    @MockBean
    private LlmService llmService;

    @MockBean
    private RagConfig ragConfig;

    @MockBean
    private DocumentProcessorService documentProcessorService;

    @MockBean
    private RedisService redisService;

    @BeforeEach
    void setUp() {
        // 重置所有模拟（注释掉，可能导致Mockito匹配器错误）
        // reset(embeddingService, milvusService, keywordRetrievalService, crossEncoderRerankService, llmService);

        // ========== 模拟RAG配置 ==========
        // 模拟CacheConfig
        RagConfig.CacheConfig mockCacheConfig = mock(RagConfig.CacheConfig.class);
        RagConfig.CacheConfig.QueryCacheConfig mockQueryCacheConfig = mock(RagConfig.CacheConfig.QueryCacheConfig.class);
        when(mockQueryCacheConfig.isEnabled()).thenReturn(false); // 测试中禁用缓存，避免干扰
        when(mockCacheConfig.getQuery()).thenReturn(mockQueryCacheConfig);
        lenient().when(ragConfig.getCache()).thenReturn(mockCacheConfig);

        // 模拟RetrievalConfig
        RagConfig.RetrievalConfig mockRetrievalConfig = mock(RagConfig.RetrievalConfig.class);
        RagConfig.RetrievalConfig.WeightsConfig mockWeightsConfig = mock(RagConfig.RetrievalConfig.WeightsConfig.class);
        when(mockWeightsConfig.getVector()).thenReturn(0.6);
        when(mockWeightsConfig.getKeyword()).thenReturn(0.4);
        when(mockRetrievalConfig.getMode()).thenReturn(RagConfig.RetrievalConfig.Mode.HYBRID);
        when(mockRetrievalConfig.getInitialTopK()).thenReturn(10);
        when(mockRetrievalConfig.getRerankTopK()).thenReturn(5);
        when(mockRetrievalConfig.getWeights()).thenReturn(mockWeightsConfig);
        when(mockRetrievalConfig.getRrfK()).thenReturn(60); // RRF融合参数
        lenient().when(ragConfig.getRetrieval()).thenReturn(mockRetrievalConfig);

        // ========== 模拟外部服务 ==========
        // 模拟嵌入服务
        when(embeddingService.getDimension()).thenReturn(1024);
        when(embeddingService.isAvailable()).thenReturn(true);

        // 模拟检索结果
        List<MultiRetrievalService.RetrievalResult> mockRetrievalResults = Arrays.asList(
                createRetrievalResult(1L, 1L, 0.9, "高血压是一种常见的心血管疾病",
                        MultiRetrievalService.RetrievalSource.HYBRID, 0.9, 0.8),
                createRetrievalResult(2L, 1L, 0.85, "高血压的治疗包括药物治疗和生活方式干预",
                        MultiRetrievalService.RetrievalSource.HYBRID, 0.85, 0.75)
        );

        // 模拟多路检索服务
        lenient().when(multiRetrievalService.hybridRetrieval(anyString(), anyInt()))
                .thenReturn(mockRetrievalResults);
        lenient().when(multiRetrievalService.getCurrentMode()).thenReturn(MultiRetrievalService.RetrievalMode.HYBRID);

        // 模拟重排序服务
        lenient().when(crossEncoderRerankService.rerank(anyString(), anyList(), anyInt()))
                .thenReturn(mockRetrievalResults);
        lenient().when(crossEncoderRerankService.isAvailable()).thenReturn(true);

        // 模拟LLM服务
        lenient().when(llmService.generateAnswer(anyString(), anyList()))
                .thenReturn("高血压是一种常见的心血管疾病，主要特征是动脉血压持续升高。治疗包括药物治疗（如ACE抑制剂、钙通道阻滞剂）和生活方式干预（如低盐饮食、规律运动）。");
        lenient().when(llmService.isAvailable()).thenReturn(true);

        // 模拟LLM带引用的回答
        LlmService.AnswerWithCitations mockAnswerWithCitations = new LlmService.AnswerWithCitations(
                "高血压是一种常见的心血管疾病，主要特征是动脉血压持续升高。治疗包括药物治疗和生活方式干预。",
                Arrays.asList(new LlmService.Citation("文档-1-片段-1", 0, "高血压是一种常见的心血管疾病"))
        );
        lenient().when(llmService.generateAnswerWithCitations(anyString(), anyList()))
                .thenReturn(mockAnswerWithCitations);

        // 模拟LLM模型信息
        LlmService.ModelInfo mockModelInfo = new LlmService.ModelInfo(
                "qwen-plus", "阿里云", 8192, false
        );
        lenient().when(llmService.getModelInfo()).thenReturn(mockModelInfo);

        // 模拟关键词检索服务
        lenient().when(keywordRetrievalService.search(anyString(), anyInt()))
                .thenReturn(Arrays.asList(
                        new KeywordRetrievalService.SearchResult(1L, 1L, 0.8, "高血压相关内容", null)
                ));

        // 模拟向量检索服务（通过MilvusService）
        // 注意：MultiRetrievalService内部使用MilvusService，但我们已经模拟了hybridRetrieval
    }

    @Test
    void testAnswer_SimpleQuery() {
        // 准备
        String query = "什么是高血压？";
        Long userId = 1L;
        Long conversationId = 1L;

        // 执行
        QaService.QaResponse response = qaService.answer(query, userId, conversationId);

        // 验证
        assertNotNull(response);
        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("高血压"));
        assertNotNull(response.getRetrievedContext());
        assertFalse(response.getRetrievedContext().isEmpty());
        assertTrue(response.getConfidence() >= 0 && response.getConfidence() <= 1);
        assertTrue(response.getProcessingTime() >= 0);

        // 验证服务调用
        verify(multiRetrievalService, atLeastOnce()).hybridRetrieval(eq(query), anyInt());
        verify(crossEncoderRerankService, atLeastOnce()).rerank(eq(query), anyList(), anyInt());
        verify(llmService, atLeastOnce()).generateAnswer(eq(query), anyList());
    }

    @Test
    void testAnswer_WithLowConfidence() {
        // 准备
        String query = "一些模糊的查询";
        Long userId = 1L;
        Long conversationId = 1L;

        // 模拟检索结果为空或低质量
        when(multiRetrievalService.hybridRetrieval(anyString(), anyInt()))
                .thenReturn(Arrays.asList());

        // 执行
        QaService.QaResponse response = qaService.answer(query, userId, conversationId);

        // 验证
        assertNotNull(response);
        // 即使检索结果为空，也应该有回答（可能是兜底回答）
        assertNotNull(response.getAnswer());
        // 置信度可能较低
        assertTrue(response.getConfidence() >= 0 && response.getConfidence() <= 1);
    }

    @Test
    void testAnswer_WithSources() {
        // 准备
        String query = "高血压的治疗方法有哪些？";
        Long userId = 1L;
        Long conversationId = 1L;

        // 执行带来源的问答
        QaService.QaResponseWithSources response = qaService.answerWithSources(query, userId, conversationId);

        // 验证
        assertNotNull(response);
        assertNotNull(response.getAnswer());
        assertNotNull(response.getCitations());
        // 带来源的回答应该包含引用信息
        assertFalse(response.getCitations().isEmpty());
    }

    @Test
    void testAnswerAsync() {
        // 准备
        String query = "高血压的病因是什么？";
        Long userId = 1L;
        Long conversationId = 1L;

        // 执行异步问答
        var future = qaService.answerAsync(query, userId, conversationId);

        // 验证
        assertNotNull(future);
        // 可以等待完成或验证非阻塞特性
    }

    @Test
    void testAnswerBatch() {
        // 准备
        List<String> queries = Arrays.asList("什么是高血压？", "如何预防高血压？");
        Long userId = 1L;
        Long conversationId = 1L;

        // 执行批量问答
        List<QaService.QaResponse> responses = qaService.answerBatch(queries, userId, conversationId);

        // 验证
        assertNotNull(responses);
        assertEquals(queries.size(), responses.size());
        for (int i = 0; i < responses.size(); i++) {
            assertNotNull(responses.get(i));
            assertNotNull(responses.get(i).getAnswer());
        }
    }

    @Test
    void testIsAvailable() {
        // 执行
        boolean available = qaService.isAvailable();

        // 验证
        assertTrue(available);
    }

    @Test
    void testGetStats() {
        // 先执行一些问答以生成统计信息
        String query = "测试查询";
        Long userId = 1L;
        Long conversationId = 1L;
        qaService.answer(query, userId, conversationId);

        // 执行
        QaService.QaStats stats = qaService.getStats();

        // 验证
        assertNotNull(stats);
        assertTrue(stats.getTotalQueries() > 0);
        assertTrue(stats.getSuccessRate() >= 0 && stats.getSuccessRate() <= 1);
    }

    /**
     * 创建检索结果对象（辅助方法）
     */
    private MultiRetrievalService.RetrievalResult createRetrievalResult(
            Long chunkId, Long documentId, Double score, String content,
            MultiRetrievalService.RetrievalSource source, Double vectorScore, Double keywordScore) {
        return new MultiRetrievalService.RetrievalResult(
                chunkId, documentId, score, content, source, vectorScore, keywordScore
        );
    }

}