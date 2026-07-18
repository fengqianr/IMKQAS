package com.imkqas.service.his.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.imkqas.service.LlmService;
import com.imkqas.service.RedisService;
import com.imkqas.service.his.IntentRouter;
import com.imkqas.service.his.IntentType;
import com.imkqas.service.his.QuestionnaireRepository;
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
 * 多层过滤架构：Caffeine缓存 → 关键词/句式规则（含MIXED判定） → 正则 → Redis → LLM(2s超时+断路器) → 兜底
 *
 * @author 系统
 * @version 2.2
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
            Pattern.compile("怎[么样樣]办"),
            Pattern.compile("如何"),
            Pattern.compile("什么原因"),
            Pattern.compile("注意什么"),
            Pattern.compile("怎么(治疗|预防|处理|调理|治|缓解|护理|消毒|补)"),
            Pattern.compile("有什么(症状|表现)"),
            Pattern.compile("[该应]?如何(治疗|处理)")
    );

    // 数据采集正则（注意：必须使用完整词交替 (A|B)，不能用单字符类 [AB]，否则会误命中单个汉字）
    private static final List<Pattern> DATA_COLLECTION_PATTERNS = List.of(
            Pattern.compile("[我俺].{0,10}(感觉|觉得|感到)"),
            Pattern.compile("昨天.*(血压|血糖|体温)"),
            Pattern.compile("(测|量)了.*(次|遍|回)"),
            Pattern.compile("数值.*(偏高|偏低)"),
            Pattern.compile("(血压|血糖|体温).*(偏高|偏低|高了|低了|升高|降低)"),
            Pattern.compile("最近.*(不舒服|难受)")
    );

    // 个人情况描述句式（第一人称/家人主语/病程叙述），用于识别MIXED中的"个人描述"侧信号
    private static final List<Pattern> PERSONAL_PATTERNS = List.of(
            Pattern.compile("^[我俺]"),
            Pattern.compile("[我俺](最近|总是|老是|经常|这两周|这段时间|一直|现在|昨晚|白天|晚上|睡前|该|应该)"),
            Pattern.compile("我家?的?(孩子|儿子|女儿|宝宝|娃)"),
            // 无"我"字的近期状态叙述（句首时间状语）
            Pattern.compile("^(最近|这两周|这段时间|这几天|近来|老是|总是|天天|整宿|半夜)"),
            // 无"我"字的家人主语（句首特定称谓；不含"儿童/小孩/患儿"等泛指词，避免误伤泛化知识问句）
            Pattern.compile("^(孩子|宝宝|娃|儿子|女儿|家里)"),
            // 病程/持续时间叙述（如"咳嗽好几天了""低落快一个月了"）
            Pattern.compile("(好几|几|一|两|三|四|五|半)(天|周|晚|宿|个月|个星期)了")
    );

    // 知识提问/求建议句式，用于识别MIXED中的"知识提问"侧信号（单独出现时不足以判定，需与个人描述组合）
    private static final List<Pattern> ADVICE_PATTERNS = List.of(
            Pattern.compile("怎么|咋|如何|怎样"),
            Pattern.compile("(吃|用|喝|挂|查)什么|什么(药|办法|方法|原因|检查|科|食物)|有什么|哪个科"),
            Pattern.compile("要不要|能不能|可不可以|会不会|是不是|应该|还是"),
            Pattern.compile("多久|多长时间"),
            Pattern.compile("[吗嘛]\\s*[？?！!。]*\\s*$"),
            Pattern.compile("去医院|看医生|就医|后遗症|忌口|复发|缓解|改善|调理|止痒|急救")
    );

    // 求知前缀：第一人称的知识请求表达（如"请告诉我""我想知道"），属于知识查询而非个人症状描述
    private static final List<Pattern> KNOWLEDGE_REQUEST_PATTERNS = List.of(
            Pattern.compile("请?(告诉我|给我(讲|介绍|科普))"),
            Pattern.compile("我想(知道|问|了解|咨询)"),
            Pattern.compile("请?帮我(查|解释)")
    );

    // 本地意图缓存（Caffeine）
    private final Cache<String, IntentType> intentCache = Caffeine.newBuilder()
            .maximumSize(10000)
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

    // Redis 断路器（第1次连接超时即熔断，保护下游）
    private final AtomicInteger redisFailures = new AtomicInteger(0);
    private final AtomicLong redisLastFailureTime = new AtomicLong(0);
    private final AtomicBoolean redisCircuitOpen = new AtomicBoolean(false);
    private static final int REDIS_CIRCUIT_THRESHOLD = 1;
    private static final long REDIS_CIRCUIT_COOLDOWN_MS = 30_000;

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

        // 1. 关键词 + 句式信号规则（快速判定）
        boolean hasKnowledgeKw = KNOWLEDGE_KEYWORDS.stream().anyMatch(lower::contains);
        boolean hasDataKw = DATA_COLLECTION_KEYWORDS.stream().anyMatch(lower::contains);
        boolean matchesQuestionnaire = !questionnaireRepository.matchByKeywords(userInput).isEmpty();
        // 求知前缀（"请告诉我""我想知道"等）：第一人称求知识，抑制个人描述信号
        boolean isKnowledgeRequest = matchAny(KNOWLEDGE_REQUEST_PATTERNS, lower);
        // 个人描述信号：数据采集关键词 或 个人/家人/病程叙述句式（求知前缀豁免）
        boolean hasPersonalSignal = !isKnowledgeRequest
                && (hasDataKw || matchAny(PERSONAL_PATTERNS, lower));
        // 强知识信号：知识关键词 / 知识正则 / 求知前缀，可独立判定KNOWLEDGE
        boolean hasStrongKnowledge = hasKnowledgeKw || isKnowledgeRequest
                || matchAny(KNOWLEDGE_PATTERNS, lower);
        // 求建议句式：单独出现时意图不明（可能是知识查询也可能是评估请求），仅与个人描述组合判定MIXED
        boolean hasAdvicePattern = matchAny(ADVICE_PATTERNS, lower);

        // 个人情况描述 + 知识提问/求建议 → 混合意图（必须先于DATA/KNOWLEDGE判定，避免被单一意图短路）
        if (hasPersonalSignal && (hasStrongKnowledge || hasAdvicePattern)) {
            log.debug("意图路由[MIXED]: 个人描述+知识提问 -> {}", userInput);
            cacheIntent(normalized, IntentType.MIXED);
            return IntentType.MIXED;
        }

        // 明确的数据采集意图（个人描述 + 问卷匹配，且无知识提问）
        if (hasDataKw && matchesQuestionnaire) {
            log.debug("意图路由[DATA_COLLECTION]: 关键词匹配 + 问卷匹配 -> {}", userInput);
            cacheIntent(normalized, IntentType.DATA_COLLECTION);
            return IntentType.DATA_COLLECTION;
        }

        // 明确的知识查询意图（强知识信号且无个人描述；仅有求建议句式的边界case交给LLM）
        if (hasStrongKnowledge && !hasPersonalSignal) {
            log.debug("意图路由[KNOWLEDGE_QUERY]: 强知识信号 -> {}", userInput);
            cacheIntent(normalized, IntentType.KNOWLEDGE_QUERY);
            return IntentType.KNOWLEDGE_QUERY;
        }

        // 2. 正则匹配（数据采集侧兜底；知识侧信号在上方已判定过）
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
     * 正则匹配：规则层未判定时用数据采集正则再做一次快速过滤
     * 到达此处时知识提问信号必为false（否则已在规则层返回），因此只需匹配数据侧
     */
    private IntentType matchByRegex(String lower) {
        return matchAny(DATA_COLLECTION_PATTERNS, lower) ? IntentType.DATA_COLLECTION : null;
    }

    /**
     * 判断文本是否命中任一正则
     */
    private static boolean matchAny(List<Pattern> patterns, String text) {
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }

    /**
     * 写入多级缓存（Caffeine同步 + Redis异步，Redis失败不影响主链路）
     */
    private void cacheIntent(String normalized, IntentType intent) {
        intentCache.put(normalized, intent);
        if (!isRedisCircuitAllowed()) {
            log.debug("Redis断路器已打开，跳过Redis写入");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                redisService.set(REDIS_KEY_PREFIX + normalized, intent.name(), null);
                recordRedisCircuitSuccess();
            } catch (Exception e) {
                log.warn("Redis缓存写入失败: {}", e.getMessage());
                recordRedisCircuitFailure();
            }
        });
    }

    /**
     * 从Redis读取缓存（带熔断保护）
     */
    private IntentType getFromRedis(String normalized) {
        if (!isRedisCircuitAllowed()) {
            log.debug("Redis断路器已打开，跳过Redis读取");
            return null;
        }
        try {
            Object val = redisService.get(REDIS_KEY_PREFIX + normalized);
            if (val instanceof String) {
                recordRedisCircuitSuccess();
                return IntentType.valueOf((String) val);
            }
            recordRedisCircuitSuccess();
        } catch (Exception e) {
            log.warn("Redis缓存读取失败: {}", e.getMessage());
            recordRedisCircuitFailure();
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

    // ==================== LLM 断路器 ====================

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

    // ==================== Redis 断路器 ====================

    private boolean isRedisCircuitAllowed() {
        if (!redisCircuitOpen.get()) return true;
        if (System.currentTimeMillis() - redisLastFailureTime.get() > REDIS_CIRCUIT_COOLDOWN_MS) {
            redisCircuitOpen.set(false);
            redisFailures.set(0);
            log.info("Redis断路器进入半开状态，允许试探请求");
            return true;
        }
        return false;
    }

    private void recordRedisCircuitSuccess() {
        redisFailures.set(0);
    }

    private void recordRedisCircuitFailure() {
        redisLastFailureTime.set(System.currentTimeMillis());
        if (redisFailures.incrementAndGet() >= REDIS_CIRCUIT_THRESHOLD) {
            redisCircuitOpen.set(true);
            log.warn("Redis断路器打开: 连续失败{}次（第1次连接超时即熔断）", REDIS_CIRCUIT_THRESHOLD);
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
