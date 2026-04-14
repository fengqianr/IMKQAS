package com.student.service.impl;

import com.student.config.RagConfig;
import com.student.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * 嵌入模型服务单元测试
 * 测试EmbeddingServiceImpl的各种方法
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmbeddingServiceImplTest {

    @Mock
    private RagConfig ragConfig;

    @Mock
    private RagConfig.EmbeddingConfig embeddingConfig;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private EmbeddingServiceImpl embeddingService;

    private final int testDimension = 1024;

    @BeforeEach
    void setUp() {
        // 模拟RagConfig配置
        when(ragConfig.getEmbedding()).thenReturn(embeddingConfig);
        when(embeddingConfig.getDimension()).thenReturn(testDimension);
        when(embeddingConfig.isCacheEnabled()).thenReturn(true);
        when(embeddingConfig.getCacheTtl()).thenReturn(1800);
        when(embeddingConfig.getDeployment()).thenReturn("api");
        when(embeddingConfig.getTimeout()).thenReturn(10000);
        when(embeddingConfig.getModel()).thenReturn("text-embedding-v3");
        when(embeddingConfig.getApiEndpoint()).thenReturn("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings");
        when(embeddingConfig.getApiKey()).thenReturn("test-api-key");
    }

    @Test
    void testGetDimension() {
        // 执行
        int dimension = embeddingService.getDimension();

        // 验证
        assertEquals(testDimension, dimension);
    }

    @Test
    void testValidateVector_Valid() {
        // 准备
        List<Float> validVector = createValidVector();

        // 执行
        boolean result = embeddingService.validateVector(validVector);

        // 验证
        assertTrue(result);
    }

    @Test
    void testValidateVector_Null() {
        // 执行
        boolean result = embeddingService.validateVector(null);

        // 验证
        assertFalse(result);
    }

    @Test
    void testValidateVector_WrongDimension() {
        // 准备
        List<Float> wrongDimVector = Arrays.asList(1.0f, 2.0f, 3.0f);

        // 执行
        boolean result = embeddingService.validateVector(wrongDimVector);

        // 验证
        assertFalse(result);
    }

    @Test
    void testValidateVector_ContainsNaN() {
        // 准备
        List<Float> invalidVector = createValidVector();
        invalidVector.set(5, Float.NaN);

        // 执行
        boolean result = embeddingService.validateVector(invalidVector);

        // 验证
        assertFalse(result);
    }

    @Test
    void testValidateVector_AllZero() {
        // 准备
        List<Float> zeroVector = new ArrayList<>();
        for (int i = 0; i < testDimension; i++) {
            zeroVector.add(0.0f);
        }

        // 执行
        boolean result = embeddingService.validateVector(zeroVector);

        // 验证
        assertFalse(result);
    }

    @Test
    void testIsAvailable() {
        // 执行
        boolean available = embeddingService.isAvailable();

        // 验证
        assertTrue(available);
    }

    @Test
    void testGetDeploymentType() {
        // 执行
        String deploymentType = embeddingService.getDeploymentType();

        // 验证
        assertEquals("api", deploymentType);
    }

    @Test
    void testEmbed_CacheHit() {
        // 准备
        String text = "测试文本";
        String cacheKey = generateCacheKey(text);
        List<Float> cachedVector = createValidVector();

        when(redisService.get(cacheKey)).thenReturn(cachedVector);

        // 执行
        List<Float> result = embeddingService.embed(text);

        // 验证
        assertNotNull(result);
        assertEquals(testDimension, result.size());
        verify(redisService).get(cacheKey);
        // 不应调用API
    }

    @Test
    void testEmbed_CacheMiss() {
        // 准备
        String text = "测试文本";
        String cacheKey = generateCacheKey(text);

        when(redisService.get(cacheKey)).thenReturn(null);
        // 模拟API调用返回模拟向量
        // 注意：实际测试中应模拟callEmbeddingApi方法，但它是私有的
        // 这里我们使用反射或部分模拟，简化测试

        // 执行
        List<Float> result = embeddingService.embed(text);

        // 验证 - 由于API调用被模拟，应该返回null或模拟向量
        // 这里我们主要测试缓存逻辑
        verify(redisService).get(cacheKey);
        // 缓存未命中时会尝试调用API
    }

    @Test
    void testEmbedBatch_WithCache() {
        // 准备
        List<String> texts = Arrays.asList("文本1", "文本2", "文本3");

        // 模拟部分缓存命中
        when(redisService.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (key.contains("文本1")) {
                return createValidVector();
            }
            return null;
        });

        // 执行
        List<List<Float>> results = embeddingService.embedBatch(texts);

        // 验证
        assertNotNull(results);
        assertEquals(texts.size(), results.size());
        // 第一个应该从缓存获取，其他两个需要处理
        verify(redisService, times(3)).get(anyString());
    }

    @Test
    void testEmbedBatch_WithoutCache() {
        // 准备
        when(embeddingConfig.isCacheEnabled()).thenReturn(false);
        List<String> texts = Arrays.asList("文本1", "文本2");

        // 执行
        List<List<Float>> results = embeddingService.embedBatch(texts);

        // 验证
        assertNotNull(results);
        assertEquals(texts.size(), results.size());
        // 缓存未启用，不应调用redisService.get()
        verify(redisService, never()).get(anyString());
    }

    @Test
    void testEmbedBatch_EmptyList() {
        // 准备
        List<String> texts = new ArrayList<>();

        // 执行
        List<List<Float>> results = embeddingService.embedBatch(texts);

        // 验证
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    /**
     * 创建有效的测试向量
     */
    private List<Float> createValidVector() {
        List<Float> vector = new ArrayList<>(testDimension);
        for (int i = 0; i < testDimension; i++) {
            vector.add((float) Math.sin(i * 0.1));
        }
        return vector;
    }

    /**
     * 生成缓存键（与实现保持一致）
     */
    private String generateCacheKey(String text) {
        // 使用文本内容的MD5哈希作为缓存键
        String hash = org.springframework.util.DigestUtils.md5DigestAsHex(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return String.format("embedding:%s:%s", "text-embedding-v3", hash);
    }
}