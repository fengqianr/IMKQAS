package com.student.service.his.impl;

import com.student.service.LlmService;
import com.student.service.his.IntentRouter;
import com.student.service.his.IntentType;
import com.student.service.his.QuestionnaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 意图路由器实现
 * LLM分类优先 + 关键词规则兜底
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRouterImpl implements IntentRouter {

    private final LlmService llmService;
    private final QuestionnaireRepository questionnaireRepository;

    // 知识查询关键词（医疗问答）
    private static final List<String> KNOWLEDGE_KEYWORDS = List.of(
            "什么是", "怎么治疗", "吃什么药", "病因", "症状有哪些",
            "如何诊断", "检查", "副作用", "禁忌", "注意事项",
            "多少", "剂量", "用法", "预后", "预防", "并发症"
    );

    // 数据采集关键词（自评/症状描述）
    private static final List<String> DATA_COLLECTION_KEYWORDS = List.of(
            "我感到", "我感觉", "我最近", "我经常", "我总是",
            "我睡不好", "我睡不着", "我很焦虑", "我很难过",
            "帮我测", "帮我评估", "填表", "问卷"
    );

    @Override
    public IntentType classify(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return IntentType.KNOWLEDGE_QUERY;
        }

        String lower = userInput.toLowerCase();

        // 1. 关键词规则（快速兜底）
        boolean hasKnowledgeKw = KNOWLEDGE_KEYWORDS.stream().anyMatch(lower::contains);
        boolean hasDataKw = DATA_COLLECTION_KEYWORDS.stream().anyMatch(lower::contains);
        boolean matchesQuestionnaire = !questionnaireRepository.matchByKeywords(userInput).isEmpty();

        // 明确的数据采集意图
        if (hasDataKw && matchesQuestionnaire) {
            log.debug("意图路由[DATA_COLLECTION]: 关键词匹配 + 问卷匹配 -> {}", userInput);
            return IntentType.DATA_COLLECTION;
        }

        // 明确的知识查询意图
        if (hasKnowledgeKw && !hasDataKw && !matchesQuestionnaire) {
            log.debug("意图路由[KNOWLEDGE_QUERY]: 知识关键词 -> {}", userInput);
            return IntentType.KNOWLEDGE_QUERY;
        }

        // 2. LLM分类（复杂场景）
        try {
            var llmIntent = classifyByLlm(userInput);
            log.debug("意图路由[LLM]: {} -> {}", userInput, llmIntent);
            return llmIntent;
        } catch (Exception e) {
            log.warn("LLM意图分类失败，使用关键词兜底: {}", e.getMessage());
            // LLM不可用时走关键词兜底
            if (matchesQuestionnaire) {
                return IntentType.MIXED;
            }
            return IntentType.KNOWLEDGE_QUERY;
        }
    }

    /**
     * 通过LLM进行意图分类
     */
    private IntentType classifyByLlm(String userInput) {
        String prompt = """
                你是一个医疗AI助手的意图分类器。请判断用户输入属于以下哪一类：
                - KNOWLEDGE_QUERY: 用户询问医学知识、疾病信息、用药方法等（知识查询）
                - DATA_COLLECTION: 用户描述自身症状或感受，希望获得评估或筛查（数据采集）
                - MIXED: 用户既有知识查询又描述了自身情况（混合型）

                请仅输出一个类别名称，不要有任何其他文字。

                用户输入：
                %s

                类别：""".formatted(userInput);

        String result = llmService.generateAnswer(prompt, List.of());
        if (result == null) return IntentType.KNOWLEDGE_QUERY;

        String trimmed = result.trim().toUpperCase();
        if (trimmed.contains("DATA_COLLECTION")) return IntentType.DATA_COLLECTION;
        if (trimmed.contains("MIXED")) return IntentType.MIXED;
        return IntentType.KNOWLEDGE_QUERY;
    }
}
