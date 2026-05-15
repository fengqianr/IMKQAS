package com.student.service.rag;

import java.util.List;
import java.util.Map;

/**
 * 同义词扩展服务接口
 * 将口语化医学术语映射为标准医学名称，用于提升检索精度
 * 流程：本地映射表 → SNOMED CT → LLM临时推断 + 人工审核队列 + 监控告警
 *
 * @author 系统
 * @version 1.0
 */
public interface SynonymExpansionService {

    /**
     * 对查询中的医学实体进行同义词扩展
     * 将口语表达映射为标准术语，返回扩展后的查询文本
     *
     * @param query 原始查询
     * @param entities 识别到的医学实体
     * @return 扩展结果（包含标准化查询和映射详情）
     */
    ExpansionResult expand(String query, List<MedicalEntityRecognitionService.MedicalEntity> entities);

    /**
     * 查询单个术语的标准名称
     *
     * @param colloquialTerm 口语表达
     * @return 标准化结果
     */
    TermMapping lookupTerm(String colloquialTerm);

    /**
     * 批量查询术语的标准名称
     *
     * @param terms 术语列表
     * @return 术语到标准化结果的映射
     */
    Map<String, TermMapping> lookupBatch(List<String> terms);

    /**
     * 获取未映射词条列表（供人工审核）
     *
     * @param limit 最大返回数
     * @return 未映射词条列表
     */
    List<UnmappedTerm> getUnmappedTerms(int limit);

    /**
     * 人工审核后回写映射表
     *
     * @param colloquialTerm 口语表达
     * @param standardTerm 标准术语
     * @param reviewer 审核人
     */
    void approveMapping(String colloquialTerm, String standardTerm, String reviewer);

    /**
     * 获取服务统计信息
     */
    ExpansionStats getStats();

    /**
     * 检查服务是否可用
     */
    boolean isAvailable();

    // ========== 内部数据类型 ==========

    /** 扩展结果 */
    class ExpansionResult {
        private final String originalQuery;
        private final String expandedQuery;
        private final List<TermMapping> mappings;
        private final List<String> unmappedTerms;
        private final long processingTimeMs;

        public ExpansionResult(String originalQuery, String expandedQuery,
                              List<TermMapping> mappings, List<String> unmappedTerms,
                              long processingTimeMs) {
            this.originalQuery = originalQuery;
            this.expandedQuery = expandedQuery;
            this.mappings = mappings;
            this.unmappedTerms = unmappedTerms;
            this.processingTimeMs = processingTimeMs;
        }

        public String getOriginalQuery() { return originalQuery; }
        public String getExpandedQuery() { return expandedQuery; }
        public List<TermMapping> getMappings() { return mappings; }
        public List<String> getUnmappedTerms() { return unmappedTerms; }
        public long getProcessingTimeMs() { return processingTimeMs; }
    }

    /** 术语映射 */
    class TermMapping {
        private final String colloquialTerm;
        private final String standardTerm;
        private final String conceptId;
        private final String source; // LOCAL / SNOMED_CT / LLM
        private final double confidence;

        public TermMapping(String colloquialTerm, String standardTerm,
                          String conceptId, String source, double confidence) {
            this.colloquialTerm = colloquialTerm;
            this.standardTerm = standardTerm;
            this.conceptId = conceptId;
            this.source = source;
            this.confidence = confidence;
        }

        public String getColloquialTerm() { return colloquialTerm; }
        public String getStandardTerm() { return standardTerm; }
        public String getConceptId() { return conceptId; }
        public String getSource() { return source; }
        public double getConfidence() { return confidence; }
    }

    /** 未映射词条 */
    class UnmappedTerm {
        private final String term;
        private final String context;
        private final long firstSeenAt;
        private final int occurrenceCount;

        public UnmappedTerm(String term, String context, long firstSeenAt, int occurrenceCount) {
            this.term = term;
            this.context = context;
            this.firstSeenAt = firstSeenAt;
            this.occurrenceCount = occurrenceCount;
        }

        public String getTerm() { return term; }
        public String getContext() { return context; }
        public long getFirstSeenAt() { return firstSeenAt; }
        public int getOccurrenceCount() { return occurrenceCount; }
    }

    /** 扩展统计 */
    class ExpansionStats {
        private final int totalQueries;
        private final int localHits;
        private final int snomedHits;
        private final int llmHits;
        private final int unmappedCount;
        private final double unmappedRate;
        private final long pendingReviewCount;
        private final double avgProcessingTimeMs;
        private final boolean alertTriggered;

        public ExpansionStats(int totalQueries, int localHits, int snomedHits, int llmHits,
                             int unmappedCount, double unmappedRate, long pendingReviewCount,
                             double avgProcessingTimeMs, boolean alertTriggered) {
            this.totalQueries = totalQueries;
            this.localHits = localHits;
            this.snomedHits = snomedHits;
            this.llmHits = llmHits;
            this.unmappedCount = unmappedCount;
            this.unmappedRate = unmappedRate;
            this.pendingReviewCount = pendingReviewCount;
            this.avgProcessingTimeMs = avgProcessingTimeMs;
            this.alertTriggered = alertTriggered;
        }

        public int getTotalQueries() { return totalQueries; }
        public int getLocalHits() { return localHits; }
        public int getSnomedHits() { return snomedHits; }
        public int getLlmHits() { return llmHits; }
        public int getUnmappedCount() { return unmappedCount; }
        public double getUnmappedRate() { return unmappedRate; }
        public long getPendingReviewCount() { return pendingReviewCount; }
        public double getAvgProcessingTimeMs() { return avgProcessingTimeMs; }
        public boolean isAlertTriggered() { return alertTriggered; }
    }
}
