package com.imkqas.service.his.impl;

import com.imkqas.service.his.QuestionnaireCacheService;
import com.imkqas.service.his.QuestionnaireMatchResult;
import com.imkqas.service.his.QuestionnaireMatchingService;
import com.imkqas.service.his.QuestionnaireRegistry;
import com.imkqas.service.his.QuestionnaireTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 问卷匹配引擎实现
 * 遍历注册表关键词统计命中数，按（命中数 desc, 优先级 asc）排序后解析为模板。
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireMatchingServiceImpl implements QuestionnaireMatchingService {

    private final QuestionnaireCacheService cacheService;

    @Override
    public List<QuestionnaireMatchResult> match(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return List.of();
        }
        String lower = userInput.toLowerCase(Locale.ROOT);

        List<QuestionnaireMatchResult> results = new ArrayList<>();
        for (QuestionnaireRegistry registry : cacheService.getRegistries()) {
            if (!"active".equalsIgnoreCase(registry.getStatus())) {
                continue;
            }
            int hitCount = countHits(lower, registry);
            if (hitCount == 0) {
                continue;
            }
            Optional<QuestionnaireTemplate> templateOpt =
                    cacheService.findById(registry.getQuestionnaireId());
            templateOpt.ifPresent(t -> results.add(new QuestionnaireMatchResult(t, hitCount)));
        }

        // 注册表已按优先级升序，稳定排序保证命中数相同时保持优先级顺序
        results.sort(Comparator.comparingInt(QuestionnaireMatchResult::getHitCount).reversed());

        if (!results.isEmpty()) {
            log.debug("问卷匹配: input={}, top={}, hits={}",
                    userInput, results.get(0).getTemplate().getId(), results.get(0).getHitCount());
        }
        return results;
    }

    private int countHits(String lowerInput, QuestionnaireRegistry registry) {
        if (registry.getKeywords() == null) {
            return 0;
        }
        int hits = 0;
        for (String keyword : registry.getKeywords()) {
            if (keyword != null && lowerInput.contains(keyword.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits;
    }
}
