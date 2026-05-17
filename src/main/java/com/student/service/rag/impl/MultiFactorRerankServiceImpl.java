package com.student.service.rag.impl;

import com.student.service.rag.CrossEncoderRerankService;
import com.student.service.rag.MultiFactorRerankService;
import com.student.service.rag.MultiRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多因子重排序服务实现
 * 综合四个维度进行精确重排序：
 * 1. 权威性（来源类型 → 证据金字塔系数）
 * 2. 时效性（指数衰减，按知识类型设定半衰期）
 * 3. 语义相似度（交叉编码器分数）
 * 4. 意图匹配度（查询意图与文档内容的关键词匹配）
 * <p>
 * 公式：final = wa * authority + wt * timeliness + ws * semantic + wi * intent
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class MultiFactorRerankServiceImpl implements MultiFactorRerankService {

    /** 默认各维度权重（总和为1.0） */
    private static final double DEFAULT_WEIGHT_AUTHORITY = 0.3;
    private static final double DEFAULT_WEIGHT_TIMELINESS = 0.15;
    private static final double DEFAULT_WEIGHT_SEMANTIC = 0.35;
    private static final double DEFAULT_WEIGHT_INTENT = 0.2;

    /** 各知识类型的半衰期（年） */
    private static final Map<KnowledgeType, Double> HALF_LIVES = Map.of(
            KnowledgeType.TREATMENT, 3.0,
            KnowledgeType.DIAGNOSIS, 6.0,
            KnowledgeType.DEVICE, 5.0,
            KnowledgeType.BASIC_SCIENCE, 18.0,
            KnowledgeType.PUBLIC_HEALTH, 4.0,
            KnowledgeType.CLASSIC_RESEARCH, 100.0,  // 几乎不衰减
            KnowledgeType.UNKNOWN, 5.0
    );

    /** 查询意图类型 */
    enum QueryIntent {
        DISEASE, DRUG, SYMPTOM, TREATMENT, PREVENTION,
        EXAMINATION, EMERGENCY, DEPARTMENT, GENERAL_HEALTH, UNKNOWN
    }

    /** 意图关键词映射（用于查询分类和文档匹配） */
    private static final Map<QueryIntent, Set<String>> INTENT_KEYWORDS = Map.of(
            QueryIntent.DISEASE, Set.of(
                    "病", "疾病", "症", "炎症", "感染", "肿瘤", "癌", "瘤",
                    "溃疡", "结石", "硬化", "糖尿病", "高血压", "心脏病", "肺炎"),
            QueryIntent.DRUG, Set.of(
                    "药", "药物", "胶囊", "片", "丸", "颗粒", "注射液", "口服液",
                    "膏", "贴", "喷雾", "用法", "用量", "副作用", "剂量"),
            QueryIntent.SYMPTOM, Set.of(
                    "疼", "痛", "痒", "肿", "胀", "红", "热", "晕", "吐",
                    "恶心", "呕吐", "咳", "喘", "乏力", "疲劳", "虚弱",
                    "失眠", "多梦", "焦虑", "抑郁", "发烧", "发热", "头痛"),
            QueryIntent.TREATMENT, Set.of(
                    "治疗", "治愈", "疗法", "手术", "开刀", "吃药", "打针",
                    "输液", "康复", "理疗", "针灸", "推拿", "按摩"),
            QueryIntent.PREVENTION, Set.of(
                    "预防", "防范", "避免", "防止", "保健", "养生", "锻炼",
                    "运动", "饮食", "营养", "疫苗", "筛查"),
            QueryIntent.EXAMINATION, Set.of(
                    "检查", "化验", "检测", "体检", "B超", "CT", "X光",
                    "核磁", "血常规", "尿常规", "心电图", "筛查"),
            QueryIntent.EMERGENCY, Set.of(
                    "急诊", "紧急", "急救", "救命", "危险", "严重",
                    "马上", "立刻", "赶快", "快点", "突发", "突然"),
            QueryIntent.DEPARTMENT, Set.of(
                    "科", "科室", "门诊", "急诊", "外科", "内科", "儿科",
                    "妇产科", "眼科", "耳鼻喉科", "皮肤科", "口腔科",
                    "神经科", "心血管科", "消化科", "呼吸科"),
            QueryIntent.GENERAL_HEALTH, Set.of(
                    "健康", "身体", "体质", "免疫力", "抵抗力",
                    "生活习惯", "作息", "睡眠", "心理", "情绪")
    );

    private final CrossEncoderRerankService crossEncoderRerankService;

    public MultiFactorRerankServiceImpl(CrossEncoderRerankService crossEncoderRerankService) {
        this.crossEncoderRerankService = crossEncoderRerankService;
    }

    @Override
    public List<MultiRetrievalService.RetrievalResult> rerank(
            String query,
            List<MultiRetrievalService.RetrievalResult> results,
            int topK) {

        if (query == null || results == null || results.isEmpty()) {
            return results != null ? results : Collections.emptyList();
        }

        // 限制处理数量
        int maxResults = Math.min(results.size(), 50);
        List<MultiRetrievalService.RetrievalResult> candidates = results.subList(0, maxResults);

        // 获取语义相似度分数（通过交叉编码器）
        Map<Integer, Double> semanticScores = getSemanticScores(query, candidates);

        // 计算综合分数
        List<ScoredResult> scoredResults = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            MultiRetrievalService.RetrievalResult result = candidates.get(i);
            double semanticScore = semanticScores.getOrDefault(i, estimateSemanticFallback(result));
            double finalScore = calculateFinalScore(query, result, semanticScore);
            scoredResults.add(new ScoredResult(result, finalScore, semanticScore));
        }

        // 按综合分数降序排序
        scoredResults.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));

        // 取 topK
        List<MultiRetrievalService.RetrievalResult> reranked = new ArrayList<>();
        for (int i = 0; i < Math.min(scoredResults.size(), topK); i++) {
            ScoredResult sr = scoredResults.get(i);
            MultiRetrievalService.RetrievalResult original = sr.result;
            reranked.add(new MultiRetrievalService.RetrievalResult(
                    original.getChunkId(),
                    original.getDocumentId(),
                    sr.finalScore,
                    original.getContent(),
                    MultiRetrievalService.RetrievalSource.HYBRID,
                    original.getVectorScore(),
                    original.getKeywordScore(),
                    original.getMetadata()
            ));
        }

        log.debug("多因子重排序完成: query={}, input={}, output={}",
                truncate(query, 50), results.size(), reranked.size());
        return reranked;
    }

    @Override
    public List<MultiRetrievalService.RetrievalResult> rerank(
            String query,
            List<MultiRetrievalService.RetrievalResult> results) {
        return rerank(query, results, 5); // 默认 top5
    }

    @Override
    public double calculateFinalScore(String query,
                                      MultiRetrievalService.RetrievalResult result,
                                      double semanticScore) {

        double authority = calculateAuthority(result);
        double timeliness = calculateTimeliness(result);
        double semantic = clamp(semanticScore, 0.0, 1.0);
        double intent = calculateIntentRelevance(query, result);

        double finalScore = DEFAULT_WEIGHT_AUTHORITY * authority
                + DEFAULT_WEIGHT_TIMELINESS * timeliness
                + DEFAULT_WEIGHT_SEMANTIC * semantic
                + DEFAULT_WEIGHT_INTENT * intent;

        return clamp(finalScore, 0.0, 1.0);
    }

    // ========== 意图匹配度评分 ==========

    /**
     * 计算查询意图与文档内容的匹配度
     * 先对查询做意图分类，再与文档内容做同组关键词匹配
     */
    double calculateIntentRelevance(String query, MultiRetrievalService.RetrievalResult result) {
        QueryIntent intent = classifyQueryIntent(query);
        return calculateIntentRelevance(intent, result);
    }

    /**
     * 基于关键词匹配的查询意图分类
     */
    QueryIntent classifyQueryIntent(String query) {
        if (query == null || query.trim().isEmpty()) {
            return QueryIntent.UNKNOWN;
        }

        QueryIntent bestIntent = QueryIntent.UNKNOWN;
        double bestRate = 0.0;

        for (Map.Entry<QueryIntent, Set<String>> entry : INTENT_KEYWORDS.entrySet()) {
            Set<String> keywords = entry.getValue();
            int hitCount = 0;
            for (String kw : keywords) {
                if (query.contains(kw)) {
                    hitCount++;
                }
            }
            double rate = (double) hitCount / keywords.size();
            if (rate > bestRate) {
                bestRate = rate;
                bestIntent = entry.getKey();
            }
        }

        return bestIntent;
    }

    /**
     * 计算文档片段与指定意图的匹配度
     */
    private double calculateIntentRelevance(QueryIntent intent,
                                            MultiRetrievalService.RetrievalResult result) {
        if (intent == QueryIntent.UNKNOWN) {
            return 0.5;
        }

        Set<String> keywords = INTENT_KEYWORDS.get(intent);
        if (keywords == null || keywords.isEmpty()) {
            return 0.5;
        }

        String content = result.getContent();
        if (content == null) {
            return 0.3;
        }

        int hitCount = 0;
        for (String kw : keywords) {
            if (content.contains(kw)) {
                hitCount++;
            }
        }

        // 按命中数计分，命中5个以上即满分
        return clamp(hitCount * 0.2, 0.0, 1.0);
    }

    // ========== 权威性评分 ==========

    /**
     * 计算权威性分数（基于来源类型映射证据金字塔）
     */
    double calculateAuthority(MultiRetrievalService.RetrievalResult result) {
        // 优先从 metadata 读取 authority
        Object authObj = result.getMetadata().get("authority");
        if (authObj instanceof Number) {
            return clamp(((Number) authObj).doubleValue(), 0.0, 1.0);
        }

        // 从 source_type 映射
        String sourceType = result.getMetadataString("source_type");
        if (sourceType != null) {
            return AuthorityLevel.fromSourceType(sourceType);
        }

        // 从内容推断来源类型
        return inferAuthorityFromContent(result.getContent());
    }

    /**
     * 从内容启发式推断权威性
     */
    private double inferAuthorityFromContent(String content) {
        if (content == null) return 0.5;

        // 包含指南/共识特征 → 高权威
        if (containsAny(content, "指南", "共识", "Cochrane", "NICE", "WHO", "FDA", "NMPA",
                "中华医学会", "meta分析", "系统评价", "systematic review")) {
            return 1.0;
        }
        // 包含RCT特征
        if (containsAny(content, "随机对照", "RCT", "双盲", "随机分组", "randomized")) {
            return 0.9;
        }
        // 包含说明书特征
        if (containsAny(content, "说明书", "适应症", "用法用量", "不良反应", "禁忌",
                "药品名称", "批准文号")) {
            return 0.95;
        }
        // 包含研究特征
        if (containsAny(content, "研究", "试验", "临床", "队列", "病例对照")) {
            return 0.7;
        }
        // 包含科普特征
        if (containsAny(content, "科普", "百科", "UpToDate", "默沙东", "梅奥", "Mayo")) {
            return 0.6;
        }
        return 0.5;
    }

    // ========== 时效性评分 ==========

    /**
     * 计算时效性分数（指数衰减）
     * 公式：timeliness = e^(-λ * ΔY)
     * 其中 λ = ln(2) / 半衰期
     */
    double calculateTimeliness(MultiRetrievalService.RetrievalResult result) {
        Integer publishYear = extractPublishYear(result);
        if (publishYear == null) {
            return 0.7; // 无年份信息，默认中等时效
        }

        int currentYear = Year.now().getValue();
        int deltaYears = currentYear - publishYear;
        if (deltaYears < 0) {
            return 1.0; // 未来年份（可能是错误数据），给予满分
        }

        // 推断知识类型以确定半衰期
        KnowledgeType knowledgeType = KnowledgeType.infer(result.getContent());
        double halfLife = HALF_LIVES.getOrDefault(knowledgeType, 5.0);

        // λ = ln(2) / halfLife
        double lambda = Math.log(2) / halfLife;

        // 时效性 = e^(-λ * ΔY)
        double timeliness = Math.exp(-lambda * deltaYears);

        return clamp(timeliness, 0.0, 1.0);
    }

    /**
     * 从检索结果中提取发表年份
     */
    private Integer extractPublishYear(MultiRetrievalService.RetrievalResult result) {
        // 优先从 metadata 读取
        Object yearObj = result.getMetadata().get("publish_year");
        if (yearObj instanceof Number) {
            return ((Number) yearObj).intValue();
        }
        // 尝试从 metadata 的 "year" 读取
        yearObj = result.getMetadata().get("year");
        if (yearObj instanceof Number) {
            return ((Number) yearObj).intValue();
        }
        // 尝试从内容中提取年份（格式：19xx 或 20xx 年）
        String content = result.getContent();
        if (content != null) {
            java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile(
                    "\\b(19\\d{2}|20\\d{2})\\s*年");
            java.util.regex.Matcher matcher = yearPattern.matcher(content);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    // ========== 语义相似度 ==========

    /**
     * 获取语义相似度分数（通过交叉编码器）
     */
    private Map<Integer, Double> getSemanticScores(
            String query,
            List<MultiRetrievalService.RetrievalResult> results) {

        try {
            // 通过现有的交叉编码器重排序服务获取语义分数
            List<MultiRetrievalService.RetrievalResult> reranked =
                    crossEncoderRerankService.rerank(query, results, results.size());

            Map<Integer, Double> scores = new HashMap<>();
            // 交叉编码器返回的结果已按分数排序，需要按内容匹配还原位置
            Map<String, Integer> contentToIndex = new HashMap<>();
            for (int i = 0; i < results.size(); i++) {
                contentToIndex.put(results.get(i).getContent(), i);
            }
            for (MultiRetrievalService.RetrievalResult r : reranked) {
                Integer idx = contentToIndex.get(r.getContent());
                if (idx != null) {
                    scores.put(idx, r.getScore() != null ? r.getScore() : 0.5);
                }
            }
            return scores;
        } catch (Exception e) {
            log.warn("交叉编码器调用失败，使用降级语义评分: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 降级语义分数：基于检索结果原有的向量相似度
     */
    private double estimateSemanticFallback(MultiRetrievalService.RetrievalResult result) {
        Double vectorScore = result.getVectorScore();
        if (vectorScore != null && vectorScore > 0) {
            // 向量相似度通常在0~1或更大范围内，归一化
            return clamp(vectorScore, 0.0, 1.0);
        }
        // 基于RRF分数估算
        Double score = result.getScore();
        return score != null ? clamp(score, 0.0, 1.0) : 0.5;
    }

    // ========== 辅助方法 ==========

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /** 带分数的内部结果 */
    private static class ScoredResult {
        final MultiRetrievalService.RetrievalResult result;
        final double finalScore;
        final double semanticScore;

        ScoredResult(MultiRetrievalService.RetrievalResult result,
                     double finalScore, double semanticScore) {
            this.result = result;
            this.finalScore = finalScore;
            this.semanticScore = semanticScore;
        }
    }
}
