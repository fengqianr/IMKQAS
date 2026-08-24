package com.imkqas.dto.his;

import com.imkqas.service.his.QuestionnaireTemplate;
import org.hl7.fhir.r4.model.Questionnaire;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FHIR 问卷转换器往返测试
 * 校验三个内置量表 JSON 资源可解析、FHIR <-> Template 双向转换无损。
 *
 * @author 系统
 * @version 1.0
 */
public class QuestionnaireFhirConverterTest {

    private static final QuestionnaireFhirConverter converter = new QuestionnaireFhirConverter();
    private static final Map<String, QuestionnaireTemplate> templates = new HashMap<>();

    @BeforeAll
    static void loadAll() {
        for (String id : new String[]{"PHQ-9", "GAD-7", "ISI"}) {
            String json = readResource("questionnaires/" + id + ".json");
            Questionnaire q = converter.parseQuestionnaire(json);
            templates.put(id, converter.fromFhirQuestionnaire(q));
        }
    }

    @Test
    void testParseAllThreeResources() {
        assertEquals(3, templates.size());
    }

    @Test
    void testRoundTripLossless() {
        for (Map.Entry<String, QuestionnaireTemplate> entry : templates.entrySet()) {
            QuestionnaireTemplate original = entry.getValue();
            Questionnaire q = converter.toFhirQuestionnaire(original);
            QuestionnaireTemplate roundTrip = converter.fromFhirQuestionnaire(
                    converter.parseQuestionnaire(converter.toJson(q)));

            assertEquals(original.getId(), roundTrip.getId(), entry.getKey() + " id 不一致");
            assertEquals(original.getTitle(), roundTrip.getTitle(), entry.getKey() + " title 不一致");
            assertEquals(original.getCategory(), roundTrip.getCategory(), entry.getKey() + " category 不一致");
            assertEquals(original.getItems().size(), roundTrip.getItems().size(), entry.getKey() + " 题目数不一致");
            assertEquals(original.getSafetyKeywords().size(), roundTrip.getSafetyKeywords().size(),
                    entry.getKey() + " 安全关键词数不一致");
            assertEquals(original.getScoringRule().getRanges().size(),
                    roundTrip.getScoringRule().getRanges().size(), entry.getKey() + " 分数区间数不一致");
            assertEquals(original.getComboRules().size(), roundTrip.getComboRules().size(),
                    entry.getKey() + " 组合规则数不一致");
        }
    }

    @Test
    void testPhq9Structure() {
        QuestionnaireTemplate phq9 = templates.get("PHQ-9");
        assertNotNull(phq9);
        assertEquals(9, phq9.getItems().size());

        // 第9题选项分数（自杀意念题，最高分3）
        QuestionnaireTemplate.QuestionItem q9 = phq9.getItems().get(8);
        assertEquals("/q9", q9.getLinkId());
        assertEquals(4, q9.getOptions().size());
        assertEquals(3, q9.getOptions().get(3).getScore());

        // 组合规则含自杀意念阳性
        boolean hasSuicidal = phq9.getComboRules().stream()
                .anyMatch(r -> "suicidal_ideation".equals(r.getRuleId()));
        assertTrue(hasSuicidal, "PHQ-9 应包含 suicidal_ideation 组合规则");

        // 评分规则边界
        assertEquals(0, phq9.getScoringRule().getMinScore());
        assertEquals(27, phq9.getScoringRule().getMaxScore());
        assertEquals(5, phq9.getScoringRule().getRanges().size());
    }

    @Test
    void testIsiUsesOwnCodeSystem() {
        QuestionnaireTemplate isi = templates.get("ISI");
        assertNotNull(isi);
        assertEquals("http://imkqas.org/fhir/CodeSystem/ISI", isi.getCodeSystem());
        assertEquals(7, isi.getItems().size());
        // ISI 选项为 5 档（0-4分）
        assertEquals(5, isi.getItems().get(0).getOptions().size());
        assertEquals(4, isi.getItems().get(0).getOptions().get(4).getScore());
    }

    private static String readResource(String path) {
        try (InputStream in = QuestionnaireFhirConverterTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(in, "资源不存在: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("读取资源失败: " + path, e);
        }
    }
}
