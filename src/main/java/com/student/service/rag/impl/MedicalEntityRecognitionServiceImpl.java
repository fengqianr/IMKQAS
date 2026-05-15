package com.student.service.rag.impl;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.NLPTokenizer;
import com.student.service.rag.MedicalEntityRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 医学实体识别服务实现
 * 使用 HanLP 分词 + THUOCL 医学词库进行命名实体识别
 * 识别药品、疾病、症状、人群、身体部位等医学实体
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class MedicalEntityRecognitionServiceImpl implements MedicalEntityRecognitionService {

    @Value("${imkqas.query-rewrite.thuocl-dict-path:/data/dict/thuocl_medical.txt}")
    private String thuoclDictPath;

    // 分类词库：按实体类型组织的THUOCL词条
    private final Map<EntityType, Set<String>> entityDictionary = new ConcurrentHashMap<>();
    private long dictLastLoadedTime = 0;

    // 统计
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger totalEntitiesFound = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    // 实体类型关键词特征（用于未登录词的启发式识别）
    private static final Map<EntityType, List<String>> TYPE_FEATURES = Map.of(
            EntityType.DRUG, List.of("药", "胶囊", "片", "丸", "颗粒", "注射液", "口服液", "膏", "贴", "喷雾", "素", "西林", "沙星", "替尼"),
            EntityType.DISEASE, List.of("病", "炎", "症", "瘤", "癌", "溃疡", "结石", "硬化", "萎缩", "增生", "畸形", "衰竭"),
            EntityType.SYMPTOM, List.of("痛", "疼", "痒", "肿", "胀", "晕", "吐", "咳", "喘", "烧", "热", "乏力", "疲劳", "失眠"),
            EntityType.POPULATION, List.of("儿童", "小儿", "老人", "老年", "孕妇", "产妇", "婴儿", "新生儿", "青少年", "成人", "男性", "女性"),
            EntityType.BODY_PART, List.of("头", "颈", "胸", "腹", "背", "腰", "手", "脚", "腿", "臂", "眼", "耳", "鼻", "口", "心", "肝", "肺", "肾", "胃", "肠"),
            EntityType.EXAMINATION, List.of("检查", "化验", "检测", "筛查", "B超", "CT", "X光", "核磁", "心电图", "血常规", "尿常规"),
            EntityType.TREATMENT, List.of("手术", "切除", "移植", "透析", "化疗", "放疗", "理疗", "针灸", "推拿")
    );

    // 人群实体精确匹配
    private static final Set<String> POPULATION_ENTITIES = Set.of(
            "儿童", "小儿", "婴幼儿", "婴儿", "新生儿", "幼儿", "老人", "老年人", "老年",
            "孕妇", "产妇", "哺乳期", "青少年", "成人", "成年人", "男性", "女性", "中老年"
    );

    @PostConstruct
    public void init() {
        loadDictionary();
        log.info("医学实体识别服务初始化完成: 词库条目数={}", getTotalDictSize());
    }

    private void loadDictionary() {
        File dictFile = new File(thuoclDictPath);
        if (!dictFile.exists()) {
            log.warn("THUOCL词库文件不存在: {}，使用内置词库", thuoclDictPath);
            loadBuiltinDictionary();
            return;
        }

        long lastModified = dictFile.lastModified();
        if (lastModified <= dictLastLoadedTime) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(dictFile))) {
            Map<EntityType, Set<String>> newDict = new HashMap<>();
            for (EntityType type : EntityType.values()) {
                newDict.put(type, new HashSet<>());
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\t");
                String term = parts[0].trim();
                EntityType type = parts.length > 1 ? parseEntityType(parts[1].trim()) : classifyByFeatures(term);

                newDict.get(type).add(term);
            }

            entityDictionary.clear();
            entityDictionary.putAll(newDict);
            dictLastLoadedTime = lastModified;

            log.info("THUOCL医学词库加载成功: 文件={}, 条目数={}", thuoclDictPath, getTotalDictSize());
        } catch (IOException e) {
            log.error("加载THUOCL词库失败: {}", thuoclDictPath, e);
            loadBuiltinDictionary();
        }
    }

    private void loadBuiltinDictionary() {
        entityDictionary.clear();
        for (EntityType type : EntityType.values()) {
            entityDictionary.put(type, ConcurrentHashMap.newKeySet());
        }

        // 内置药品词条
        addBuiltinTerms(EntityType.DRUG, "阿司匹林", "青霉素", "华法林", "布洛芬", "对乙酰氨基酚",
                "扑热息痛", "阿莫西林", "头孢", "胰岛素", "二甲双胍", "硝苯地平", "卡托普利",
                "奥美拉唑", "雷尼替丁", "地高辛", "硝酸甘油", "沙丁胺醇", "氨茶碱");

        // 内置疾病词条
        addBuiltinTerms(EntityType.DISEASE, "感冒", "流感", "肺炎", "支气管炎", "哮喘", "高血压",
                "糖尿病", "心脏病", "冠心病", "胃炎", "肝炎", "肾炎", "关节炎", "脑梗塞",
                "脑出血", "心肌梗死", "心绞痛", "阑尾炎", "胆囊炎", "胰腺炎");

        // 内置症状词条
        addBuiltinTerms(EntityType.SYMPTOM, "头痛", "发热", "咳嗽", "咳痰", "胸痛", "腹痛", "腹泻",
                "便秘", "恶心", "呕吐", "头晕", "乏力", "失眠", "心悸", "呼吸困难",
                "水肿", "黄疸", "皮疹", "瘙痒", "关节痛");

        // 内置人群词条
        addBuiltinTerms(EntityType.POPULATION, "儿童", "小儿", "婴幼儿", "老人", "老年人", "孕妇",
                "产妇", "哺乳期", "青少年", "成人", "男性", "女性", "新生儿");

        // 内置检查词条
        addBuiltinTerms(EntityType.EXAMINATION, "血常规", "尿常规", "心电图", "B超", "CT", "X光",
                "核磁共振", "胃镜", "肠镜", "肝功能", "肾功能", "血糖", "血压");

        log.info("内置医学词库加载完成: 条目数={}", getTotalDictSize());
    }

    private void addBuiltinTerms(EntityType type, String... terms) {
        Set<String> set = entityDictionary.get(type);
        if (set != null) {
            set.addAll(Arrays.asList(terms));
        }
    }

    private EntityType parseEntityType(String typeStr) {
        switch (typeStr.toLowerCase()) {
            case "drug": case "药品": return EntityType.DRUG;
            case "disease": case "疾病": return EntityType.DISEASE;
            case "symptom": case "症状": return EntityType.SYMPTOM;
            case "population": case "人群": return EntityType.POPULATION;
            case "body_part": case "身体部位": return EntityType.BODY_PART;
            case "examination": case "检查": return EntityType.EXAMINATION;
            case "treatment": case "治疗": return EntityType.TREATMENT;
            default: return EntityType.OTHER;
        }
    }

    /**
     * 根据特征词启发式分类（未登录词）
     */
    private EntityType classifyByFeatures(String term) {
        for (Map.Entry<EntityType, List<String>> entry : TYPE_FEATURES.entrySet()) {
            for (String feature : entry.getValue()) {
                if (term.contains(feature)) {
                    return entry.getKey();
                }
            }
        }
        return EntityType.OTHER;
    }

    @Override
    public List<MedicalEntity> recognize(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();

        try {
            List<MedicalEntity> entities = new ArrayList<>();
            Set<String> foundTexts = new HashSet<>(); // 去重

            // 1. HanLP 分词
            List<Term> terms = HanLP.segment(query);

            // 2. 对每个分词结果进行实体类型匹配
            for (Term term : terms) {
                String word = term.word.trim();
                if (word.isEmpty() || word.length() == 1 && !isPunctuation(word)) continue;

                // 跳过纯标点和数字
                if (word.matches("[\\d.,;:!?，。；：！？、]+")) continue;

                // 查找匹配的实体类型
                EntityType matchedType = matchEntityType(word);
                if (matchedType != EntityType.OTHER && foundTexts.add(word)) {
                    entities.add(new MedicalEntity(
                            word, matchedType,
                            calculateConfidence(word, matchedType),
                            term.offset,
                            term.offset + word.length()
                    ));
                }
            }

            // 3. 使用NLPTokenizer进行更精确的实体识别（如人群实体等）
            List<Term> nlpTerms = NLPTokenizer.segment(query);
            for (Term term : nlpTerms) {
                String word = term.word.trim();
                if (word.isEmpty() || foundTexts.contains(word)) continue;

                // 精确匹配人群实体
                if (POPULATION_ENTITIES.contains(word) && foundTexts.add(word)) {
                    entities.add(new MedicalEntity(word, EntityType.POPULATION, 0.95, term.offset, term.offset + word.length()));
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            totalEntitiesFound.addAndGet(entities.size());

            log.debug("实体识别完成: query={}, entities={}, time={}ms", query, entities, processingTime);
            return entities;

        } catch (Exception e) {
            log.error("实体识别异常: query={}", query, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> segment(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Term> terms = HanLP.segment(query);
            return terms.stream()
                    .map(t -> t.word)
                    .filter(w -> !w.matches("[\\d.,;:!?，。；：！？、\\s]+"))
                    .toList();
        } catch (Exception e) {
            log.error("分词异常: query={}", query, e);
            return Arrays.asList(query.split("\\s+"));
        }
    }

    /**
     * 匹配实体类型：先查词典，再启发式匹配
     */
    private EntityType matchEntityType(String word) {
        for (Map.Entry<EntityType, Set<String>> entry : entityDictionary.entrySet()) {
            if (entry.getValue().contains(word)) {
                return entry.getKey();
            }
        }

        // 词典未命中，使用特征启发式
        return classifyByFeatures(word);
    }

    /**
     * 计算实体置信度
     */
    private double calculateConfidence(String word, EntityType type) {
        // 词典中的词条置信度高
        Set<String> dict = entityDictionary.get(type);
        if (dict != null && dict.contains(word)) {
            return 0.9;
        }

        // 启发式匹配置信度适中
        if (word.length() >= 4) return 0.7;
        if (word.length() >= 3) return 0.6;
        return 0.5;
    }

    private boolean isPunctuation(String s) {
        return s.matches("[，。；：！？、\\s]");
    }

    private int getTotalDictSize() {
        return entityDictionary.values().stream().mapToInt(Set::size).sum();
    }

    @Scheduled(fixedDelay = 600000)
    public void reloadDictionary() {
        loadDictionary();
    }

    @Override
    public boolean isAvailable() {
        return !entityDictionary.isEmpty();
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalQueries", totalQueries.get());
        stats.put("totalEntitiesFound", totalEntitiesFound.get());
        stats.put("dictSize", getTotalDictSize());
        stats.put("entityTypeSizes", getEntityTypeSizes());
        int total = totalQueries.get();
        if (total > 0) {
            stats.put("avgEntitiesPerQuery", String.format("%.2f", (double) totalEntitiesFound.get() / total));
            stats.put("avgProcessingTime", String.format("%.2fms", (double) totalProcessingTime.get() / total));
        }
        return stats;
    }

    private Map<String, Integer> getEntityTypeSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        for (Map.Entry<EntityType, Set<String>> entry : entityDictionary.entrySet()) {
            sizes.put(entry.getKey().getLabel(), entry.getValue().size());
        }
        return sizes;
    }
}
