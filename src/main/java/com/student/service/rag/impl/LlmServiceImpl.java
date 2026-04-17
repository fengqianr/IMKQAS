package com.student.service.rag.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.config.RagConfig;
import com.student.service.LlmService;
import com.student.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.student.utils.EnvUtil;

/**
 * 大语言模型服务实现类
 * 集成阿里云百炼（DashScope）API，支持医疗问答生成
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmServiceImpl implements LlmService {

    private final RagConfig ragConfig;
    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient httpClient;

    // 统计信息
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger successfulCalls = new AtomicInteger(0);
    private final AtomicInteger failedCalls = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicInteger totalTokensGenerated = new AtomicInteger(0);
    private final AtomicInteger totalTokensConsumed = new AtomicInteger(0);

    // 缓存：查询+上下文 -> 回答（可选）
    private final Map<String, String> answerCache = new ConcurrentHashMap<>();

    /**
     * 初始化方法
     */
    // 注：@PostConstruct 可在此添加，用于日志输出

    /**
     * 获取HTTP客户端
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            RagConfig.LlmConfig config = ragConfig.getLlm();
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
                    .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
                    .writeTimeout(config.getTimeout(), TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }

    @Override
    public String generateAnswer(String query, List<String> context) {
        long startTime = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        // 检查缓存
        if (ragConfig.getCache().getLlm().isEnabled()) {
            String cacheKey = generateCacheKey(query, context);
            Object cached = redisService.get(cacheKey);
            if (cached instanceof String) {
                String cachedAnswer = (String) cached;
                if (cachedAnswer != null) {
                    log.debug("LLM回答缓存命中: query={}, cacheKey={}",
                            query.substring(0, Math.min(query.length(), 50)), cacheKey);
                    updateStats(true, startTime, 0, 0); // 缓存命中不计token
                    return cachedAnswer;
                }
            }
        }

        try {
            // 构建Prompt
            String prompt = buildMedicalPrompt(query, context);

            // 调用LLM API
            LlmApiResponse response = callLlmApi(prompt);

            // 提取回答
            String answer = extractAnswerFromResponse(response);

            // 缓存回答
            if (ragConfig.getCache().getLlm().isEnabled() && answer != null && !answer.trim().isEmpty()) {
                String cacheKey = generateCacheKey(query, context);
                redisService.set(cacheKey, answer, (long) ragConfig.getCache().getLlm().getTtl());
                log.debug("LLM回答缓存设置: query={}, cacheKey={}",
                        query.substring(0, Math.min(query.length(), 50)), cacheKey);
            }

            // 更新统计信息
            updateStats(true, startTime, response.getTokensGenerated(), response.getTokensConsumed());

            return answer;

        } catch (Exception e) {
            log.error("LLM生成回答异常: query={}", query, e);
            updateStats(false, startTime, 0, 0);
            return getFallbackAnswer(query);
        }
    }

    @Override
    public CompletableFuture<String> generateAnswerAsync(String query, List<String> context) {
        return CompletableFuture.supplyAsync(() -> generateAnswer(query, context));
    }

    @Override
    public List<String> generateAnswersBatch(List<String> queries, List<List<String>> contextsList) {
        // 简化实现：循环调用单个生成
        // 实际可优化为批量API调用
        List<String> answers = new ArrayList<>();

        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            List<String> context = i < contextsList.size() ? contextsList.get(i) : Collections.emptyList();
            answers.add(generateAnswer(query, context));
        }

        return answers;
    }

    @Override
    public AnswerWithCitations generateAnswerWithCitations(String query, List<ContextWithSource> contextWithSources) {
        // 简化实现：先生成回答，然后匹配引用
        // 实际应设计更精确的引用匹配算法
        List<String> contextContents = new ArrayList<>();
        for (ContextWithSource ctx : contextWithSources) {
            contextContents.add(ctx.getContent());
        }

        String answer = generateAnswer(query, contextContents);

        // 简单引用匹配：在回答中查找上下文片段
        List<Citation> citations = new ArrayList<>();
        for (ContextWithSource ctx : contextWithSources) {
            if (answer.contains(ctx.getContent().substring(0, Math.min(ctx.getContent().length(), 20)))) {
                int position = answer.indexOf(ctx.getContent().substring(0, Math.min(ctx.getContent().length(), 20)));
                citations.add(new Citation(ctx.getSource(), position,
                        ctx.getContent().substring(0, Math.min(ctx.getContent().length(), 100))));
            }
        }

        return new AnswerWithCitations(answer, citations);
    }

    @Override
    public ModelInfo getModelInfo() {
        RagConfig.LlmConfig config = ragConfig.getLlm();
        return new ModelInfo(
                config.getModel(),
                "阿里云百炼",
                2000, // 假设最大token数
                true   // 假设支持流式
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

        return new LlmStats(
                total, successful, failed, avgTime, successRate,
                totalTokensGenerated.get(), totalTokensConsumed.get()
        );
    }

    // ========== 私有辅助方法 ==========

    /**
     * 生成缓存键
     */
    private String generateCacheKey(String query, List<String> context) {
        StringBuilder content = new StringBuilder(query);
        if (context != null) {
            for (String ctx : context) {
                content.append(ctx);
            }
        }
        String hash = DigestUtils.md5DigestAsHex(content.toString().getBytes(StandardCharsets.UTF_8));
        return String.format("llm:%s:%s", ragConfig.getLlm().getModel(), hash);
    }

    /**
     * 构建医疗问答Prompt
     */
    private String buildMedicalPrompt(String query, List<String> context) {
        // 医疗专业Prompt模板
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一位专业的医疗助手，请基于以下医学知识回答用户的问题。\n\n");

        if (context != null && !context.isEmpty()) {
            prompt.append("参考知识：\n");
            for (int i = 0; i < context.size(); i++) {
                prompt.append(String.format("[知识片段 %d]\n%s\n\n", i + 1, context.get(i)));
            }
        }

        prompt.append("用户问题：").append(query).append("\n\n");

        prompt.append("请按照以下要求回答：\n");
        prompt.append("1. 回答必须基于提供的参考知识，如果知识中没有相关信息，请明确说明\"根据现有资料无法回答\"\n");
        prompt.append("2. 回答应专业、准确、简洁，避免使用模糊词汇\n");
        prompt.append("3. 如果涉及疾病诊断或治疗建议，必须强调\"此信息仅供参考，不能替代专业医疗建议，请咨询医生\"\n");
        prompt.append("4. 可以适当补充一般性医学常识，但需与参考知识一致\n");
        prompt.append("5. 使用中文回答，语言通俗易懂\n\n");

        prompt.append("回答：");

        return prompt.toString();
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
                .url(config.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        OkHttpClient client = getHttpClient();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LLM API请求失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return parseOpenAIResponse(responseBody);
        }
    }

    /**
     * 构建OpenAI兼容请求体
     */
    private String buildOpenAIRequest(String prompt, RagConfig.LlmConfig config) throws JsonProcessingException {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        requestMap.put("messages", messages);
        requestMap.put("temperature", config.getTemperature());
        requestMap.put("max_tokens", config.getMaxTokens());
        requestMap.put("stream", false);

        return objectMapper.writeValueAsString(requestMap);
    }

    /**
     * 解析OpenAI兼容响应
     */
    private LlmApiResponse parseOpenAIResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        String answer = "";
        int tokensGenerated = 0;
        int tokensConsumed = 0;

        if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
            JsonNode firstChoice = root.get("choices").get(0);
            if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                answer = firstChoice.get("message").get("content").asText();
            }
        }

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
     * 降级回答（当LLM调用失败时）
     */
    private String getFallbackAnswer(String query) {
        log.warn("使用降级回答: query={}", query);
        return "抱歉，当前无法生成回答。请稍后重试或联系管理员。";
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