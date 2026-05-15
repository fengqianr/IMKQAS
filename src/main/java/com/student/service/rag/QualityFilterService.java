package com.student.service.rag;

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

        public List<MultiRetrievalService.RetrievalResult> getPassed() { return passed; }
        public List<MultiRetrievalService.RetrievalResult> getDiscarded() { return discarded; }
        public Map<String, Integer> getDiscardReasons() { return discardReasons; }
        public int getPassedCount() { return passed.size(); }
        public int getDiscardedCount() { return discarded.size(); }
    }

    /** 矛盾检测结果 */
    class ContradictionResult {
        private final boolean hasContradiction;
        private final String drugPopulationPair;
        private final String detail;
        private final double maxAuthority;

        public ContradictionResult(boolean hasContradiction, String drugPopulationPair,
                                   String detail, double maxAuthority) {
            this.hasContradiction = hasContradiction;
            this.drugPopulationPair = drugPopulationPair;
            this.detail = detail;
            this.maxAuthority = maxAuthority;
        }

        public static ContradictionResult noContradiction() {
            return new ContradictionResult(false, null, null, 0.0);
        }

        public static ContradictionResult conflict(String pair, String detail, double authority) {
            return new ContradictionResult(true, pair, detail, authority);
        }

        public boolean isHasContradiction() { return hasContradiction; }
        public String getDrugPopulationPair() { return drugPopulationPair; }
        public String getDetail() { return detail; }
        public double getMaxAuthority() { return maxAuthority; }
    }
}
