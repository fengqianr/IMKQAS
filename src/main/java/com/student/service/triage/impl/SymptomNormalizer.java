package com.student.service.triage.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 症状标准化器
 * 负责症状描述的标准化处理，包括同义词扩展、模糊匹配和格式规范化
 *
 * @author 系统生成
 * @version 1.0
 */
@Slf4j
public class SymptomNormalizer {

    private final Map<String, List<String>> symptomSynonyms;
    private final boolean enableFuzzyMatch;
    private final double fuzzyMatchThreshold;

    // 常见症状分隔符正则表达式
    private static final Pattern SYMPTOM_SEPARATOR = Pattern.compile("[，,;；、\\s]+");
    // 中文标点符号正则表达式
    private static final Pattern CHINESE_PUNCTUATION = Pattern.compile("[。，、；：？！（）《》【】「」『』]");

    /**
     * 构造函数
     *
     * @param symptomSynonyms 症状同义词映射
     * @param enableFuzzyMatch 是否启用模糊匹配
     * @param fuzzyMatchThreshold 模糊匹配阈值
     */
    public SymptomNormalizer(Map<String, List<String>> symptomSynonyms,
                             boolean enableFuzzyMatch,
                             double fuzzyMatchThreshold) {
        this.symptomSynonyms = symptomSynonyms != null ? symptomSynonyms : Map.of();
        this.enableFuzzyMatch = enableFuzzyMatch;
        this.fuzzyMatchThreshold = fuzzyMatchThreshold;
        log.info("症状标准化器初始化完成，同义词组数: {}, 启用模糊匹配: {}, 模糊匹配阈值: {}",
            this.symptomSynonyms.size(), enableFuzzyMatch, fuzzyMatchThreshold);
    }

    /**
     * 标准化症状描述
     * 主要处理步骤：
     * 1. 清理和规范化文本
     * 2. 同义词扩展
     * 3. 症状拆分和去重
     * 4. 模糊匹配扩展（如果启用）
     *
     * @param symptoms 原始症状描述
     * @return 标准化后的症状描述
     */
    public String normalize(String symptoms) {
        if (symptoms == null || symptoms.trim().isEmpty()) {
            return "";
        }

        long startTime = System.currentTimeMillis();

        try {
            // 1. 清理和规范化文本
            String cleaned = cleanSymptoms(symptoms);

            // 2. 拆分症状
            List<String> symptomList = splitSymptoms(cleaned);

            // 3. 同义词扩展
            List<String> expandedSymptoms = expandSynonyms(symptomList);

            // 4. 去重
            List<String> uniqueSymptoms = removeDuplicates(expandedSymptoms);

            // 5. 模糊匹配扩展（如果启用）
            if (enableFuzzyMatch && !symptomSynonyms.isEmpty()) {
                uniqueSymptoms = expandWithFuzzyMatch(uniqueSymptoms);
            }

            // 6. 重新组合为字符串
            String normalized = String.join("，", uniqueSymptoms);

            long processingTime = System.currentTimeMillis() - startTime;
            log.debug("症状标准化完成: 原始='{}', 标准化='{}', 处理时间={}ms",
                symptoms, normalized, processingTime);

            return normalized;

        } catch (Exception e) {
            log.error("症状标准化失败: symptoms={}", symptoms, e);
            return symptoms; // 失败时返回原始症状
        }
    }

    /**
     * 清理症状文本
     * 移除多余空格、标点符号，转换为标准格式
     */
    private String cleanSymptoms(String symptoms) {
        // 1. 移除首尾空格
        String cleaned = symptoms.trim();

        // 2. 移除多余空格（多个连续空格合并为一个）
        cleaned = cleaned.replaceAll("\\s+", " ");

        // 3. 标准化标点符号（将英文标点转换为中文标点）
        cleaned = cleaned.replace(',', '，')
                        .replace(';', '；')
                        .replace(':', '：')
                        .replace('?', '？')
                        .replace('!', '！')
                        .replace('(', '（')
                        .replace(')', '）');

        // 4. 移除开头和结尾的标点符号
        cleaned = cleaned.replaceAll("^[，；：。！？]+", "")
                        .replaceAll("[，；：。！？]+$", "");

        return cleaned;
    }

    /**
     * 拆分症状描述为独立症状列表
     */
    public List<String> splitSymptoms(String symptoms) {
        List<String> symptomList = new ArrayList<>();
        if (symptoms == null || symptoms.isEmpty()) {
            return symptomList;
        }

        // 使用分隔符拆分症状
        String[] parts = SYMPTOM_SEPARATOR.split(symptoms);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                symptomList.add(trimmed);
            }
        }

        return symptomList;
    }

    /**
     * 同义词扩展
     * 将症状替换为其同义词组中的所有可能表达
     */
    private List<String> expandSynonyms(List<String> symptoms) {
        List<String> expanded = new ArrayList<>();

        for (String symptom : symptoms) {
            // 添加原始症状
            expanded.add(symptom);

            // 查找同义词
            List<String> synonyms = findSynonyms(symptom);
            expanded.addAll(synonyms);
        }

        return expanded;
    }

    /**
     * 查找症状的同义词
     */
    private List<String> findSynonyms(String symptom) {
        List<String> synonyms = new ArrayList<>();

        // 直接匹配
        if (symptomSynonyms.containsKey(symptom)) {
            synonyms.addAll(symptomSynonyms.get(symptom));
        }

        // 检查是否作为其他症状的同义词存在
        for (Map.Entry<String, List<String>> entry : symptomSynonyms.entrySet()) {
            if (entry.getValue().contains(symptom)) {
                synonyms.add(entry.getKey());
            }
        }

        return synonyms;
    }

    /**
     * 去重症状
     */
    private List<String> removeDuplicates(List<String> symptoms) {
        Set<String> seen = new HashSet<>();
        List<String> unique = new ArrayList<>();

        for (String symptom : symptoms) {
            if (!seen.contains(symptom)) {
                seen.add(symptom);
                unique.add(symptom);
            }
        }

        return unique;
    }

    /**
     * 模糊匹配扩展
     * 使用字符串相似度匹配找到相似症状的同义词
     */
    private List<String> expandWithFuzzyMatch(List<String> symptoms) {
        List<String> expanded = new ArrayList<>(symptoms);

        for (String symptom : symptoms) {
            // 查找模糊匹配的同义词
            List<String> fuzzySynonyms = findFuzzySynonyms(symptom);
            for (String fuzzySynonym : fuzzySynonyms) {
                // 查找模糊匹配的同义词的同义词
                List<String> synonymSynonyms = findSynonyms(fuzzySynonym);
                expanded.addAll(synonymSynonyms);
            }
        }

        return removeDuplicates(expanded);
    }

    /**
     * 查找模糊匹配的同义词
     * 基于字符串相似度匹配
     */
    private List<String> findFuzzySynonyms(String symptom) {
        List<String> fuzzySynonyms = new ArrayList<>();

        if (symptom.length() < 2) {
            return fuzzySynonyms; // 太短的字符串不进行模糊匹配
        }

        // 检查所有已知症状和同义词
        Set<String> allTerms = new HashSet<>();
        allTerms.addAll(symptomSynonyms.keySet());
        symptomSynonyms.values().forEach(allTerms::addAll);

        for (String term : allTerms) {
            if (term.equals(symptom)) {
                continue; // 跳过完全相同的项
            }

            double similarity = calculateSimilarity(symptom, term);
            if (similarity >= fuzzyMatchThreshold) {
                fuzzySynonyms.add(term);
                log.debug("模糊匹配发现: '{}' ≈ '{}' (相似度: {})", symptom, term, similarity);
            }
        }

        return fuzzySynonyms;
    }

    /**
     * 计算字符串相似度（基于编辑距离）
     * 使用简单的编辑距离算法，归一化为0-1之间的值
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }

        int len1 = s1.length();
        int len2 = s2.length();
        int maxLen = Math.max(len1, len2);

        if (maxLen == 0) {
            return 1.0;
        }

        // 计算编辑距离
        int editDistance = calculateEditDistance(s1, s2);

        // 将编辑距离转换为相似度
        double similarity = 1.0 - (double) editDistance / maxLen;
        return Math.max(0.0, similarity);
    }

    /**
     * 计算编辑距离（Levenshtein距离）
     */
    private int calculateEditDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * 获取症状的同义词映射（用于调试和测试）
     */
    public Map<String, List<String>> getSymptomSynonyms() {
        return new HashMap<>(symptomSynonyms);
    }

    /**
     * 检查是否启用模糊匹配
     */
    public boolean isFuzzyMatchEnabled() {
        return enableFuzzyMatch;
    }

    /**
     * 获取模糊匹配阈值
     */
    public double getFuzzyMatchThreshold() {
        return fuzzyMatchThreshold;
    }
}