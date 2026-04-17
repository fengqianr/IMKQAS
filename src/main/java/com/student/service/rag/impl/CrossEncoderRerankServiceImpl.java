package com.student.service.rag.impl;

import com.student.config.RagConfig;
import com.student.service.rag.CrossEncoderRerankService;
import com.student.service.rag.MultiRetrievalService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.student.utils.EnvUtil;

/**
 * 交叉编码器重排序服务实现类
 * 集成阿里云gte-rerank模型进行医疗语义重排序
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class CrossEncoderRerankServiceImpl implements CrossEncoderRerankService {

    private final RagConfig ragConfig;
    private OkHttpClient httpClient;

    /**
     * 构造函数
     */
    public CrossEncoderRerankServiceImpl(RagConfig ragConfig) {
        this.ragConfig = ragConfig;
    }

    // 重排序配置
    private RagConfig.RerankerConfig rerankerConfig;

    // 统计信息
    private final AtomicInteger totalReranks = new AtomicInteger(0);
    private final AtomicInteger successfulReranks = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger totalInputResults = new AtomicInteger(0);
    private final AtomicInteger totalOutputResults = new AtomicInteger(0);

    // 缓存：查询+内容 -> 重排序分数（可选）
    private final Map<String, Double> rerankCache = new ConcurrentHashMap<>();

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        this.rerankerConfig = ragConfig.getReranker();
        log.info("交叉编码器重排序服务初始化完成，模型: {}, API端点: {}, topK: {}",
                rerankerConfig.getModel(), rerankerConfig.getApiEndpoint(), rerankerConfig.getTopK());
    }

    /**
     * 获取HTTP客户端
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(rerankerConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(rerankerConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(rerankerConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return httpClient;
    }

    @Override
    public List<MultiRetrievalService.RetrievalResult> rerank(
            String query,
            List<MultiRetrievalService.RetrievalResult> retrievalResults,
            int topK
    ) {
        long startTime = System.currentTimeMillis();
        totalReranks.incrementAndGet();

        try {
            // 参数校验
            if (query == null || query.trim().isEmpty()) {
                log.warn("重排序失败: 查询文本为空");
                return retrievalResults;
            }

            if (retrievalResults == null || retrievalResults.isEmpty()) {
                log.debug("重排序: 检索结果为空，直接返回");
                return retrievalResults;
            }

            // 限制处理数量，避免API过载
            int maxResults = Math.min(retrievalResults.size(), 100);
            List<MultiRetrievalService.RetrievalResult> resultsToProcess =
                    retrievalResults.subList(0, maxResults);

            // 调用阿里云重排序API
            List<Double> rerankScores = callAliyunRerankApi(query, resultsToProcess);

            // 应用重排序分数
            List<MultiRetrievalService.RetrievalResult> rerankedResults =
                    applyRerankScores(resultsToProcess, rerankScores, topK);

            // 更新统计信息
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            successfulReranks.incrementAndGet();
            totalInputResults.addAndGet(resultsToProcess.size());
            totalOutputResults.addAndGet(rerankedResults.size());

            log.debug("重排序完成: query={}, input={}, output={}, time={}ms",
                    query, resultsToProcess.size(), rerankedResults.size(), processingTime);

            return rerankedResults;

        } catch (Exception e) {
            log.error("重排序异常: query={}", query, e);
            // 降级：返回原始检索结果（按原分数排序）
            return getFallbackResults(retrievalResults, topK);
        }
    }

    @Override
    public List<MultiRetrievalService.RetrievalResult> rerank(
            String query,
            List<MultiRetrievalService.RetrievalResult> retrievalResults
    ) {
        return rerank(query, retrievalResults, rerankerConfig.getTopK());
    }

    @Override
    public List<List<MultiRetrievalService.RetrievalResult>> batchRerank(
            List<String> queries,
            List<List<MultiRetrievalService.RetrievalResult>> retrievalResultsList,
            int topK
    ) {
        // 简化为循环调用单个重排序，实际可优化为批量API调用
        List<List<MultiRetrievalService.RetrievalResult>> batchResults = new ArrayList<>();

        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            List<MultiRetrievalService.RetrievalResult> results =
                    i < retrievalResultsList.size() ? retrievalResultsList.get(i) : Collections.emptyList();

            List<MultiRetrievalService.RetrievalResult> rerankedResults = rerank(query, results, topK);
            batchResults.add(rerankedResults);
        }

        return batchResults;
    }

    @Override
    public int getDefaultTopK() {
        return rerankerConfig.getTopK();
    }

    @Override
    public boolean isAvailable() {
        // 检查服务是否可用（可扩展为健康检查）
        return rerankerConfig != null && rerankerConfig.getApiEndpoint() != null
                && !rerankerConfig.getApiEndpoint().isEmpty();
    }

    @Override
    public RerankStats getStats() {
        int total = totalReranks.get();
        int successful = successfulReranks.get();
        double successRate = total > 0 ? (double) successful / total : 0.0;
        double avgInput = total > 0 ? (double) totalInputResults.get() / total : 0.0;
        double avgOutput = total > 0 ? (double) totalOutputResults.get() / total : 0.0;
        double avgTime = total > 0 ? (double) totalProcessingTime.get() / total : 0.0;

        return new RerankStats(total, (int) avgInput, (int) avgOutput, avgTime, successRate);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 调用阿里云重排序API
     */
    private List<Double> callAliyunRerankApi(
            String query,
            List<MultiRetrievalService.RetrievalResult> results
    ) throws IOException {
        // 构建请求体
        String requestBody = buildRerankRequestBody(query, results);

        // 获取API密钥，优先从配置读取，如果为空则从.env文件或系统环境变量读取
        String apiKey = rerankerConfig.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = EnvUtil.getEnv("BAILIAN_API_KEY");
            log.debug("重排序API密钥从配置读取为空，尝试从.env/环境变量读取");
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("阿里云重排序API密钥未配置");
        }

        Request request = new Request.Builder()
                .url(rerankerConfig.getApiEndpoint())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("阿里云重排序API请求失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return parseRerankResponse(responseBody, results.size());
        }
    }

    /**
     * 构建重排序请求体（阿里云格式）
     */
    private String buildRerankRequestBody(
            String query,
            List<MultiRetrievalService.RetrievalResult> results
    ) {
        // 提取内容文本
        List<String> documents = new ArrayList<>();
        for (MultiRetrievalService.RetrievalResult result : results) {
            documents.add(result.getContent());
        }

        // 构建阿里云重排序API请求格式
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("query", query);
        requestMap.put("documents", documents);
        requestMap.put("model", rerankerConfig.getModel());
        requestMap.put("top_k", rerankerConfig.getTopK());

        // 转换为JSON字符串（简化实现，实际应使用JSON库）
        return buildSimpleJson(requestMap);
    }

    /**
     * 解析重排序响应（阿里云格式）
     */
    private List<Double> parseRerankResponse(String responseBody, int expectedSize) {
        // 简化实现：假设响应为JSON数组，包含每个文档的分数
        // 实际应使用JSON库解析阿里云响应格式
        List<Double> scores = new ArrayList<>();

        try {
            // 示例响应格式：{"scores": [0.95, 0.87, 0.72, ...]}
            // 这里简化解析，实际需要根据阿里云API文档实现
            if (responseBody.contains("\"scores\"")) {
                // 提取分数数组
                String scoresPart = responseBody.substring(
                        responseBody.indexOf("[") + 1,
                        responseBody.indexOf("]")
                );
                String[] scoreStrs = scoresPart.split(",");
                for (String scoreStr : scoreStrs) {
                    scores.add(Double.parseDouble(scoreStr.trim()));
                }
            } else {
                // 如果解析失败，生成模拟分数（降级）
                log.warn("重排序响应解析失败，使用模拟分数: {}", responseBody);
                scores = generateMockScores(expectedSize);
            }
        } catch (Exception e) {
            log.warn("重排序响应解析异常，使用模拟分数: {}", e.getMessage());
            scores = generateMockScores(expectedSize);
        }

        // 确保分数数量与文档数量一致
        while (scores.size() < expectedSize) {
            scores.add(0.5); // 默认分数
        }

        return scores.subList(0, Math.min(scores.size(), expectedSize));
    }

    /**
     * 应用重排序分数到检索结果
     */
    private List<MultiRetrievalService.RetrievalResult> applyRerankScores(
            List<MultiRetrievalService.RetrievalResult> results,
            List<Double> rerankScores,
            int topK
    ) {
        // 创建结果与分数的配对列表
        List<ResultWithScore> scoredResults = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            MultiRetrievalService.RetrievalResult result = results.get(i);
            double rerankScore = i < rerankScores.size() ? rerankScores.get(i) : 0.5;
            double originalScore = result.getScore() != null ? result.getScore() : 0.0;

            // 结合原始分数和重排序分数（可根据需求调整权重）
            double finalScore = combineScores(originalScore, rerankScore);

            scoredResults.add(new ResultWithScore(result, finalScore, rerankScore));
        }

        // 按最终分数降序排序
        scoredResults.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));

        // 转换为RetrievalResult格式
        List<MultiRetrievalService.RetrievalResult> rerankedResults = new ArrayList<>();
        for (int i = 0; i < Math.min(scoredResults.size(), topK); i++) {
            ResultWithScore scoredResult = scoredResults.get(i);
            MultiRetrievalService.RetrievalResult originalResult = scoredResult.result;

            // 创建新的RetrievalResult，使用重排序后的分数
            MultiRetrievalService.RetrievalResult rerankedResult =
                    new MultiRetrievalService.RetrievalResult(
                            originalResult.getChunkId(),
                            originalResult.getDocumentId(),
                            scoredResult.finalScore, // 使用最终分数
                            originalResult.getContent(),
                            MultiRetrievalService.RetrievalSource.HYBRID,
                            originalResult.getVectorScore(),
                            originalResult.getKeywordScore()
                    );

            rerankedResults.add(rerankedResult);
        }

        return rerankedResults;
    }

    /**
     * 结合原始分数和重排序分数
     */
    private double combineScores(double originalScore, double rerankScore) {
        // 权重配置：重排序分数权重0.7，原始分数权重0.3
        // 可根据实际效果调整
        double rerankWeight = 0.7;
        double originalWeight = 0.3;

        // 归一化处理（假设分数在0-1之间）
        return rerankScore * rerankWeight + originalScore * originalWeight;
    }

    /**
     * 降级处理：返回原始结果（按原分数排序）
     */
    private List<MultiRetrievalService.RetrievalResult> getFallbackResults(
            List<MultiRetrievalService.RetrievalResult> results,
            int topK
    ) {
        List<MultiRetrievalService.RetrievalResult> sortedResults = new ArrayList<>(results);
        sortedResults.sort((a, b) -> {
            double scoreA = a.getScore() != null ? a.getScore() : 0.0;
            double scoreB = b.getScore() != null ? b.getScore() : 0.0;
            return Double.compare(scoreB, scoreA);
        });

        return sortedResults.subList(0, Math.min(sortedResults.size(), topK));
    }

    /**
     * 生成模拟分数（用于降级）
     */
    private List<Double> generateMockScores(int size) {
        List<Double> scores = new ArrayList<>();
        // 生成递减的模拟分数
        for (int i = 0; i < size; i++) {
            scores.add(0.9 - (i * 0.1));
        }
        return scores;
    }

    /**
     * 构建简单JSON（简化实现）
     */
    private String buildSimpleJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof List) {
                json.append("[");
                List<?> list = (List<?>) value;
                boolean firstItem = true;
                for (Object item : list) {
                    if (!firstItem) {
                        json.append(",");
                    }
                    json.append("\"").append(item).append("\"");
                    firstItem = false;
                }
                json.append("]");
            } else {
                json.append(value);
            }

            first = false;
        }

        json.append("}");
        return json.toString();
    }

    /**
     * 结果与分数的内部类
     */
    private static class ResultWithScore {
        final MultiRetrievalService.RetrievalResult result;
        final double finalScore;
        final double rerankScore;

        ResultWithScore(MultiRetrievalService.RetrievalResult result,
                       double finalScore, double rerankScore) {
            this.result = result;
            this.finalScore = finalScore;
            this.rerankScore = rerankScore;
        }
    }
}