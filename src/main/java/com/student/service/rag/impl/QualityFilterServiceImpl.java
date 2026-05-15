package com.student.service.rag.impl;

import com.student.service.rag.MedicalEntityRecognitionService;
import com.student.service.rag.MultiRetrievalService;
import com.student.service.rag.QualityFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 质量过滤服务实现
 * 对检索结果进行四层规则过滤：
 * 1. 片段长度过滤（< 20 字丢弃）
 * 2. 黑名单域名过滤（黑名单来源 + 短文本丢弃）
 * 3. 纯免责声明检测（无实质医学内容丢弃）
 * 4. 矛盾检测（药物-人群对正反断言冲突）
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class QualityFilterServiceImpl implements QualityFilterService {

    /** 最小片段长度（字符数） */
    private static final int MIN_FRAGMENT_LENGTH = 20;
    /** 黑名单来源的额外长度阈值（低于此值的黑名单来源才丢弃） */
    private static final int BLACKLIST_MIN_LENGTH = 100;

    /** 黑名单域名（可扩展为配置驱动） */
    private static final Set<String> BLACKLIST_DOMAINS = Set.of(
            "zhidao.baidu.com", "baike.baidu.com",
            "zhihu.com", "tieba.baidu.com",
            "haodf.com", "xywy.com"
    );

    /** 纯免责声明特征词 */
    private static final Set<String> DISCLAIMER_KEYWORDS = Set.of(
            "免责声明", "仅供参考", "不能替代", "请咨询", "谨遵医嘱",
            "本内容不代表", "不构成医疗建议", "如需诊疗", "请前往"
    );

    /** 实质医学内容特征词（用于判断是否有医学实质） */
    private static final Set<String> MEDICAL_SUBSTANCE_KEYWORDS = Set.of(
            "适应症", "用法用量", "不良反应", "禁忌", "注意事项",
            "药理作用", "临床试验", "诊断", "治疗", "手术",
            "检查", "病因", "发病机制", "临床表现", "预后",
            "预防", "流行病学", "指南", "共识", "标准"
    );

    /** 正向断言模式（药物可用于某人群） */
    private static final List<String> POSITIVE_PATTERNS = List.of(
            "可用", "安全", "适用", "推荐", "首选", "一线",
            "有效", "可以服用", "可以使用", "可用于", "适用于"
    );

    /** 负向断言模式（药物禁用于某人群） */
    private static final List<String> NEGATIVE_PATTERNS = List.of(
            "禁用", "禁忌", "不宜", "避免", "慎用", "不推荐",
            "不应使用", "禁止使用", "不得使用", "忌用", "不可"
    );

    /** 矛盾检测需要的最低权威性阈值 */
    private static final double CONTRADICTION_AUTHORITY_THRESHOLD = 0.7;

    private final MedicalEntityRecognitionService entityRecognitionService;

    public QualityFilterServiceImpl(MedicalEntityRecognitionService entityRecognitionService) {
        this.entityRecognitionService = entityRecognitionService;
    }

    @Override
    public FilterResult filter(List<MultiRetrievalService.RetrievalResult> results) {
        FilterResult result = new FilterResult();
        if (results == null || results.isEmpty()) {
            return result;
        }

        for (MultiRetrievalService.RetrievalResult r : results) {
            // 规则1：片段长度过滤
            if (isTooShort(r)) {
                result.discard(r, "片段过短(<20字)");
                continue;
            }
            // 规则2：黑名单域名过滤
            if (isBlacklistedSource(r)) {
                result.discard(r, "黑名单来源");
                continue;
            }
            // 规则3：纯免责声明过滤
            if (isDisclaimerOnly(r)) {
                result.discard(r, "纯免责声明无实质内容");
                continue;
            }
            result.pass(r);
        }

        if (result.getDiscardedCount() > 0) {
            log.info("质量过滤完成: 输入={}, 通过={}, 丢弃={}, 原因={}",
                    results.size(), result.getPassedCount(),
                    result.getDiscardedCount(), result.getDiscardReasons());
        }
        return result;
    }

    @Override
    public ContradictionResult detectContradictions(
            List<MultiRetrievalService.RetrievalResult> results, String query) {
        if (results == null || results.isEmpty() || query == null || query.trim().isEmpty()) {
            return ContradictionResult.noContradiction();
        }

        // 提取查询中的药物和人群实体
        List<MedicalEntityRecognitionService.MedicalEntity> entities =
                entityRecognitionService.recognize(query);

        List<String> drugs = new ArrayList<>();
        List<String> populations = new ArrayList<>();

        for (MedicalEntityRecognitionService.MedicalEntity entity : entities) {
            if (entity.getType() == MedicalEntityRecognitionService.EntityType.DRUG) {
                drugs.add(entity.getText());
            } else if (entity.getType() == MedicalEntityRecognitionService.EntityType.POPULATION) {
                populations.add(entity.getText());
            }
        }

        if (drugs.isEmpty() || populations.isEmpty()) {
            return ContradictionResult.noContradiction();
        }

        // 检查每个药物-人群对是否存在矛盾
        for (String drug : drugs) {
            for (String population : populations) {
                ContradictionResult cr = checkDrugPopulationPair(results, drug, population);
                if (cr.isHasContradiction()) {
                    return cr;
                }
            }
        }

        return ContradictionResult.noContradiction();
    }

    // ========== 私有过滤规则 ==========

    /** 规则1：片段长度 < 20 字 */
    private boolean isTooShort(MultiRetrievalService.RetrievalResult r) {
        String content = r.getContent();
        return content == null || content.trim().length() < MIN_FRAGMENT_LENGTH;
    }

    /** 规则2：来源域名在黑名单 且 内容 < 100 字 */
    private boolean isBlacklistedSource(MultiRetrievalService.RetrievalResult r) {
        String sourceUrl = r.getMetadataString("source_url");
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            return false;
        }
        String host = extractHost(sourceUrl);
        if (host == null || !BLACKLIST_DOMAINS.contains(host)) {
            return false;
        }
        String content = r.getContent();
        return content != null && content.trim().length() < BLACKLIST_MIN_LENGTH;
    }

    /** 规则3：只含免责声明而无实质医学内容 */
    private boolean isDisclaimerOnly(MultiRetrievalService.RetrievalResult r) {
        String content = r.getContent();
        if (content == null || content.trim().isEmpty()) {
            return true;
        }

        boolean hasDisclaimer = DISCLAIMER_KEYWORDS.stream().anyMatch(content::contains);
        if (!hasDisclaimer) {
            return false;
        }

        // 有免责声明时，检查是否同时包含实质医学内容
        boolean hasSubstance = MEDICAL_SUBSTANCE_KEYWORDS.stream().anyMatch(content::contains);
        return !hasSubstance;
    }

    /** 提取URL中的主机名 */
    private String extractHost(String url) {
        try {
            String host = url;
            if (host.contains("://")) {
                host = host.substring(host.indexOf("://") + 3);
            }
            if (host.contains("/")) {
                host = host.substring(0, host.indexOf("/"));
            }
            if (host.contains("?")) {
                host = host.substring(0, host.indexOf("?"));
            }
            return host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 矛盾检测 ==========

    /**
     * 检查特定药物-人群对是否存在正反断言冲突
     */
    private ContradictionResult checkDrugPopulationPair(
            List<MultiRetrievalService.RetrievalResult> results,
            String drug, String population) {

        ResultAssertion positiveAssertion = null;
        ResultAssertion negativeAssertion = null;

        for (MultiRetrievalService.RetrievalResult r : results) {
            String content = r.getContent();
            if (content == null) continue;

            // 必须同时包含药物和人群关键词
            if (!content.contains(drug) || !content.contains(population)) {
                continue;
            }

            // 检测正向断言
            for (String posPattern : POSITIVE_PATTERNS) {
                int idx = content.indexOf(posPattern);
                if (idx >= 0) {
                    // 检查上下文：确保断言与药物-人群对相关
                    String context = getContext(content, idx, 50);
                    if (context.contains(drug) || context.contains(population)) {
                        double authority = estimateAuthority(r);
                        if (positiveAssertion == null || authority > positiveAssertion.authority) {
                            positiveAssertion = new ResultAssertion(r, authority, true, posPattern);
                        }
                    }
                }
            }

            // 检测负向断言
            for (String negPattern : NEGATIVE_PATTERNS) {
                int idx = content.indexOf(negPattern);
                if (idx >= 0) {
                    String context = getContext(content, idx, 50);
                    if (context.contains(drug) || context.contains(population)) {
                        double authority = estimateAuthority(r);
                        if (negativeAssertion == null || authority > negativeAssertion.authority) {
                            negativeAssertion = new ResultAssertion(r, authority, false, negPattern);
                        }
                    }
                }
            }
        }

        // 双方都存在且权威性均 > 阈值 → 矛盾
        if (positiveAssertion != null && negativeAssertion != null
                && positiveAssertion.authority > CONTRADICTION_AUTHORITY_THRESHOLD
                && negativeAssertion.authority > CONTRADICTION_AUTHORITY_THRESHOLD) {

            String pair = drug + "-" + population;
            String detail = String.format(
                    "正向断言(%.2f): '%s', 负向断言(%.2f): '%s'",
                    positiveAssertion.authority, positiveAssertion.pattern,
                    negativeAssertion.authority, negativeAssertion.pattern);
            double maxAuth = Math.max(positiveAssertion.authority, negativeAssertion.authority);

            log.warn("矛盾检测命中: pair={}, detail={}", pair, detail);
            return ContradictionResult.conflict(pair, detail, maxAuth);
        }

        return ContradictionResult.noContradiction();
    }

    /** 获取断言周围的上下文 */
    private String getContext(String content, int pos, int window) {
        int start = Math.max(0, pos - window);
        int end = Math.min(content.length(), pos + window);
        return content.substring(start, end);
    }

    /**
     * 估算检索结果的权威性分数
     * 优先级：metadata中的authority > source_type映射 > 默认值
     */
    private double estimateAuthority(MultiRetrievalService.RetrievalResult r) {
        // 从metadata读取
        Object authObj = r.getMetadata().get("authority");
        if (authObj instanceof Number) {
            return ((Number) authObj).doubleValue();
        }
        // 从source_type映射
        String sourceType = r.getMetadataString("source_type");
        if (sourceType != null) {
            return mapSourceTypeToAuthority(sourceType);
        }
        return 0.5; // 默认中等权威
    }

    /** 来源类型 → 权威系数映射 */
    private double mapSourceTypeToAuthority(String sourceType) {
        return switch (sourceType.toLowerCase()) {
            case "systematic_review", "meta_analysis", "clinical_guideline" -> 1.0;
            case "drug_label" -> 0.95;
            case "rct" -> 0.9;
            case "cohort_study", "case_control" -> 0.7;
            case "hospital_science", "medical_encyclopedia" -> 0.6;
            case "expert_opinion", "case_report" -> 0.5;
            case "general_website", "self_media" -> 0.3;
            case "forum", "qa" -> 0.1;
            default -> 0.5;
        };
    }

    /** 断言的内部记录 */
    private static class ResultAssertion {
        final MultiRetrievalService.RetrievalResult result;
        final double authority;
        final boolean isPositive;
        final String pattern;

        ResultAssertion(MultiRetrievalService.RetrievalResult result,
                        double authority, boolean isPositive, String pattern) {
            this.result = result;
            this.authority = authority;
            this.isPositive = isPositive;
            this.pattern = pattern;
        }
    }
}
