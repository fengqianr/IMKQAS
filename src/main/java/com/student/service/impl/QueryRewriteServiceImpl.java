package com.student.service.impl;

import com.student.service.QueryRewriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 查询改写服务实现类
 * 基于规则和关键词的医疗查询改写与意图识别
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryRewriteServiceImpl implements QueryRewriteService {

    // 停用词
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说",
            "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "什么", "怎么", "为什么", "如何",
            "吗", "呢", "啊", "呀", "吧", "哦", "嗯", "请问", "谢谢", "帮忙", "帮助", "咨询", "了解", "想知道"
    );

    // 医疗同义词映射
    private static final Map<String, List<String>> MEDICAL_SYNONYMS = Map.ofEntries(
            Map.entry("发烧", Arrays.asList("发热", "高烧", "体温升高", "发热症状")),
            Map.entry("头痛", Arrays.asList("头疼", "头部疼痛", "头胀痛")),
            Map.entry("咳嗽", Arrays.asList("咳痰", "干咳", "咳嗽症状")),
            Map.entry("感冒", Arrays.asList("上呼吸道感染", "普通感冒", "伤风")),
            Map.entry("高血压", Arrays.asList("高血压病", "血压高", "原发性高血压")),
            Map.entry("糖尿病", Arrays.asList("血糖高", "糖尿病病", "消渴症")),
            Map.entry("心脏病", Arrays.asList("心脏疾病", "心血管疾病", "心脏问题")),
            Map.entry("肺炎", Arrays.asList("肺部感染", "肺炎症", "肺炎病")),
            Map.entry("胃痛", Arrays.asList("胃部疼痛", "胃疼", "胃不适")),
            Map.entry("腹泻", Arrays.asList("拉肚子", "泄泻", "大便稀溏"))
    );

    // 疾病相关关键词
    private static final Set<String> DISEASE_KEYWORDS = Set.of(
            "病", "疾病", "症", "炎症", "感染", "肿瘤", "癌", "瘤", "溃疡", "结石", "硬化"
    );

    // 药物相关关键词
    private static final Set<String> DRUG_KEYWORDS = Set.of(
            "药", "药物", "胶囊", "片", "丸", "颗粒", "注射液", "口服液", "膏", "贴", "喷雾"
    );

    // 症状相关关键词
    private static final Set<String> SYMPTOM_KEYWORDS = Set.of(
            "疼", "痛", "痒", "肿", "胀", "红", "热", "晕", "吐", "恶心", "呕吐", "咳", "喘",
            "乏力", "疲劳", "虚弱", "失眠", "多梦", "焦虑", "抑郁"
    );

    // 科室相关关键词
    private static final Set<String> DEPARTMENT_KEYWORDS = Set.of(
            "科", "科室", "门诊", "急诊", "外科", "内科", "儿科", "妇产科", "眼科", "耳鼻喉科",
            "皮肤科", "口腔科", "神经科", "心血管科", "消化科", "呼吸科"
    );

    // 统计信息
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger rewrittenQueries = new AtomicInteger(0);
    private final AtomicInteger expandedQueries = new AtomicInteger(0);
    private final AtomicInteger simplifiedQueries = new AtomicInteger(0);
    private final AtomicInteger correctedQueries = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    @Override
    public String rewrite(String originalQuery, Long userId, Long conversationId) {
        if (originalQuery == null) {
            return null;
        }

        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();

        try {
            // 1. 拼写纠正（简化实现：目前仅日志记录）
            String correctedQuery = correctSpelling(originalQuery);
            if (correctedQuery != null && !correctedQuery.equals(originalQuery)) {
                correctedQueries.incrementAndGet();
            }

            // 2. 查询简化
            String simplifiedQuery = simplify(correctedQuery);
            if (simplifiedQuery != null && !simplifiedQuery.equals(correctedQuery)) {
                simplifiedQueries.incrementAndGet();
            }

            // 3. 查询扩展
            String expandedQuery = expand(simplifiedQuery);
            if (expandedQuery != null && !expandedQuery.equals(simplifiedQuery)) {
                expandedQueries.incrementAndGet();
            }

            // 4. 医疗术语化
            String medicalizedQuery = medicalize(expandedQuery);

            // 记录改写
            if (medicalizedQuery != null && !medicalizedQuery.equals(originalQuery)) {
                rewrittenQueries.incrementAndGet();
            }

            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);

            log.debug("查询改写完成: original={}, rewritten={}, time={}ms",
                    originalQuery, medicalizedQuery, processingTime);

            return medicalizedQuery;

        } catch (Exception e) {
            log.error("查询改写异常: query={}", originalQuery, e);
            // 降级：返回原始查询
            return originalQuery;
        }
    }

    @Override
    public List<String> rewriteBatch(List<String> queries, Long userId, Long conversationId) {
        List<String> rewrittenQueries = new ArrayList<>();

        for (String query : queries) {
            rewrittenQueries.add(rewrite(query, userId, conversationId));
        }

        return rewrittenQueries;
    }

    @Override
    public String expand(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        StringBuilder expandedQuery = new StringBuilder(query);

        // 添加同义词
        for (Map.Entry<String, List<String>> entry : MEDICAL_SYNONYMS.entrySet()) {
            String term = entry.getKey();
            if (query.contains(term)) {
                // 添加第一个同义词
                List<String> synonyms = entry.getValue();
                if (!synonyms.isEmpty()) {
                    expandedQuery.append(" ").append(synonyms.get(0));
                }
            }
        }

        // 添加相关术语（基于意图）
        IntentClassification intent = classifyIntent(query);
        if (intent.getPrimaryIntent() == IntentType.DISEASE_QUERY) {
            expandedQuery.append(" 症状 治疗 预防");
        } else if (intent.getPrimaryIntent() == IntentType.DRUG_QUERY) {
            expandedQuery.append(" 用法 用量 副作用");
        } else if (intent.getPrimaryIntent() == IntentType.SYMPTOM_QUERY) {
            expandedQuery.append(" 可能疾病 诊断 检查");
        }

        return expandedQuery.toString().trim();
    }

    @Override
    public String simplify(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        // 移除停用词：遍历停用词集合，从查询中移除
        String result = query;
        // 按长度降序排序，先移除长词
        List<String> sortedStopWords = STOP_WORDS.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());

        for (String stopWord : sortedStopWords) {
            result = result.replace(stopWord, "");
        }

        // 移除多余空格和空白字符
        result = result.replaceAll("\\s+", " ").trim();

        // 如果简化后为空，返回原始查询
        if (result.isEmpty()) {
            return query;
        }

        return result;
    }

    @Override
    public String correctSpelling(String query) {
        if (query == null) {
            return null;
        }

        // 简化实现：常见医疗术语拼写纠正
        // 实际应集成拼写纠正库或API
        Map<String, String> commonCorrections = Map.of(
                "感冐", "感冒",
                "糖原病", "糖尿病",
                "高血圧", "高血压",
                "心藏病", "心脏病",
                "肺焱", "肺炎",
                "胃疼", "胃痛"
        );

        String correctedQuery = query;
        for (Map.Entry<String, String> correction : commonCorrections.entrySet()) {
            if (correctedQuery.contains(correction.getKey())) {
                correctedQuery = correctedQuery.replace(correction.getKey(), correction.getValue());
                log.debug("拼写纠正: {} -> {}", correction.getKey(), correction.getValue());
            }
        }

        return correctedQuery;
    }

    @Override
    public String medicalize(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        String medicalizedQuery = query;

        // 将口语化表达转为医学术语
        Map<String, String> medicalTerms = Map.of(
                "脑袋疼", "头痛",
                "肚子疼", "腹痛",
                "拉肚子", "腹泻",
                "发高烧", "高热",
                "心慌", "心悸",
                "喘不上气", "呼吸困难",
                "睡不着", "失眠",
                "没胃口", "食欲不振"
        );

        for (Map.Entry<String, String> term : medicalTerms.entrySet()) {
            if (medicalizedQuery.contains(term.getKey())) {
                medicalizedQuery = medicalizedQuery.replace(term.getKey(), term.getValue());
            }
        }

        return medicalizedQuery;
    }

    @Override
    public IntentClassification classifyIntent(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new IntentClassification(
                    query,
                    IntentType.OTHER,
                    Collections.emptyList(),
                    0.0
            );
        }

        // 计算各意图的分数
        Map<IntentType, Double> intentScores = new HashMap<>();

        // 疾病查询分数
        double diseaseScore = calculateKeywordScore(query, DISEASE_KEYWORDS);
        intentScores.put(IntentType.DISEASE_QUERY, diseaseScore);

        // 药物查询分数
        double drugScore = calculateKeywordScore(query, DRUG_KEYWORDS);
        intentScores.put(IntentType.DRUG_QUERY, drugScore);

        // 症状查询分数
        double symptomScore = calculateKeywordScore(query, SYMPTOM_KEYWORDS);
        intentScores.put(IntentType.SYMPTOM_QUERY, symptomScore);

        // 科室导诊分数
        double departmentScore = calculateKeywordScore(query, DEPARTMENT_KEYWORDS);
        intentScores.put(IntentType.DEPARTMENT_GUIDANCE, departmentScore);

        // 其他意图的启发式规则
        double treatmentScore = calculateTreatmentIntentScore(query);
        intentScores.put(IntentType.TREATMENT_QUERY, treatmentScore);

        double preventionScore = calculatePreventionIntentScore(query);
        intentScores.put(IntentType.PREVENTION_QUERY, preventionScore);

        double examinationScore = calculateExaminationIntentScore(query);
        intentScores.put(IntentType.EXAMINATION_QUERY, examinationScore);

        double emergencyScore = calculateEmergencyIntentScore(query);
        intentScores.put(IntentType.EMERGENCY_QUERY, emergencyScore);

        double generalHealthScore = calculateGeneralHealthIntentScore(query);
        intentScores.put(IntentType.GENERAL_HEALTH, generalHealthScore);

        // 找出主要意图
        IntentType primaryIntent = IntentType.OTHER;
        double maxScore = 0.0;

        for (Map.Entry<IntentType, Double> entry : intentScores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                primaryIntent = entry.getKey();
            }
        }

        // 找出次要意图（分数大于阈值）
        List<IntentType> secondaryIntents = new ArrayList<>();
        double threshold = 0.3;

        for (Map.Entry<IntentType, Double> entry : intentScores.entrySet()) {
            if (entry.getKey() != primaryIntent && entry.getValue() > threshold) {
                secondaryIntents.add(entry.getKey());
            }
        }

        // 计算置信度
        double confidence = Math.min(maxScore, 1.0);

        log.debug("意图识别: query={}, primary={}, confidence={}, secondary={}",
                query, primaryIntent, confidence, secondaryIntents);

        return new IntentClassification(query, primaryIntent, secondaryIntents, confidence);
    }

    @Override
    public boolean isAvailable() {
        return true; // 基于规则的服务始终可用
    }

    @Override
    public QueryRewriteStats getStats() {
        int total = totalQueries.get();
        double avgTime = total > 0 ? (double) totalProcessingTime.get() / total : 0.0;

        return new QueryRewriteStats(
                total,
                rewrittenQueries.get(),
                expandedQueries.get(),
                simplifiedQueries.get(),
                correctedQueries.get(),
                avgTime
        );
    }

    // ========== 私有辅助方法 ==========

    /**
     * 计算关键词匹配分数
     */
    private double calculateKeywordScore(String query, Set<String> keywords) {
        if (query == null || keywords == null || keywords.isEmpty()) {
            return 0.0;
        }

        int matches = 0;
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                matches++;
            }
        }

        // 分数基于匹配的关键词数量
        return Math.min(matches * 0.3, 1.0);
    }

    /**
     * 计算治疗意图分数
     */
    private double calculateTreatmentIntentScore(String query) {
        Set<String> treatmentKeywords = Set.of(
                "治疗", "治愈", "疗法", "手术", "开刀", "吃药", "打针", "输液",
                "康复", "理疗", "针灸", "推拿", "按摩"
        );

        return calculateKeywordScore(query, treatmentKeywords);
    }

    /**
     * 计算预防意图分数
     */
    private double calculatePreventionIntentScore(String query) {
        Set<String> preventionKeywords = Set.of(
                "预防", "防范", "避免", "防止", "预防措施", "健康", "保健",
                "养生", "锻炼", "运动", "饮食", "营养"
        );

        return calculateKeywordScore(query, preventionKeywords);
    }

    /**
     * 计算检查意图分数
     */
    private double calculateExaminationIntentScore(String query) {
        Set<String> examinationKeywords = Set.of(
                "检查", "化验", "检测", "筛查", "体检", "B超", "CT", "X光",
                "核磁", "血常规", "尿常规", "心电图"
        );

        return calculateKeywordScore(query, examinationKeywords);
    }

    /**
     * 计算急诊意图分数
     */
    private double calculateEmergencyIntentScore(String query) {
        Set<String> emergencyKeywords = Set.of(
                "急诊", "紧急", "急救", "救命", "危险", "严重", "马上", "立刻",
                "赶快", "快点", "突发", "突然"
        );

        return calculateKeywordScore(query, emergencyKeywords);
    }

    /**
     * 计算一般健康意图分数
     */
    private double calculateGeneralHealthIntentScore(String query) {
        Set<String> generalKeywords = Set.of(
                "健康", "身体", "体质", "免疫力", "抵抗力", "生活习惯",
                "作息", "睡眠", "饮食", "运动", "心理", "情绪"
        );

        return calculateKeywordScore(query, generalKeywords);
    }
}