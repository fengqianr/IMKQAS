package com.student.service.rag.impl;

import com.student.config.RagConfig;
import com.student.service.rag.MultiRetrievalService;
import com.student.service.rag.CrossEncoderRerankService;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 交叉编码器重排序服务测试
 * 测试CrossEncoderRerankServiceImpl的重排序功能，包括正常流程、降级处理和异常场景
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
public class CrossEncoderRerankServiceImplTest {

    @Mock
    private RagConfig ragConfig;

    @Mock
    private RagConfig.RerankerConfig rerankerConfig;

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call call;

    @Mock
    private Response response;

    @Mock
    private ResponseBody responseBody;

    private CrossEncoderRerankServiceImpl rerankService;

    @BeforeEach
    void setUp() {
        // 配置模拟的RagConfig
        when(ragConfig.getReranker()).thenReturn(rerankerConfig);
        when(rerankerConfig.getModel()).thenReturn("gte-rerank-v2");
        when(rerankerConfig.getApiEndpoint()).thenReturn("https://dashscope.aliyuncs.com/api/v1/services/rerank");
        when(rerankerConfig.getTopK()).thenReturn(5);
        when(rerankerConfig.getTimeout()).thenReturn(5000);
        when(rerankerConfig.getApiKey()).thenReturn("test-api-key");

        // 创建服务实例
        rerankService = new CrossEncoderRerankServiceImpl(ragConfig);

        // 使用反射手动调用init方法，因为@PostConstruct不会在测试中自动执行
        ReflectionTestUtils.invokeMethod(rerankService, "init");
    }

    /**
     * 测试服务初始化
     */
    @Test
    @DisplayName("重排序服务初始化测试")
    void testServiceInitialization() {
        assertNotNull(rerankService);
        assertTrue(rerankService.isAvailable(), "服务应可用");

        int defaultTopK = rerankService.getDefaultTopK();
        assertEquals(5, defaultTopK, "默认topK应为5");
    }

    /**
     * 测试空查询处理
     */
    @Test
    @DisplayName("空查询重排序测试")
    void testRerankWithEmptyQuery() {
        // 准备测试数据
        String query = "";
        List<MultiRetrievalService.RetrievalResult> results = createMockResults(3);
        int topK = 3;

        // 执行重排序
        List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankService.rerank(query, results, topK);

        // 验证：空查询应返回原始结果
        assertNotNull(rerankedResults);
        assertEquals(results.size(), rerankedResults.size(), "空查询应返回原始结果");
        assertEquals(results.get(0).getChunkId(), rerankedResults.get(0).getChunkId(), "结果应保持不变");
    }

    /**
     * 测试空结果处理
     */
    @Test
    @DisplayName("空结果重排序测试")
    void testRerankWithEmptyResults() {
        // 准备测试数据
        String query = "高血压有什么症状？";
        List<MultiRetrievalService.RetrievalResult> results = new ArrayList<>();
        int topK = 3;

        // 执行重排序
        List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankService.rerank(query, results, topK);

        // 验证：空结果应返回空列表
        assertNotNull(rerankedResults);
        assertTrue(rerankedResults.isEmpty(), "空结果应返回空列表");
    }

    /**
     * 测试null结果处理
     */
    @Test
    @DisplayName("null结果重排序测试")
    void testRerankWithNullResults() {
        // 准备测试数据
        String query = "高血压有什么症状？";
        List<MultiRetrievalService.RetrievalResult> results = null;
        int topK = 3;

        // 执行重排序
        List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankService.rerank(query, results, topK);

        // 验证：null结果应返回null（或空列表，取决于实现）
        // 根据实现，可能返回null或空列表
        assertNotNull(rerankedResults, "null结果应返回非null值（可能是空列表）");
    }

    /**
     * 测试默认topK重排序
     */
    @Test
    @DisplayName("默认topK重排序测试")
    void testRerankWithDefaultTopK() {
        // 准备测试数据
        String query = "高血压有什么症状？";
        List<MultiRetrievalService.RetrievalResult> results = createMockResults(10);

        // 执行重排序（使用默认topK）
        List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankService.rerank(query, results);

        // 验证：应返回结果，数量不超过默认topK
        assertNotNull(rerankedResults);
        assertTrue(rerankedResults.size() <= 5, "结果数量不应超过默认topK(5)");
    }

    /**
     * 测试批量重排序
     */
    @Test
    @DisplayName("批量重排序测试")
    void testBatchRerank() {
        // 准备测试数据
        List<String> queries = List.of("高血压症状", "糖尿病治疗");
        List<List<MultiRetrievalService.RetrievalResult>> resultsList = List.of(
                createMockResults(3),
                createMockResults(4)
        );
        int topK = 2;

        // 执行批量重排序
        List<List<MultiRetrievalService.RetrievalResult>> batchResults = rerankService.batchRerank(queries, resultsList, topK);

        // 验证
        assertNotNull(batchResults);
        assertEquals(2, batchResults.size(), "应返回2组结果");
        assertEquals(2, batchResults.get(0).size(), "第一组结果数量应为topK(2)");
        assertEquals(2, batchResults.get(1).size(), "第二组结果数量应为topK(2)");
    }

    /**
     * 测试API调用异常降级处理
     */
    @Test
    @DisplayName("API异常降级处理测试")
    void testApiExceptionFallback() throws IOException {
        // 准备测试数据
        String query = "高血压有什么症状？";
        List<MultiRetrievalService.RetrievalResult> results = createMockResults(5);
        int topK = 3;

        // 模拟HTTP客户端
        setupHttpClient();

        // 模拟API调用异常
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("API连接失败"));

        // 注入模拟的HTTP客户端
        ReflectionTestUtils.setField(rerankService, "httpClient", httpClient);

        // 执行重排序（应触发降级处理）
        List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankService.rerank(query, results, topK);

        // 验证：异常时应返回降级结果
        assertNotNull(rerankedResults);
        assertEquals(topK, rerankedResults.size(), "降级结果数量应为topK(3)");

        // 验证降级结果已按原始分数排序
        double firstScore = rerankedResults.get(0).getScore() != null ? rerankedResults.get(0).getScore() : 0.0;
        double secondScore = rerankedResults.get(1).getScore() != null ? rerankedResults.get(1).getScore() : 0.0;
        assertTrue(firstScore >= secondScore, "降级结果应按分数降序排列");
    }

    /**
     * 测试统计信息获取
     */
    @Test
    @DisplayName("重排序统计信息测试")
    void testGetStats() {
        // 初始统计信息
        CrossEncoderRerankService.RerankStats initialStats = rerankService.getStats();
        assertNotNull(initialStats);
        assertEquals(0, initialStats.getTotalReranks(), "初始总重排序次数应为0");

        // 执行几次重排序操作
        String query = "测试查询";
        List<MultiRetrievalService.RetrievalResult> results = createMockResults(3);

        rerankService.rerank(query, results, 2);
        rerankService.rerank(query, results, 2);
        rerankService.rerank(query, results, 2);

        // 获取更新后的统计信息
        CrossEncoderRerankService.RerankStats updatedStats = rerankService.getStats();
        assertNotNull(updatedStats);
        assertTrue(updatedStats.getTotalReranks() > 0, "重排序后总次数应大于0");

        // 验证成功率（由于使用模拟数据，成功率应为100%或根据实现而定）
        double successRate = updatedStats.getSuccessRate();
        assertTrue(successRate >= 0.0 && successRate <= 1.0, "成功率应在0-1之间");
    }

    /**
     * 测试服务可用性检查
     */
    @Test
    @DisplayName("服务可用性检查测试")
    void testServiceAvailability() {
        // 测试正常配置下的可用性
        assertTrue(rerankService.isAvailable(), "正常配置下服务应可用");

        // 测试无API端点时的可用性
        when(rerankerConfig.getApiEndpoint()).thenReturn("");
        // 重新初始化服务以应用新配置
        rerankService = new CrossEncoderRerankServiceImpl(ragConfig);
        ReflectionTestUtils.invokeMethod(rerankService, "init");

        assertFalse(rerankService.isAvailable(), "无API端点时服务应不可用");
    }

    /**
     * 测试结果数量限制（避免API过载）
     */
    @Test
    @DisplayName("结果数量限制测试")
    void testResultLimit() throws IOException {
        // 准备大量测试数据
        String query = "高血压有什么症状？";
        List<MultiRetrievalService.RetrievalResult> results = createMockResults(150); // 超过100个
        int topK = 10;

        // 模拟HTTP客户端
        setupHttpClient();

        // 模拟成功的API响应
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);

        // 模拟响应体：包含分数数组
        String mockResponse = "{\"scores\":[0.95,0.90,0.85,0.80,0.75,0.70,0.65,0.60,0.55,0.50]}";
        when(responseBody.string()).thenReturn(mockResponse);

        // 注入模拟的HTTP客户端
        ReflectionTestUtils.setField(rerankService, "httpClient", httpClient);

        // 执行重排序
        List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankService.rerank(query, results, topK);

        // 验证：应限制处理数量（不超过100个）
        // 注意：具体限制取决于实现，这里假设限制为100
        assertNotNull(rerankedResults);
        assertTrue(rerankedResults.size() <= topK, "返回结果数量不应超过topK");

        // 验证统计信息中的输入数量
        CrossEncoderRerankService.RerankStats stats = rerankService.getStats();
        assertTrue(stats.getTotalInputResults() <= 100, "处理的输入结果数量应限制在100以内");
    }

    /**
     * 测试API密钥未配置异常
     */
    @Test
    @DisplayName("API密钥未配置异常测试")
    void testApiKeyNotConfigured() throws IOException {
        // 配置无API密钥
        when(rerankerConfig.getApiKey()).thenReturn("");

        // 模拟EnvUtil.getEnv返回null
        try (MockedStatic<com.student.utils.EnvUtil> envUtilMock = Mockito.mockStatic(com.student.utils.EnvUtil.class)) {
            envUtilMock.when(() -> com.student.utils.EnvUtil.getEnv("BAILIAN_API_KEY")).thenReturn(null);

            // 重新初始化服务
            rerankService = new CrossEncoderRerankServiceImpl(ragConfig);
            ReflectionTestUtils.invokeMethod(rerankService, "init");

            // 准备测试数据
            String query = "高血压有什么症状？";
            List<MultiRetrievalService.RetrievalResult> results = createMockResults(3);
            int topK = 2;

            // 执行重排序（应触发降级处理）
            List<MultiRetrievalService.RetrievalResult> rerankedResults = rerankService.rerank(query, results, topK);

            // 验证：应返回降级结果
            assertNotNull(rerankedResults);
            assertEquals(topK, rerankedResults.size(), "API密钥异常时应返回降级结果");
        }
    }

    /**
     * 创建模拟的检索结果
     */
    private List<MultiRetrievalService.RetrievalResult> createMockResults(int count) {
        List<MultiRetrievalService.RetrievalResult> results = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            // 创建递减的分数，便于验证排序
            double score = 1.0 - (i - 1) * 0.1;
            String content = "这是第" + i + "个测试文档，关于高血压的症状和治疗。";

            MultiRetrievalService.RetrievalResult result = new MultiRetrievalService.RetrievalResult(
                    (long) i,           // chunkId
                    100L + i,           // documentId
                    score,              // score
                    content,            // content
                    MultiRetrievalService.RetrievalSource.HYBRID, // source
                    score,              // vectorScore
                    score               // keywordScore
            );
            results.add(result);
        }
        return results;
    }

    /**
     * 设置模拟的HTTP客户端
     */
    private void setupHttpClient() {
        // 模拟OkHttpClient.Builder
        OkHttpClient.Builder builderMock = mock(OkHttpClient.Builder.class);
        when(builderMock.connectTimeout(anyLong(), any())).thenReturn(builderMock);
        when(builderMock.readTimeout(anyLong(), any())).thenReturn(builderMock);
        when(builderMock.writeTimeout(anyLong(), any())).thenReturn(builderMock);
        when(builderMock.build()).thenReturn(httpClient);
    }
}