package com.student.service.rag.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.student.config.RagConfig;
import com.student.service.LlmService;
import com.student.service.RedisService;
import com.student.service.rag.QueryRewriteService;
import com.student.utils.EnvUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 大语言模型服务实现类（重构版）
 * 集成阿里云百炼（DashScope）API，支持医疗问答生成
 * 改进点：
 * 1. 多级缓存：本地Caffeine缓存 → Redis → LLM API
 * 2. 缓存键优化：查询归一化 + 上下文排序
 * 3. 资源管理：OkHttpClient连接池关闭
 * 4. 异步线程池：自定义线程池替代CompletableFuture默认池
 * 5. Prompt长度控制：超限截断最长上下文
 * 6. 降级增强：友好提示 + 异常记录
 * 7. 统计重置：支持resetStats()方法
 * 8. 错误处理：区分不同HTTP状态码
 *
 * @author 系统
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmServiceImpl implements LlmService {

    private final RagConfig ragConfig;
    private final RedisService redisService;
    private final QueryRewriteService queryRewriteService; // 新增：查询改写服务
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient httpClient;
    private ExecutorService asyncExecutor; // 自定义异步线程池

    // 本地缓存（Caffeine）
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .maximumSize(10000) // 最大缓存条目数
            .expireAfterWrite(10, TimeUnit.MINUTES) // 写入后10分钟过期
            .recordStats() // 记录统计信息
            .build();

    // 统计信息
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger successfulCalls = new AtomicInteger(0);
    private final AtomicInteger failedCalls = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicInteger totalTokensGenerated = new AtomicInteger(0);
    private final AtomicInteger totalTokensConsumed = new AtomicInteger(0);

    // 错误统计
    private final AtomicInteger authErrors = new AtomicInteger(0); // 401/403
    private final AtomicInteger rateLimitErrors = new AtomicInteger(0); // 429
    private final AtomicInteger serverErrors = new AtomicInteger(0); // 5xx
    private final AtomicInteger networkErrors = new AtomicInteger(0); // 网络/超时

    /**
     * 初始化方法：创建HTTP客户端和线程池
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        RagConfig.LlmConfig config = ragConfig.getLlm();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES)) // 连接池配置
                .build();

        // 自定义线程池配置
        asyncExecutor = new ThreadPoolExecutor(
                10, // 核心线程数
                50, // 最大线程数
                60L, TimeUnit.SECONDS, // 空闲线程存活时间
                new LinkedBlockingQueue<>(200), // 工作队列
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy() // 饱和策略：调用者运行
        );

        log.info("LLM服务初始化完成：模型={}, 本地缓存大小=10000, 线程池核心=10最大=50",
                config.getModel());
    }

    /**
     * 销毁方法：关闭连接池和线程池
     */
    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
            log.debug("OkHttpClient连接池已关闭");
        }
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.debug("异步线程池已关闭");
        }
    }

    @Override
    public String generateAnswer(String query, List<String> context) {
        long startTime = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        // 1. 查询归一化（用于缓存键生成）
        String normalizedQuery = normalizeQuery(query);

        // 2. 检查多级缓存
        if (ragConfig.getCache().getLlm().isEnabled()) {
            String cacheKey = generateCacheKey(normalizedQuery, context);

            // 2.1 检查本地缓存
            String cachedAnswer = localCache.getIfPresent(cacheKey);
            if (cachedAnswer != null) {
                log.debug("LLM本地缓存命中: query={}, cacheKey={}",
                        query.substring(0, Math.min(query.length(), 50)), cacheKey);
                updateStats(true, startTime, 0, 0);
                return cachedAnswer;
            }

            // 2.2 检查Redis缓存
            Object redisCached = redisService.get(cacheKey);
            if (redisCached instanceof String) {
                String redisAnswer = (String) redisCached;
                if (redisAnswer != null && !redisAnswer.trim().isEmpty()) {
                    log.debug("LLM Redis缓存命中: query={}, cacheKey={}",
                            query.substring(0, Math.min(query.length(), 50)), cacheKey);
                    // 回填本地缓存
                    localCache.put(cacheKey, redisAnswer);
                    updateStats(true, startTime, 0, 0);
                    return redisAnswer;
                }
            }
        }

        try {
            // 3. 构建Prompt（控制长度）
            String prompt = buildMedicalPrompt(query, context);

            // 4. 调用LLM API
            LlmApiResponse response = callLlmApi(prompt);

            // 5. 提取回答
            String answer = extractAnswerFromResponse(response);

            // 6. 缓存回答（多级缓存）
            if (ragConfig.getCache().getLlm().isEnabled() && answer != null && !answer.trim().isEmpty()) {
                String cacheKey = generateCacheKey(normalizedQuery, context);
                // 6.1 写入Redis
                redisService.set(cacheKey, answer, (long) ragConfig.getCache().getLlm().getTtl());
                // 6.2 写入本地缓存
                localCache.put(cacheKey, answer);
                log.debug("LLM回答缓存设置: query={}, cacheKey={}",
                        query.substring(0, Math.min(query.length(), 50)), cacheKey);
            }

            // 7. 更新统计信息
            updateStats(true, startTime, response.getTokensGenerated(), response.getTokensConsumed());

            return answer;

        } catch (Exception e) {
            log.error("LLM生成回答异常: query={}", query, e);
            updateErrorStats(e);
            updateStats(false, startTime, 0, 0);
            return getFallbackAnswer(query, e);
        }
    }

    @Override
    public CompletableFuture<String> generateAnswerAsync(String query, List<String> context) {
        return CompletableFuture.supplyAsync(() -> generateAnswer(query, context), asyncExecutor);
    }

    @Override
    public List<String> generateAnswersBatch(List<String> queries, List<List<String>> contextsList) {
        // 使用并行流提高批量处理效率
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            final String query = queries.get(i);
            final List<String> context = i < contextsList.size() ? contextsList.get(i) : Collections.emptyList();
            futures.add(generateAnswerAsync(query, context));
        }

        // 等待所有异步任务完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get(ragConfig.getLlm().getTimeout() * 2L, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("批量生成回答超时或异常", e);
        }

        return futures.stream()
                .map(future -> {
                    try {
                        return future.getNow(""); // 非阻塞获取结果
                    } catch (Exception e) {
                        log.warn("获取异步结果失败", e);
                        return getFallbackAnswer("", e);
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public AnswerWithCitations generateAnswerWithCitations(String query, List<ContextWithSource> contextWithSources) {
        // 先生成回答
        List<String> contextContents = new ArrayList<>();
        for (ContextWithSource ctx : contextWithSources) {
            contextContents.add(ctx.getContent());
        }
        String answer = generateAnswer(query, contextContents);

        // 多策略引用匹配：句子匹配 → 滑动窗口 → 关键词子串
        List<Citation> citations = new ArrayList<>();
        // 记录已在答案中匹配过的字符位置范围，避免重叠引用
        boolean[] answerMatched = new boolean[answer.length()];

        for (ContextWithSource ctx : contextWithSources) {
            Citation citation = matchContextToAnswer(ctx, answer, answerMatched);
            if (citation != null) {
                citations.add(citation);
            }
        }

        // 按在回答中出现的位置排序
        citations.sort(Comparator.comparingInt(Citation::getPosition));
        return new AnswerWithCitations(answer, citations);
    }

    /**
     * 多策略引用匹配：将上下文与回答进行匹配，找到最佳引用片段
     */
    private Citation matchContextToAnswer(ContextWithSource ctx, String answer, boolean[] answerMatched) {
        String content = ctx.getContent();
        if (content.length() < 15) return null;

        String bestQuote = null;
        int bestPosition = -1;
        int bestLength = 0;

        // 策略1：提取上下文中的句子进行精确匹配
        List<String> sentences = extractSentences(content);
        for (String sentence : sentences) {
            if (sentence.length() < 6) continue;
            int pos = findUnmatchedPosition(answer, sentence, answerMatched);
            if (pos >= 0 && sentence.length() > bestLength) {
                bestQuote = sentence;
                bestPosition = pos;
                bestLength = sentence.length();
            }
        }

        // 策略2：如果句子匹配不理想，使用滑动窗口提取不同位置的片段
        if (bestLength < 15) {
            int contentLen = content.length();
            // 从上下文的不同位置取片段（开头1/4、1/2、3/4处）
            int[] anchorPositions = {
                    0,
                    Math.max(0, contentLen / 4),
                    Math.max(0, contentLen / 2),
                    Math.max(0, contentLen * 3 / 4)
            };
            for (int anchor : anchorPositions) {
                for (int windowSize = Math.min(60, contentLen - anchor);
                     windowSize >= 15; windowSize -= 5) {
                    String window = content.substring(anchor, anchor + windowSize);
                    int pos = findUnmatchedPosition(answer, window, answerMatched);
                    if (pos >= 0 && window.length() > bestLength) {
                        bestQuote = window;
                        bestPosition = pos;
                        bestLength = window.length();
                        break;
                    }
                }
                if (bestLength >= 20) break;
            }
        }

        // 策略3：滑动窗口在整个上下文中扫描，寻找最长匹配子串
        if (bestLength < 12) {
            int contentLen = content.length();
            for (int windowSize = Math.min(40, contentLen); windowSize >= 10; windowSize -= 3) {
                for (int i = 0; i <= contentLen - windowSize; i++) {
                    String window = content.substring(i, i + windowSize);
                    int pos = findUnmatchedPosition(answer, window, answerMatched);
                    if (pos >= 0 && window.length() > bestLength) {
                        bestQuote = window;
                        bestPosition = pos;
                        bestLength = window.length();
                        if (bestLength >= 15) break;
                    }
                }
                if (bestLength >= 15) break;
            }
        }

        if (bestQuote != null && bestPosition >= 0 && bestLength >= 10) {
            // 标记匹配的字符位置，避免后续引用重叠
            for (int i = bestPosition; i < bestPosition + bestLength && i < answerMatched.length; i++) {
                answerMatched[i] = true;
            }
            // 引用片段控制在100字符以内
            String finalQuote = bestQuote.length() > 100
                    ? bestQuote.substring(0, 100) + "…"
                    : bestQuote;
            return new Citation(ctx.getSource(), bestPosition, finalQuote);
        }

        return null;
    }

    /**
     * 从文本中提取中文句子（按句号、问号、感叹号、分号分割）
     */
    private List<String> extractSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 中文句子分割
        String[] parts = text.split("(?<=[。！？；.!?;\\n])");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 6) {
                // 若句子过长（超过80字），从中切出关键的短句
                if (trimmed.length() > 80) {
                    // 按逗号、顿号进一步分割
                    String[] subParts = trimmed.split("(?<=[，、；;])");
                    for (String sub : subParts) {
                        String subTrimmed = sub.trim();
                        if (subTrimmed.length() >= 6 && subTrimmed.length() <= 80) {
                            sentences.add(subTrimmed);
                        }
                    }
                } else {
                    sentences.add(trimmed);
                }
            }
        }
        // 如果句子分割结果太少，把文本按长度切段补充
        if (sentences.size() <= 1 && text.length() > 20) {
            for (int i = 0; i < text.length(); i += 40) {
                int end = Math.min(i + 50, text.length());
                if (end - i >= 10) {
                    sentences.add(text.substring(i, end));
                }
            }
        }
        return sentences;
    }

    /**
     * 在回答中查找未匹配过的位置上的子串
     */
    private int findUnmatchedPosition(String answer, String snippet, boolean[] matched) {
        int searchFrom = 0;
        while (true) {
            int pos = answer.indexOf(snippet, searchFrom);
            if (pos < 0) return -1;

            // 检查该位置是否已被现有引用覆盖
            boolean isCovered = false;
            for (int i = pos; i < pos + snippet.length() && i < matched.length; i++) {
                if (matched[i]) {
                    isCovered = true;
                    break;
                }
            }

            if (!isCovered) {
                return pos;
            }
            // 已被覆盖，从下一个位置继续搜索
            searchFrom = pos + 1;
        }
    }

    @Override
    public ModelInfo getModelInfo() {
        RagConfig.LlmConfig config = ragConfig.getLlm();
        return new ModelInfo(
                config.getModel(),
                "阿里云百炼",
                config.getMaxTokens(),
                false // 暂不支持流式
        );
    }

    @Override
    public boolean isAvailable() {
        try {
            getHttpClient();
            return true;
        } catch (Exception e) {
            log.error("LLM服务不可用", e);
            return false;
        }
    }

    @Override
    public LlmStats getStats() {
        int total = totalCalls.get();
        int successful = successfulCalls.get();
        int failed = failedCalls.get();
        double avgTime = total > 0 ? (double) totalResponseTime.get() / total : 0.0;
        double successRate = total > 0 ? (double) successful / total : 0.0;

        // 本地缓存统计
        com.github.benmanes.caffeine.cache.stats.CacheStats cacheStats = localCache.stats();
        double localCacheHitRate = cacheStats.requestCount() > 0 ?
                cacheStats.hitRate() : 0.0;

        return new LlmStats(
                total, successful, failed, avgTime, successRate,
                totalTokensGenerated.get(), totalTokensConsumed.get()
        );
    }

    /**
     * 重置统计信息（新增方法）
     */
    public void resetStats() {
        totalCalls.set(0);
        successfulCalls.set(0);
        failedCalls.set(0);
        totalResponseTime.set(0);
        totalTokensGenerated.set(0);
        totalTokensConsumed.set(0);
        authErrors.set(0);
        rateLimitErrors.set(0);
        serverErrors.set(0);
        networkErrors.set(0);
        log.info("LLM服务统计信息已重置");
    }

    /**
     * 获取错误统计信息（新增方法）
     */
    public Map<String, Integer> getErrorStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("authErrors", authErrors.get());
        stats.put("rateLimitErrors", rateLimitErrors.get());
        stats.put("serverErrors", serverErrors.get());
        stats.put("networkErrors", networkErrors.get());
        return stats;
    }

    /**
     * 获取本地缓存统计信息（新增方法）
     */
    public Map<String, Object> getCacheStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = localCache.stats();
        Map<String, Object> cacheStats = new HashMap<>();
        cacheStats.put("hitCount", stats.hitCount());
        cacheStats.put("missCount", stats.missCount());
        cacheStats.put("loadSuccessCount", stats.loadSuccessCount());
        cacheStats.put("loadFailureCount", stats.loadFailureCount());
        cacheStats.put("totalLoadTime", stats.totalLoadTime());
        cacheStats.put("evictionCount", stats.evictionCount());
        cacheStats.put("evictionWeight", stats.evictionWeight());
        cacheStats.put("hitRate", stats.hitRate());
        cacheStats.put("missRate", stats.missRate());
        cacheStats.put("averageLoadPenalty", stats.averageLoadPenalty());
        return cacheStats;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取HTTP客户端（懒加载）
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    init();
                }
            }
        }
        return httpClient;
    }

    /**
     * 生成缓存键（优化版）
     * 改进点：
     * 1. 查询归一化：使用QueryRewriteService.normalize
     * 2. 上下文排序：确保相同内容生成相同键
     * 3. 模型信息：包含模型名称
     */
    private String generateCacheKey(String normalizedQuery, List<String> context) {
        StringBuilder content = new StringBuilder(normalizedQuery);

        if (context != null && !context.isEmpty()) {
            // 对上下文排序以确保一致性
            List<String> sortedContext = new ArrayList<>(context);
            Collections.sort(sortedContext);

            for (String ctx : sortedContext) {
                content.append(ctx);
            }
        }

        String hash = DigestUtils.md5DigestAsHex(content.toString().getBytes(StandardCharsets.UTF_8));
        return String.format("llm:%s:%s", ragConfig.getLlm().getModel(), hash);
    }

    /**
     * 查询归一化
     */
    private String normalizeQuery(String query) {
        try {
            // 调用QueryRewriteService的归一化接口
            return queryRewriteService.normalize(query);
        } catch (Exception e) {
            log.warn("查询归一化失败，使用原始查询: {}", query, e);
            return query;
        }
    }

    /**
     * 构建医疗问答Prompt（增强版——强制约束 + 矛盾处理 + 权威性标识）
     *
     * 改进点：
     * 1. 强制系统约束：只基于提供的片段回答，不依赖内部知识
     * 2. 矛盾处理规则：优先采信权威性高且发布时间新的片段
     * 3. 明确拒答引导：知识不足时引导咨询医生
     * 4. 来源和权威性标识附在片段前
     */
    private String buildMedicalPrompt(String query, List<String> context) {
        StringBuilder prompt = new StringBuilder();

        // ═══ 系统指令（System Prompt）—— 强制约束 ═══
        prompt.append("【系统指令】\n");
        prompt.append("你是一个严格的医疗知识助手，请严格遵守以下规则：\n\n");
        prompt.append("1. 只基于下方提供的「参考知识片段」回答，不要使用你自己的内部知识或训练数据中的信息。\n");
        prompt.append("2. 如果不同片段之间存在矛盾，优先采信权威性高（等级数值更高）且发布时间新的片段。\n");
        prompt.append("3. 如果提供的片段无法回答用户问题，请直接说「当前知识不足，建议咨询医生」，"
                + "不要编造或推测任何信息。\n");
        prompt.append("4. 回答应专业、准确、简洁，使用中文。\n");
        prompt.append("5. 严禁给出具体的药物剂量、用法建议。\n\n");

        // ═══ 参考知识片段（按最终分数从高到低排列） ═══
        if (context != null && !context.isEmpty()) {
            prompt.append("【参考知识片段】\n");
            int maxPromptLength = ragConfig.getLlm().getMaxTokens() * 3;
            int currentLength = prompt.length() + query.length() + 300;

            for (int i = 0; i < context.size(); i++) {
                String ctx = context.get(i);
                if (ctx == null || ctx.isEmpty()) continue;
                int ctxLength = ctx.length();

                // 超限截断
                if (currentLength + ctxLength > maxPromptLength) {
                    int remaining = Math.max(maxPromptLength - currentLength, 100);
                    if (remaining < 100) break;
                    ctx = ctx.substring(0, Math.min(ctx.length(), remaining)) + "...[已截断]";
                    ctxLength = ctx.length();
                }

                prompt.append(String.format("--- 片段 %d ---\n%s\n\n", i + 1, ctx));
                currentLength += ctxLength;
            }
        } else {
            prompt.append("【参考知识片段】\n（无可用片段）\n\n");
        }

        // ═══ 用户问题 ═══
        prompt.append("【用户问题】\n").append(query).append("\n\n");

        // ═══ 回答要求 ═══
        prompt.append("【回答要求】\n");
        prompt.append("请基于以上参考知识片段回答。如果无法回答，务必说「当前知识不足，建议咨询医生」。\n");
        prompt.append("涉及诊断或治疗时，必须强调「此信息仅供参考，不能替代专业医疗建议」。\n\n");

        prompt.append("回答：");

        return prompt.toString();
    }

    /**
     * 查找最长的上下文索引
     */
    private int findLongestContextIndex(List<String> context) {
        int maxLength = -1;
        int maxIndex = -1;
        for (int i = 0; i < context.size(); i++) {
            int length = context.get(i).length();
            if (length > maxLength) {
                maxLength = length;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * 调用LLM API（阿里云百炼兼容OpenAI格式）
     */
    private LlmApiResponse callLlmApi(String prompt) throws IOException {
        RagConfig.LlmConfig config = ragConfig.getLlm();
        String apiKey = config.getApiKey();

        // 如果配置中的API密钥为空，尝试从.env文件或系统环境变量读取
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = EnvUtil.getEnv("BAILIAN_API_KEY");
            log.debug("LLM API密钥从配置读取为空，尝试从.env/环境变量读取");
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("LLM API密钥未配置");
        }

        // 构建请求体（OpenAI兼容格式）
        String requestBody = buildOpenAIRequest(prompt, config);

        Request request = new Request.Builder()
                .url(config.getBaseUrl())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        // 记录请求信息（脱敏）
        log.debug("LLM API请求: model={}, prompt长度={}, url={}",
                config.getModel(), prompt.length(), config.getBaseUrl());

        OkHttpClient client = getHttpClient();
        try (Response response = client.newCall(request).execute()) {
            int statusCode = response.code();
            String responseBody = response.body() != null ? response.body().string() : "";

            log.debug("LLM API响应: status={}, body长度={}, 内容预览={}", statusCode, responseBody.length(),
                    responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);

            // 错误处理：区分不同状态码
            if (!response.isSuccessful()) {
                handleApiError(statusCode, responseBody);
                throw new IOException("LLM API请求失败: " + statusCode + " " + response.message());
            }

            return parseOpenAIResponse(responseBody);
        }
    }

    /**
     * 处理API错误（区分错误类型）
     */
    private void handleApiError(int statusCode, String responseBody) {
        switch (statusCode) {
            case 401:
            case 403:
                log.error("LLM API认证失败: status={}, body={}", statusCode,
                        responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
                authErrors.incrementAndGet();
                break;
            case 429:
                log.error("LLM API限流: status={}, body={}", statusCode,
                        responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
                rateLimitErrors.incrementAndGet();
                break;
            case 500:
            case 502:
            case 503:
            case 504:
                log.error("LLM API服务端错误: status={}, body={}", statusCode,
                        responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
                serverErrors.incrementAndGet();
                break;
            default:
                log.error("LLM API请求失败: status={}, body={}", statusCode,
                        responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
                break;
        }
    }

    /**
     * 构建阿里云百炼专有API请求体
     */
    private String buildOpenAIRequest(String prompt, RagConfig.LlmConfig config) throws JsonProcessingException {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", config.getModel());

        // 阿里云百炼文本生成API使用"input"字段包含"prompt"
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("prompt", prompt);
        requestMap.put("input", inputMap);

        // 构建parameters对象
        Map<String, Object> parametersMap = new HashMap<>();
        parametersMap.put("temperature", config.getTemperature());
        parametersMap.put("max_tokens", config.getMaxTokens());
        // 阿里云百炼可能使用"max_new_tokens"而不是"max_tokens"
        parametersMap.put("max_new_tokens", config.getMaxTokens());
        parametersMap.put("stream", false);
        requestMap.put("parameters", parametersMap);

        return objectMapper.writeValueAsString(requestMap);
    }

    /**
     * 解析阿里云百炼专有API响应（兼容OpenAI格式）
     */
    private LlmApiResponse parseOpenAIResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        String answer = "";
        int tokensGenerated = 0;
        int tokensConsumed = 0;

        // 首先尝试阿里云专有格式：output字段
        if (root.has("output")) {
            JsonNode output = root.get("output");
            if (output.has("text")) {
                answer = output.get("text").asText();
            } else if (output.has("choices") && output.get("choices").isArray() && output.get("choices").size() > 0) {
                // output.choices格式
                JsonNode firstChoice = output.get("choices").get(0);
                if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                    answer = firstChoice.get("message").get("content").asText();
                } else if (firstChoice.has("text")) {
                    answer = firstChoice.get("text").asText();
                }
            }
        }
        // 回退到OpenAI兼容格式
        else if (root.has("choices") && root.get("choices").isArray() && !root.get("choices").isEmpty()) {
            JsonNode firstChoice = root.get("choices").get(0);
            if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                answer = firstChoice.get("message").get("content").asText();
            }
        }

        // 解析token使用情况
        if (root.has("usage")) {
            JsonNode usage = root.get("usage");
            if (usage.has("completion_tokens")) {
                tokensGenerated = usage.get("completion_tokens").asInt();
            }
            if (usage.has("prompt_tokens")) {
                tokensConsumed = usage.get("prompt_tokens").asInt();
            } else if (usage.has("total_tokens")) {
                // 如果没有prompt_tokens，用total_tokens - completion_tokens估算
                int totalTokens = usage.get("total_tokens").asInt();
                tokensConsumed = totalTokens - tokensGenerated;
            }
        }

        return new LlmApiResponse(answer, tokensGenerated, tokensConsumed);
    }

    /**
     * 从API响应中提取回答
     */
    private String extractAnswerFromResponse(LlmApiResponse response) {
        String answer = response.getAnswer();

        // 清理回答：移除多余的空白、标记等
        if (answer != null) {
            answer = answer.trim();
            // 如果回答以"回答："开头，移除它
            if (answer.startsWith("回答：")) {
                answer = answer.substring(3).trim();
            }
        }

        return answer;
    }

    /**
     * 降级回答（增强版）
     */
    private String getFallbackAnswer(String query, Exception e) {
        log.warn("使用降级回答: query={}, error={}", query, e.getMessage());

        // 根据错误类型提供不同的降级回答
        if (e instanceof IOException) {
            String msg = e.getMessage();
            if (msg.contains("401") || msg.contains("403")) {
                return "抱歉，服务认证失败，请检查API密钥配置。";
            } else if (msg.contains("429")) {
                return "抱歉，服务请求过于频繁，请稍后重试。";
            } else if (msg.contains("500") || msg.contains("502") || msg.contains("503")) {
                return "抱歉，服务暂时不可用，请稍后重试。";
            } else if (msg.contains("timeout") || msg.contains("Timeout")) {
                return "抱歉，服务响应超时，请稍后重试。";
            }
        }

        return "抱歉，当前无法生成回答。请稍后重试或联系管理员。";
    }

    /**
     * 降级回答（兼容旧版本）
     */
    private String getFallbackAnswer(String query) {
        return getFallbackAnswer(query, new Exception("未知错误"));
    }

    /**
     * 更新统计信息
     */
    private void updateStats(boolean success, long startTime, int tokensGenerated, int tokensConsumed) {
        long responseTime = System.currentTimeMillis() - startTime;
        totalResponseTime.addAndGet(responseTime);

        if (success) {
            successfulCalls.incrementAndGet();
            totalTokensGenerated.addAndGet(tokensGenerated);
            totalTokensConsumed.addAndGet(tokensConsumed);
        } else {
            failedCalls.incrementAndGet();
        }
    }

    /**
     * 更新错误统计
     */
    private void updateErrorStats(Exception e) {
        if (e instanceof IOException) {
            String msg = e.getMessage();
            if (msg.contains("401") || msg.contains("403")) {
                authErrors.incrementAndGet();
            } else if (msg.contains("429")) {
                rateLimitErrors.incrementAndGet();
            } else if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) {
                serverErrors.incrementAndGet();
            } else {
                networkErrors.incrementAndGet();
            }
        } else {
            networkErrors.incrementAndGet();
        }
    }

    /**
     * LLM API响应内部类
     */
    private static class LlmApiResponse {
        private final String answer;
        private final int tokensGenerated;
        private final int tokensConsumed;

        public LlmApiResponse(String answer, int tokensGenerated, int tokensConsumed) {
            this.answer = answer;
            this.tokensGenerated = tokensGenerated;
            this.tokensConsumed = tokensConsumed;
        }

        public String getAnswer() {
            return answer;
        }

        public int getTokensGenerated() {
            return tokensGenerated;
        }

        public int getTokensConsumed() {
            return tokensConsumed;
        }
    }
}