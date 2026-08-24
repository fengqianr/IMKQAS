package com.imkqas.service.agent;

import com.imkqas.service.his.IntentRouter;
import com.imkqas.service.his.IntentType;
import com.imkqas.service.rag.MedicalEntityRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 问题复杂度分流器
 * 规则级判断（不主动调用 LLM），满足任一条件判定为 COMPLEX 走 Agent，
 * 否则 SIMPLE 走原 RAG 快速链路。设计原则：宁可误判为复杂，也不漏判。
 *
 * @author 系统
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryComplexityClassifier {

    private final IntentRouter intentRouter;
    private final MedicalEntityRecognitionService entityRecognitionService;

    /** 对比/并列关键词 */
    private static final Set<String> COMPARE_WORDS = Set.of("和", "vs", "对比", "哪个", "区别", "还是", "比较");

    /** 高风险人群关键词 */
    private static final Set<String> HIGH_RISK_POPULATIONS =
            Set.of("孕妇", "儿童", "老人", "哺乳期", "婴幼儿", "新生儿", "备孕");

    /**
     * 判断问题复杂度
     *
     * @param query 用户查询文本
     * @return COMPLEX 或 SIMPLE
     */
    public Route classify(String query) {
        if (query == null || query.isBlank()) {
            return Route.SIMPLE;
        }

        // 规则1：意图为 MIXED（既有知识问询又有个人描述）或 DATA_COLLECTION（个人症状描述，需问卷工具）
        IntentType intent = safeIntent(query);
        if (intent == IntentType.MIXED || intent == IntentType.DATA_COLLECTION) {
            return Route.COMPLEX;
        }

        // 规则2：含对比词
        for (String word : COMPARE_WORDS) {
            if (query.contains(word)) {
                return Route.COMPLEX;
            }
        }

        // 规则3：高风险人群 + 药物名同时出现
        boolean hasHighRiskPopulation = HIGH_RISK_POPULATIONS.stream().anyMatch(query::contains);

        // 规则4：医学实体数量 >= 2
        List<MedicalEntityRecognitionService.MedicalEntity> entities = safeRecognize(query);
        long drugCount = entities.stream()
                .filter(e -> e.getType() == MedicalEntityRecognitionService.EntityType.DRUG)
                .count();

        if (hasHighRiskPopulation && drugCount > 0) {
            return Route.COMPLEX;
        }
        if (entities.size() >= 2) {
            return Route.COMPLEX;
        }
        return Route.SIMPLE;
    }

    /** 安全执行意图分类（失败降级为 KNOWLEDGE_QUERY） */
    private IntentType safeIntent(String query) {
        try {
            return intentRouter.classify(query);
        } catch (Exception e) {
            log.warn("意图分类失败，降级为 KNOWLEDGE_QUERY: {}", e.getMessage());
            return IntentType.KNOWLEDGE_QUERY;
        }
    }

    /** 安全执行实体识别（失败返回空列表） */
    private List<MedicalEntityRecognitionService.MedicalEntity> safeRecognize(String query) {
        try {
            return entityRecognitionService.recognize(query);
        } catch (Exception e) {
            log.warn("实体识别失败，返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    /** 复杂度枚举 */
    public enum Route {
        SIMPLE, COMPLEX
    }
}
