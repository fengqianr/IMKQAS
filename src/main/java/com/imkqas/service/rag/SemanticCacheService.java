package com.imkqas.service.rag;

import java.util.List;
import java.util.Map;

/**
 * 语义化缓存链服务接口
 * 核心：问题归一化 + 语义缓存键（排序无关）+ 版本控制 → 相似语义问题复用答案
 * 降低LLM调用延迟和成本，同时通过版本控制保证缓存与知识库同步
 *
 * @author 系统
 * @version 1.0
 */
public interface SemanticCacheService {

    /**
     * 查询语义缓存
     *
     * @param normalizedQuery 归一化后的查询
     * @param fragmentIds    排序后的知识片段ID列表（用于生成缓存键）
     * @return 缓存命中时返回CachedAnswer，否则返回null
     */
    CachedAnswer get(String normalizedQuery, List<Long> fragmentIds);

    /**
     * 存储语义缓存
     *
     * @param normalizedQuery 归一化后的查询
     * @param fragmentIds     排序后的知识片段ID列表
     * @param answer          生成的回答
     * @param sources         参考来源列表
     * @param confidence      LLM 原生置信度（命中时沿用，避免门控误判）
     * @param citations       参考文献引用列表（命中时沿用，避免丢失引用）
     */
    void put(String normalizedQuery, List<Long> fragmentIds, String answer,
             List<String> sources, double confidence, List<QaService.SourceCitation> citations);

    /**
     * 按标准化术语精确删除缓存（术语变更时使用）
     * 仅删除一个key，而非全量清空
     *
     * @param standardTerm 标准化后的术语（如"发热"）
     */
    void invalidateByTerm(String standardTerm);

    /**
     * 全量清空语义缓存
     */
    void invalidateAll();

    /**
     * 递增知识库版本号（知识库更新时调用）
     * 版本号变更后旧缓存自然失效
     *
     * @return 新版本号
     */
    int incrementKnowledgeVersion();

    /**
     * 获取当前知识库版本号
     */
    int getCurrentVersion();

    /**
     * 获取缓存统计信息
     */
    SemanticCacheStats getStats();

    /**
     * 检查服务是否可用
     */
    boolean isAvailable();

    // ========== 内部数据类型 ==========

    /**
     * 缓存的回答
     */
    class CachedAnswer {
        private final String answer;
        private final List<String> sources;
        private final List<QaService.SourceCitation> citations;
        private final long timestamp;
        private final int version;
        private final double confidence;

        public CachedAnswer(String answer, List<String> sources, List<QaService.SourceCitation> citations,
                            long timestamp, int version, double confidence) {
            this.answer = answer;
            this.sources = sources;
            this.citations = citations;
            this.timestamp = timestamp;
            this.version = version;
            this.confidence = confidence;
        }

        public String getAnswer() { return answer; }
        public List<String> getSources() { return sources; }
        public List<QaService.SourceCitation> getCitations() { return citations; }
        public long getTimestamp() { return timestamp; }
        public int getVersion() { return version; }
        public double getConfidence() { return confidence; }
    }

    /**
     * 语义缓存统计信息
     */
    class SemanticCacheStats {
        private final long hits;
        private final long misses;
        private final long puts;
        private final long invalidations;
        private final double hitRate;
        private final int currentVersion;

        public SemanticCacheStats(long hits, long misses, long puts, long invalidations,
                                   double hitRate, int currentVersion) {
            this.hits = hits;
            this.misses = misses;
            this.puts = puts;
            this.invalidations = invalidations;
            this.hitRate = hitRate;
            this.currentVersion = currentVersion;
        }

        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getPuts() { return puts; }
        public long getInvalidations() { return invalidations; }
        public double getHitRate() { return hitRate; }
        public int getCurrentVersion() { return currentVersion; }
    }
}
