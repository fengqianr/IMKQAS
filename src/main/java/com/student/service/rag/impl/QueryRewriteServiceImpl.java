package com.student.service.rag.impl;

import com.student.service.rag.QueryRewriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 查询改写服务实现类
 * 基于规则和医学词库的查询预处理
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

            // 3. 医疗术语化
            String medicalizedQuery = medicalize(simplifiedQuery);

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

}