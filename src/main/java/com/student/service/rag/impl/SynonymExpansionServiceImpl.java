package com.student.service.rag.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.student.entity.synonym.MedicalSynonymMapping;
import com.student.entity.synonym.UnmappedTermRecord;
import com.student.mapper.MedicalSynonymMappingMapper;
import com.student.mapper.UnmappedTermRecordMapper;
import com.student.service.LlmService;
import com.student.service.rag.MedicalEntityRecognitionService;
import com.student.service.rag.SynonymExpansionService;
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

    public SynonymExpansionServiceImpl(
            MedicalSynonymMappingMapper synonymMappingMapper,
            UnmappedTermRecordMapper unmappedTermMapper,
            SnomedTerminologyService snomedTerminologyService,
            @Lazy LlmService llmService) {
        this.synonymMappingMapper = synonymMappingMapper;
        this.unmappedTermMapper = unmappedTermMapper;
        this.snomedTerminologyService = snomedTerminologyService;
        this.llmService = llmService;
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

    // 本地映射表内存缓存（Caffeine，10分钟过期）
    private Cache<String, String> localMappingCache;

    // 统计
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger localHits = new AtomicInteger(0);
    private final AtomicInteger snomedHits = new AtomicInteger(0);
    private final AtomicInteger llmHits = new AtomicInteger(0);
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

    /**
     * 内部术语查询：本地映射表 → SNOMED CT → LLM推断
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

        // ② 查 SNOMED CT
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

        // ③ LLM临时推断（兜底）
        if (llmFallbackEnabled && contextQuery != null) {
            TermMapping llmResult = tryLlmInference(term, contextQuery);
            if (llmResult != null && llmResult.getConfidence() >= llmMinConfidence) {
                llmHits.incrementAndGet();
                return llmResult;
            }

            // LLM低置信度结果：本次使用但不写入映射表
            if (llmResult != null) {
                log.debug("LLM推断置信度过低，仅本次使用: term={}, guess={}, confidence={}",
                        term, llmResult.getStandardTerm(), llmResult.getConfidence());
                return llmResult;
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

            return new TermMapping(term, standardTerm, null, "LLM", confidence);
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
        } catch (Exception e) {
            log.warn("记录未映射词条失败: term={}", term, e);
        }
    }

    /**
     * 检查告警阈值
     */
    private void checkAlertThreshold() {
        double currentRate = slidingWindow.getAverage();
        if (currentRate > unmappedAlertThreshold && !alertTriggered) {
            alertTriggered = true;
            log.warn("同义词扩展未映射率超过阈值: {}% > {}%，建议更新映射库",
                    String.format("%.2f", currentRate), String.format("%.2f", unmappedAlertThreshold));
        } else if (currentRate <= unmappedAlertThreshold && alertTriggered) {
            alertTriggered = false;
            log.info("同义词扩展未映射率已恢复正常: {:.2f}%", currentRate);
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
        int llmH = llmHits.get();
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
        log.info("同义词扩展统计: 总查询={}, 本地命中={}, SNOMED命中={}, LLM命中={}, 未映射={}, "
                        + "未映射率={}%, 待审核={}, 告警={}",
                stats.getTotalQueries(), stats.getLocalHits(), stats.getSnomedHits(),
                stats.getLlmHits(), stats.getUnmappedCount(),
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
