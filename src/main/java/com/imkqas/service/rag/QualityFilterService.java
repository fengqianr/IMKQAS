package com.imkqas.service.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量过滤服务接口
 * 对检索结果进行规则过滤和安全质量控制
 * 包含：片段长度过滤、黑名单过滤、纯免责声明检测、矛盾检测
 *
 * @author 系统
 * @version 1.0
 */
public interface QualityFilterService {

    /**
     * 对检索结果进行质量过滤
     *
     * @param results 原始检索结果
     * @return 过滤结果（通过 + 丢弃 + 原因统计）
     */
    FilterResult filter(List<MultiRetrievalService.RetrievalResult> results);

    /**
     * 对检索结果进行质量过滤（含查询上下文）
     * 当 query 中包含药物+人群实体时，才启用禁忌规则的在线文档检测
     *
     * @param results 原始检索结果
     * @param query   用户查询文本
     * @return 过滤结果（通过 + 丢弃 + 原因统计）
     */
    default FilterResult filter(List<MultiRetrievalService.RetrievalResult> results, String query) {
        return filter(results);
    }

    /**
     * 检测高风险查询中的矛盾信息
     * 针对药物-人群对，检测是否存在正反断言冲突
     *
     * @param results 检索结果
     * @param query   原始查询
     * @return 矛盾检测结果
     */
    ContradictionResult detectContradictions(
            List<MultiRetrievalService.RetrievalResult> results, String query);

    /** 过滤结果 */
    class FilterResult {
        private final List<MultiRetrievalService.RetrievalResult> passed;
        private final List<MultiRetrievalService.RetrievalResult> discarded;
        private final Map<String, Integer> discardReasons;

        public FilterResult() {
            this.passed = new ArrayList<>();
            this.discarded = new ArrayList<>();
            this.discardReasons = new LinkedHashMap<>();
        }

        public void pass(MultiRetrievalService.RetrievalResult r) { passed.add(r); }

        public void discard(MultiRetrievalService.RetrievalResult r, String reason) {
            discarded.add(r);
            discardReasons.merge(reason, 1, Integer::sum);
        }

        /** 降权：修改分数后仍保留到通过列表 */
        public void downgrade(MultiRetrievalService.RetrievalResult r, double factor, String reason) {
            r.setScore(r.getScore() * factor);
            passed.add(r);
            discardReasons.merge("降权-" + reason, 1, Integer::sum);
        }

        public List<MultiRetrievalService.RetrievalResult> getPassed() { return passed; }
        public List<MultiRetrievalService.RetrievalResult> getDiscarded() { return discarded; }
        public Map<String, Integer> getDiscardReasons() { return discardReasons; }
        public int getPassedCount() { return passed.size(); }
        public int getDiscardedCount() { return discarded.size(); }
    }

    /** 矛盾检测结果 */
    class ContradictionResult {

        /** 裁决类型 */
        public enum ResolutionType {
            /** 无矛盾 */
            NONE,
            /** 权威性有差异 → 以高权威表述为准，过滤低权威结果 */
            RESOLVED,
            /** 权威性相当 → 交LLM综合判断，附加冲突说明 */
            UNRESOLVED
        }

        private final boolean hasContradiction;
        private final String drugPopulationPair;
        private final String detail;
        private final double maxAuthority;
        /** 裁决类型 */
        private final ResolutionType resolutionType;
        /** RESOLVED 时：胜出方的权威分数 */
        private final double winnerAuthority;
        /** RESOLVED 时：胜出方的断言方向（true=正向/可用, false=负向/禁用） */
        private final boolean winnerDirectionPositive;

        public ContradictionResult(boolean hasContradiction, String drugPopulationPair,
                                   String detail, double maxAuthority) {
            this(hasContradiction, drugPopulationPair, detail, maxAuthority,
                 ResolutionType.NONE, 0.0, false);
        }

        public ContradictionResult(boolean hasContradiction, String drugPopulationPair,
                                   String detail, double maxAuthority,
                                   ResolutionType resolutionType, double winnerAuthority,
                                   boolean winnerDirectionPositive) {
            this.hasContradiction = hasContradiction;
            this.drugPopulationPair = drugPopulationPair;
            this.detail = detail;
            this.maxAuthority = maxAuthority;
            this.resolutionType = resolutionType;
            this.winnerAuthority = winnerAuthority;
            this.winnerDirectionPositive = winnerDirectionPositive;
        }

        public static ContradictionResult noContradiction() {
            return new ContradictionResult(false, null, null, 0.0,
                    ResolutionType.NONE, 0.0, false);
        }

        /**
         * 权威性有差异 → 以高权威表述为准
         */
        public static ContradictionResult resolved(String pair, String detail,
                                                    double winnerAuth, boolean winnerPositive) {
            return new ContradictionResult(true, pair, detail,
                    winnerAuth, ResolutionType.RESOLVED, winnerAuth, winnerPositive);
        }

        /**
         * 权威性相当 → 交LLM综合判断
         */
        public static ContradictionResult unresolved(String pair, String detail, double maxAuth) {
            return new ContradictionResult(true, pair, detail, maxAuth,
                    ResolutionType.UNRESOLVED, 0.0, false);
        }

        /** @deprecated 使用 resolved() 或 unresolved() 替代 */
        @Deprecated
        public static ContradictionResult conflict(String pair, String detail, double authority) {
            return new ContradictionResult(true, pair, detail, authority);
        }

        public boolean isHasContradiction() { return hasContradiction; }
        public String getDrugPopulationPair() { return drugPopulationPair; }
        public String getDetail() { return detail; }
        public double getMaxAuthority() { return maxAuthority; }
        public ResolutionType getResolutionType() { return resolutionType; }
        public double getWinnerAuthority() { return winnerAuthority; }
        public boolean isWinnerDirectionPositive() { return winnerDirectionPositive; }
    }
}
