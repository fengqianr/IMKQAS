package com.student.service.his.impl;

import com.student.service.his.QuestionnaireRepository;
import com.student.service.his.QuestionnaireTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 问卷模板库实现 — 内置标准量表
 *
 * @author 系统
 * @version 1.0
 */
@Component
public class QuestionnaireRepositoryImpl implements QuestionnaireRepository {

    private final Map<String, QuestionnaireTemplate> templates = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        initPhq9();
        initGad7();
        initIsi();
    }

    @Override
    public Optional<QuestionnaireTemplate> findById(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public List<QuestionnaireTemplate> findAll() {
        return new ArrayList<>(templates.values());
    }

    @Override
    public List<QuestionnaireTemplate> matchByKeywords(String userInput) {
        if (userInput == null || userInput.isBlank()) return List.of();
        String lower = userInput.toLowerCase();
        return templates.values().stream()
                .filter(t -> t.getTriggerKeywords() != null && t.getTriggerKeywords().stream()
                        .anyMatch(k -> lower.contains(k.toLowerCase())))
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionnaireTemplate> findByCategory(String category) {
        return templates.values().stream()
                .filter(t -> category.equals(t.getCategory()))
                .collect(Collectors.toList());
    }

    // ============ 内置量表 ============

    private void initPhq9() {
        QuestionnaireTemplate phq9 = QuestionnaireTemplate.builder()
                .id("PHQ-9")
                .title("PHQ-9 抑郁症筛查量表")
                .description("在过去2周内，以下情况困扰您的频率是？")
                .category("mental_health")
                .codeSystem("http://loinc.org")
                .triggerKeywords(List.of("情绪低落", "抑郁", "不开心", "没兴趣", "沮丧",
                        "提不起精神", "不想动", "心情差", "消沉", "快乐不起来"))
                .safetyKeywords(List.of("自杀", "自伤", "死掉", "伤害自己", "结束生命",
                        "不想活", "了断", "轻生", "寻死", "自残"))
                .items(List.of(
                        item(1, "/q1", "做事时提不起劲或没有兴趣",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(2, "/q2", "感到心情低落、沮丧或绝望",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(3, "/q3", "入睡困难、睡不安稳或睡眠过多",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(4, "/q4", "感到疲倦或没有活力",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(5, "/q5", "食欲不振或吃太多",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(6, "/q6", "觉得自己很糟，或觉得自己很失败",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(7, "/q7", "对事物专注有困难",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(8, "/q8", "动作或说话速度缓慢，或相反地烦躁坐立不安",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(9, "/q9", "有不如死掉或用某种方式伤害自己的念头",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3))))
                .comboRules(List.of(
                        comboRule("anhedonia_with_depression", "快感缺失伴情绪低落", "combo",
                                comboCond("/q1", "GTE", 2),
                                comboCond("/q2", "GTE", 2)),
                        comboRule("suicidal_ideation", "自杀意念阳性", "safety",
                                comboCond("/q9", "GTE", 1))))
                .scoringRule(QuestionnaireTemplate.ScoringRule.builder()
                        .minScore(0).maxScore(27)
                        .ranges(List.of(
                                range(0, 4, "无抑郁", "没有抑郁症状"),
                                range(5, 9, "轻度抑郁", "建议关注，可尝试自我调节"),
                                range(10, 14, "中度抑郁", "建议寻求心理咨询"),
                                range(15, 19, "中重度抑郁", "建议就医评估"),
                                range(20, 27, "重度抑郁", "强烈建议立即就医"))).build())
                .build();
        templates.put("PHQ-9", phq9);
    }

    private void initGad7() {
        QuestionnaireTemplate gad7 = QuestionnaireTemplate.builder()
                .id("GAD-7")
                .title("GAD-7 广泛性焦虑障碍量表")
                .description("在过去2周内，以下情况困扰您的频率是？")
                .category("mental_health")
                .codeSystem("http://loinc.org")
                .triggerKeywords(List.of("焦虑", "紧张", "担心", "不安", "心慌",
                        "坐立不安", "烦躁", "害怕", "恐慌", "惴惴不安"))
                .safetyKeywords(List.of("惊恐发作", "濒死感", "失控", "发疯",
                        "要死了", "心脏病发作"))
                .items(List.of(
                        item(1, "/q1", "感到紧张、焦虑或烦躁",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(2, "/q2", "无法停止或控制担忧",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(3, "/q3", "对各种各样的事情担忧过多",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(4, "/q4", "很难放松下来",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(5, "/q5", "非常焦躁以至无法静坐",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(6, "/q6", "变得容易烦恼或急躁",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3)),
                        item(7, "/q7", "感到害怕就像要发生可怕的事情",
                                option("LA6568-5", "完全没有", 0),
                                option("LA6569-3", "有几天", 1),
                                option("LA6570-1", "一半以上天数", 2),
                                option("LA6571-9", "几乎每天", 3))))
                .comboRules(List.of(
                        comboRule("generalized_anxiety_core", "广泛性焦虑核心症状", "combo",
                                comboCond("/q1", "GTE", 2),
                                comboCond("/q2", "GTE", 2)),
                        comboRule("panic_tendency", "惊恐倾向", "safety",
                                comboCond("/q7", "GTE", 2))))
                .scoringRule(QuestionnaireTemplate.ScoringRule.builder()
                        .minScore(0).maxScore(21)
                        .ranges(List.of(
                                range(0, 4, "无焦虑", "没有焦虑症状"),
                                range(5, 9, "轻度焦虑", "建议关注，可尝试放松训练"),
                                range(10, 14, "中度焦虑", "建议寻求心理咨询"),
                                range(15, 21, "重度焦虑", "强烈建议就医评估"))).build())
                .build();
        templates.put("GAD-7", gad7);
    }

    private void initIsi() {
        QuestionnaireTemplate isi = QuestionnaireTemplate.builder()
                .id("ISI")
                .title("ISI 失眠严重指数量表")
                .description("请评估过去2周内您的睡眠情况：")
                .category("sleep")
                .codeSystem("http://imkqas.org/fhir/CodeSystem/ISI")
                .triggerKeywords(List.of("失眠", "睡不着", "睡不好", "入睡困难",
                        "早醒", "多梦", "睡眠差", "熬夜", "半夜醒"))
                .safetyKeywords(List.of("彻夜不眠", "连续几天没睡", "安眠药过量",
                        "想不开", "崩溃"))
                .items(List.of(
                        item(1, "/q1", "入睡困难的程度",
                                option("ISI-0", "无", 0),
                                option("ISI-1", "轻度", 1),
                                option("ISI-2", "中度", 2),
                                option("ISI-3", "重度", 3),
                                option("ISI-4", "极重度", 4)),
                        item(2, "/q2", "维持睡眠困难的程度",
                                option("ISI-0", "无", 0),
                                option("ISI-1", "轻度", 1),
                                option("ISI-2", "中度", 2),
                                option("ISI-3", "重度", 3),
                                option("ISI-4", "极重度", 4)),
                        item(3, "/q3", "早醒的程度",
                                option("ISI-0", "无", 0),
                                option("ISI-1", "轻度", 1),
                                option("ISI-2", "中度", 2),
                                option("ISI-3", "重度", 3),
                                option("ISI-4", "极重度", 4)),
                        item(4, "/q4", "对当前睡眠模式的满意程度",
                                option("ISI-0", "非常满意", 0),
                                option("ISI-1", "满意", 1),
                                option("ISI-2", "一般", 2),
                                option("ISI-3", "不满意", 3),
                                option("ISI-4", "非常不满意", 4)),
                        item(5, "/q5", "睡眠问题对日间功能的影响程度",
                                option("ISI-0", "无影响", 0),
                                option("ISI-1", "轻度影响", 1),
                                option("ISI-2", "中度影响", 2),
                                option("ISI-3", "重度影响", 3),
                                option("ISI-4", "极重度影响", 4)),
                        item(6, "/q6", "他人是否注意到您的睡眠问题对生活质量的影响",
                                option("ISI-0", "没有", 0),
                                option("ISI-1", "轻微", 1),
                                option("ISI-2", "有些", 2),
                                option("ISI-3", "明显", 3),
                                option("ISI-4", "非常明显", 4)),
                        item(7, "/q7", "对当前睡眠问题的担忧/苦恼程度",
                                option("ISI-0", "没有", 0),
                                option("ISI-1", "轻微", 1),
                                option("ISI-2", "有些", 2),
                                option("ISI-3", "明显", 3),
                                option("ISI-4", "非常明显", 4))))
                .comboRules(List.of(
                        comboRule("insomnia_triad", "入睡-维持-早醒三联征", "combo",
                                comboCond("/q1", "GTE", 2),
                                comboCond("/q2", "GTE", 2),
                                comboCond("/q3", "GTE", 2))))
                .scoringRule(QuestionnaireTemplate.ScoringRule.builder()
                        .minScore(0).maxScore(28)
                        .ranges(List.of(
                                range(0, 7, "无失眠", "无临床显著失眠"),
                                range(8, 14, "轻度失眠", "亚临床失眠"),
                                range(15, 21, "中度失眠", "临床失眠（中度）"),
                                range(22, 28, "重度失眠", "临床失眠（重度）"))).build())
                .build();
        templates.put("ISI", isi);
    }

    // ============ 构建工具 ============

    private QuestionnaireTemplate.QuestionItem item(int index, String linkId,
                                                      String text,
                                                      QuestionnaireTemplate.AnswerOption... options) {
        return QuestionnaireTemplate.QuestionItem.builder()
                .index(index).linkId(linkId).text(text)
                .options(List.of(options)).build();
    }

    private QuestionnaireTemplate.AnswerOption option(String code, String display, int score) {
        return QuestionnaireTemplate.AnswerOption.builder()
                .code(code).display(display).score(score).build();
    }

    private QuestionnaireTemplate.ScoreRange range(int min, int max,
                                                    String severity, String interpretation) {
        return QuestionnaireTemplate.ScoreRange.builder()
                .minScore(min).maxScore(max)
                .severity(severity).interpretation(interpretation).build();
    }

    private QuestionnaireTemplate.ComboRule comboRule(String ruleId, String label,
                                                       String flagType,
                                                       QuestionnaireTemplate.ComboCondition... conditions) {
        return QuestionnaireTemplate.ComboRule.builder()
                .ruleId(ruleId).label(label).flagType(flagType)
                .conditions(List.of(conditions)).build();
    }

    private QuestionnaireTemplate.ComboCondition comboCond(String linkId,
                                                             String operator, int value) {
        return QuestionnaireTemplate.ComboCondition.builder()
                .linkId(linkId).operator(operator).value(value).build();
    }
}
