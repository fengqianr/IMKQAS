package com.student.service.his;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 转换管道 —— 将采集完成的InterviewSession转换为完整的FHIR资源和规则计算结果
 * 三阶段确定性管道：完整性校验 → 规则计算 → FHIR组装
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Component
public class TransformationPipeline {

    /** 自定义Extension基础URL */
    private static final String EXT_BASE = "http://imkqas.org/fhir/StructureDefinition/";

    /**
     * 执行完整的三阶段转换管道
     */
    public PipelineResult execute(InterviewSession session, QuestionnaireTemplate template) {
        log.info("转换管道开始: sessionId={}, questionnaire={}, answers={}",
                session.getSessionId(), template.getId(), session.getAnswers().size());

        log.info("转换管道→阶段1-完整性校验: sessionId={}, 题目数={}, 已答={}",
                session.getSessionId(), template.getItems().size(), session.getAnswers().size());

        // 阶段1：完整性校验
        List<String> validationErrors = validate(session, template);
        if (!validationErrors.isEmpty()) {
            log.warn("完整性校验失败: sessionId={}, errors={}",
                    session.getSessionId(), validationErrors);
            return PipelineResult.builder()
                    .success(false)
                    .validationErrors(validationErrors)
                    .build();
        }

        // 阶段2：规则计算
        log.info("转换管道→阶段2-规则计算: sessionId={}", session.getSessionId());
        RuleResult ruleResult = calculate(session, template);
        log.info("规则计算完成: totalScore={}/{}, riskLevel={}, comboFlags={}",
                ruleResult.totalScore, ruleResult.maxScore, ruleResult.riskLevel,
                ruleResult.comboFlags.size());

        // 阶段3：FHIR组装
        log.info("转换管道→阶段3-FHIR组装: sessionId={}", session.getSessionId());
        QuestionnaireResponse qr = assembleFhir(session, template, ruleResult);
        log.info("FHIR资源组装完成: id={}, items={}", qr.getId(), qr.getItem().size());

        log.info("转换管道完成: sessionId={}, totalScore={}, riskLevel={}, comboFlags={}",
                session.getSessionId(), ruleResult.totalScore,
                ruleResult.riskLevel, ruleResult.comboFlags);

        return PipelineResult.builder()
                .success(true)
                .totalScore(ruleResult.totalScore)
                .maxScore(ruleResult.maxScore)
                .riskLevel(ruleResult.riskLevel)
                .interpretation(ruleResult.interpretation)
                .comboFlags(ruleResult.comboFlags)
                .safetyFlags(ruleResult.safetyFlags)
                .questionnaireResponse(qr)
                .build();
    }

    // ==================== 阶段1：完整性校验 ====================

    private List<String> validate(InterviewSession session, QuestionnaireTemplate template) {
        List<String> errors = new ArrayList<>();

        // 检查必填项
        List<String> requiredItems = template.getRequiredItems();
        if (requiredItems != null && !requiredItems.isEmpty()) {
            for (String requiredLinkId : requiredItems) {
                if (!session.getAnswers().containsKey(requiredLinkId)) {
                    errors.add("必填题目未回答: " + requiredLinkId);
                }
            }
        }

        // 检查所有题目是否已回答
        for (var item : template.getItems()) {
            String answerCode = session.getAnswers().get(item.getLinkId());
            if (answerCode == null) {
                errors.add("题目未回答: " + item.getLinkId() + " (" + item.getText() + ")");
                continue;
            }
            // 检查编码合法性
            boolean validCode = item.getOptions().stream()
                    .anyMatch(o -> o.getCode().equals(answerCode));
            if (!validCode) {
                errors.add("非法答案编码: " + item.getLinkId() + " code=" + answerCode);
            }
        }

        return errors;
    }

    // ==================== 阶段2：规则计算 ====================

    private RuleResult calculate(InterviewSession session, QuestionnaireTemplate template) {
        var scoringRule = template.getScoringRule();

        // 计算总分
        int totalScore = 0;
        for (var item : template.getItems()) {
            String answerCode = session.getAnswers().get(item.getLinkId());
            if (answerCode != null) {
                totalScore += item.getOptions().stream()
                        .filter(o -> o.getCode().equals(answerCode))
                        .mapToInt(QuestionnaireTemplate.AnswerOption::getScore)
                        .findFirst().orElse(0);
            }
        }

        // 查找风险等级
        String riskLevel = "未知";
        String interpretation = "";
        for (var range : scoringRule.getRanges()) {
            if (totalScore >= range.getMinScore() && totalScore <= range.getMaxScore()) {
                riskLevel = range.getSeverity();
                interpretation = range.getInterpretation();
                break;
            }
        }

        // 评估组合规则
        List<ComboFlag> comboFlags = new ArrayList<>();
        List<String> safetyFlags = new ArrayList<>();

        if (template.getComboRules() != null) {
            for (var rule : template.getComboRules()) {
                boolean allMatch = true;
                for (var cond : rule.getConditions()) {
                    String code = session.getAnswers().get(cond.getLinkId());
                    int value = getScoreForLinkId(template, cond.getLinkId(), code);
                    if (!evaluateCondition(value, cond.getOperator(), cond.getValue())) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    ComboFlag flag = ComboFlag.builder()
                            .ruleId(rule.getRuleId())
                            .label(rule.getLabel())
                            .type(rule.getFlagType())
                            .build();
                    comboFlags.add(flag);
                    if ("safety".equals(rule.getFlagType())) {
                        safetyFlags.add(rule.getLabel());
                    }
                }
            }
        }

        // 安全关键词检测
        if (template.getSafetyKeywords() != null && session.getRawInputs() != null) {
            for (var entry : session.getRawInputs().entrySet()) {
                String rawInput = entry.getValue();
                if (rawInput == null) continue;
                String lower = rawInput.toLowerCase();
                for (String keyword : template.getSafetyKeywords()) {
                    if (lower.contains(keyword.toLowerCase())) {
                        String flag = "安全关键词触发: " + keyword;
                        if (!safetyFlags.contains(flag)) {
                            safetyFlags.add(flag);
                        }
                    }
                }
            }
        }

        return new RuleResult(totalScore, scoringRule.getMaxScore(),
                riskLevel, interpretation, comboFlags, safetyFlags);
    }

    // ==================== 阶段3：FHIR组装 ====================

    private QuestionnaireResponse assembleFhir(InterviewSession session,
                                                QuestionnaireTemplate template,
                                                RuleResult ruleResult) {
        QuestionnaireResponse qr = new QuestionnaireResponse();

        // 基础标识
        qr.setId("qr-" + session.getSessionId());
        qr.setQuestionnaire(template.getId());
        qr.setStatus(QuestionnaireResponseStatus.COMPLETED);
        qr.setAuthored(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));

        // Meta标签
        qr.getMeta().addProfile("http://hl7.org/fhir/StructureDefinition/QuestionnaireResponse");
        qr.getMeta().addTag(new Coding()
                .setSystem("http://imkqas.org/fhir/tags")
                .setCode(session.getCollectionMode() != null
                        ? session.getCollectionMode() : "manual_form"));

        // 主体引用
        if (session.getUserId() != null) {
            qr.setSubject(new Reference("Patient/pat-" + session.getUserId()));
        }

        // 作者
        qr.setAuthor(new Reference("Device/imkqas-collection-agent"));

        // 根Extension：总分、风险等级、采集模式
        qr.addExtension(createExtension("qr-total-score", new IntegerType(ruleResult.totalScore)));
        qr.addExtension(createExtension("qr-risk-level", new StringType(ruleResult.riskLevel)));
        qr.addExtension(createExtension("qr-max-score", new IntegerType(ruleResult.maxScore)));
        qr.addExtension(createExtension("qr-collection-mode",
                new StringType(session.getCollectionMode() != null
                        ? session.getCollectionMode() : "manual_form")));

        if (session.getConversationId() != null) {
            qr.addExtension(createExtension("qr-conversation-id",
                    new StringType(String.valueOf(session.getConversationId()))));
        }

        // 组合标记Extension
        for (ComboFlag flag : ruleResult.comboFlags) {
            qr.addExtension(createExtension("combo-flag", new StringType(flag.getLabel())));
        }

        // 组装每个题目
        String codeSystem = template.getCodeSystem() != null
                ? template.getCodeSystem() : "http://imkqas.org/fhir/CodeSystem/" + template.getId();

        for (var item : template.getItems()) {
            QuestionnaireResponseItemComponent qrItem = qr.addItem();
            qrItem.setLinkId(item.getLinkId());
            qrItem.setText(item.getText());

            String answerCode = session.getAnswers().get(item.getLinkId());
            if (answerCode == null) continue;

            // 查找答案选项
            var opt = item.getOptions().stream()
                    .filter(o -> o.getCode().equals(answerCode))
                    .findAny();

            // Item级别Extension：原始表述、答案来源、置信度
            String rawInput = session.getRawInputs() != null
                    ? session.getRawInputs().get(item.getLinkId()) : null;
            if (rawInput != null && !rawInput.isEmpty()) {
                qrItem.addExtension(createExtension("raw-input", new StringType(rawInput)));
            }

            String source = "llm_driven".equals(session.getCollectionMode()) ? "llm" : "direct_input";
            qrItem.addExtension(createExtension("answer-source", new StringType(source)));

            // 答案组件
            QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent answer =
                    qrItem.addAnswer();
            answer.setValue(new Coding()
                    .setSystem(codeSystem)
                    .setCode(answerCode)
                    .setDisplay(opt.map(QuestionnaireTemplate.AnswerOption::getDisplay).orElse("")));

            // 答案级别Extension：分数
            answer.addExtension(createExtension("score",
                    new IntegerType(opt.map(QuestionnaireTemplate.AnswerOption::getScore).orElse(0))));
        }

        return qr;
    }

    // ==================== 工具方法 ====================

    private int getScoreForLinkId(QuestionnaireTemplate template, String linkId, String code) {
        if (code == null) return 0;
        for (var item : template.getItems()) {
            if (item.getLinkId().equals(linkId)) {
                return item.getOptions().stream()
                        .filter(o -> o.getCode().equals(code))
                        .mapToInt(QuestionnaireTemplate.AnswerOption::getScore)
                        .findFirst().orElse(0);
            }
        }
        return 0;
    }

    private boolean evaluateCondition(int actualValue, String operator, int threshold) {
        return switch (operator) {
            case "GTE" -> actualValue >= threshold;
            case "GT"  -> actualValue > threshold;
            case "LTE" -> actualValue <= threshold;
            case "LT"  -> actualValue < threshold;
            case "EQ"  -> actualValue == threshold;
            default    -> false;
        };
    }

    private Extension createExtension(String name, org.hl7.fhir.r4.model.Type value) {
        return new Extension(EXT_BASE + name, value);
    }

    // ==================== 内部数据类型 ====================

    /**
     * 阶段2计算结果
     */
    public static class RuleResult {
        public final int totalScore;
        public final int maxScore;
        public final String riskLevel;
        public final String interpretation;
        public final List<ComboFlag> comboFlags;
        public final List<String> safetyFlags;

        public RuleResult(int totalScore, int maxScore, String riskLevel,
                          String interpretation, List<ComboFlag> comboFlags,
                          List<String> safetyFlags) {
            this.totalScore = totalScore;
            this.maxScore = maxScore;
            this.riskLevel = riskLevel;
            this.interpretation = interpretation;
            this.comboFlags = comboFlags;
            this.safetyFlags = safetyFlags;
        }
    }

    /**
     * 组合标记
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComboFlag {
        private String ruleId;
        private String label;
        private String type;
    }

    /**
     * 管道执行结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PipelineResult {
        private boolean success;
        private List<String> validationErrors;
        private int totalScore;
        private int maxScore;
        private String riskLevel;
        private String interpretation;
        private List<ComboFlag> comboFlags;
        private List<String> safetyFlags;
        private QuestionnaireResponse questionnaireResponse;

        /** 有历史记录时的趋势描述（由外部设置） */
        private String trendDescription;

        public boolean hasSafetyFlags() {
            return safetyFlags != null && !safetyFlags.isEmpty();
        }
    }
}
