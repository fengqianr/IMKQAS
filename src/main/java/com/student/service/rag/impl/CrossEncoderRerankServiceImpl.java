package com.student.service.rag.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        log.info("交叉编码器重排序服务初始化完成，模型: {}, API端点: {}, topK: {}, 完整URL长度: {}",
                rerankerConfig.getModel(),
                rerankerConfig.getApiEndpoint(),
                rerankerConfig.getTopK(),
                rerankerConfig.getApiEndpoint() != null ? rerankerConfig.getApiEndpoint().length() : 0);

        // 调试：打印完整URL的前100个字符
        if (rerankerConfig.getApiEndpoint() != null && rerankerConfig.getApiEndpoint().length() > 0) {
            log.debug("API端点完整URL: {}", rerankerConfig.getApiEndpoint());
        }
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
                return retrievalResults == null ? new ArrayList<>() : retrievalResults;
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

        // 调试：记录请求体（前500字符，避免敏感信息）
        String requestBodyPreview = requestBody.length() > 500 ? requestBody.substring(0, 500) + "..." : requestBody;
        log.debug("重排序API请求体: {}", requestBodyPreview);

        // 获取API密钥，优先从配置读取，如果为空则从.env文件或系统环境变量读取
        String apiKey = rerankerConfig.getApiKey();
        String keySource = "配置";

        if (apiKey == null || apiKey.trim().isEmpty()) {
            // 优先尝试DASHSCOPE_API_KEY（阿里云官方环境变量名）
            apiKey = EnvUtil.getEnv("DASHSCOPE_API_KEY");
            keySource = "环境变量(DASHSCOPE_API_KEY)";

            if (apiKey == null || apiKey.trim().isEmpty()) {
                // 回退到BAILIAN_API_KEY（向后兼容）
                apiKey = EnvUtil.getEnv("BAILIAN_API_KEY");
                keySource = "环境变量(BAILIAN_API_KEY)";
                log.debug("重排序API密钥从DASHSCOPE_API_KEY读取为空，尝试BAILIAN_API_KEY");
            } else {
                log.debug("重排序API密钥从DASHSCOPE_API_KEY环境变量读取");
            }
        }

        // 调试：记录API密钥来源和部分内容（安全处理）
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            String maskedKey = maskApiKey(apiKey);
            log.debug("重排序API密钥来源: {}, 密钥: {}", keySource, maskedKey);
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
            log.debug("重排序API响应状态: {} {}", response.code(), response.message());

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("重排序API请求失败: {} {}, 响应体: {}", response.code(), response.message(), errorBody);
                throw new IOException("阿里云重排序API请求失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            log.debug("重排序API响应体: {}", responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
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

        // 获取模型名称
        String modelName = rerankerConfig.getModel();

        // 构建阿里云重排序API请求格式
        // 根据阿里云文档，gte-rerank-v2需要使用嵌套格式
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", modelName);

        if ("gte-rerank-v2".equals(modelName)) {
            // 嵌套格式：gte-rerank-v2模型需要input对象
            Map<String, Object> inputMap = new HashMap<>();
            inputMap.put("query", query);
            inputMap.put("documents", documents);
            requestMap.put("input", inputMap);

            Map<String, Object> parametersMap = new HashMap<>();
            parametersMap.put("return_documents", true);
            parametersMap.put("top_n", rerankerConfig.getTopK());
            requestMap.put("parameters", parametersMap);
        } else {
            // 默认扁平格式（兼容qwen3-rerank等模型）
            requestMap.put("query", query);
            requestMap.put("documents", documents);
            requestMap.put("top_n", rerankerConfig.getTopK());

            // 为qwen3-rerank模型添加instruct参数
            if (modelName.contains("qwen3")) {
                requestMap.put("instruct", "Given a web search query, retrieve relevant passages that answer the query.");
            }
        }

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
            // 尝试解析嵌套格式（gte-rerank-v2）
            if (responseBody.contains("\"results\"") && responseBody.contains("\"relevance_score\"")) {
                // 解析嵌套格式：{"output": {"results": [{"index": 0, "relevance_score": 0.933, ...}]}}
                parseNestedResponse(responseBody, scores, expectedSize);
            }
            // 尝试解析扁平格式（旧格式或兼容格式）
            else if (responseBody.contains("\"scores\"")) {
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
     * 解析嵌套格式的重排序响应（gte-rerank-v2格式）
     */
    private void parseNestedResponse(String responseBody, List<Double> scores, int expectedSize) {
        try {
            // 查找"results"数组
            int resultsStart = responseBody.indexOf("\"results\":");
            if (resultsStart == -1) return;

            int bracketStart = responseBody.indexOf("[", resultsStart);
            if (bracketStart == -1) return;

            int bracketEnd = findMatchingBracket(responseBody, bracketStart);
            if (bracketEnd == -1) return;

            String resultsArray = responseBody.substring(bracketStart, bracketEnd + 1);

            // 按顺序解析每个结果对象
            int currentPos = 0;
            while (true) {
                // 查找下一个"index"字段
                int indexStart = resultsArray.indexOf("\"index\":", currentPos);
                if (indexStart == -1) break;

                // 查找下一个"relevance_score"字段
                int scoreStart = resultsArray.indexOf("\"relevance_score\":", currentPos);
                if (scoreStart == -1) break;

                // 提取索引值
                int indexValueStart = resultsArray.indexOf(":", indexStart) + 1;
                int indexValueEnd = resultsArray.indexOf(",", indexValueStart);
                if (indexValueEnd == -1) indexValueEnd = resultsArray.indexOf("}", indexValueStart);
                if (indexValueEnd == -1) break;

                String indexStr = resultsArray.substring(indexValueStart, indexValueEnd).trim();
                int index = Integer.parseInt(indexStr);

                // 提取分数值
                int scoreValueStart = resultsArray.indexOf(":", scoreStart) + 1;
                int scoreValueEnd = resultsArray.indexOf(",", scoreValueStart);
                if (scoreValueEnd == -1) scoreValueEnd = resultsArray.indexOf("}", scoreValueStart);
                if (scoreValueEnd == -1) break;

                String scoreStr = resultsArray.substring(scoreValueStart, scoreValueEnd).trim();
                // 去除末尾的 }（当逗号位于对象之间时，substring 会包含前一个对象的 }）
                while (scoreStr.endsWith("}")) {
                    scoreStr = scoreStr.substring(0, scoreStr.length() - 1).trim();
                }
                double score = Double.parseDouble(scoreStr);

                // 确保scores列表有足够大小
                while (scores.size() <= index) {
                    scores.add(0.5); // 默认分数
                }

                // 根据索引位置设置分数
                scores.set(index, score);

                // 移动到下一个对象
                currentPos = Math.max(indexValueEnd, scoreValueEnd) + 1;
                if (currentPos >= resultsArray.length()) break;
            }

            log.debug("成功解析嵌套格式重排序响应，处理了{}个分数", scores.size());

        } catch (Exception e) {
            log.warn("解析嵌套格式响应异常: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 查找匹配的方括号位置
     */
    private int findMatchingBracket(String str, int startIndex) {
        int count = 1;
        for (int i = startIndex + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '[') count++;
            else if (c == ']') count--;

            if (count == 0) return i;
        }
        return -1; // 未找到匹配的方括号
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
     * 构建JSON（使用Jackson ObjectMapper）
     */
    private String buildSimpleJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("构建JSON异常，使用简化实现", e);
            // 降级：使用原始简化实现
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
    }

    /**
     * 安全屏蔽API密钥显示
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        // 显示前4位和后4位，中间用****代替
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
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