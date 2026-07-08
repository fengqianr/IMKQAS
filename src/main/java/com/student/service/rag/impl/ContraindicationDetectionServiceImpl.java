package com.student.service.rag.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.entity.DocumentChunk;
import com.student.entity.contraindication.DrugPopulationContraindication;
import com.student.entity.drug.Drug;
import com.student.entity.drug.DrugAlias;
import com.student.entity.synonym.MedicalSynonymMapping;
import com.student.mapper.*;
import com.student.service.rag.ContraindicationDetectionService;
import com.student.service.rag.MedicalEntityRecognitionService;
import com.student.service.rag.MedicalEntityRecognitionService.EntityType;
import com.student.service.rag.MedicalEntityRecognitionService.MedicalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 禁忌检测服务实现
 * 离线阶段：文档分块 → 实体抽取 → 规则表匹配 → 标注chunk
 * 在线阶段：读取chunk预标注标记，做过滤/降权决策
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
public class ContraindicationDetectionServiceImpl implements ContraindicationDetectionService {

    private final DrugPopulationContraindicationMapper ruleMapper;
    private final MedicalEntityRecognitionService entityRecognitionService;
    private final DrugAliasMapper drugAliasMapper;
    private final DrugMapper drugMapper;
    private final MedicalSynonymMappingMapper synonymMappingMapper;
    private final ObjectMapper objectMapper;

    /** 禁忌规则缓存（药物名 -> 人群名 -> 禁忌规则） */
    private volatile Map<String, Map<String, DrugPopulationContraindication>> ruleCache = Collections.emptyMap();

    /** 规则缓存最后加载时间 */
    private volatile long ruleCacheLoadTime = 0L;

    /** 药品别名→通用名映射缓存 */
    private volatile Map<String, String> aliasToGenericMap = Collections.emptyMap();

    /** 人群同义词→标准名映射缓存 */
    private volatile Map<String, String> populationSynonymMap = Collections.emptyMap();

    public ContraindicationDetectionServiceImpl(
            DrugPopulationContraindicationMapper ruleMapper,
            MedicalEntityRecognitionService entityRecognitionService,
            DrugAliasMapper drugAliasMapper,
            DrugMapper drugMapper,
            MedicalSynonymMappingMapper synonymMappingMapper,
            ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.entityRecognitionService = entityRecognitionService;
        this.drugAliasMapper = drugAliasMapper;
        this.drugMapper = drugMapper;
        this.synonymMappingMapper = synonymMappingMapper;
        this.objectMapper = objectMapper;
        refreshCache();
        log.info("禁忌检测服务初始化完成: 规则缓存大小={}, 别名映射={}, 人群同义词={}",
                getRuleCount(), aliasToGenericMap.size(), populationSynonymMap.size());
    }

    // ========== 公共方法 ==========

    @Override
    public void annotateChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;

        int flaggedCount = 0;
        for (DocumentChunk chunk : chunks) {
            List<ContraindicationMatch> matches = detectChunk(chunk.getContent());
            if (!matches.isEmpty()) {
                chunk.setHasContraindication(1);
                try {
                    chunk.setContraindicationInfo(objectMapper.writeValueAsString(matches));
                } catch (JsonProcessingException e) {
                    log.warn("序列化禁忌标注失败: chunkId={}", chunk.getId(), e);
                }
                flaggedCount++;
            }
        }
        if (flaggedCount > 0) {
            log.info("禁忌标注完成: 总chunk={}, 标记={}, 比例={:.1f}%",
                    chunks.size(), flaggedCount, flaggedCount * 100.0 / chunks.size());
        }
    }

    @Override
    public List<ContraindicationMatch> detectChunk(String chunkContent) {
        if (chunkContent == null || chunkContent.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 实体抽取（只取DRUG和POPULATION）
        List<MedicalEntity> entities = entityRecognitionService.recognize(chunkContent);
        List<String> drugs = entities.stream()
                .filter(e -> e.getType() == EntityType.DRUG)
                .map(MedicalEntity::getText)
                .distinct()
                .collect(Collectors.toList());
        List<String> populations = entities.stream()
                .filter(e -> e.getType() == EntityType.POPULATION)
                .map(MedicalEntity::getText)
                .distinct()
                .collect(Collectors.toList());

        if (drugs.isEmpty() || populations.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 别名/同义词扩展后匹配规则表
        List<ContraindicationMatch> matches = new ArrayList<>();
        for (String drug : drugs) {
            String genericName = resolveDrugGenericName(drug);
            for (String population : populations) {
                String standardPopulation = resolvePopulationSynonym(population);
                ContraindicationMatch match = matchRule(genericName, standardPopulation);
                if (match != null) {
                    matches.add(match);
                }
            }
        }
        return matches;
    }

    @Override
    public String buildSafetyNote(String query) {
        if (query == null || query.trim().isEmpty()) return null;

        List<MedicalEntity> entities = entityRecognitionService.recognize(query);
        List<String> drugs = entities.stream()
                .filter(e -> e.getType() == EntityType.DRUG)
                .map(MedicalEntity::getText)
                .distinct()
                .collect(Collectors.toList());
        List<String> populations = entities.stream()
                .filter(e -> e.getType() == EntityType.POPULATION)
                .map(MedicalEntity::getText)
                .distinct()
                .collect(Collectors.toList());

        if (drugs.isEmpty() || populations.isEmpty()) return null;

        // 检查是否规则已覆盖
        for (String drug : drugs) {
            String genericName = resolveDrugGenericName(drug);
            for (String population : populations) {
                String standardPopulation = resolvePopulationSynonym(population);
                if (matchRule(genericName, standardPopulation) != null) {
                    return null; // 规则已覆盖，不需要兜底
                }
            }
        }

        // 规则未覆盖 → 生成兜底提示
        String drugText = String.join("、", drugs);
        String populationText = String.join("、", populations);
        return String.format(
                "> **安全提示**：关于%s在%s中的使用，建议在使用前咨询专业医生或药师，"
                        + "结合具体病情和个体情况综合评估风险。",
                drugText, populationText);
    }

    @Override
    public void refreshCache() {
        List<DrugPopulationContraindication> allRules = ruleMapper.selectActive();
        Map<String, Map<String, DrugPopulationContraindication>> newCache = new HashMap<>();
        for (DrugPopulationContraindication rule : allRules) {
            newCache.computeIfAbsent(rule.getDrugName(), k -> new HashMap<>())
                    .put(rule.getPopulationName(), rule);
        }
        this.ruleCache = newCache;
        this.ruleCacheLoadTime = System.currentTimeMillis();

        loadAliasMap();
        loadPopulationSynonyms();

        log.info("禁忌规则缓存已刷新: 规则数={}, 别名映射={}, 人群同义词={}",
                allRules.size(), aliasToGenericMap.size(), populationSynonymMap.size());
    }

    // ========== 私有方法 ==========

    /** 药物名 → 通用名（通过 Drug 表 + DrugAlias 表做别名扩展） */
    private String resolveDrugGenericName(String drugText) {
        // 直接匹配 Drug.genericName
        List<Drug> directMatch = drugMapper.selectList(
                new LambdaQueryWrapper<Drug>()
                        .eq(Drug::getGenericName, drugText)
                        .eq(Drug::getDeleted, 0));
        if (!directMatch.isEmpty()) return drugText;

        // 别名缓存命中
        String cached = aliasToGenericMap.get(drugText);
        if (cached != null) return cached;

        // 查 DrugAlias 表
        List<DrugAlias> aliases = drugAliasMapper.selectList(
                new LambdaQueryWrapper<DrugAlias>()
                        .eq(DrugAlias::getAliasName, drugText)
                        .eq(DrugAlias::getDeleted, 0));
        if (!aliases.isEmpty()) {
            Drug drug = drugMapper.selectById(aliases.get(0).getDrugId());
            if (drug != null && drug.getDeleted() == 0) {
                return drug.getGenericName();
            }
        }

        // 没找到映射，直接用原文本
        return drugText;
    }

    /** 人群名 → 标准名（通过 medical_synonym_mapping 表做同义词扩展） */
    private String resolvePopulationSynonym(String populationText) {
        return populationSynonymMap.getOrDefault(populationText, populationText);
    }

    /** 查规则缓存：<药物通用名, 人群标准名> → 禁忌规则 */
    private ContraindicationMatch matchRule(String genericName, String population) {
        Map<String, DrugPopulationContraindication> popRules = ruleCache.get(genericName);
        if (popRules == null) return null;

        DrugPopulationContraindication rule = popRules.get(population);
        if (rule == null) return null;

        return new ContraindicationMatch(
                rule.getDrugName(),
                rule.getPopulationName(),
                rule.getContraindicationType(),
                rule.getEvidenceLevel(),
                rule.getDescription());
    }

    private void loadAliasMap() {
        Map<String, String> map = new HashMap<>();
        // 从 Drug 表直接映射 brandName → genericName
        List<Drug> allDrugs = drugMapper.selectList(
                new LambdaQueryWrapper<Drug>().eq(Drug::getDeleted, 0));
        for (Drug drug : allDrugs) {
            if (drug.getBrandName() != null && !drug.getBrandName().isEmpty()) {
                map.put(drug.getBrandName(), drug.getGenericName());
            }
            if (drug.getEnglishName() != null && !drug.getEnglishName().isEmpty()) {
                map.put(drug.getEnglishName(), drug.getGenericName());
            }
            map.put(drug.getGenericName(), drug.getGenericName());
        }
        // 从 DrugAlias 表加载
        List<DrugAlias> allAliases = drugAliasMapper.selectList(
                new LambdaQueryWrapper<DrugAlias>().eq(DrugAlias::getDeleted, 0));
        for (DrugAlias alias : allAliases) {
            Drug drug = drugMapper.selectById(alias.getDrugId());
            if (drug != null && drug.getDeleted() == 0) {
                map.put(alias.getAliasName(), drug.getGenericName());
            }
        }
        this.aliasToGenericMap = map;
    }

    private void loadPopulationSynonyms() {
        Map<String, String> map = new HashMap<>();
        List<MedicalSynonymMapping> mappings = synonymMappingMapper.findAllApproved();
        for (MedicalSynonymMapping m : mappings) {
            if ("POPULATION".equals(m.getEntityType())) {
                map.put(m.getColloquialTerm(), m.getStandardTerm());
            }
        }
        // 内置兜底
        map.putIfAbsent("小儿", "儿童");
        map.putIfAbsent("婴幼儿", "儿童");
        map.putIfAbsent("新生儿", "儿童");
        map.putIfAbsent("老年人", "老人");
        map.putIfAbsent("高龄", "老人");
        map.putIfAbsent("哺乳期妇女", "哺乳期");
        map.putIfAbsent("孕产妇", "孕妇");
        map.putIfAbsent("妊娠期", "孕妇");
        map.putIfAbsent("备孕期", "孕妇");
        this.populationSynonymMap = map;
    }

    private int getRuleCount() {
        return ruleCache.values().stream().mapToInt(Map::size).sum();
    }
}
