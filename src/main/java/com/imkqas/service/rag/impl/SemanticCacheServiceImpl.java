package com.imkqas.service.rag.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imkqas.config.RagConfig;
import com.imkqas.service.RedisService;
import com.imkqas.service.rag.EmbeddingService;
import com.imkqas.service.rag.QaService;
import com.imkqas.service.rag.SemanticCacheService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 语义化缓存链服务实现类
 * <p>
 * 核心原理：
 * 1. 问题归一化：利用QueryRewriteService将口语化问题转为标准术语
 * 2. 缓存键排序无关：对fragmentIds排序后生成哈希，相同语义不因检索顺序变化而缓存穿透
 * 3. 版本控制：知识库更新时递增版本号，旧索引自然失效
 * 4. 分布式锁懒加载：缓存未命中时，通过Redis SETNX锁保证只有一个线程调用LLM重建
 * <p>
 * 命中判定分两级：
 * ① 精确快速路径：fragmentSig + queryKey 完全一致
 * ② 语义相似度兜底：fragmentSig 相同、queryKey 不同时比对 query embedding 余弦相似度 > 阈值
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class SemanticCacheServiceImpl implements SemanticCacheService {

    private final RedisService redisService;
    private final RagConfig ragConfig;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public SemanticCacheServiceImpl(RedisService redisService, RagConfig ragConfig, EmbeddingService embeddingService) {
        this.redisService = redisService;
        this.ragConfig = ragConfig;
        this.embeddingService = embeddingService;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static final String CACHE_PREFIX = "sem:cache:";
    private static final String LOCK_PREFIX = "sem:lock:";
    private static final String INDEX_PREFIX = "sem:index:";
    private static final String VERSION_KEY = "sem:version:knowledge";

    // 统计信息
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong puts = new AtomicLong(0);
    private final AtomicLong invalidations = new AtomicLong(0);

    // 本地版本缓存（避免每次查Redis）
    private volatile int cachedVersion;
    private volatile boolean versionLoaded = false;

    @PostConstruct
    public void init() {
        this.cachedVersion = loadVersionFromRedis();
        this.versionLoaded = true;
        log.info("语义缓存链初始化完成: 版本={}, TTL={}s, 锁TTL={}s, 相似度阈值={}",
                cachedVersion, ragConfig.getSemanticCache().getTtl(),
                ragConfig.getSemanticCache().getLockTtl(),
                ragConfig.getSemanticCache().getSimilarityThreshold());
    }

    @Override
    public CachedAnswer get(String normalizedQuery, List<Long> fragmentIds) {
        if (!ragConfig.getSemanticCache().isEnabled()) {
            misses.incrementAndGet();
            return null;
        }
        if (normalizedQuery == null || normalizedQuery.trim().isEmpty()) {
            misses.incrementAndGet();
            return null;
        }

        String fragmentSig = buildFragmentSignature(fragmentIds);
        String queryKey = buildQueryKey(normalizedQuery);
        int version = getCurrentVersion();

        // ① 精确快速路径：fragmentSig + queryKey 完全一致
        CachedAnswer exact = readData(buildDataKey(fragmentSig, queryKey), version);
        if (exact != null) {
            hits.incrementAndGet();
            log.debug("语义缓存精确命中: query={}, version={}", truncate(normalizedQuery), version);
            return exact;
        }

        // ② 语义相似度兜底：fragmentSig 相同、queryKey 不同时比对余弦相似度
        Map<String, List<Float>> index = readIndex(fragmentSig, version);
        if (index == null || index.isEmpty()) {
            misses.incrementAndGet();
            return null;
        }
        List<Float> qEmb = embeddingService.embed(normalizedQuery);
        if (qEmb == null || qEmb.isEmpty()) {
            misses.incrementAndGet();
            return null;
        }

        double threshold = ragConfig.getSemanticCache().getSimilarityThreshold();
        String bestKey = null;
        double best = -1;
        for (Map.Entry<String, List<Float>> e : index.entrySet()) {
            double sim = cosineSimilarity(qEmb, e.getValue());
            if (sim > best) {
                best = sim;
                bestKey = e.getKey();
            }
        }
        if (bestKey != null && best >= threshold) {
            CachedAnswer hit = readData(buildDataKey(fragmentSig, bestKey), version);
            if (hit != null) {
                hits.incrementAndGet();
                log.info("语义缓存相似度命中: query={}, 相似度={}", truncate(normalizedQuery), String.format("%.3f", best));
                return hit;
            }
        }

        misses.incrementAndGet();
        return null;
    }

    @Override
    public void put(String normalizedQuery, List<Long> fragmentIds, String answer,
                    List<String> sources, double confidence, List<QaService.SourceCitation> citations) {
        if (!ragConfig.getSemanticCache().isEnabled()) return;
        if (normalizedQuery == null || answer == null) return;

        String fragmentSig = buildFragmentSignature(fragmentIds);
        String queryKey = buildQueryKey(normalizedQuery);
        int version = getCurrentVersion();
        long ttl = ragConfig.getSemanticCache().getTtl();

        try {
            // 写主缓存 value
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("answer", answer);
            value.put("sources", sources != null ? sources : Collections.emptyList());
            value.put("citations", citations != null ? citations : Collections.emptyList());
            value.put("confidence", confidence);
            value.put("query", normalizedQuery);
            value.put("timestamp", System.currentTimeMillis());
            redisService.setWithVersion(buildDataKey(fragmentSig, queryKey), value, version, ttl);

            // 写分组索引（embedding 失败则跳过索引，仅保留精确路径）
            List<Float> qEmb = embeddingService.embed(normalizedQuery);
            if (qEmb != null && !qEmb.isEmpty()) {
                String indexKey = buildIndexKey(fragmentSig, version);
                Map<String, List<Float>> index = readIndex(fragmentSig, version);
                if (index == null) {
                    index = new LinkedHashMap<>();
                }
                index.put(queryKey, qEmb);
                redisService.set(indexKey, index, ttl);
            }
            puts.incrementAndGet();
            log.debug("语义缓存写入: query={}, version={}, ttl={}s", truncate(normalizedQuery), version, ttl);
        } catch (Exception e) {
            log.error("语义缓存写入异常: fragmentSig={}, queryKey={}", fragmentSig, queryKey, e);
        }
    }

    @Override
    public void invalidateByTerm(String standardTerm) {
        if (standardTerm == null || standardTerm.trim().isEmpty()) return;

        // 按标准化术语精确匹配删除
        // 由于缓存key基于MD5哈希，精确反查需要维护 inverted index: term → cacheKeys
        // 当前简化实现：术语变更时推荐调用 incrementKnowledgeVersion() 触发版本自然失效
        log.info("术语变更触发精确失效: term={}（推荐使用 incrementKnowledgeVersion 全量版本失效）", standardTerm);
        invalidations.incrementAndGet();
    }

    @Override
    public void invalidateAll() {
        int newVersion = incrementKnowledgeVersion();
        invalidations.incrementAndGet();
        log.info("语义缓存全量失效完成: 新版本={}", newVersion);
    }

    @Override
    public int incrementKnowledgeVersion() {
        try {
            Long newVersion = redisService.increment(VERSION_KEY, 1);
            int version = newVersion != null ? newVersion.intValue() : 1;
            this.cachedVersion = version;

            // 同步更新配置中的版本号
            ragConfig.getSemanticCache().setKnowledgeVersion(version);

            log.info("知识库版本已递增: version={}", version);
            return version;
        } catch (Exception e) {
            log.error("递增知识库版本失败", e);
            return cachedVersion;
        }
    }

    @Override
    public int getCurrentVersion() {
        if (!versionLoaded) {
            synchronized (this) {
                if (!versionLoaded) {
                    this.cachedVersion = loadVersionFromRedis();
                    this.versionLoaded = true;
                }
            }
        }
        return cachedVersion;
    }

    @Override
    public SemanticCacheStats getStats() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;
        double hitRate = total > 0 ? (double) totalHits / total : 0.0;
        return new SemanticCacheStats(totalHits, totalMisses, puts.get(), invalidations.get(), hitRate, getCurrentVersion());
    }

    @Override
    public boolean isAvailable() {
        try {
            redisService.exists(CACHE_PREFIX + "health");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 分布式锁方法（公开，供QaService在缓存未命中时使用） ==========

    /**
     * 尝试获取缓存重建的分布式锁
     */
    public boolean tryAcquireRebuildLock(String normalizedQuery, List<Long> fragmentIds) {
        int version = getCurrentVersion();
        String lockKey = buildLockKey(normalizedQuery, fragmentIds, version);
        long lockTtl = ragConfig.getSemanticCache().getLockTtl();
        return redisService.acquireLock(lockKey, lockTtl);
    }

    /**
     * 释放缓存重建的分布式锁
     */
    public void releaseRebuildLock(String normalizedQuery, List<Long> fragmentIds) {
        int version = getCurrentVersion();
        String lockKey = buildLockKey(normalizedQuery, fragmentIds, version);
        redisService.releaseLock(lockKey);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从Redis读取并反序列化缓存数据（版本匹配）
     */
    private CachedAnswer readData(String dataKey, int version) {
        try {
            Object data = redisService.getWithVersion(dataKey, version);
            if (data == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.convertValue(data, Map.class);
            String answer = (String) map.get("answer");
            if (answer == null) return null;

            @SuppressWarnings("unchecked")
            List<String> sources = (List<String>) map.get("sources");
            long timestamp = ((Number) map.getOrDefault("timestamp", 0L)).longValue();
            double confidence = ((Number) map.getOrDefault("confidence", 0.5)).doubleValue();
            List<QaService.SourceCitation> citations = readCitations(map.get("citations"));

            return new CachedAnswer(answer,
                    sources != null ? sources : Collections.emptyList(),
                    citations != null ? citations : Collections.emptyList(),
                    timestamp, version, confidence);
        } catch (Exception e) {
            log.warn("语义缓存读取异常: key={}", dataKey, e);
            return null;
        }
    }

    /**
     * 反序列化引用列表（SourceCitation 带 @JsonCreator，直接 convertValue 即可）
     */
    private List<QaService.SourceCitation> readCitations(Object raw) {
        if (raw == null) return null;
        try {
            return objectMapper.convertValue(raw, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, QaService.SourceCitation.class));
        } catch (Exception e) {
            log.warn("缓存引用反序列化失败，忽略引用字段", e);
            return null;
        }
    }

    /**
     * 读取分组索引（queryKey → embedding）
     */
    private Map<String, List<Float>> readIndex(String fragmentSig, int version) {
        try {
            Object raw = redisService.get(buildIndexKey(fragmentSig, version));
            if (raw == null) return null;
            return objectMapper.convertValue(raw, objectMapper.getTypeFactory().constructMapType(
                    LinkedHashMap.class,
                    objectMapper.getTypeFactory().constructType(String.class),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Float.class)));
        } catch (Exception e) {
            log.warn("语义缓存索引读取异常: fragmentSig={}, version={}", fragmentSig, version, e);
            return null;
        }
    }

    /**
     * 余弦相似度计算
     */
    private static double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            na += a.get(i) * a.get(i);
            nb += b.get(i) * b.get(i);
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /**
     * 构建片段签名：MD5(sortedFragmentIds)，排序无关
     */
    private String buildFragmentSignature(List<Long> fragmentIds) {
        List<Long> sorted = new ArrayList<>(fragmentIds == null ? List.of() : fragmentIds);
        Collections.sort(sorted);
        return DigestUtils.md5DigestAsHex(sorted.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构建查询键：MD5(normalizedQuery)
     */
    private String buildQueryKey(String normalizedQuery) {
        return DigestUtils.md5DigestAsHex(normalizedQuery.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构建数据键：sem:cache:{fragmentSig}:{queryKey}
     */
    private String buildDataKey(String fragmentSig, String queryKey) {
        return CACHE_PREFIX + fragmentSig + ":" + queryKey;
    }

    /**
     * 构建索引键：sem:index:{fragmentSig}_v{version}
     */
    private String buildIndexKey(String fragmentSig, int version) {
        return INDEX_PREFIX + fragmentSig + "_v" + version;
    }

    /**
     * 构建分布式锁键
     */
    private String buildLockKey(String normalizedQuery, List<Long> fragmentIds, int version) {
        StringBuilder content = new StringBuilder(normalizedQuery);
        if (fragmentIds != null && !fragmentIds.isEmpty()) {
            List<Long> sorted = new ArrayList<>(fragmentIds);
            Collections.sort(sorted);
            for (Long id : sorted) {
                content.append("|").append(id);
            }
        }
        String hash = DigestUtils.md5DigestAsHex(content.toString().getBytes(StandardCharsets.UTF_8));
        return LOCK_PREFIX + hash + "_v" + version;
    }

    /**
     * 从Redis加载知识库版本号
     */
    private int loadVersionFromRedis() {
        try {
            Object versionObj = redisService.get(VERSION_KEY);
            if (versionObj instanceof Number) {
                int version = ((Number) versionObj).intValue();
                ragConfig.getSemanticCache().setKnowledgeVersion(version);
                return version;
            }
        } catch (Exception e) {
            log.warn("从Redis加载版本号失败，使用配置默认值", e);
        }
        return ragConfig.getSemanticCache().getKnowledgeVersion();
    }

    private static String truncate(String s) {
        return s != null && s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }
}
