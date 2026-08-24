package com.imkqas.service.his.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imkqas.config.his.HisConfigProperties;
import com.imkqas.dto.his.QuestionnaireFhirConverter;
import com.imkqas.entity.his.QuestionnaireRegistryEntity;
import com.imkqas.mapper.his.QuestionnaireRegistryMapper;
import com.imkqas.service.RedisService;
import com.imkqas.service.his.FhirQuestionnaireClient;
import com.imkqas.service.his.QuestionnaireCacheService;
import com.imkqas.service.his.QuestionnaireRegistry;
import com.imkqas.service.his.QuestionnaireTemplate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Questionnaire;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 问卷缓存协调服务实现
 * 三级缓存：内存 → Redis → FHIR（本地 JSON），FHIR 为权威源。
 * 注册表关键词索引由 MySQL 加载进内存，供匹配引擎使用。
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireCacheServiceImpl implements QuestionnaireCacheService {

    private static final String TEMPLATE_KEY_PREFIX = "his:questionnaire:template:";

    private final QuestionnaireRegistryMapper registryMapper;
    private final FhirQuestionnaireClient fhirClient;
    private final QuestionnaireFhirConverter converter;
    private final RedisService redisService;
    private final HisConfigProperties hisConfig;
    private final ObjectMapper objectMapper;

    /** 内存一级缓存：问卷模板 */
    private final Map<String, QuestionnaireTemplate> templateCache = new ConcurrentHashMap<>();

    /** 内存关键词索引：问卷注册表 */
    private volatile List<QuestionnaireRegistry> registries = List.of();

    @PostConstruct
    public void init() {
        loadRegistries();
        warmTemplateCache();
    }

    @Override
    public Optional<QuestionnaireTemplate> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        // L1 内存
        QuestionnaireTemplate cached = templateCache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }

        // L2 Redis
        try {
            Object redisValue = redisService.get(templateKey(id));
            if (redisValue instanceof String json) {
                Questionnaire q = converter.parseQuestionnaire(json);
                QuestionnaireTemplate template = converter.fromFhirQuestionnaire(q);
                if (template.getId() != null) {
                    templateCache.put(template.getId(), template);
                    return Optional.of(template);
                }
            }
        } catch (Exception e) {
            log.warn("读取 Redis 问卷缓存失败: id={}", id, e);
        }

        // L3 FHIR（本地 JSON，权威源）
        Optional<Questionnaire> questionnaireOpt = fhirClient.getById(id);
        if (questionnaireOpt.isPresent()) {
            Questionnaire q = questionnaireOpt.get();
            QuestionnaireTemplate template = converter.fromFhirQuestionnaire(q);
            templateCache.put(template.getId(), template);
            writeToRedis(template.getId(), q);
            return Optional.of(template);
        }

        log.warn("问卷不存在于任何缓存层级: {}", id);
        return Optional.empty();
    }

    @Override
    public List<QuestionnaireTemplate> findAll() {
        if (templateCache.isEmpty()) {
            warmTemplateCache();
        }
        Map<String, Integer> priorityMap = new HashMap<>();
        for (QuestionnaireRegistry r : registries) {
            priorityMap.put(r.getQuestionnaireId(),
                    r.getPriority() == null ? Integer.MAX_VALUE : r.getPriority());
        }
        return templateCache.values().stream()
                .sorted(Comparator
                        .comparingInt((QuestionnaireTemplate t) ->
                                priorityMap.getOrDefault(t.getId(), Integer.MAX_VALUE))
                        .thenComparing(QuestionnaireTemplate::getId))
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionnaireTemplate> findByCategory(String category) {
        if (category == null) {
            return List.of();
        }
        return findAll().stream()
                .filter(t -> category.equals(t.getCategory()))
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionnaireRegistry> getRegistries() {
        return registries;
    }

    @Override
    public void refresh() {
        loadRegistries();
        warmTemplateCache();
    }

    // ==================== 内部方法 ====================

    private void loadRegistries() {
        try {
            List<QuestionnaireRegistryEntity> entities = registryMapper.selectList(null);
            List<QuestionnaireRegistry> list = new ArrayList<>();
            for (QuestionnaireRegistryEntity entity : entities) {
                list.add(toRegistry(entity));
            }
            list.sort(Comparator
                    .comparingInt((QuestionnaireRegistry r) ->
                            r.getPriority() == null ? Integer.MAX_VALUE : r.getPriority())
                    .thenComparing(QuestionnaireRegistry::getQuestionnaireId));
            this.registries = list;
            log.info("加载问卷注册表完成，共 {} 条", list.size());
        } catch (Exception e) {
            log.error("加载问卷注册表失败，保留现有 {} 条", registries.size(), e);
        }
    }

    private QuestionnaireRegistry toRegistry(QuestionnaireRegistryEntity entity) {
        return QuestionnaireRegistry.builder()
                .questionnaireId(entity.getQuestionnaireId())
                .title(entity.getTitle())
                .category(entity.getCategory())
                .keywords(parseKeywords(entity.getKeywords()))
                .fhirPath(entity.getFhirPath())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .itemCount(entity.getItemCount())
                .build();
    }

    private List<String> parseKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(keywordsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析问卷关键词 JSON 失败: {}", keywordsJson, e);
            return List.of();
        }
    }

    private void warmTemplateCache() {
        try {
            List<Questionnaire> questionnaires = fhirClient.listActive();
            for (Questionnaire q : questionnaires) {
                QuestionnaireTemplate template = converter.fromFhirQuestionnaire(q);
                if (template.getId() != null) {
                    templateCache.put(template.getId(), template);
                    writeToRedis(template.getId(), q);
                }
            }
            log.info("问卷模板缓存预热完成，共 {} 个", templateCache.size());
        } catch (Exception e) {
            log.error("问卷模板缓存预热失败", e);
        }
    }

    private void writeToRedis(String id, Questionnaire q) {
        try {
            redisService.set(templateKey(id), converter.toJson(q),
                    hisConfig.getQuestionnaireCacheTtlSeconds());
        } catch (Exception e) {
            log.warn("写入 Redis 问卷缓存失败: id={}", id, e);
        }
    }

    private String templateKey(String id) {
        return TEMPLATE_KEY_PREFIX + id;
    }
}
