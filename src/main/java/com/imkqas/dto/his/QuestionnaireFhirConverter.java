package com.imkqas.dto.his;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.imkqas.service.his.QuestionnaireTemplate;
import com.imkqas.service.his.QuestionnaireTemplate.AnswerOption;
import com.imkqas.service.his.QuestionnaireTemplate.ComboCondition;
import com.imkqas.service.his.QuestionnaireTemplate.ComboRule;
import com.imkqas.service.his.QuestionnaireTemplate.QuestionItem;
import com.imkqas.service.his.QuestionnaireTemplate.ScoreRange;
import com.imkqas.service.his.QuestionnaireTemplate.ScoringRule;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemAnswerOptionComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 问卷模板 <-> FHIR Questionnaire 双向转换器
 * 用 FHIR Extension 承载标准资源无法表达的业务字段：
 * 选项分数、安全关键词、计分规则、组合规则。
 * 触发关键词归属本地注册表，不在此转换范围内。
 *
 * @author 系统
 * @version 1.0
 */
@Component
public class QuestionnaireFhirConverter {

    /** 自定义Extension基础URL */
    public static final String EXT_BASE = "http://imkqas.org/fhir/StructureDefinition/";

    /** 选项分数 */
    public static final String EXT_OPTION_SCORE = EXT_BASE + "option-score";

    /** 安全关键词 */
    public static final String EXT_SAFETY_KEYWORDS = EXT_BASE + "safety-keywords";

    /** 计分规则（复合） */
    public static final String EXT_SCORING_RULE = EXT_BASE + "scoring-rule";
    public static final String EXT_SCORING_RULE_MIN = EXT_BASE + "scoring-rule/min-score";
    public static final String EXT_SCORING_RULE_MAX = EXT_BASE + "scoring-rule/max-score";
    public static final String EXT_SCORE_RANGE = EXT_BASE + "scoring-rule/score-range";
    public static final String EXT_SCORE_RANGE_MIN = EXT_BASE + "scoring-rule/score-range/min-score";
    public static final String EXT_SCORE_RANGE_MAX = EXT_BASE + "scoring-rule/score-range/max-score";
    public static final String EXT_SCORE_RANGE_SEVERITY = EXT_BASE + "scoring-rule/score-range/severity";
    public static final String EXT_SCORE_RANGE_INTERPRETATION = EXT_BASE + "scoring-rule/score-range/interpretation";

    /** 组合规则（复合） */
    public static final String EXT_COMBO_RULES = EXT_BASE + "combo-rules";
    public static final String EXT_COMBO_RULE_ID = EXT_BASE + "combo-rules/rule-id";
    public static final String EXT_COMBO_LABEL = EXT_BASE + "combo-rules/label";
    public static final String EXT_COMBO_FLAG_TYPE = EXT_BASE + "combo-rules/flag-type";
    public static final String EXT_COMBO_CONDITION = EXT_BASE + "combo-rules/condition";
    public static final String EXT_COMBO_CONDITION_LINK_ID = EXT_BASE + "combo-rules/condition/link-id";
    public static final String EXT_COMBO_CONDITION_OPERATOR = EXT_BASE + "combo-rules/condition/operator";
    public static final String EXT_COMBO_CONDITION_VALUE = EXT_BASE + "combo-rules/condition/value";

    /** 问卷分类编码系统 */
    public static final String CATEGORY_CODE_SYSTEM = "http://imkqas.org/fhir/CodeSystem/questionnaire-category";

    private final FhirContext fhirContext;
    private final IParser jsonParser;

    public QuestionnaireFhirConverter() {
        this.fhirContext = FhirContext.forR4();
        this.jsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
    }

    /**
     * 问卷模板 -> FHIR Questionnaire
     */
    public Questionnaire toFhirQuestionnaire(QuestionnaireTemplate template) {
        Questionnaire q = new Questionnaire();
        q.setName(template.getId());
        q.setTitle(template.getTitle());
        q.setDescription(template.getDescription());
        q.setStatus(Enumerations.PublicationStatus.ACTIVE);
        q.setPublisher("IMKQAS");

        // 分类
        if (template.getCategory() != null) {
            q.addCode(new Coding()
                    .setSystem(CATEGORY_CODE_SYSTEM)
                    .setCode(template.getCategory()));
        }

        // 安全关键词 Extension
        if (template.getSafetyKeywords() != null) {
            for (String keyword : template.getSafetyKeywords()) {
                q.addExtension(createExtension(EXT_SAFETY_KEYWORDS, new StringType(keyword)));
            }
        }

        // 计分规则 Extension
        if (template.getScoringRule() != null) {
            q.addExtension(buildScoringRuleExtension(template.getScoringRule()));
        }

        // 组合规则 Extension
        if (template.getComboRules() != null) {
            for (ComboRule rule : template.getComboRules()) {
                q.addExtension(buildComboRuleExtension(rule));
            }
        }

        // 题目
        for (QuestionItem item : template.getItems()) {
            QuestionnaireItemComponent qi = q.addItem();
            qi.setLinkId(item.getLinkId());
            qi.setText(item.getText());
            qi.setType(Questionnaire.QuestionnaireItemType.CHOICE);

            // 必填项
            if (template.getRequiredItems() != null
                    && template.getRequiredItems().contains(item.getLinkId())) {
                qi.setRequired(true);
            }

            // 答案选项
            for (AnswerOption opt : item.getOptions()) {
                QuestionnaireItemAnswerOptionComponent ao = qi.addAnswerOption();
                ao.setValue(new Coding()
                        .setSystem(template.getCodeSystem())
                        .setCode(opt.getCode())
                        .setDisplay(opt.getDisplay()));
                ao.addExtension(createExtension(EXT_OPTION_SCORE,
                        new IntegerType(opt.getScore())));
            }
        }

        return q;
    }

    /**
     * FHIR Questionnaire -> 问卷模板
     */
    public QuestionnaireTemplate fromFhirQuestionnaire(Questionnaire q) {
        List<QuestionItem> items = new ArrayList<>();
        List<String> requiredItems = new ArrayList<>();

        int index = 1;
        for (QuestionnaireItemComponent qi : q.getItem()) {
            QuestionItem item = new QuestionItem();
            item.setIndex(index++);
            item.setLinkId(qi.getLinkId());
            item.setText(qi.getText());

            List<AnswerOption> options = new ArrayList<>();
            if (qi.hasAnswerOption()) {
                for (QuestionnaireItemAnswerOptionComponent ao : qi.getAnswerOption()) {
                    AnswerOption opt = new AnswerOption();
                    if (ao.hasValue() && ao.getValue() instanceof Coding coding) {
                        opt.setCode(coding.getCode());
                        opt.setDisplay(coding.getDisplay());
                    }
                    opt.setScore(extractOptionScore(ao));
                    options.add(opt);
                }
            }
            item.setOptions(options);
            items.add(item);

            if (qi.getRequired()) {
                requiredItems.add(qi.getLinkId());
            }
        }

        return QuestionnaireTemplate.builder()
                .id(q.getName())
                .title(q.getTitle())
                .description(q.getDescription())
                .category(extractCategory(q))
                .codeSystem(extractCodeSystem(q))
                .items(items)
                .scoringRule(extractScoringRule(q))
                .requiredItems(requiredItems.isEmpty() ? null : requiredItems)
                .safetyKeywords(extractStringExtensions(q, EXT_SAFETY_KEYWORDS))
                .comboRules(extractComboRules(q))
                .build();
    }

    /**
     * 序列化为JSON字符串
     */
    public String toJson(Questionnaire questionnaire) {
        return jsonParser.encodeResourceToString(questionnaire);
    }

    /**
     * 从JSON字符串解析Questionnaire
     */
    public Questionnaire parseQuestionnaire(String json) {
        return jsonParser.parseResource(Questionnaire.class, json);
    }

    // ==================== Extension 构建 ====================

    private Extension buildScoringRuleExtension(ScoringRule rule) {
        Extension ext = new Extension(EXT_SCORING_RULE);
        ext.addExtension(EXT_SCORING_RULE_MIN, new IntegerType(rule.getMinScore()));
        ext.addExtension(EXT_SCORING_RULE_MAX, new IntegerType(rule.getMaxScore()));
        for (ScoreRange range : rule.getRanges()) {
            Extension rangeExt = new Extension(EXT_SCORE_RANGE);
            rangeExt.addExtension(EXT_SCORE_RANGE_MIN, new IntegerType(range.getMinScore()));
            rangeExt.addExtension(EXT_SCORE_RANGE_MAX, new IntegerType(range.getMaxScore()));
            rangeExt.addExtension(EXT_SCORE_RANGE_SEVERITY, new StringType(range.getSeverity()));
            rangeExt.addExtension(EXT_SCORE_RANGE_INTERPRETATION,
                    new StringType(range.getInterpretation()));
            ext.addExtension(rangeExt);
        }
        return ext;
    }

    private Extension buildComboRuleExtension(ComboRule rule) {
        Extension ext = new Extension(EXT_COMBO_RULES);
        ext.addExtension(EXT_COMBO_RULE_ID, new StringType(rule.getRuleId()));
        ext.addExtension(EXT_COMBO_LABEL, new StringType(rule.getLabel()));
        ext.addExtension(EXT_COMBO_FLAG_TYPE, new StringType(rule.getFlagType()));
        for (ComboCondition cond : rule.getConditions()) {
            Extension condExt = new Extension(EXT_COMBO_CONDITION);
            condExt.addExtension(EXT_COMBO_CONDITION_LINK_ID, new StringType(cond.getLinkId()));
            condExt.addExtension(EXT_COMBO_CONDITION_OPERATOR, new StringType(cond.getOperator()));
            condExt.addExtension(EXT_COMBO_CONDITION_VALUE, new IntegerType(cond.getValue()));
            ext.addExtension(condExt);
        }
        return ext;
    }

    private Extension createExtension(String url, Type value) {
        return new Extension(url, value);
    }

    // ==================== Extension 解析 ====================

    private int extractOptionScore(QuestionnaireItemAnswerOptionComponent ao) {
        List<Extension> exts = ao.getExtensionsByUrl(EXT_OPTION_SCORE);
        if (exts.isEmpty() || !exts.get(0).hasValue()) {
            return 0;
        }
        Type value = exts.get(0).getValue();
        return value instanceof IntegerType integerType ? integerType.getValue() : 0;
    }

    private List<String> extractStringExtensions(Questionnaire q, String url) {
        List<String> result = new ArrayList<>();
        for (Extension ext : q.getExtensionsByUrl(url)) {
            if (ext.hasValue() && ext.getValue() instanceof StringType stringType) {
                result.add(stringType.getValue());
            }
        }
        return result;
    }

    private ScoringRule extractScoringRule(Questionnaire q) {
        List<Extension> exts = q.getExtensionsByUrl(EXT_SCORING_RULE);
        if (exts.isEmpty()) {
            return null;
        }
        Extension ext = exts.get(0);

        ScoringRule rule = new ScoringRule();
        rule.setMinScore(getIntegerSub(ext, EXT_SCORING_RULE_MIN, 0));
        rule.setMaxScore(getIntegerSub(ext, EXT_SCORING_RULE_MAX, 0));

        List<ScoreRange> ranges = new ArrayList<>();
        for (Extension rangeExt : ext.getExtensionsByUrl(EXT_SCORE_RANGE)) {
            ScoreRange range = new ScoreRange();
            range.setMinScore(getIntegerSub(rangeExt, EXT_SCORE_RANGE_MIN, 0));
            range.setMaxScore(getIntegerSub(rangeExt, EXT_SCORE_RANGE_MAX, 0));
            range.setSeverity(getStringSub(rangeExt, EXT_SCORE_RANGE_SEVERITY));
            range.setInterpretation(getStringSub(rangeExt, EXT_SCORE_RANGE_INTERPRETATION));
            ranges.add(range);
        }
        rule.setRanges(ranges);
        return rule;
    }

    private List<ComboRule> extractComboRules(Questionnaire q) {
        List<ComboRule> rules = new ArrayList<>();
        for (Extension ext : q.getExtensionsByUrl(EXT_COMBO_RULES)) {
            ComboRule rule = new ComboRule();
            rule.setRuleId(getStringSub(ext, EXT_COMBO_RULE_ID));
            rule.setLabel(getStringSub(ext, EXT_COMBO_LABEL));
            rule.setFlagType(getStringSub(ext, EXT_COMBO_FLAG_TYPE));

            List<ComboCondition> conditions = new ArrayList<>();
            for (Extension condExt : ext.getExtensionsByUrl(EXT_COMBO_CONDITION)) {
                ComboCondition cond = new ComboCondition();
                cond.setLinkId(getStringSub(condExt, EXT_COMBO_CONDITION_LINK_ID));
                cond.setOperator(getStringSub(condExt, EXT_COMBO_CONDITION_OPERATOR));
                cond.setValue(getIntegerSub(condExt, EXT_COMBO_CONDITION_VALUE, 0));
                conditions.add(cond);
            }
            rule.setConditions(conditions);
            rules.add(rule);
        }
        return rules;
    }

    private String extractCategory(Questionnaire q) {
        if (q.hasCode()) {
            return q.getCodeFirstRep().getCode();
        }
        return null;
    }

    private String extractCodeSystem(Questionnaire q) {
        for (QuestionnaireItemComponent qi : q.getItem()) {
            if (qi.hasAnswerOption()) {
                for (QuestionnaireItemAnswerOptionComponent ao : qi.getAnswerOption()) {
                    if (ao.hasValue() && ao.getValue() instanceof Coding coding
                            && coding.hasSystem()) {
                        return coding.getSystem();
                    }
                }
            }
        }
        return null;
    }

    private int getIntegerSub(Extension parent, String url, int defaultValue) {
        List<Extension> subs = parent.getExtensionsByUrl(url);
        if (subs.isEmpty() || !subs.get(0).hasValue()) {
            return defaultValue;
        }
        Type value = subs.get(0).getValue();
        return value instanceof IntegerType integerType ? integerType.getValue() : defaultValue;
    }

    private String getStringSub(Extension parent, String url) {
        List<Extension> subs = parent.getExtensionsByUrl(url);
        if (subs.isEmpty() || !subs.get(0).hasValue()) {
            return null;
        }
        Type value = subs.get(0).getValue();
        return value instanceof StringType stringType ? stringType.getValue() : null;
    }
}
