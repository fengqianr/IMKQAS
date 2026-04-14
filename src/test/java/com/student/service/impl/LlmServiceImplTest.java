package com.student.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.config.RagConfig;
import com.student.service.LlmService;
import com.student.service.RedisService;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 大语言模型服务单元测试
 * 测试LlmServiceImpl的各种方法
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class LlmServiceImplTest {

    @Mock
    private RagConfig ragConfig;

    @Mock
    private RagConfig.LlmConfig llmConfig;

    @Mock
    private RagConfig.CacheConfig cacheConfig;

    @Mock
    private RagConfig.CacheConfig.LlmCacheConfig llmCacheConfig;

    @Mock
    private RedisService redisService;

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call httpCall;

    @InjectMocks
    private LlmServiceImpl llmService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 模拟配置
        when(ragConfig.getLlm()).thenReturn(llmConfig);
        when(ragConfig.getCache()).thenReturn(cacheConfig);
        when(cacheConfig.getLlm()).thenReturn(llmCacheConfig);

        // LLM配置
        when(llmConfig.getBaseUrl()).thenReturn("https://dashscope.aliyuncs.com/compatible-mode/v1");
        when(llmConfig.getApiKey()).thenReturn("test-api-key");
        when(llmConfig.getModel()).thenReturn("qwen-plus");
        when(llmConfig.getTemperature()).thenReturn(0.1);
        when(llmConfig.getMaxTokens()).thenReturn(2000);
        when(llmConfig.getTimeout()).thenReturn(60);

        // 缓存配置
        when(llmCacheConfig.isEnabled()).thenReturn(true);
        when(llmCacheConfig.getTtl()).thenReturn(1800);

        // 注入模拟的HTTP客户端
        ReflectionTestUtils.setField(llmService, "httpClient", httpClient);
    }

    @Test
    void testGenerateAnswer_CacheHit() {
        // 准备
        String query = "高血压有什么症状";
        List<String> context = Arrays.asList("高血压是常见的心血管疾病", "症状包括头痛、头晕等");
        String cacheKey = generateCacheKey(query, context); // 使用实际缓存键生成逻辑

        String cachedAnswer = "高血压的常见症状包括头痛、头晕、心悸等。";

        when(redisService.get(cacheKey)).thenReturn(cachedAnswer);

        // 执行
        String answer = llmService.generateAnswer(query, context);

        // 验证
        assertNotNull(answer);
        assertEquals(cachedAnswer, answer);
        verify(redisService, times(1)).get(cacheKey);
        // 不应调用HTTP客户端
        verify(httpClient, never()).newCall(any(Request.class));
    }

    @Test
    void testGenerateAnswer_CacheMiss_Success() throws IOException {
        // 准备
        String query = "高血压有什么症状";
        List<String> context = Arrays.asList("高血压是常见的心血管疾病", "症状包括头痛、头晕等");
        String cacheKey = generateCacheKey(query, context); // 使用实际缓存键生成逻辑

        when(redisService.get(cacheKey)).thenReturn(null);
        when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);

        // 模拟成功的API响应
        String apiResponse = createMockApiResponse("高血压的常见症状包括头痛、头晕、心悸等。");
        Response response = createMockResponse(200, apiResponse);
        when(httpCall.execute()).thenReturn(response);

        when(redisService.set(eq(cacheKey), anyString(), eq(1800L))).thenReturn(true);

        // 执行
        String answer = llmService.generateAnswer(query, context);

        // 验证
        assertNotNull(answer);
        assertTrue(answer.contains("高血压"));
        verify(redisService, times(1)).get(cacheKey);
        verify(httpClient, times(1)).newCall(any(Request.class));
        verify(redisService, times(1)).set(eq(cacheKey), anyString(), eq(1800L));
    }

    @Test
    void testGenerateAnswer_CacheMiss_ApiFailure() throws IOException {
        // 准备
        String query = "高血压有什么症状";
        List<String> context = Arrays.asList("高血压是常见的心血管疾病", "症状包括头痛、头晕等");
        String cacheKey = generateCacheKey(query, context);

        when(redisService.get(cacheKey)).thenReturn(null);
        when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);

        // 模拟API失败
        Response response = createMockResponse(500, "Internal Server Error");
        when(httpCall.execute()).thenReturn(response);

        // 执行
        String answer = llmService.generateAnswer(query, context);

        // 验证 - 应该返回降级回答
        assertNotNull(answer);
        assertTrue(answer.contains("抱歉") || answer.contains("无法生成回答"));
        verify(redisService, times(1)).get(cacheKey);
        verify(httpClient, times(1)).newCall(any(Request.class));
        verify(redisService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void testGenerateAnswer_CacheMiss_IOException() throws IOException {
        // 准备
        String query = "高血压有什么症状";
        List<String> context = Arrays.asList("高血压是常见的心血管疾病", "症状包括头痛、头晕等");
        String cacheKey = generateCacheKey(query, context);

        when(redisService.get(cacheKey)).thenReturn(null);
        when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);

        // 模拟IO异常
        when(httpCall.execute()).thenThrow(new IOException("网络连接失败"));

        // 执行
        String answer = llmService.generateAnswer(query, context);

        // 验证 - 应该返回降级回答
        assertNotNull(answer);
        assertTrue(answer.contains("抱歉") || answer.contains("无法生成回答"));
    }

    @Test
    void testGenerateAnswer_CacheDisabled() throws IOException {
        // 准备
        String query = "高血压有什么症状";
        List<String> context = Arrays.asList("高血压是常见的心血管疾病", "症状包括头痛、头晕等");

        when(llmCacheConfig.isEnabled()).thenReturn(false);
        when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);

        String apiResponse = createMockApiResponse("高血压症状包括头痛、头晕等。");
        Response response = createMockResponse(200, apiResponse);
        when(httpCall.execute()).thenReturn(response);

        // 执行
        String answer = llmService.generateAnswer(query, context);

        // 验证
        assertNotNull(answer);
        verify(redisService, never()).get(anyString());
        verify(redisService, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    void testGenerateAnswerAsync_Success() throws ExecutionException, InterruptedException {
        // 准备
        String query = "高血压有什么症状";
        List<String> context = Arrays.asList("高血压是常见的心血管疾病", "症状包括头痛、头晕等");

        when(redisService.get(anyString())).thenReturn(null);
        when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);

        try {
            String apiResponse = createMockApiResponse("高血压症状包括头痛、头晕等。");
            Response response = createMockResponse(200, apiResponse);
            when(httpCall.execute()).thenReturn(response);
        } catch (IOException e) {
            // 测试不会执行到这里
        }

        // 执行
        CompletableFuture<String> future = llmService.generateAnswerAsync(query, context);
        String answer = future.get(); // 等待异步完成

        // 验证
        assertNotNull(answer);
        assertTrue(answer.contains("高血压"));
    }

    @Test
    void testGenerateAnswersBatch() {
        // 准备
        List<String> queries = Arrays.asList("高血压症状", "糖尿病治疗");
        List<List<String>> contextsList = Arrays.asList(
                Arrays.asList("高血压是心血管疾病"),
                Arrays.asList("糖尿病是代谢性疾病")
        );

        // 计算缓存键
        String cacheKey1 = generateCacheKey(queries.get(0), contextsList.get(0));
        String cacheKey2 = generateCacheKey(queries.get(1), contextsList.get(1));

        // 模拟两次调用，第一次缓存命中，第二次缓存未命中
        when(redisService.get(cacheKey1)).thenReturn("高血压症状包括头痛、头晕等。");
        when(redisService.get(cacheKey2)).thenReturn(null);

        try {
            when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);
            String apiResponse = createMockApiResponse("糖尿病治疗包括饮食控制和药物治疗。");
            Response response = createMockResponse(200, apiResponse);
            when(httpCall.execute()).thenReturn(response);
        } catch (IOException e) {
            // 测试不会执行到这里
        }

        // 执行
        List<String> answers = llmService.generateAnswersBatch(queries, contextsList);

        // 验证
        assertNotNull(answers);
        assertEquals(2, answers.size());
        assertTrue(answers.get(0).contains("高血压"));
        assertTrue(answers.get(1).contains("糖尿病"));
    }

    @Test
    void testGenerateAnswersBatch_EmptyLists() {
        // 准备
        List<String> queries = Collections.emptyList();
        List<List<String>> contextsList = Collections.emptyList();

        // 执行
        List<String> answers = llmService.generateAnswersBatch(queries, contextsList);

        // 验证
        assertNotNull(answers);
        assertTrue(answers.isEmpty());
    }

    @Test
    void testGenerateAnswerWithCitations() {
        // 准备
        String query = "高血压有什么症状";
        List<LlmService.ContextWithSource> contexts = Arrays.asList(
                new LlmService.ContextWithSource("高血压症状包括头痛、头晕、心悸等。", "文档1", 0.9),
                new LlmService.ContextWithSource("高血压患者需要定期监测血压。", "文档2", 0.8)
        );

        // 模拟基础生成回答
        when(redisService.get(anyString())).thenReturn(null);
        try {
            when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);
            String apiResponse = createMockApiResponse("高血压症状包括头痛、头晕等。需要定期监测血压。");
            Response response = createMockResponse(200, apiResponse);
            when(httpCall.execute()).thenReturn(response);
        } catch (IOException e) {
            // 测试不会执行到这里
        }

        // 执行
        LlmService.AnswerWithCitations result = llmService.generateAnswerWithCitations(query, contexts);

        // 验证
        assertNotNull(result);
        assertNotNull(result.getAnswer());
        assertNotNull(result.getCitations());
        // 至少应该有一个引用（如果回答中包含上下文片段）
        assertTrue(result.getAnswer().contains("高血压"));
    }

    @Test
    void testGetModelInfo() {
        // 执行
        LlmService.ModelInfo modelInfo = llmService.getModelInfo();

        // 验证
        assertNotNull(modelInfo);
        assertEquals("qwen-plus", modelInfo.getName());
        assertEquals("阿里云百炼", modelInfo.getProvider());
        assertEquals(2000, modelInfo.getMaxTokens());
        assertTrue(modelInfo.isSupportsStreaming());
    }

    @Test
    void testIsAvailable() {
        // 执行
        boolean available = llmService.isAvailable();

        // 验证 - HTTP客户端已初始化
        assertTrue(available);
    }

    @Test
    void testGetStats_Initial() {
        // 执行
        LlmService.LlmStats stats = llmService.getStats();

        // 验证
        assertNotNull(stats);
        assertEquals(0, stats.getTotalCalls());
        assertEquals(0, stats.getSuccessfulCalls());
        assertEquals(0, stats.getFailedCalls());
        assertEquals(0.0, stats.getAverageResponseTime());
        assertEquals(0.0, stats.getSuccessRate());
        assertEquals(0, stats.getTotalTokensGenerated());
        assertEquals(0, stats.getTotalTokensConsumed());
    }

    @Test
    void testGetStats_AfterCalls() throws IOException {
        // 准备 - 执行一些调用
        String query = "测试";
        List<String> context = Collections.emptyList();

        when(redisService.get(anyString())).thenReturn(null);
        when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);

        String apiResponse = createMockApiResponseWithTokens("测试回答", 50, 100);
        Response response = createMockResponse(200, apiResponse);
        when(httpCall.execute()).thenReturn(response);

        llmService.generateAnswer(query, context);

        // 执行
        LlmService.LlmStats stats = llmService.getStats();

        // 验证
        assertNotNull(stats);
        assertEquals(1, stats.getTotalCalls());
        assertEquals(1, stats.getSuccessfulCalls());
        assertEquals(0, stats.getFailedCalls());
        assertTrue(stats.getAverageResponseTime() >= 0);
        assertEquals(1.0, stats.getSuccessRate());
        assertEquals(50, stats.getTotalTokensGenerated());
        assertEquals(100, stats.getTotalTokensConsumed());
    }

    @Test
    void testGenerateAnswer_EmptyContext() throws IOException {
        // 准备
        String query = "高血压有什么症状";
        List<String> context = Collections.emptyList();

        when(redisService.get(anyString())).thenReturn(null);
        when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);

        String apiResponse = createMockApiResponse("高血压症状包括头痛、头晕等。");
        Response response = createMockResponse(200, apiResponse);
        when(httpCall.execute()).thenReturn(response);

        // 执行
        String answer = llmService.generateAnswer(query, context);

        // 验证
        assertNotNull(answer);
        assertTrue(answer.contains("高血压"));
    }

    @Test
    void testGenerateAnswer_NullContext() throws IOException {
        // 准备
        String query = "高血压有什么症状";

        when(redisService.get(anyString())).thenReturn(null);
        when(httpClient.newCall(any(Request.class))).thenReturn(httpCall);

        String apiResponse = createMockApiResponse("高血压症状包括头痛、头晕等。");
        Response response = createMockResponse(200, apiResponse);
        when(httpCall.execute()).thenReturn(response);

        // 执行
        String answer = llmService.generateAnswer(query, null);

        // 验证
        assertNotNull(answer);
        assertTrue(answer.contains("高血压"));
    }

    /**
     * 创建模拟的API响应
     */
    private String createMockApiResponse(String answer) {
        return String.format("{\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"message\": {\n" +
                "        \"content\": \"%s\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"completion_tokens\": 50,\n" +
                "    \"prompt_tokens\": 100,\n" +
                "    \"total_tokens\": 150\n" +
                "  }\n" +
                "}", answer);
    }

    /**
     * 创建带token统计的模拟API响应
     */
    private String createMockApiResponseWithTokens(String answer, int completionTokens, int promptTokens) {
        return String.format("{\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"message\": {\n" +
                "        \"content\": \"%s\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"completion_tokens\": %d,\n" +
                "    \"prompt_tokens\": %d,\n" +
                "    \"total_tokens\": %d\n" +
                "  }\n" +
                "}", answer, completionTokens, promptTokens, completionTokens + promptTokens);
    }

    /**
     * 生成缓存键（复制LlmServiceImpl中的逻辑）
     */
    private String generateCacheKey(String query, List<String> context) {
        StringBuilder content = new StringBuilder(query);
        if (context != null) {
            for (String ctx : context) {
                content.append(ctx);
            }
        }
        String hash = org.springframework.util.DigestUtils.md5DigestAsHex(content.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return String.format("llm:%s:%s", llmConfig.getModel(), hash);
    }

    /**
     * 创建模拟的HTTP响应
     */
    private Response createMockResponse(int code, String body) {
        Response response = mock(Response.class);
        ResponseBody responseBody = ResponseBody.create(body, MediaType.parse("application/json"));

        when(response.code()).thenReturn(code);
        when(response.isSuccessful()).thenReturn(code >= 200 && code < 300);
        when(response.body()).thenReturn(responseBody);

        return response;
    }
}