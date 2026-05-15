package com.student.service.rag.impl;

import com.student.service.RedisService;
import com.student.service.rag.MedicalEntityRecognitionService;
import com.student.service.rag.QueryRewriteService;
import com.student.service.rag.SynonymExpansionService;
import com.student.service.snomed.SnomedTerminologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 查询改写服务实现类（重构版）
 * 基于规则和关键词的医疗查询改写与意图识别
 * 改进点：
 * 1. THUOCL医学词库集成：从THUOCL官网加载医学术语，支持热加载
 * 2. 拼写纠正升级：集成symspell-java进行智能拼写纠正
 * 3. 简化改进：保留否定副词，避免错误移除
 * 4. 扩展优化：条件性追加相关术语
 * 5. 意图识别增强：THUOCL术语匹配 + 否定词检测 + 权重优化
 * 6. 归一化接口：提供完整的查询归一化流程
 * 7. 配置化：同义词、停用词从配置文件或Redis加载
 * 8. SNOMED CT集成：支持医学术语标准化
 * 9. 医学实体识别：HanLP + THUOCL 分词与实体抽取
 * 10. 同义词扩展：本地映射表 → SNOMED CT → LLM兜底 + 人工审核队列
 *
 * @author 系统
 * @version 3.0
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class QueryRewriteServiceImplRefactored implements QueryRewriteService {

    private final RedisService redisService;
    private final SnomedTerminologyService snomedTerminologyService;
    private final MedicalEntityRecognitionService entityRecognitionService;
    private final SynonymExpansionService synonymExpansionService;

    // 配置参数
    @Value("${imkqas.query-rewrite.thuocl-dict-path:/data/dict/thuocl_medical.txt}")
    private String thuoclDictPath;

    @Value("${imkqas.query-rewrite.synonyms-file:classpath:synonyms.txt}")
    private String synonymsFile;

    @Value("${imkqas.query-rewrite.stopwords-file:classpath:stopwords.txt}")
    private String stopwordsFile;

    @Value("${imkqas.query-rewrite.enable-spell-checker:true}")
    private boolean enableSpellChecker;

    // THUOCL医学词库
    private Set<String> medicalTerms = ConcurrentHashMap.newKeySet();
    private long thuoclLastLoadedTime = 0;

    // 同义词映射（可热加载）
    private Map<String, List<String>> synonymsMap = new ConcurrentHashMap<>();

    // 停用词集合（可热加载）
    private Set<String> stopWords = ConcurrentHashMap.newKeySet();

    // 否定词集合（用于意图识别）
    private static final Set<String> NEGATION_WORDS = Set.of(
            "不", "没", "没有", "无", "未", "非", "否", "不是", "不会", "不能", "不要", "不用"
    );

    // 拼写纠正器（symspell-java）
    // 暂时注释，待添加依赖后启用
    // private SymSpell symSpell;

    // 统计信息
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger rewrittenQueries = new AtomicInteger(0);
    private final AtomicInteger expandedQueries = new AtomicInteger(0);
    private final AtomicInteger simplifiedQueries = new AtomicInteger(0);
    private final AtomicInteger correctedQueries = new AtomicInteger(0);
    private final AtomicInteger normalizedQueries = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    // 意图关键词权重映射（长词权重更高）
    private static final Map<String, Double> KEYWORD_WEIGHTS = Map.ofEntries(
            // 疾病相关（长词权重高）
            Map.entry("糖尿病", 2.0),
            Map.entry("高血压", 2.0),
            Map.entry("心脏病", 2.0),
            Map.entry("肺炎", 1.5),
            Map.entry("感冒", 1.0),
            // 症状相关
            Map.entry("头痛", 1.5),
            Map.entry("发烧", 1.5),
            Map.entry("咳嗽", 1.5),
            Map.entry("腹泻", 1.5),
            // 通用关键词（权重较低）
            Map.entry("病", 0.5),
            Map.entry("症", 0.5),
            Map.entry("药", 0.5),
            Map.entry("疼", 0.5)
    );

    /**
     * 初始化方法：加载THUOCL词库、同义词、停用词
     */
    @PostConstruct
    public void init() {
        loadMedicalTerms();
        loadSynonyms();
        loadStopWords();
        // initSpellChecker(); // 待添加依赖后启用

        log.info("查询改写服务初始化完成：医学术语数={}, 同义词数={}, 停用词数={}",
                medicalTerms.size(), synonymsMap.size(), stopWords.size());
    }

    /**
     * 加载THUOCL医学词库
     */
    private void loadMedicalTerms() {
        File dictFile = new File(thuoclDictPath);
        if (!dictFile.exists()) {
            log.warn("THUOCL词库文件不存在: {}，使用内置医学术语", thuoclDictPath);
            // 使用内置的医学术语作为回退
            medicalTerms.addAll(getBuiltinMedicalTerms());
            return;
        }

        long lastModified = dictFile.lastModified();
        if (lastModified <= thuoclLastLoadedTime) {
            return; // 文件未修改，无需重新加载
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(dictFile))) {
            Set<String> newTerms = new HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    newTerms.add(line);
                }
            }

            medicalTerms.clear();
            medicalTerms.addAll(newTerms);
            thuoclLastLoadedTime = lastModified;

            log.info("THUOCL医学词库加载成功: 文件={}, 术语数={}", thuoclDictPath, medicalTerms.size());
        } catch (IOException e) {
            log.error("加载THUOCL医学词库失败: {}", thuoclDictPath, e);
            // 使用内置术语作为回退
            medicalTerms.addAll(getBuiltinMedicalTerms());
        }
    }

    /**
     * 内置医学术语（THUOCL词库不可用时使用）
     */
    private Set<String> getBuiltinMedicalTerms() {
        return Set.of(
                "感冒", "流感", "发热", "发烧", "咳嗽", "头痛", "头晕", "恶心", "呕吐", "腹泻",
                "便秘", "腹痛", "胃痛", "心脏病", "高血压", "糖尿病", "肺炎", "支气管炎",
                "哮喘", "鼻炎", "咽炎", "胃炎", "肝炎", "肾炎", "关节炎", "皮肤病",
                "眼科疾病", "耳科疾病", "口腔疾病", "骨科疾病", "神经疾病", "精神疾病",
                "肿瘤", "癌症", "良性肿瘤", "恶性肿瘤", "感染", "炎症", "溃疡", "结石",
                "硬化", "萎缩", "增生", "畸形", "损伤", "创伤", "中毒", "过敏", "免疫",
                "遗传", "代谢", "内分泌", "生殖", "泌尿", "呼吸", "循环", "消化", "神经"
        );
    }

    /**
     * 加载同义词映射
     */
    private void loadSynonyms() {
        // 首先尝试从文件加载
        try {
            Path path = Paths.get(synonymsFile.replace("classpath:", ""));
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                Map<String, List<String>> newMap = new HashMap<>();

                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] parts = line.split("=>");
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String[] values = parts[1].split(",");
                        List<String> synonymList = Arrays.stream(values)
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());

                        if (!synonymList.isEmpty()) {
                            newMap.put(key, synonymList);
                        }
                    }
                }

                synonymsMap.clear();
                synonymsMap.putAll(newMap);
                log.info("同义词文件加载成功: 文件={}, 条目数={}", synonymsFile, synonymsMap.size());
                return;
            }
        } catch (Exception e) {
            log.warn("加载同义词文件失败: {}", synonymsFile, e);
        }

        // 文件加载失败，使用内置同义词
        synonymsMap.clear();
        synonymsMap.putAll(getBuiltinSynonyms());
    }

    /**
     * 内置同义词映射
     */
    private Map<String, List<String>> getBuiltinSynonyms() {
        Map<String, List<String>> synonyms = new HashMap<>();
        synonyms.put("发烧", Arrays.asList("发热", "高烧", "体温升高"));
        synonyms.put("头痛", Arrays.asList("头疼", "头部疼痛"));
        synonyms.put("咳嗽", Arrays.asList("咳痰", "干咳"));
        synonyms.put("感冒", Arrays.asList("上呼吸道感染", "伤风"));
        synonyms.put("高血压", Arrays.asList("血压高", "原发性高血压"));
        synonyms.put("糖尿病", Arrays.asList("血糖高", "消渴症"));
        synonyms.put("心脏病", Arrays.asList("心脏疾病", "心血管疾病"));
        synonyms.put("肺炎", Arrays.asList("肺部感染", "肺炎症"));
        synonyms.put("胃痛", Arrays.asList("胃部疼痛", "胃疼"));
        synonyms.put("腹泻", Arrays.asList("拉肚子", "泄泻"));
        return synonyms;
    }

    /**
     * 加载停用词
     */
    private void loadStopWords() {
        // 首先尝试从文件加载
        try {
            Path path = Paths.get(stopwordsFile.replace("classpath:", ""));
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                Set<String> newStopWords = lines.stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toSet());

                stopWords.clear();
                stopWords.addAll(newStopWords);
                log.info("停用词文件加载成功: 文件={}, 词数={}", stopwordsFile, stopWords.size());
                return;
            }
        } catch (Exception e) {
            log.warn("加载停用词文件失败: {}", stopwordsFile, e);
        }

        // 文件加载失败，使用内置停用词（排除否定词）
        stopWords.clear();
        stopWords.addAll(getBuiltinStopWords());
    }

    /**
     * 内置停用词（排除否定词）
     */
    private Set<String> getBuiltinStopWords() {
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "的", "了", "在", "是", "我", "有", "和", "就", "人", "都", "一", "一个", "上", "也", "很", "到", "说",
                "要", "去", "你", "会", "着", "看", "好", "自己", "这", "那", "什么", "怎么", "为什么", "如何",
                "吗", "呢", "啊", "呀", "吧", "哦", "嗯", "请问", "谢谢", "帮忙", "帮助", "咨询", "了解", "想知道"
        ));

        // 确保不包含否定词
        stopWords.removeAll(NEGATION_WORDS);
        return stopWords;
    }

    /**
     * 初始化拼写纠正器（symspell-java）
     */
    /*
    private void initSpellChecker() {
        if (!enableSpellChecker) {
            log.info("拼写纠正器已禁用");
            return;
        }

        try {
            symSpell = new SymSpell(2, 7); // 最大编辑距离2，前缀长度7
            // 加载词典（使用THUOCL词库 + 内置医学术语）
            for (String term : medicalTerms) {
                symSpell.createDictionaryEntry(term, 1);
            }
            // 添加常见医疗词汇
            for (String term : getBuiltinMedicalTerms()) {
                symSpell.createDictionaryEntry(term, 1);
            }

            log.info("拼写纠正器初始化成功: 词典大小={}", medicalTerms.size());
        } catch (Exception e) {
            log.error("初始化拼写纠正器失败", e);
            symSpell = null;
        }
    }
    */

    /**
     * 定时任务：每10分钟检查并重新加载THUOCL词库
     */
    @Scheduled(fixedDelay = 600000) // 10分钟
    public void reloadMedicalTerms() {
        loadMedicalTerms();
        // 如果拼写纠正器启用，重新初始化
        // if (enableSpellChecker && symSpell != null) {
        //     initSpellChecker();
        // }
    }

    /**
     * 定时任务：每30分钟重新加载同义词和停用词
     */
    @Scheduled(fixedDelay = 1800000) // 30分钟
    public void reloadSynonymsAndStopWords() {
        loadSynonyms();
        loadStopWords();
    }

    @Override
    public String rewrite(String originalQuery, Long userId, Long conversationId) {
        if (originalQuery == null) {
            return null;
        }

        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();

        try {
            // 1. 拼写纠正
            String correctedQuery = correctSpelling(originalQuery);
            if (correctedQuery != null && !correctedQuery.equals(originalQuery)) {
                correctedQueries.incrementAndGet();
            }

            // 2. 查询简化（改进版）
            String simplifiedQuery = simplify(correctedQuery);
            if (simplifiedQuery != null && !simplifiedQuery.equals(correctedQuery)) {
                simplifiedQueries.incrementAndGet();
            }

            // 3. 医学实体识别（HanLP + THUOCL）
            List<MedicalEntityRecognitionService.MedicalEntity> entities =
                    entityRecognitionService.recognize(simplifiedQuery);
            log.debug("实体识别: query={}, entities={}", simplifiedQuery,
                    entities.stream().map(MedicalEntityRecognitionService.MedicalEntity::toString)
                            .collect(Collectors.joining(", ")));

            // 4. 同义词扩展（本地映射表 → SNOMED CT → LLM兜底）
            SynonymExpansionService.ExpansionResult expansionResult =
                    synonymExpansionService.expand(simplifiedQuery, entities);
            String expandedQuery = expansionResult.getExpandedQuery();
            if (!expandedQuery.equals(simplifiedQuery)) {
                expandedQueries.incrementAndGet();
                log.debug("同义词扩展: mappings={}, unmapped={}",
                        expansionResult.getMappings().size(), expansionResult.getUnmappedTerms().size());
            }

            // 5. 查询扩展（基于意图添加相关术语）
            String intentExpanded = expand(expandedQuery);

            // 6. 医疗术语化（SNOMED CT标准化）
            String medicalizedQuery = medicalize(intentExpanded);

            // 记录改写
            if (medicalizedQuery != null && !medicalizedQuery.equals(originalQuery)) {
                rewrittenQueries.incrementAndGet();
            }

            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);

            log.debug("查询改写完成: original={}, rewritten={}, entities={}, time={}ms",
                    originalQuery, medicalizedQuery, entities.size(), processingTime);

            return medicalizedQuery;

        } catch (Exception e) {
            log.error("查询改写异常: query={}", originalQuery, e);
            return originalQuery;
        }
    }

    /**
     * 新增：查询归一化接口（增强版）
     * 完整的归一化流程：拼写纠正 → 简化 → 实体识别 → 同义词扩展 → 术语化
     */
    public String normalize(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        try {
            String corrected = correctSpelling(query);
            String simplified = simplify(corrected);

            // 实体识别 + 同义词扩展
            List<MedicalEntityRecognitionService.MedicalEntity> entities =
                    entityRecognitionService.recognize(simplified);
            SynonymExpansionService.ExpansionResult expansionResult =
                    synonymExpansionService.expand(simplified, entities);

            String medicalized = medicalize(expansionResult.getExpandedQuery());

            if (!medicalized.equals(query)) {
                normalizedQueries.incrementAndGet();
            }

            return medicalized;
        } catch (Exception e) {
            log.warn("查询归一化失败: query={}", query, e);
            return query;
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
        Set<String> alreadyAdded = new HashSet<>();

        // 1. 添加同义词（仅当同义词不在原查询中时）
        for (Map.Entry<String, List<String>> entry : synonymsMap.entrySet()) {
            String term = entry.getKey();
            if (query.contains(term)) {
                List<String> synonyms = entry.getValue();
                for (String synonym : synonyms) {
                    if (!query.contains(synonym) && !alreadyAdded.contains(synonym)) {
                        expandedQuery.append(" ").append(synonym);
                        alreadyAdded.add(synonym);
                        break; // 只添加第一个不在查询中的同义词
                    }
                }
            }
        }

        // 2. 添加相关术语（基于意图，条件性追加）
        IntentClassification intent = classifyIntent(query);
        List<String> relatedTerms = getRelatedTermsByIntent(intent.getPrimaryIntent());

        for (String term : relatedTerms) {
            if (!query.contains(term) && !alreadyAdded.contains(term)) {
                expandedQuery.append(" ").append(term);
                alreadyAdded.add(term);
                break; // 只添加一个最相关的术语
            }
        }

        return expandedQuery.toString().trim();
    }

    /**
     * 根据意图获取相关术语
     */
    private List<String> getRelatedTermsByIntent(IntentType intent) {
        switch (intent) {
            case DISEASE_QUERY:
                return Arrays.asList("症状", "治疗", "预防");
            case DRUG_QUERY:
                return Arrays.asList("用法", "用量", "副作用");
            case SYMPTOM_QUERY:
                return Arrays.asList("可能疾病", "诊断", "检查");
            case TREATMENT_QUERY:
                return Arrays.asList("手术", "康复", "理疗");
            case PREVENTION_QUERY:
                return Arrays.asList("健康", "保健", "锻炼");
            case EXAMINATION_QUERY:
                return Arrays.asList("化验", "检测", "筛查");
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public String simplify(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        // 改进版：避免移除否定词
        String[] words = query.split("\\s+");
        List<String> keptWords = new ArrayList<>();

        for (String word : words) {
            // 检查是否为否定词
            if (NEGATION_WORDS.contains(word)) {
                keptWords.add(word);
                continue;
            }

            // 检查是否为停用词
            if (!stopWords.contains(word)) {
                keptWords.add(word);
            }
        }

        // 如果简化后为空，返回原始查询
        if (keptWords.isEmpty()) {
            return query;
        }

        return String.join(" ", keptWords);
    }

    @Override
    public String correctSpelling(String query) {
        if (query == null) {
            return null;
        }

        // 如果拼写纠正器禁用，使用简单纠正
        if (!enableSpellChecker) {
            return simpleSpellCorrect(query);
        }

        // 使用symspell-java进行智能纠正（待启用）
        /*
        if (symSpell != null) {
            StringBuilder corrected = new StringBuilder();
            String[] words = query.split("\\s+");

            for (String word : words) {
                List<SuggestItem> suggestions = symSpell.lookup(word, SymSpell.Verbosity.Closest);
                if (!suggestions.isEmpty() && suggestions.get(0).distance <= 2) {
                    corrected.append(suggestions.get(0).term).append(" ");
                } else {
                    corrected.append(word).append(" ");
                }
            }

            String result = corrected.toString().trim();
            if (!result.equals(query)) {
                log.debug("智能拼写纠正: {} -> {}", query, result);
            }
            return result;
        }
        */

        // 回退到简单纠正
        return simpleSpellCorrect(query);
    }

    /**
     * 简单拼写纠正（兼容旧版本）
     */
    private String simpleSpellCorrect(String query) {
        Map<String, String> commonCorrections = Map.of(
                "感冐", "感冒",
                "糖原病", "糖尿病",
                "高血圧", "高血压",
                "心藏病", "心脏病",
                "肺焱", "肺炎",
                "胃疼", "胃痛",
                "头庝", "头痛",
                "腹写", "腹泻",
                "发焼", "发烧",
                "咳嗾", "咳嗽"
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

        // 优先使用SNOMED CT服务进行术语化（包含降级策略）
        if (snomedTerminologyService != null) {
            try {
                String snomedResult = snomedTerminologyService.medicalize(query);
                if (snomedResult != null && !snomedResult.equals(query)) {
                    log.debug("SNOMED CT术语化: {} -> {}", query, snomedResult);
                    return snomedResult;
                }
            } catch (Exception e) {
                log.warn("SNOMED CT术语化失败，使用本地映射: {}", e.getMessage());
            }
        }

        // 降级：使用本地映射表
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
                "没胃口", "食欲不振",
                "感冒了", "感冒",
                "发烧了", "发热"
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

        // 检查是否包含否定词 + 医学实体
        boolean hasNegation = false;
        for (String negation : NEGATION_WORDS) {
            if (query.contains(negation)) {
                hasNegation = true;
                break;
            }
        }

        // 计算各意图的分数（增强版）
        Map<IntentType, Double> intentScores = new HashMap<>();

        // 疾病查询分数（使用THUOCL术语匹配）
        double diseaseScore = calculateEnhancedScore(query, IntentType.DISEASE_QUERY, hasNegation);
        intentScores.put(IntentType.DISEASE_QUERY, diseaseScore);

        // 症状查询分数
        double symptomScore = calculateEnhancedScore(query, IntentType.SYMPTOM_QUERY, hasNegation);
        intentScores.put(IntentType.SYMPTOM_QUERY, symptomScore);

        // 药物查询分数
        double drugScore = calculateEnhancedScore(query, IntentType.DRUG_QUERY, hasNegation);
        intentScores.put(IntentType.DRUG_QUERY, drugScore);

        // 其他意图分数
        intentScores.put(IntentType.DEPARTMENT_GUIDANCE, calculateDepartmentScore(query));
        intentScores.put(IntentType.TREATMENT_QUERY, calculateTreatmentIntentScore(query));
        intentScores.put(IntentType.PREVENTION_QUERY, calculatePreventionIntentScore(query));
        intentScores.put(IntentType.EXAMINATION_QUERY, calculateExaminationIntentScore(query));
        intentScores.put(IntentType.EMERGENCY_QUERY, calculateEmergencyIntentScore(query));
        intentScores.put(IntentType.GENERAL_HEALTH, calculateGeneralHealthIntentScore(query));

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

        log.debug("意图识别: query={}, primary={}, confidence={}, secondary={}, hasNegation={}",
                query, primaryIntent, confidence, secondaryIntents, hasNegation);

        return new IntentClassification(query, primaryIntent, secondaryIntents, confidence);
    }

    /**
     * 计算增强版意图分数（考虑THUOCL术语和否定词）
     */
    private double calculateEnhancedScore(String query, IntentType intentType, boolean hasNegation) {
        Set<String> keywords = getKeywordsByIntent(intentType);
        double baseScore = calculateWeightedKeywordScore(query, keywords);

        // 如果查询包含THUOCL医学术语，增加分数
        double medicalTermBonus = calculateMedicalTermBonus(query);
        baseScore += medicalTermBonus * 0.3; // 医学术语加成

        // 如果包含否定词，降低疾病/症状相关意图的分数
        if (hasNegation && (intentType == IntentType.DISEASE_QUERY || intentType == IntentType.SYMPTOM_QUERY)) {
            baseScore *= 0.5; // 减半
        }

        return Math.min(baseScore, 1.0);
    }

    /**
     * 计算加权关键词分数（考虑关键词权重）
     */
    private double calculateWeightedKeywordScore(String query, Set<String> keywords) {
        if (query == null || keywords == null || keywords.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                // 使用权重映射，默认为1.0
                double weight = KEYWORD_WEIGHTS.getOrDefault(keyword, 1.0);
                totalScore += weight * 0.2; // 基础分数
            }
        }

        return Math.min(totalScore, 1.0);
    }

    /**
     * 计算医学术语加成（THUOCL词库匹配）
     */
    private double calculateMedicalTermBonus(String query) {
        int matches = 0;
        for (String term : medicalTerms) {
            if (query.contains(term)) {
                matches++;
                if (matches >= 3) break; // 最多匹配3个
            }
        }
        return matches * 0.2; // 每个匹配加0.2分
    }

    /**
     * 根据意图获取关键词集合
     */
    private Set<String> getKeywordsByIntent(IntentType intentType) {
        switch (intentType) {
            case DISEASE_QUERY:
                return Set.of("病", "疾病", "症", "炎症", "感染", "肿瘤", "癌", "瘤", "溃疡", "结石", "硬化");
            case DRUG_QUERY:
                return Set.of("药", "药物", "胶囊", "片", "丸", "颗粒", "注射液", "口服液", "膏", "贴", "喷雾");
            case SYMPTOM_QUERY:
                return Set.of("疼", "痛", "痒", "肿", "胀", "红", "热", "晕", "吐", "恶心", "呕吐", "咳", "喘",
                        "乏力", "疲劳", "虚弱", "失眠", "多梦", "焦虑", "抑郁");
            default:
                return Collections.emptySet();
        }
    }

    /**
     * 计算科室导诊分数
     */
    private double calculateDepartmentScore(String query) {
        Set<String> departmentKeywords = Set.of(
                "科", "科室", "门诊", "急诊", "外科", "内科", "儿科", "妇产科", "眼科", "耳鼻喉科",
                "皮肤科", "口腔科", "神经科", "心血管科", "消化科", "呼吸科"
        );
        return calculateWeightedKeywordScore(query, departmentKeywords);
    }

    /**
     * 计算治疗意图分数
     */
    private double calculateTreatmentIntentScore(String query) {
        Set<String> treatmentKeywords = Set.of(
                "治疗", "治愈", "疗法", "手术", "开刀", "吃药", "打针", "输液",
                "康复", "理疗", "针灸", "推拿", "按摩"
        );
        return calculateWeightedKeywordScore(query, treatmentKeywords);
    }

    /**
     * 计算预防意图分数
     */
    private double calculatePreventionIntentScore(String query) {
        Set<String> preventionKeywords = Set.of(
                "预防", "防范", "避免", "防止", "预防措施", "健康", "保健",
                "养生", "锻炼", "运动", "饮食", "营养"
        );
        return calculateWeightedKeywordScore(query, preventionKeywords);
    }

    /**
     * 计算检查意图分数
     */
    private double calculateExaminationIntentScore(String query) {
        Set<String> examinationKeywords = Set.of(
                "检查", "化验", "检测", "筛查", "体检", "B超", "CT", "X光",
                "核磁", "血常规", "尿常规", "心电图"
        );
        return calculateWeightedKeywordScore(query, examinationKeywords);
    }

    /**
     * 计算急诊意图分数
     */
    private double calculateEmergencyIntentScore(String query) {
        Set<String> emergencyKeywords = Set.of(
                "急诊", "紧急", "急救", "救命", "危险", "严重", "马上", "立刻",
                "赶快", "快点", "突发", "突然"
        );
        return calculateWeightedKeywordScore(query, emergencyKeywords);
    }

    /**
     * 计算一般健康意图分数
     */
    private double calculateGeneralHealthIntentScore(String query) {
        Set<String> generalKeywords = Set.of(
                "健康", "身体", "体质", "免疫力", "抵抗力", "生活习惯",
                "作息", "睡眠", "饮食", "运动", "心理", "情绪"
        );
        return calculateWeightedKeywordScore(query, generalKeywords);
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

    /**
     * 获取归一化统计（新增方法）
     */
    public int getNormalizedQueriesCount() {
        return normalizedQueries.get();
    }

    /**
     * 获取THUOCL词库信息（增强版）
     */
    public Map<String, Object> getMedicalTermsInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("termCount", medicalTerms.size());
        info.put("lastLoadedTime", new Date(thuoclLastLoadedTime));
        info.put("dictPath", thuoclDictPath);
        // 实体识别统计
        info.put("entityRecognition", entityRecognitionService.getStats());
        // 同义词扩展统计
        SynonymExpansionService.ExpansionStats expStats = synonymExpansionService.getStats();
        Map<String, Object> expansionInfo = new HashMap<>();
        expansionInfo.put("totalQueries", expStats.getTotalQueries());
        expansionInfo.put("localHits", expStats.getLocalHits());
        expansionInfo.put("snomedHits", expStats.getSnomedHits());
        expansionInfo.put("llmHits", expStats.getLlmHits());
        expansionInfo.put("unmappedCount", expStats.getUnmappedCount());
        expansionInfo.put("unmappedRate", String.format("%.2f%%", expStats.getUnmappedRate()));
        expansionInfo.put("pendingReviewCount", expStats.getPendingReviewCount());
        expansionInfo.put("alertTriggered", expStats.isAlertTriggered());
        info.put("synonymExpansion", expansionInfo);
        return info;
    }

    /**
     * 获取待审核的未映射词条
     */
    public List<SynonymExpansionService.UnmappedTerm> getPendingUnmappedTerms(int limit) {
        return synonymExpansionService.getUnmappedTerms(limit);
    }

    /**
     * 人工审核通过同义词映射
     */
    public void approveSynonymMapping(String colloquialTerm, String standardTerm, String reviewer) {
        synonymExpansionService.approveMapping(colloquialTerm, standardTerm, reviewer);
    }

    /**
     * 手动重新加载THUOCL词库（新增方法）
     */
    public boolean reloadMedicalTermsManually() {
        try {
            loadMedicalTerms();
            log.info("手动重新加载THUOCL词库成功");
            return true;
        } catch (Exception e) {
            log.error("手动重新加载THUOCL词库失败", e);
            return false;
        }
    }
}