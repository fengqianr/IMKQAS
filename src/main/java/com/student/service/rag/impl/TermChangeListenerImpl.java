package com.student.service.rag.impl;

import com.student.config.RagConfig;
import com.student.service.RedisService;
import com.student.service.rag.SemanticCacheService;
import com.student.service.rag.TermChangeListener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 术语变更监听器实现类
 * <p>
 * 失效流程（背诵版）：
 * "人工/消息/定时触发 → 按标准化术语精确删除 → 懒加载+分布式锁 → 版本号+TTL兜底"
 * <p>
 * 触发机制：
 * 1. 人工触发：后台API调用 manualInvalidate()
 * 2. 消息订阅：预留MQ接口 onTermChanged()（当前本地缓存变更模拟）
 * 3. 定时兜底：scheduledVersionCheck() 按配置间隔运行
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class TermChangeListenerImpl implements TermChangeListener {

    private final SemanticCacheService semanticCacheService;
    private final RedisService redisService;
    private final RagConfig ragConfig;

    public TermChangeListenerImpl(SemanticCacheService semanticCacheService,
                                   RedisService redisService,
                                   RagConfig ragConfig) {
        this.semanticCacheService = semanticCacheService;
        this.redisService = redisService;
        this.ragConfig = ragConfig;
    }

    private static final String TERM_HASH_KEY = "sem:term:mapping-hash";
    private static final String TERM_VERSION_KEY = "sem:term:mapping-version";

    private final AtomicLong scheduledCheckCount = new AtomicLong(0);
    private volatile String lastKnownHash;

    // 本地定时器（兜底）
    private ScheduledExecutorService localScheduler;

    @PostConstruct
    public void init() {
        this.lastKnownHash = getTermMappingHash();
        // 启动本地定时器（作为兜底机制）
        long interval = ragConfig.getSemanticCache().getVersionCheckInterval();
        localScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "term-change-checker");
            t.setDaemon(true);
            return t;
        });
        localScheduler.scheduleWithFixedDelay(
                this::scheduledVersionCheck,
                interval,
                interval,
                TimeUnit.SECONDS
        );
        log.info("术语变更监听器初始化完成: 初始Hash={}, 检查间隔={}s", lastKnownHash, interval);
    }

    @Override
    public void manualInvalidate(String standardTerm) {
        if (standardTerm != null && !standardTerm.trim().isEmpty()) {
            // 精确失效：只删除与该术语相关的缓存
            semanticCacheService.invalidateByTerm(standardTerm);
            log.info("人工触发精确失效: term={}", standardTerm);
        } else {
            // 全量失效：递增版本号
            semanticCacheService.invalidateAll();
            log.info("人工触发全量缓存失效");
        }
    }

    @Override
    public void onTermChanged(TermChangeEvent event) {
        log.info("收到术语变更事件: term={}, type={}, {} -> {}",
                event.getTerm(), event.getChangeType(),
                event.getOldValue(), event.getNewValue());

        // 精确失效该术语对应的缓存key
        semanticCacheService.invalidateByTerm(event.getTerm());

        // 递增术语映射版本号
        redisService.increment(TERM_VERSION_KEY, 1);

        // 更新mapping hash
        updateTermMappingHash();
    }

    @Override
    public void scheduledVersionCheck() {
        try {
            String currentHash = getTermMappingHash();
            scheduledCheckCount.incrementAndGet();

            if (lastKnownHash != null && !lastKnownHash.equals(currentHash)) {
                log.warn("术语映射表已变更: oldHash={}, newHash={}, 触发缓存失效",
                        lastKnownHash, currentHash);

                // 递增术语版本
                redisService.increment(TERM_VERSION_KEY, 1);

                // 递增知识库版本触发缓存自然失效
                semanticCacheService.incrementKnowledgeVersion();

                // 更新本地记录的Hash
                lastKnownHash = currentHash;
                updateTermMappingHash();
            } else {
                log.debug("术语映射表未变更: hash={}, 检查次数={}",
                        currentHash, scheduledCheckCount.get());
            }
        } catch (Exception e) {
            log.error("定时版本检查异常", e);
        }
    }

    @Override
    public String getTermMappingHash() {
        try {
            // 从Redis读取缓存Hash
            Object cached = redisService.get(TERM_HASH_KEY);
            if (cached instanceof String && !((String) cached).isEmpty()) {
                return (String) cached;
            }
        } catch (Exception e) {
            log.warn("从Redis读取术语Hash失败，返回本地缓存", e);
        }
        // 降级：返回本地缓存的Hash
        return lastKnownHash != null ? lastKnownHash : computeCurrentHash();
    }

    // ========== 私有辅助方法 ==========

    /**
     * 更新术语映射Hash到Redis
     */
    private void updateTermMappingHash() {
        String newHash = computeCurrentHash();
        redisService.set(TERM_HASH_KEY, newHash, 7 * 24 * 3600L);
        this.lastKnownHash = newHash;
    }

    /**
     * 计算当前术语映射表的Hash
     * 基于关键配置项计算：同义词映射 + SNOMED CT版本 + 口语映射表
     * 实际生产应接入真实的术语库版本接口
     */
    private String computeCurrentHash() {
        StringBuilder content = new StringBuilder();
        content.append("knowledge-version:").append(semanticCacheService.getCurrentVersion());
        content.append("|term-version:").append(getTermMappingVersion());
        content.append("|timestamp:").append(System.currentTimeMillis() / 3600_000); // 小时粒度

        return DigestUtils.md5DigestAsHex(
                content.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 获取术语映射版本号
     */
    private long getTermMappingVersion() {
        try {
            Object v = redisService.get(TERM_VERSION_KEY);
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
        } catch (Exception e) {
            log.warn("获取术语映射版本失败", e);
        }
        return 1L;
    }
}
