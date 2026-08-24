package com.imkqas.service.his.impl;

import com.imkqas.service.his.QuestionnaireCacheService;
import com.imkqas.service.his.QuestionnaireMatchResult;
import com.imkqas.service.his.QuestionnaireMatchingService;
import com.imkqas.service.his.QuestionnaireRepository;
import com.imkqas.service.his.QuestionnaireTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 问卷模板库实现
 * 委托缓存服务加载问卷内容、匹配引擎做关键词匹配，问卷内容不再内置硬编码。
 *
 * @author 系统
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class QuestionnaireRepositoryImpl implements QuestionnaireRepository {

    private final QuestionnaireCacheService cacheService;
    private final QuestionnaireMatchingService matchingService;

    @Override
    public Optional<QuestionnaireTemplate> findById(String id) {
        return cacheService.findById(id);
    }

    @Override
    public List<QuestionnaireTemplate> findAll() {
        return cacheService.findAll();
    }

    @Override
    public List<QuestionnaireTemplate> matchByKeywords(String userInput) {
        return matchingService.match(userInput).stream()
                .map(QuestionnaireMatchResult::getTemplate)
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionnaireTemplate> findByCategory(String category) {
        return cacheService.findByCategory(category);
    }
}
