package com.student.service.his.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.student.service.LlmService;
import com.student.service.RedisService;
import com.student.service.his.IntentRouter;
import com.student.service.his.IntentType;
import com.student.service.his.QuestionnaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 意图路由器实现
 * 多层过滤架构：Caffeine缓存 → 关键词 → 正则 → Redis → LLM(2s超时+断路器) → 兜底
 *
 * @author 系统
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRouterImpl implements IntentRouter {

    private final LlmService llmService;
    private final QuestionnaireRepository questionnaireRepository;
    private final RedisService redisService;

    // 知识查询关键词（医疗问答）
    private static final List<String> KNOWLEDGE_KEYWORDS = List.of(
            "什么是", "怎么治疗", "吃什么药", "病因", "症状有哪些",
            "如何诊断", "检查", "副作用", "禁忌", "注意事项",
            "多少", "剂量", "用法", "预后", "预防", "并发症"
    );

    // 数据采集关键词（自评/症状描述）
    private static final List<String> DATA_COLLECTION_KEYWORDS = List.of(
            "我感到", "我感觉", "我最近", "我经常", "我总是",
            "我睡不好", "我睡不着", "我很焦虑", "我很难过",
            "帮我测", "帮我评估", "填表", "问卷"
    );

    // 知识查询正则（补充关键词无法覆盖的边界case）
    private static final List<Pattern> KNOWLEDGE_PATTERNS = List.of(
            Pattern.compile("怎[么樣样]办"),
            Pattern.compile("如何[改善改]?!"),
            Pattern.compile("什么原因"),
            Pattern.compile("需要注意什么"),
            Pattern.compile("怎么[治疗预防处理调理]"),
            Pattern.compile("有什么[症状表现]"),
            Pattern.compile("[该应]?如何[治疗处理]")
    );

    // 数据采集正则
    private static final List<Pattern> DATA_COLLECTION_PATTERNS = List.of(
            Pattern.compile("[我俺].*[感觉觉得感到]"),
            Pattern.compile("昨天.*[血压血糖体温]"),
            Pattern.compile("[测测量]了.*[次遍回]"),
            Pattern.compile("数值.*[偏高偏低]"),
            Pattern.compile("[血压血糖体温].*[高了高低低]"),
            Pattern.compile("最近.*[不舒服难受]")
    );

    // 本地意图缓存（Caffeine）
    private final Cache<String, IntentType> intentCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    // Redis缓存键前缀
    private static final String REDIS_KEY_PREFIX = "intent:";

    // LLM 断路器
    private final AtomicInteger llmFailures = new AtomicInteger(0);
    private final AtomicLong llmLastFailureTime = new AtomicLong(0);
    private final AtomicBoolean llmCircuitOpen = new AtomicBoolean(false);
    private static final int CIRCUIT_THRESHOLD = 5;
    private static final long CIRCUIT_COOLDOWN_MS = 30_000;

    @Override
    public IntentType classify(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return IntentType.KNOWLEDGE_QUERY;
        }

        // 0. 查询本地缓存
        String normalized = normalize(userInput);
        IntentType cached = intentCache.getIfPresent(normalized);
        if (cached != null) {
            log.debug("意图路由[CACHE]: {} -> {}", userInput, cached);
            return cached;
        }

        String lower = userInput.toLowerCase();

        // 1. 关键词规则（快速兜底）
        boolean hasKnowledgeKw = KNOWLEDGE_KEYWORDS.stream().anyMatch(lower::contains);
        boolean hasDataKw = DATA_COLLECTION_KEYWORDS.stream().anyMatch(lower::contains);
        boolean matchesQuestionnaire = !questionnaireRepository.matchByKeywords(userInput).isEmpty();

        // 明确的数据采集意图
        if (hasDataKw && matchesQuestionnaire) {
            log.debug("意图路由[DATA_COLLECTION]: 关键词匹配 + 问卷匹配 -> {}", userInput);
            cacheIntent(normalized, IntentType.DATA_COLLECTION);
            return IntentType.DATA_COLLECTION;
        }

        // 明确的知识查询意图
        if (hasKnowledgeKw && !hasDataKw && !matchesQuestionnaire) {
            log.debug("意图路由[KNOWLEDGE_QUERY]: 知识关键词 -> {}", userInput);
            cacheIntent(normalized, IntentType.KNOWLEDGE_QUERY);
            return IntentType.KNOWLEDGE_QUERY;
        }

        // 2. 正则匹配（关键词未命中时补充）
        IntentType regexResult = matchByRegex(lower);
        if (regexResult != null) {
            log.debug("意图路由[REGEX]: {} -> {}", userInput, regexResult);
            cacheIntent(normalized, regexResult);
            return regexResult;
        }

        // 3. Redis 分布式缓存
        IntentType redisResult = getFromRedis(normalized);
        if (redisResult != null) {
            log.debug("意图路由[REDIS]: {} -> {}", userInput, redisResult);
            intentCache.put(normalized, redisResult);
            return redisResult;
        }

        // 4. LLM分类（复杂场景，带超时保护 + 断路器）
        try {
            IntentType llmIntent = classifyByLlm(userInput);
            log.debug("意图路由[LLM]: {} -> {}", userInput, llmIntent);
            cacheIntent(normalized, llmIntent);
            return llmIntent;
        } catch (Exception e) {
            log.warn("LLM意图分类失败，使用关键词兜底: {}", e.getMessage());
            IntentType fallback = matchesQuestionnaire ? IntentType.MIXED : IntentType.KNOWLEDGE_QUERY;
            cacheIntent(normalized, fallback);
            return fallback;
        }
    }

    /**
     * 正则匹配：关键词无法判定时用正则再做一次快速过滤
     */
    private IntentType matchByRegex(String lower) {
        boolean hasKnowledgePattern = KNOWLEDGE_PATTERNS.stream().anyMatch(p -> p.matcher(lower).find());
        boolean hasDataPattern = DATA_COLLECTION_PATTERNS.stream().anyMatch(p -> p.matcher(lower).find());

        if (hasDataPattern && !hasKnowledgePattern) {
            return IntentType.DATA_COLLECTION;
        }
        if (hasKnowledgePattern && !hasDataPattern) {
            return IntentType.KNOWLEDGE_QUERY;
        }
        return null;
    }

    /**
     * 写入多级缓存（Caffeine + Redis）
     */
    private void cacheIntent(String normalized, IntentType intent) {
        intentCache.put(normalized, intent);
        try {
            redisService.set(REDIS_KEY_PREFIX + normalized, intent.name(), 7200L);
        } catch (Exception e) {
            log.debug("Redis缓存写入失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 从Redis读取缓存
     */
    private IntentType getFromRedis(String normalized) {
        try {
            Object val = redisService.get(REDIS_KEY_PREFIX + normalized);
            if (val instanceof String) {
                return IntentType.valueOf((String) val);
            }
        } catch (Exception e) {
            log.debug("Redis缓存读取失败（不影响主流程）: {}", e.getMessage());
        }
        return null;
    }

    // ==================== LLM 分类（超时 + 断路器） ====================

    /**
     * 通过LLM进行意图分类（带超时保护 + 断路器）
     */
    private IntentType classifyByLlm(String userInput) {
        if (!isCircuitAllowed()) {
            log.warn("LLM断路器已打开，跳过LLM调用: {}", userInput);
            return IntentType.KNOWLEDGE_QUERY;
        }
        try {
            IntentType result = CompletableFuture
                    .supplyAsync(() -> doClassifyByLlm(userInput))
                    .get(2, TimeUnit.SECONDS);
            recordCircuitSuccess();
            return result;
        } catch (TimeoutException e) {
            log.warn("LLM意图分类超时(2s)，走兜底: {}", userInput);
            recordCircuitFailure();
            return IntentType.KNOWLEDGE_QUERY;
        } catch (Exception e) {
            log.warn("LLM意图分类异常，走兜底: {}", e.getMessage());
            recordCircuitFailure();
            return IntentType.KNOWLEDGE_QUERY;
        }
    }

    /**
     * LLM分类核心逻辑
     */
    private IntentType doClassifyByLlm(String userInput) {
        String prompt = """
                你是一个医疗AI助手的意图分类器。请判断用户输入属于以下哪一类：
                - KNOWLEDGE_QUERY: 用户询问医学知识、疾病信息、用药方法等（知识查询）
                - DATA_COLLECTION: 用户描述自身症状或感受，希望获得评估或筛查（数据采集）
                - MIXED: 用户既有知识查询又描述了自身情况（混合型）

                请仅输出一个类别名称，不要有任何其他文字。

                用户输入：
                %s

                类别：""".formatted(userInput);

        String result = llmService.generateAnswerDirect(prompt);
        if (result == null) return IntentType.KNOWLEDGE_QUERY;

        String trimmed = result.trim().toUpperCase();
        if (trimmed.contains("DATA_COLLECTION")) return IntentType.DATA_COLLECTION;
        if (trimmed.contains("MIXED")) return IntentType.MIXED;
        return IntentType.KNOWLEDGE_QUERY;
    }

    // ==================== 断路器 ====================

    private boolean isCircuitAllowed() {
        if (!llmCircuitOpen.get()) return true;
        if (System.currentTimeMillis() - llmLastFailureTime.get() > CIRCUIT_COOLDOWN_MS) {
            llmCircuitOpen.set(false);
            llmFailures.set(0);
            log.info("LLM断路器进入半开状态，允许试探请求");
            return true;
        }
        return false;
    }

    private void recordCircuitSuccess() {
        llmFailures.set(0);
    }

    private void recordCircuitFailure() {
        llmLastFailureTime.set(System.currentTimeMillis());
        if (llmFailures.incrementAndGet() >= CIRCUIT_THRESHOLD) {
            llmCircuitOpen.set(true);
            log.warn("LLM断路器打开: 连续失败{}次", CIRCUIT_THRESHOLD);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 查询文本归一化：去空格、全角转半角、转小写
     */
    private static String normalize(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (c >= '！' && c <= '～') {
                c = (char) (c - 0xFEE0);
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
