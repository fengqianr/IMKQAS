package com.imkqas.service.rag.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imkqas.entity.DocumentChunk;
import com.imkqas.entity.contraindication.DrugPopulationContraindication;
import com.imkqas.mapper.DrugPopulationContraindicationMapper;
import com.imkqas.service.rag.ContraindicationDetectionService;
import com.imkqas.service.rag.MedicalEntityRecognitionService;
import com.imkqas.service.rag.MedicalEntityRecognitionService.EntityType;
import com.imkqas.service.rag.MedicalEntityRecognitionService.MedicalEntity;
import com.imkqas.service.rag.SynonymExpansionService;
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
    private final SynonymExpansionService synonymExpansionService;
    private final ObjectMapper objectMapper;

    /** 禁忌规则缓存（药物名 -> 人群名 -> 禁忌规则） */
    private volatile Map<String, Map<String, DrugPopulationContraindication>> ruleCache = Collections.emptyMap();

    /** 规则缓存最后加载时间 */
    private volatile long ruleCacheLoadTime = 0L;

    public ContraindicationDetectionServiceImpl(
            DrugPopulationContraindicationMapper ruleMapper,
            MedicalEntityRecognitionService entityRecognitionService,
            SynonymExpansionService synonymExpansionService,
            ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.entityRecognitionService = entityRecognitionService;
        this.synonymExpansionService = synonymExpansionService;
        this.objectMapper = objectMapper;
        refreshCache();
        log.info("禁忌检测服务初始化完成: 规则缓存大小={}", getRuleCount());
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

        // 2. 统一走 SynonymExpansionService 标准化（LOCAL → SNOMED → LLM）
        //    expand() 内部自动将未映射词条写入审核队列
        SynonymExpansionService.ExpansionResult expansion =
                synonymExpansionService.expand(chunkContent, entities);

        Map<String, String> drugStandardMap = buildStandardMap(expansion, drugs);
        Map<String, String> popStandardMap = buildStandardMap(expansion, populations);

        // 3. 标准化后的名称匹配规则表，标准化失败用原始文本兜底
        List<ContraindicationMatch> matches = new ArrayList<>();
        for (String drug : drugs) {
            String genericName = drugStandardMap.getOrDefault(drug, drug);
            for (String population : populations) {
                String standardPopulation = popStandardMap.getOrDefault(population, population);
                ContraindicationMatch match = matchRule(genericName, standardPopulation);
                if (match != null) {
                    matches.add(match);
                }
            }
        }
        return matches;
    }

    /** 从 ExpansionResult 中提取指定原始文本到标准名称的映射 */
    private Map<String, String> buildStandardMap(
            SynonymExpansionService.ExpansionResult expansion, List<String> originalTexts) {
        Map<String, String> result = new HashMap<>();
        Set<String> origSet = new HashSet<>(originalTexts);
        for (SynonymExpansionService.TermMapping mapping : expansion.getMappings()) {
            if (origSet.contains(mapping.getColloquialTerm())
                    && mapping.getStandardTerm() != null) {
                result.put(mapping.getColloquialTerm(), mapping.getStandardTerm());
            }
        }
        return result;
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

        // 统一走 SynonymExpansionService 标准化
        SynonymExpansionService.ExpansionResult expansion =
                synonymExpansionService.expand(query, entities);
        Map<String, String> drugStandardMap = buildStandardMap(expansion, drugs);
        Map<String, String> popStandardMap = buildStandardMap(expansion, populations);

        // 检查是否规则已覆盖
        for (String drug : drugs) {
            String genericName = drugStandardMap.getOrDefault(drug, drug);
            for (String population : populations) {
                String standardPopulation = popStandardMap.getOrDefault(population, population);
                if (matchRule(genericName, standardPopulation) != null) {
                    return null; // 规则已覆盖，不需要兜底
                }
            }
        }

        // 规则未覆盖 → 用原始文本生成兜底安全提示
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

        log.info("禁忌规则缓存已刷新: 规则数={}", allRules.size());
    }

    // ========== 私有方法 ==========

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

    private int getRuleCount() {
        return ruleCache.values().stream().mapToInt(Map::size).sum();
    }
}
