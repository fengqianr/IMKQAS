package com.student.service.rag.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.student.entity.evaluation.OnlineMetricsSnapshot;
import com.student.entity.synonym.MedicalSynonymMapping;
import com.student.entity.synonym.UnmappedTermRecord;
import com.student.mapper.MedicalSynonymMappingMapper;
import com.student.mapper.UnmappedTermRecordMapper;
import com.student.mapper.evaluation.OnlineMetricsSnapshotMapper;
import com.student.service.LlmService;
import com.student.service.RedisService;
import com.student.service.rag.MedicalEntityRecognitionService;
import com.student.service.rag.SynonymExpansionService;
import com.student.service.rag.UnmappedTermProducer;
import com.student.service.snomed.SnomedTerminologyService;
import com.student.service.snomed.dto.SnomedTermResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 同义词扩展服务实现
 * 查询流程：本地映射表 → SNOMED CT → LLM临时推断 + 人工审核队列 + 监控告警
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class SynonymExpansionServiceImpl implements SynonymExpansionService {

    private final MedicalSynonymMappingMapper synonymMappingMapper;
    private final UnmappedTermRecordMapper unmappedTermMapper;
    private final SnomedTerminologyService snomedTerminologyService;
    private final LlmService llmService;
    private final RedisService redisService;
    private final UnmappedTermProducer unmappedTermProducer;
    private final OnlineMetricsSnapshotMapper metricsSnapshotMapper;

    public SynonymExpansionServiceImpl(
            MedicalSynonymMappingMapper synonymMappingMapper,
            UnmappedTermRecordMapper unmappedTermMapper,
            SnomedTerminologyService snomedTerminologyService,
            @Lazy LlmService llmService,
            @Lazy RedisService redisService,
            @Lazy UnmappedTermProducer unmappedTermProducer,
            @Lazy OnlineMetricsSnapshotMapper metricsSnapshotMapper) {
        this.synonymMappingMapper = synonymMappingMapper;
        this.unmappedTermMapper = unmappedTermMapper;
        this.snomedTerminologyService = snomedTerminologyService;
        this.llmService = llmService;
        this.redisService = redisService;
        this.unmappedTermProducer = unmappedTermProducer;
        this.metricsSnapshotMapper = metricsSnapshotMapper;
    }

    /** 未映射率告警阈值（默认5%） */
    @Value("${imkqas.query-rewrite.synonym-expansion.unmapped-alert-threshold:5.0}")
    private double unmappedAlertThreshold;

    /** LLM推断最低置信度阈值，低于此值不采纳 */
    @Value("${imkqas.query-rewrite.synonym-expansion.llm-min-confidence:0.85}")
    private double llmMinConfidence;

    /** 是否启用LLM兜底推断 */
    @Value("${imkqas.query-rewrite.synonym-expansion.llm-fallback-enabled:true}")
    private boolean llmFallbackEnabled;

    /** LLM推断结果Redis缓存开关 */
    @Value("${imkqas.query-rewrite.synonym-expansion.llm-cache.enabled:true}")
    private boolean llmCacheEnabled;

    /** LLM推断结果Redis缓存TTL（秒），默认7天 */
    @Value("${imkqas.query-rewrite.synonym-expansion.llm-cache.ttl:604800}")
    private long llmCacheTtl;

    private static final String LLM_CACHE_KEY_PREFIX = "synonym:llm:";

    // 本地映射表内存缓存（Caffeine，10分钟过期）
    private Cache<String, String> localMappingCache;

    // 统计
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger localHits = new AtomicInteger(0);
    private final AtomicInteger snomedHits = new AtomicInteger(0);
    private final AtomicInteger llmHits = new AtomicInteger(0);
    private final AtomicInteger redisCacheHits = new AtomicInteger(0);
    private final AtomicInteger unmappedCount = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private volatile boolean alertTriggered = false;

    // 滑动窗口统计（用于实时告警判断）
    private final SlidingWindowStats slidingWindow = new SlidingWindowStats(100);

    @PostConstruct
    public void init() {
        localMappingCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
        refreshLocalCache();
        log.info("同义词扩展服务初始化完成: 本地映射缓存条目数={}", localMappingCache.estimatedSize());
    }

    /**
     * 刷新本地映射缓存（从数据库加载所有已审核的映射）
     */
    private void refreshLocalCache() {
        try {
            List<MedicalSynonymMapping> allMappings = synonymMappingMapper.findAllApproved();
            Cache<String, String> newCache = Caffeine.newBuilder()
                    .maximumSize(5000)
                    .expireAfterWrite(Duration.ofMinutes(10))
                    .build();
            for (MedicalSynonymMapping mapping : allMappings) {
                if (mapping.getColloquialTerm() != null && mapping.getStandardTerm() != null) {
                    newCache.put(mapping.getColloquialTerm(), mapping.getStandardTerm());
                }
            }
            localMappingCache = newCache;
            log.debug("本地映射缓存刷新完成: 条目数={}", allMappings.size());
        } catch (Exception e) {
            log.warn("刷新本地映射缓存失败", e);
        }
    }

    @Override
    public ExpansionResult expand(String query, List<MedicalEntityRecognitionService.MedicalEntity> entities) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();

        List<TermMapping> mappings = new ArrayList<>();
        List<String> unmappedTerms = new ArrayList<>();
        String expandedQuery = query;

        try {
            for (MedicalEntityRecognitionService.MedicalEntity entity : entities) {
                String term = entity.getText();

                // 跳过停用词和太短的词
                if (term.length() <= 1) continue;

                TermMapping mapping = lookupTermInternal(term, query);

                if (mapping != null) {
                    mappings.add(mapping);

                    // 替换查询中的口语表达为标准术语
                    if (!mapping.getStandardTerm().equals(term)) {
                        expandedQuery = expandedQuery.replace(term, mapping.getStandardTerm());
                    }
                } else {
                    unmappedTerms.add(term);
                    // 记录到审核队列
                    recordUnmappedTerm(term, query, entity.getType().name());
                }
            }

            // 更新滑动窗口并检查告警
            int totalTerms = mappings.size() + unmappedTerms.size();
            if (totalTerms > 0) {
                double unmappedRate = (double) unmappedTerms.size() / totalTerms * 100;
                slidingWindow.add(unmappedRate);
                checkAlertThreshold();
            }

            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);

            return new ExpansionResult(query, expandedQuery, mappings, unmappedTerms, processingTime);

        } catch (Exception e) {
            log.error("同义词扩展异常: query={}", query, e);
            long processingTime = System.currentTimeMillis() - startTime;
            return new ExpansionResult(query, query, mappings, unmappedTerms, processingTime);
        }
    }

    @Override
    public TermMapping lookupTerm(String colloquialTerm) {
        return lookupTermInternal(colloquialTerm, null);
    }

    @Override
    public TermMapping lookupTermForceLlm(String colloquialTerm, String contextQuery) {
        TermMapping result = tryLlmInference(colloquialTerm, contextQuery);
        if (result != null) {
            return result;
        }
        return new TermMapping(colloquialTerm, null, null, "LLM", 0.0);
    }

    /**
     * 内部术语查询：本地映射表 → Redis LLM缓存 → SNOMED CT → LLM推断
     */
    private TermMapping lookupTermInternal(String term, String contextQuery) {
        // ① 查本地映射表（内存缓存）
        String localResult = localMappingCache.getIfPresent(term);
        if (localResult != null) {
            localHits.incrementAndGet();
            return new TermMapping(term, localResult, null, "LOCAL", 1.0);
        }

        // 查询数据库
        MedicalSynonymMapping dbMapping = synonymMappingMapper.findByColloquialTerm(term);
        if (dbMapping != null) {
            localMappingCache.put(term, dbMapping.getStandardTerm());
            localHits.incrementAndGet();
            return new TermMapping(term, dbMapping.getStandardTerm(),
                    dbMapping.getSnomedConceptId(), "LOCAL", dbMapping.getConfidence() != null ? dbMapping.getConfidence() : 1.0);
        }

        // ② 查Redis LLM高置信度推断缓存（7天有效，减少LLM重复调用）
        TermMapping cachedLlm = getLlmCache(term);
        if (cachedLlm != null) {
            return cachedLlm;
        }

        // ③ 查 SNOMED CT
        try {
            SnomedTermResponse snomedResult = snomedTerminologyService.lookupByTerm(term);
            if (snomedResult != null && snomedResult.getTerm() != null) {
                snomedHits.incrementAndGet();
                return new TermMapping(term, snomedResult.getTerm(),
                        snomedResult.getConceptId(), "SNOMED_CT", 0.9);
            }
        } catch (Exception e) {
            log.debug("SNOMED CT查询失败: term={}, error={}", term, e.getMessage());
        }

        // ④ LLM临时推断（兜底）
        if (llmFallbackEnabled && contextQuery != null) {
            TermMapping llmResult = tryLlmInference(term, contextQuery);
            if (llmResult != null && llmResult.getConfidence() >= llmMinConfidence) {
                // 高置信度：采纳 + 写入Redis缓存
                setLlmCache(term, llmResult.getStandardTerm(), llmResult.getConfidence(), llmResult.getConceptId());
                llmHits.incrementAndGet();
                return llmResult;
            }

            if (llmResult != null && llmResult.getConfidence() > 0) {
                // 低置信度：不改写查询，原始Query直接走向量检索（语义容错，保证不空结果）
                // term由expand()的unmappedTerms分支统一处理，发RabbitMQ审核队列
                log.debug("LLM推断置信度过低({}<{})，不改写查询，发审核队列: term={}, guess={}",
                        llmResult.getConfidence(), llmMinConfidence, term, llmResult.getStandardTerm());
                unmappedCount.incrementAndGet();
                return null;
            }
        }

        unmappedCount.incrementAndGet();
        return null;
    }

    /**
     * LLM临时推断
     */
    private TermMapping tryLlmInference(String term, String contextQuery) {
        try {
            String prompt = buildInferencePrompt(term, contextQuery);
            String llmResponse = llmService.generateAnswerDirect(prompt);

            return parseLlmInferenceResponse(term, llmResponse);
        } catch (Exception e) {
            log.debug("LLM推断失败: term={}, error={}", term, e.getMessage());
            return null;
        }
    }

    private String buildInferencePrompt(String term, String contextQuery) {
        return String.format("""
                你是一个医学术语标准化专家。请将患者的口语表达转换为标准医学术语。

                原始查询: %s
                需要标准化的口语词: %s

                请严格按照以下JSON格式回复（不要包含其他内容）：
                {"standard_term": "标准术语", "confidence": 0.XX, "concept_type": "DRUG/DISEASE/SYMPTOM/..."}

                如果无法确定标准术语，回复：{"standard_term": null, "confidence": 0}
                """, contextQuery, term);
    }

    private TermMapping parseLlmInferenceResponse(String term, String llmResponse) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) return null;

        try {
            // 简单JSON提取
            String json = llmResponse.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            String standardTerm = extractJsonValue(json, "standard_term");
            if (standardTerm == null || "null".equals(standardTerm)) return null;

            double confidence = 0.5;
            String confStr = extractJsonValue(json, "confidence");
            if (confStr != null) {
                try { confidence = Double.parseDouble(confStr); } catch (NumberFormatException ignored) {}
            }

            String conceptType = extractJsonValue(json, "concept_type");

            return new TermMapping(term, standardTerm, conceptType, "LLM", confidence);
        } catch (Exception e) {
            log.debug("解析LLM推断响应失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) return null;

        int colonIdx = json.indexOf(':', keyIdx);
        if (colonIdx < 0) return null;

        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) return null;
            return json.substring(valueStart + 1, valueEnd);
        } else {
            int valueEnd = valueStart;
            while (valueEnd < json.length() && !Character.isWhitespace(json.charAt(valueEnd))
                    && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        }
    }

    /**
     * 记录未映射词条到审核队列
     * 1. 写入数据库（同步）
     * 2. 发送RabbitMQ审核消息（异步，可配置开关）
     */
    private void recordUnmappedTerm(String term, String contextQuery, String entityType) {
        try {
            UnmappedTermRecord existing = unmappedTermMapper.findPendingByTerm(term);
            if (existing != null) {
                unmappedTermMapper.incrementOccurrence(existing.getId());
            } else {
                UnmappedTermRecord record = new UnmappedTermRecord();
                record.setTerm(term);
                record.setContextQuery(contextQuery);
                record.setGuessedEntityType(entityType);
                record.setOccurrenceCount(1);
                record.setStatus("PENDING");
                record.setFirstSeenAt(LocalDateTime.now());
                record.setLastSeenAt(LocalDateTime.now());
                unmappedTermMapper.insert(record);

                log.info("新未映射词条加入审核队列: term={}, context={}", term, contextQuery);
            }

            // 异步发送到RabbitMQ审核队列（生产者内部处理开关和幂等）
            try {
                com.student.dto.rag.UnmappedTermMessage message =
                        new com.student.dto.rag.UnmappedTermMessage(term, contextQuery, entityType, null, null);
                unmappedTermProducer.sendToReviewQueue(message);
            } catch (Exception mqEx) {
                log.debug("MQ发送跳过（可能未启用或不可用）: term={}", term);
            }
        } catch (Exception e) {
            log.warn("记录未映射词条失败: term={}", term, e);
        }
    }

    /**
     * 从Redis读取LLM高置信度推断缓存
     * 缓存不可用时自动降级返回null，不阻断主流程
     */
    private TermMapping getLlmCache(String term) {
        if (!llmCacheEnabled) return null;
        try {
            String key = LLM_CACHE_KEY_PREFIX + term;
            Object cached = redisService.get(key);
            if (cached == null) return null;

            if (!(cached instanceof Map)) {
                log.debug("LLM缓存格式异常: term={}, type={}", term, cached.getClass().getSimpleName());
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) cached;

            String standardTerm = (String) data.get("standardTerm");
            if (standardTerm == null || standardTerm.isEmpty()) return null;

            double confidence = ((Number) data.getOrDefault("confidence", 0.0)).doubleValue();
            if (confidence < llmMinConfidence) {
                log.debug("LLM缓存置信度过低被跳过: term={}, confidence={}", term, confidence);
                return null;
            }

            String conceptId = (String) data.get("conceptId");

            redisCacheHits.incrementAndGet();
            log.debug("LLM缓存命中: term={} -> {}, confidence={}", term, standardTerm, confidence);
            return new TermMapping(term, standardTerm, conceptId, "LLM_CACHE", confidence);

        } catch (Exception e) {
            log.debug("读取LLM缓存异常（已降级）: term={}, error={}", term, e.getMessage());
            return null;
        }
    }

    /**
     * 将LLM高置信度推断结果写入Redis缓存
     * 写入失败时静默降级，不影响主流程
     */
    private void setLlmCache(String term, String standardTerm, double confidence, String conceptId) {
        if (!llmCacheEnabled) return;
        try {
            String key = LLM_CACHE_KEY_PREFIX + term;
            Map<String, Object> data = new HashMap<>(4);
            data.put("standardTerm", standardTerm);
            data.put("confidence", confidence);
            data.put("conceptId", conceptId != null ? conceptId : "");
            data.put("cachedAt", System.currentTimeMillis());

            redisService.set(key, data, llmCacheTtl);
            log.debug("LLM推断结果已缓存: term={} -> {}, confidence={}", term, standardTerm, confidence);
        } catch (Exception e) {
            log.debug("写入LLM缓存异常（已降级）: term={}, error={}", term, e.getMessage());
        }
    }

    /**
     * 检查告警阈值
     * 触发/恢复时持久化到OnlineMetricsSnapshot
     */
    private void checkAlertThreshold() {
        double currentRate = slidingWindow.getAverage();
        if (currentRate > unmappedAlertThreshold && !alertTriggered) {
            alertTriggered = true;
            log.warn("同义词扩展未映射率超过阈值: {}% > {}%，建议更新映射库",
                    String.format("%.2f", currentRate), String.format("%.2f", unmappedAlertThreshold));

            // 持久化告警快照
            try {
                OnlineMetricsSnapshot snapshot = OnlineMetricsSnapshot.builder()
                        .snapshotTime(java.time.LocalDateTime.now())
                        .windowSeconds(300)
                        .unmappedTermRate(currentRate)
                        .build();
                metricsSnapshotMapper.insert(snapshot);
            } catch (Exception e) {
                log.debug("持久化告警快照失败", e);
            }

            // Redis告警计数
            try {
                redisService.set("alert:unmapped:triggered", "true", 3600L);
            } catch (Exception ignored) {}
        } else if (currentRate <= unmappedAlertThreshold && alertTriggered) {
            alertTriggered = false;
            log.info("同义词扩展未映射率已恢复正常: {}%", currentRate);

            try {
                redisService.set("alert:unmapped:triggered", "false", 60L);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public Map<String, TermMapping> lookupBatch(List<String> terms) {
        Map<String, TermMapping> results = new ConcurrentHashMap<>();
        for (String term : terms) {
            TermMapping mapping = lookupTerm(term);
            if (mapping != null) {
                results.put(term, mapping);
            }
        }
        return results;
    }

    @Override
    public List<UnmappedTerm> getUnmappedTerms(int limit) {
        try {
            List<UnmappedTermRecord> records = unmappedTermMapper.findPendingTerms(limit);
            return records.stream()
                    .map(r -> new UnmappedTerm(
                            r.getTerm(),
                            r.getContextQuery(),
                            r.getFirstSeenAt() != null
                                    ? r.getFirstSeenAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    : 0,
                            r.getOccurrenceCount() != null ? r.getOccurrenceCount() : 1
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("获取未映射词条失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void approveMapping(String colloquialTerm, String standardTerm, String reviewer) {
        try {
            // 1. 更新审核队列状态
            UnmappedTermRecord existing = unmappedTermMapper.findPendingByTerm(colloquialTerm);
            if (existing != null) {
                existing.setStatus("APPROVED");
                existing.setReviewer(reviewer);
                existing.setReviewNote("人工审核通过，映射为: " + standardTerm);
                unmappedTermMapper.updateById(existing);
            }

            // 2. 写入映射表
            MedicalSynonymMapping mapping = new MedicalSynonymMapping();
            mapping.setColloquialTerm(colloquialTerm);
            mapping.setStandardTerm(standardTerm);
            mapping.setSource("MANUAL");
            mapping.setStatus("APPROVED");
            mapping.setReviewer(reviewer);
            mapping.setConfidence(1.0);
            mapping.setUsageCount(0);
            synonymMappingMapper.insert(mapping);

            // 3. 更新内存缓存
            localMappingCache.put(colloquialTerm, standardTerm);

            // 4. 发送Redis Pub/Sub广播通知所有节点刷新缓存
            try {
                redisService.set("term:cache:event:" + System.currentTimeMillis(),
                        colloquialTerm + "->" + standardTerm, 60L);
            } catch (Exception e) {
                log.debug("Redis Pub/Sub广播失败，仅本地缓存已刷新: term={}", colloquialTerm);
            }

            log.info("人工审核通过: {} -> {}, 审核人={}", colloquialTerm, standardTerm, reviewer);
        } catch (Exception e) {
            log.error("审核映射失败: {} -> {}", colloquialTerm, standardTerm, e);
        }
    }

    @Override
    public ExpansionStats getStats() {
        int total = totalQueries.get();
        int localH = localHits.get();
        int snomedH = snomedHits.get();
        int llmH = llmHits.get() + redisCacheHits.get();
        int unmapped = unmappedCount.get();
        int totalTermOps = localH + snomedH + llmH + unmapped;
        double unmappedRate = totalTermOps > 0 ? (double) unmapped / totalTermOps * 100 : 0;
        long pendingCount = unmappedTermMapper.countPending();
        double avgTime = total > 0 ? (double) totalProcessingTime.get() / total : 0;

        return new ExpansionStats(total, localH, snomedH, llmH,
                unmapped, unmappedRate, pendingCount, avgTime, alertTriggered);
    }

    @Override
    public boolean isAvailable() {
        return synonymMappingMapper != null && snomedTerminologyService != null;
    }

    @Override
    public int cleanupByStandardTerm(String standardTerm) {
        int deleted = synonymMappingMapper.deleteByStandardTerm(standardTerm);
        log.info("清理映射记录完成: standardTerm={}, 删除条数={}", standardTerm, deleted);
        refreshLocalCache();
        return deleted;
    }

    @Override
    public int batchUpsertMappings(List<Map<String, Object>> mappings) {
        int inserted = 0;
        for (Map<String, Object> m : mappings) {
            String colloquialTerm = (String) m.get("colloquialTerm");
            String standardTerm = (String) m.get("standardTerm");
            if (colloquialTerm == null || standardTerm == null) continue;

            // 跳过已存在的
            if (synonymMappingMapper.existsByColloquialTerm(colloquialTerm) > 0) {
                log.debug("映射已存在，跳过: {} -> {}", colloquialTerm, standardTerm);
                continue;
            }

            MedicalSynonymMapping mapping = new MedicalSynonymMapping();
            mapping.setColloquialTerm(colloquialTerm);
            mapping.setStandardTerm(standardTerm);
            mapping.setSource("LLM_INFERRED");
            mapping.setStatus("APPROVED");
            mapping.setReviewer("system");
            Object conf = m.get("confidence");
            mapping.setConfidence(conf instanceof Number ? ((Number) conf).doubleValue() : 1.0);
            mapping.setUsageCount(0);
            synonymMappingMapper.insert(mapping);
            inserted++;
        }
        log.info("批量写入映射完成: 提交{}条, 实际写入{}条", mappings.size(), inserted);
        refreshLocalCache();
        return inserted;
    }

    /**
     * 定时刷新本地缓存（每5分钟）
     */
    @Scheduled(fixedDelay = 300000)
    public void scheduledRefreshCache() {
        refreshLocalCache();
    }

    /**
     * 定时输出统计日志（每30分钟）
     */
    @Scheduled(fixedDelay = 1800000)
    public void logStats() {
        ExpansionStats stats = getStats();
        int redisH = redisCacheHits.get();
        log.info("同义词扩展统计: 总查询={}, 本地命中={}, Redis缓存命中={}, SNOMED命中={}, LLM实时命中={}, 未映射={}, "
                        + "未映射率={}%, 待审核={}, 告警={}",
                stats.getTotalQueries(), stats.getLocalHits(), redisH,
                stats.getSnomedHits(), llmHits.get(), stats.getUnmappedCount(),
                String.format("%.2f", stats.getUnmappedRate()), stats.getPendingReviewCount(),
                stats.isAlertTriggered() ? "触发" : "正常");
    }

    /**
     * 滑动窗口统计（用于计算近期未映射率）
     */
    private static class SlidingWindowStats {
        private final int maxSize;
        private final LinkedList<Double> values = new LinkedList<>();

        SlidingWindowStats(int maxSize) {
            this.maxSize = maxSize;
        }

        synchronized void add(double value) {
            values.addLast(value);
            if (values.size() > maxSize) {
                values.removeFirst();
            }
        }

        synchronized double getAverage() {
            if (values.isEmpty()) return 0;
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
    }
}
