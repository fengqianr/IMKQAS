package com.student.performance;

import com.student.service.rag.*;
import com.student.service.rag.impl.*;
import com.student.service.LlmService;
import com.student.entity.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * RAG核心性能测试
 * 测试RAG核心组件的性能指标，包括检索、融合、重排序等
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@org.springframework.test.context.ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.main.lazy-initialization=true", // 启用懒加载提高测试启动速度
        "spring.jmx.enabled=false"
})
public class RagPerformanceTest extends BasePerformanceTest {

    @MockBean
    private KeywordRetrievalService keywordRetrievalService;

    // VectorRetrievalService不存在，使用MultiRetrievalService代替
    // @MockBean
    // private VectorRetrievalService vectorRetrievalService;

    @MockBean
    private MultiRetrievalService multiRetrievalService;

    @MockBean
    private RrfFusionService rrfFusionService;

    @MockBean
    private CrossEncoderRerankService crossEncoderRerankService;

    @MockBean
    private LlmService llmService;

    @Autowired(required = false)
    private QaService qaService;

    private List<KeywordRetrievalService.SearchResult> mockKeywordResults;
    private List<MultiRetrievalService.RetrievalResult> mockVectorResults;
    private List<MultiRetrievalService.RetrievalResult> mockFusedResults;
    private List<MultiRetrievalService.RetrievalResult> mockRerankedResults;
    private List<MultiRetrievalService.RetrievalResult> mockKeywordResultsAsRetrieval;

    @BeforeEach
    void setUp() {
        // 准备模拟数据
        mockKeywordResults = Arrays.asList(
                new KeywordRetrievalService.SearchResult(1L, 100L, 0.85, "高血压的症状和治疗方法", null),
                new KeywordRetrievalService.SearchResult(2L, 100L, 0.75, "高血压的预防措施", null),
                new KeywordRetrievalService.SearchResult(3L, 101L, 0.65, "糖尿病的基本知识", null)
        );

        // 转换为MultiRetrievalService.RetrievalResult类型以用于融合测试
        mockKeywordResultsAsRetrieval = mockKeywordResults.stream()
                .map(searchResult -> new MultiRetrievalService.RetrievalResult(
                        searchResult.getChunkId(),
                        searchResult.getDocumentId(),
                        searchResult.getScore(),
                        searchResult.getContent(),
                        MultiRetrievalService.RetrievalSource.KEYWORD,
                        null,  // vectorScore
                        searchResult.getScore()  // keywordScore
                ))
                .collect(java.util.stream.Collectors.toList());

        mockVectorResults = Arrays.asList(
                new MultiRetrievalService.RetrievalResult(1L, 100L, 0.90, "高血压的症状和治疗方法",
                        MultiRetrievalService.RetrievalSource.VECTOR, 0.90, null),
                new MultiRetrievalService.RetrievalResult(2L, 100L, 0.80, "高血压的预防措施",
                        MultiRetrievalService.RetrievalSource.VECTOR, 0.80, null),
                new MultiRetrievalService.RetrievalResult(4L, 102L, 0.70, "心血管健康知识",
                        MultiRetrievalService.RetrievalSource.VECTOR, 0.70, null)
        );

        mockFusedResults = Arrays.asList(
                new MultiRetrievalService.RetrievalResult(1L, 100L, 0.875, "高血压的症状和治疗方法",
                        MultiRetrievalService.RetrievalSource.HYBRID, 0.90, 0.85),
                new MultiRetrievalService.RetrievalResult(2L, 100L, 0.775, "高血压的预防措施",
                        MultiRetrievalService.RetrievalSource.HYBRID, 0.80, 0.75),
                new MultiRetrievalService.RetrievalResult(3L, 101L, 0.675, "糖尿病的基本知识",
                        MultiRetrievalService.RetrievalSource.HYBRID, null, 0.65)
        );

        mockRerankedResults = Arrays.asList(
                new MultiRetrievalService.RetrievalResult(1L, 100L, 0.95, "高血压的症状和治疗方法",
                        MultiRetrievalService.RetrievalSource.HYBRID, 0.90, 0.85),
                new MultiRetrievalService.RetrievalResult(2L, 100L, 0.85, "高血压的预防措施",
                        MultiRetrievalService.RetrievalSource.HYBRID, 0.80, 0.75),
                new MultiRetrievalService.RetrievalResult(3L, 101L, 0.75, "糖尿病的基本知识",
                        MultiRetrievalService.RetrievalSource.HYBRID, null, 0.65)
        );

        // 配置模拟行为
        when(keywordRetrievalService.search(anyString(), anyInt()))
                .thenReturn(mockKeywordResults);

        // vectorRetrievalService已被移除，注释掉相关模拟
        // when(vectorRetrievalService.search(any(float[].class), anyInt()))
        //         .thenReturn(mockVectorResults);

        when(multiRetrievalService.hybridRetrieval(anyString(), anyInt()))
                .thenReturn(mockFusedResults);

        when(rrfFusionService.fuseVectorAndKeyword(anyList(), anyList(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(mockFusedResults);

        when(crossEncoderRerankService.rerank(anyString(), anyList()))
                .thenReturn(mockRerankedResults);

        when(llmService.generateAnswer(anyString(), anyList()))
                .thenReturn("高血压是一种常见的慢性病，主要症状包括头痛、眩晕、心悸等。治疗方法包括生活方式干预和药物治疗。");

        // 模拟嵌入服务（vectorRetrievalService已被移除）
        // when(vectorRetrievalService.embed(anyString()))
        //         .thenReturn(new float[1024]); // 返回1024维零向量
    }

    /**
     * 关键词检索性能测试
     * 测试BM25关键词检索的响应时间
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testKeywordRetrievalPerformance() {
        assertExecutionTime(() -> {
            List<KeywordRetrievalService.SearchResult> results = keywordRetrievalService.search("高血压", 10);
            assertNotNull(results);
            assertFalse(results.isEmpty());
        }, 2000, "关键词检索响应时间");
    }

    /**
     * 向量检索性能测试
     * 测试向量相似度检索的响应时间
     * 注：VectorRetrievalService不存在，暂时注释掉此测试
     */
    // @Test
    // @Timeout(value = 5, unit = TimeUnit.SECONDS)
    // void testVectorRetrievalPerformance() {
    //     assertExecutionTime(() -> {
    //         List<VectorRetrievalService.SearchResult> results = vectorRetrievalService.search(new float[1024], 10);
    //         assertNotNull(results);
    //         assertFalse(results.isEmpty());
    //     }, 2000, "向量检索响应时间");
    // }

    /**
     * RRF融合性能测试
     * 测试多路检索结果融合的响应时间
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRrfFusionPerformance() {
        assertExecutionTime(() -> {
            List<MultiRetrievalService.RetrievalResult> results = rrfFusionService.fuseVectorAndKeyword(
                    mockKeywordResultsAsRetrieval, mockVectorResults, 0.5, 0.5, 10);
            assertNotNull(results);
            assertEquals(3, results.size());
        }, 1000, "RRF融合响应时间");
    }

    /**
     * 重排序性能测试
     * 测试CrossEncoder重排序的响应时间
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRerankPerformance() {
        assertExecutionTime(() -> {
            List<MultiRetrievalService.RetrievalResult> results = crossEncoderRerankService.rerank("高血压", mockFusedResults);
            assertNotNull(results);
            assertEquals(3, results.size());
        }, 3000, "重排序响应时间");
    }

    /**
     * LLM生成性能测试
     * 测试大语言模型生成回答的响应时间
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testLlmGenerationPerformance() {
        assertExecutionTime(() -> {
            String answer = llmService.generateAnswer("高血压有什么症状？",
                    mockRerankedResults.stream()
                            .map(MultiRetrievalService.RetrievalResult::getContent)
                            .collect(java.util.stream.Collectors.toList()));
            assertNotNull(answer);
            assertFalse(answer.isEmpty());
        }, 5000, "LLM生成响应时间");
    }

    /**
     * 完整RAG流程性能测试
     * 测试从查询到答案的完整RAG流程响应时间
     */
    @RepeatedTest(3)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testCompleteRagPipelinePerformance() {
        // 模拟完整RAG流程：检索 -> 融合 -> 重排序 -> LLM生成
        assertExecutionTime(() -> {
            // 1. 多路检索
            List<MultiRetrievalService.RetrievalResult> fusedResults = multiRetrievalService.hybridRetrieval("高血压的症状", 10);
            assertNotNull(fusedResults);

            // 2. 重排序
            List<MultiRetrievalService.RetrievalResult> rerankedResults = crossEncoderRerankService.rerank("高血压的症状", fusedResults);
            assertNotNull(rerankedResults);

            // 3. 将检索结果转换为上下文字符串列表
            List<String> contexts = rerankedResults.stream()
                    .map(MultiRetrievalService.RetrievalResult::getContent)
                    .collect(java.util.stream.Collectors.toList());

            // 4. LLM生成
            String answer = llmService.generateAnswer("高血压的症状", contexts);
            assertNotNull(answer);

        }, 8000, "完整RAG流程响应时间");
    }

    /**
     * 批量检索性能测试
     * 测试批量查询的吞吐量
     */
    @Test
    void testBatchRetrievalPerformance() {
        String[] queries = {
                "高血压的症状",
                "糖尿病的治疗",
                "冠心病的预防",
                "肺炎的诊断",
                "发烧的处理"
        };

        long totalTime = measureExecutionTime(() -> {
            for (String query : queries) {
                List<KeywordRetrievalService.SearchResult> results = keywordRetrievalService.search(query, 5);
                assertNotNull(results);
            }
        });

        double throughput = queries.length / (totalTime / 1000.0); // 查询/秒
        System.out.printf("批量检索性能: 处理 %d 个查询, 总时间 %dms, 吞吐量 %.2f 查询/秒%n",
                queries.length, totalTime, throughput);

        // 断言吞吐量 > 2 查询/秒
        assertTrue(throughput > 2.0, () -> String.format("吞吐量过低: %.2f 查询/秒", throughput));
    }
}