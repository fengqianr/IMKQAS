package com.imkqas.service.his.impl;

import com.imkqas.dto.his.QuestionnaireFhirConverter;
import com.imkqas.service.his.FhirQuestionnaireClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Questionnaire;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本地 JSON 问卷客户端
 * 扫描 classpath:questionnaires/*.json，经 FHIR 解析器加载 Questionnaire 资源。
 * 作为 FhirQuestionnaireClient 的本地实现，后续可平滑替换为真实 HAPI FHIR 服务器客户端。
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalJsonQuestionnaireClient implements FhirQuestionnaireClient {

    private static final String QUESTIONNAIRE_PATTERN = "classpath:questionnaires/*.json";

    private final QuestionnaireFhirConverter converter;

    private final Map<String, Questionnaire> questionnaires = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 重新扫描 classpath 下的问卷 JSON（供定时同步或热加载调用）
     */
    public synchronized void reload() {
        Map<String, Questionnaire> loaded = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(QUESTIONNAIRE_PATTERN);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Questionnaire q = converter.parseQuestionnaire(json);
                    String id = q.getName();
                    if (id == null || id.isBlank()) {
                        log.warn("跳过无 name 的问卷 JSON: {}", resource.getFilename());
                        continue;
                    }
                    loaded.put(id, q);
                }
            }
        } catch (Exception e) {
            log.error("扫描 {} 失败", QUESTIONNAIRE_PATTERN, e);
        }
        if (loaded.isEmpty()) {
            log.warn("未加载到任何本地问卷 JSON，请检查 classpath:questionnaires/ 目录");
        } else {
            log.info("加载本地问卷 JSON 完成，共 {} 个：{}", loaded.size(), loaded.keySet());
        }
        questionnaires.clear();
        questionnaires.putAll(loaded);
    }

    @Override
    public Optional<Questionnaire> getById(String id) {
        return Optional.ofNullable(questionnaires.get(id));
    }

    @Override
    public List<Questionnaire> searchByTitle(String title) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        return questionnaires.values().stream()
                .filter(q -> q.getTitle() != null && q.getTitle().contains(title))
                .toList();
    }

    @Override
    public List<Questionnaire> listActive() {
        return questionnaires.values().stream()
                .filter(q -> q.getStatus() == Enumerations.PublicationStatus.ACTIVE)
                .toList();
    }
}
