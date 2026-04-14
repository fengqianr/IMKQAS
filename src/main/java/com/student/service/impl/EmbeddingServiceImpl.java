package com.student.service.impl;

import com.student.config.RagConfig;
import com.student.service.EmbeddingService;
import com.student.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.student.utils.EnvUtil;

/**
 * 嵌入模型服务实现类
 * 支持远程API调用（如ModelScope）和本地模型推理（未来扩展）
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private final RagConfig ragConfig;
    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient httpClient;

    /**
     * 初始化HTTP客户端
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            RagConfig.EmbeddingConfig config = ragConfig.getEmbedding();
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return httpClient;
    }

    @Override
    public List<Float> embed(String text) {
        // 检查缓存
        if (ragConfig.getEmbedding().isCacheEnabled()) {
            String cacheKey = generateCacheKey(text);
            Object cached = redisService.get(cacheKey);
            if (cached instanceof List) {
                log.debug("嵌入缓存命中: text={}, cacheKey={}", text.substring(0, Math.min(text.length(), 50)), cacheKey);
                return (List<Float>) cached;
            }
        }

        // 调用API
        List<Float> embedding = callEmbeddingApi(text);

        // 存入缓存
        if (ragConfig.getEmbedding().isCacheEnabled() && embedding != null) {
            String cacheKey = generateCacheKey(text);
            redisService.set(cacheKey, embedding, (long) ragConfig.getEmbedding().getCacheTtl());
            log.debug("嵌入缓存设置: text={}, cacheKey={}", text.substring(0, Math.min(text.length(), 50)), cacheKey);
        }

        return embedding;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        // 批量处理：先检查缓存，对未命中缓存的文本调用API
        List<List<Float>> results = new ArrayList<>();
        List<String> textsToProcess = new ArrayList<>();
        List<Integer> indicesToProcess = new ArrayList<>();

        if (ragConfig.getEmbedding().isCacheEnabled()) {
            for (int i = 0; i < texts.size(); i++) {
                String text = texts.get(i);
                String cacheKey = generateCacheKey(text);
                Object cached = redisService.get(cacheKey);
                if (cached instanceof List) {
                    results.add((List<Float>) cached);
                    log.debug("批量嵌入缓存命中: index={}, text={}", i, text.substring(0, Math.min(text.length(), 50)));
                } else {
                    results.add(null); // 占位符
                    textsToProcess.add(text);
                    indicesToProcess.add(i);
                }
            }
        } else {
            // 未启用缓存，全部处理
            for (int i = 0; i < texts.size(); i++) {
                textsToProcess.add(texts.get(i));
                indicesToProcess.add(i);
                results.add(null); // 占位符
            }
        }

        // 批量处理未命中缓存的文本
        if (!textsToProcess.isEmpty()) {
            List<List<Float>> processedEmbeddings = callBatchEmbeddingApi(textsToProcess);

            // 将结果填充到正确位置并缓存
            for (int i = 0; i < processedEmbeddings.size(); i++) {
                List<Float> embedding = processedEmbeddings.get(i);
                int originalIndex = indicesToProcess.get(i);
                String originalText = textsToProcess.get(i);

                results.set(originalIndex, embedding);

                // 缓存结果
                if (ragConfig.getEmbedding().isCacheEnabled() && embedding != null) {
                    String cacheKey = generateCacheKey(originalText);
                    redisService.set(cacheKey, embedding, (long) ragConfig.getEmbedding().getCacheTtl());
                    log.debug("批量嵌入缓存设置: index={}, text={}", originalIndex,
                            originalText.substring(0, Math.min(originalText.length(), 50)));
                }
            }
        }

        return results;
    }

    @Override
    public int getDimension() {
        return ragConfig.getEmbedding().getDimension();
    }

    @Override
    public boolean validateVector(List<Float> vector) {
        if (vector == null) {
            return false;
        }
        int expectedDimension = getDimension();
        if (vector.size() != expectedDimension) {
            log.warn("向量维度验证失败: 期望维度={}, 实际维度={}", expectedDimension, vector.size());
            return false;
        }
        // 检查向量是否全零或包含NaN/Infinity
        boolean allZero = true;
        for (Float value : vector) {
            if (value == null || Float.isNaN(value) || Float.isInfinite(value)) {
                log.warn("向量包含无效值: {}", value);
                return false;
            }
            if (Math.abs(value) > 1e-6) {
                allZero = false;
            }
        }
        if (allZero) {
            log.warn("向量全为零，可能嵌入失败");
            return false;
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        // 简单检查：HTTP客户端是否可以创建
        try {
            getHttpClient();
            return true;
        } catch (Exception e) {
            log.error("嵌入服务不可用", e);
            return false;
        }
    }

    @Override
    public String getDeploymentType() {
        return ragConfig.getEmbedding().getDeployment();
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(String text) {
        // 使用文本内容的MD5哈希作为缓存键
        String hash = DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
        return String.format("embedding:%s:%s", ragConfig.getEmbedding().getModel(), hash);
    }

    /**
     * 调用远程嵌入API（单文本）
     */
    private List<Float> callEmbeddingApi(String text) {
        List<List<Float>> results = callEmbeddingApiBatch(List.of(text));
        if (results == null || results.isEmpty()) {
            log.warn("单文本嵌入API调用失败，返回模拟向量");
            return generateMockEmbedding();
        }
        return results.get(0);
    }

    /**
     * 调用远程嵌入API（批量文本）
     */
    private List<List<Float>> callBatchEmbeddingApi(List<String> texts) {
        List<List<Float>> results = callEmbeddingApiBatch(texts);
        if (results == null) {
            log.warn("批量嵌入API调用失败，返回模拟向量");
            results = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                results.add(generateMockEmbedding());
            }
        }
        return results;
    }

    /**
     * 批量调用远程嵌入API（核心实现）
     */
    private List<List<Float>> callEmbeddingApiBatch(List<String> texts) {
        RagConfig.EmbeddingConfig config = ragConfig.getEmbedding();
        String apiEndpoint = config.getApiEndpoint();
        String apiKey = config.getApiKey();

        // 如果配置中的API密钥为空，尝试从.env文件或系统环境变量读取
        if (apiKey == null || apiKey.trim().isEmpty()) {
            // 优先从.env文件读取，然后从系统环境变量读取
            apiKey = EnvUtil.getEnv("BAILIAN_API_KEY");
            log.debug("从配置读取API密钥为空，尝试从.env/环境变量读取: {}",
                    apiKey != null ? "已找到环境变量" : "环境变量未设置");
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("嵌入API密钥未配置，使用模拟向量");
            return null;
        }

        try {
            // 构建请求体
            String requestBody = buildEmbeddingRequest(texts);

            Request request = new Request.Builder()
                    .url(apiEndpoint)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            OkHttpClient client = getHttpClient();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("嵌入API调用失败: code={}, body={}", response.code(), response.body() != null ? response.body().string() : "null");
                    return null;
                }

                String responseBody = response.body().string();
                return parseEmbeddingResponse(responseBody, texts.size());
            }
        } catch (IOException e) {
            log.error("嵌入API调用异常", e);
            return null;
        } catch (Exception e) {
            log.error("嵌入API处理异常", e);
            return null;
        }
    }

    /**
     * 构建嵌入API请求体
     */
    private String buildEmbeddingRequest(List<String> texts) throws JsonProcessingException {
        // ModelScope API格式: {"input": ["text1", "text2"]}
        // 注意：不同API端点可能有不同格式，这里需要根据实际API调整
        java.util.Map<String, Object> requestMap = new java.util.HashMap<>();
        requestMap.put("input", texts);
        return objectMapper.writeValueAsString(requestMap);
    }

    /**
     * 解析嵌入API响应
     */
    private List<List<Float>> parseEmbeddingResponse(String responseBody, int expectedCount) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        // 尝试不同响应格式
        JsonNode embeddingsNode = null;
        if (root.has("embeddings")) {
            embeddingsNode = root.get("embeddings");
        } else if (root.has("data")) {
            // OpenAI兼容格式
            JsonNode dataNode = root.get("data");
            if (dataNode.isArray() && dataNode.size() > 0) {
                JsonNode first = dataNode.get(0);
                if (first.has("embedding")) {
                    // 单个嵌入对象
                    List<List<Float>> result = new ArrayList<>();
                    for (JsonNode item : dataNode) {
                        JsonNode embeddingNode = item.get("embedding");
                        result.add(parseFloatArray(embeddingNode));
                    }
                    return result;
                }
            }
            embeddingsNode = dataNode;
        } else if (root.has("output")) {
            // ModelScope可能格式
            embeddingsNode = root.get("output");
        }

        if (embeddingsNode == null || !embeddingsNode.isArray()) {
            log.error("无法解析嵌入API响应，未找到embeddings/data/output字段: {}", responseBody);
            return null;
        }

        List<List<Float>> embeddings = new ArrayList<>();
        for (JsonNode embeddingNode : embeddingsNode) {
            embeddings.add(parseFloatArray(embeddingNode));
        }

        if (embeddings.size() != expectedCount) {
            log.warn("嵌入返回数量不匹配: 期望={}, 实际={}", expectedCount, embeddings.size());
        }

        return embeddings;
    }

    /**
     * 解析浮点数数组
     */
    private List<Float> parseFloatArray(JsonNode arrayNode) {
        List<Float> floats = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode valueNode : arrayNode) {
                floats.add(valueNode.floatValue());
            }
        }
        return floats;
    }

    /**
     * 生成模拟嵌入向量（用于测试）
     */
    private List<Float> generateMockEmbedding() {
        int dimension = getDimension();
        List<Float> embedding = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            // 生成随机但确定性的向量（用于测试）
            embedding.add((float) Math.sin(i * 0.1));
        }
        return embedding;
    }
}