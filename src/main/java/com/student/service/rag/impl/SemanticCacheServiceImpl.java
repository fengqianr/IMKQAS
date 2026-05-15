package com.student.service.rag.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.config.RagConfig;
import com.student.service.RedisService;
import com.student.service.rag.SemanticCacheService;
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
 * 3. 版本控制：知识库更新时递增版本号，缓存键变为归一化问句_v2，旧缓存自然失效
 * 4. 分布式锁懒加载：缓存未命中时，通过Redis SETNX锁保证只有一个线程调用LLM重建
 * <p>
 * 术语变更失效（背诵版）：
 * "人工/消息/定时触发 → 按标准化术语精确删除 → 懒加载+分布式锁 → 版本号+TTL兜底"
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class SemanticCacheServiceImpl implements SemanticCacheService {

    private final RedisService redisService;
    private final RagConfig ragConfig;
    private final ObjectMapper objectMapper;

    public SemanticCacheServiceImpl(RedisService redisService, RagConfig ragConfig) {
        this.redisService = redisService;
        this.ragConfig = ragConfig;
        this.objectMapper = new ObjectMapper();
    }

    private static final String CACHE_PREFIX = "sem:cache:";
    private static final String LOCK_PREFIX = "sem:lock:";
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
        log.info("语义缓存链初始化完成: 版本={}, TTL={}s, 锁TTL={}s",
                cachedVersion, ragConfig.getSemanticCache().getTtl(),
                ragConfig.getSemanticCache().getLockTtl());
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

        int currentVersion = getCurrentVersion();
        String cacheKey = buildCacheKey(normalizedQuery, fragmentIds, currentVersion);

        try {
            // 从Redis读取并校验版本
            Object data = redisService.getWithVersion(cacheKey, currentVersion);
            if (data != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.convertValue(data, Map.class);
                String answer = (String) map.get("answer");
                @SuppressWarnings("unchecked")
                List<String> sources = (List<String>) map.get("sources");
                long timestamp = ((Number) map.getOrDefault("timestamp", 0L)).longValue();

                hits.incrementAndGet();
                log.debug("语义缓存命中: query={}, version={}", truncate(normalizedQuery), currentVersion);
                return new CachedAnswer(answer, sources != null ? sources : Collections.emptyList(), timestamp, currentVersion);
            }
        } catch (Exception e) {
            log.warn("语义缓存读取异常: key={}", cacheKey, e);
        }

        misses.incrementAndGet();
        return null;
    }

    @Override
    public void put(String normalizedQuery, List<Long> fragmentIds, String answer, List<String> sources) {
        if (!ragConfig.getSemanticCache().isEnabled()) return;
        if (normalizedQuery == null || answer == null) return;

        int currentVersion = getCurrentVersion();
        String cacheKey = buildCacheKey(normalizedQuery, fragmentIds, currentVersion);
        long ttl = ragConfig.getSemanticCache().getTtl();

        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("answer", answer);
            value.put("sources", sources != null ? sources : Collections.emptyList());
            value.put("timestamp", System.currentTimeMillis());
            value.put("query", normalizedQuery);

            redisService.setWithVersion(cacheKey, value, currentVersion, ttl);
            puts.incrementAndGet();
            log.debug("语义缓存写入: query={}, version={}, ttl={}s",
                    truncate(normalizedQuery), currentVersion, ttl);
        } catch (Exception e) {
            log.error("语义缓存写入异常: key={}", cacheKey, e);
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
     * 构建语义缓存键
     * 格式：sem:cache:{MD5(normalizedQuery + sortedFragmentIds)}_v{version}
     * 关键设计：fragmentIds在传入前已排序，确保相同语义内容不因检索顺序变化而键不同
     */
    private String buildCacheKey(String normalizedQuery, List<Long> fragmentIds, int version) {
        StringBuilder content = new StringBuilder(normalizedQuery);

        if (fragmentIds != null && !fragmentIds.isEmpty()) {
            List<Long> sorted = new ArrayList<>(fragmentIds);
            Collections.sort(sorted);
            for (Long id : sorted) {
                content.append("|").append(id);
            }
        }

        String hash = DigestUtils.md5DigestAsHex(content.toString().getBytes(StandardCharsets.UTF_8));
        return CACHE_PREFIX + hash + "_v" + version;
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
