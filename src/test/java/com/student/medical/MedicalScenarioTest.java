package com.student.medical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 医疗场景准确性测试
 * 测试MedicalAccuracyValidator对普通医疗场景的验证功能
 *
 * @author 系统
 * @version 1.0
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.main.lazy-initialization=true",
    "spring.jmx.enabled=false",
    "logging.level.com.student.medical=INFO"
})
public class MedicalScenarioTest {

    @Autowired
    private MedicalAccuracyValidator validator;

    private List<MedicalAccuracyValidator.TestScenario> testScenarios;

    @BeforeEach
    void setUp() {
        // 获取所有测试场景
        testScenarios = validator.getAllTestScenarios();
        assertFalse(testScenarios.isEmpty(), "测试场景应至少包含一个场景");
        System.out.printf("加载了 %d 个测试场景%n", testScenarios.size());
    }

    /**
     * 测试高血压咨询场景
     */
    @Test
    @DisplayName("高血压症状咨询准确性测试")
    void testHypertensionScenario() {
        MedicalAccuracyValidator.TestScenario scenario = findScenarioById("SCENARIO_001");
        assertNotNull(scenario, "应找到高血压场景");

        // 模拟一个正确的回答
        String userQuery = "高血压有什么症状？";
        String correctResponse = "高血压的常见症状包括头痛、眩晕、心悸、耳鸣等。如果出现这些症状，建议及时就医测量血压。";

        MedicalAccuracyValidator.ValidationResult result = validator.validateResponse(userQuery, correctResponse, "SCENARIO_001");

        System.out.println("高血压场景验证结果: " + result);

        // 验证关键词匹配
        assertTrue(result.getMatchedKeywords().size() >= 3, "应至少匹配3个关键词");
        assertTrue(result.getMatchedKeywords().contains("高血压") || result.getMatchedKeywords().contains("症状"),
                "应匹配高血压或症状关键词");

        // 验证匹配率
        assertTrue(result.getKeywordMatchRate() >= 0.5, "关键词匹配率应≥50%");

        // 验证安全建议
        assertTrue(result.isSafetyCheckPassed(), "安全检查应通过");

        // 验证总体结果
        assertTrue(result.isPassed(), "验证应通过");
    }

    /**
     * 测试糖尿病治疗场景
     */
    @Test
    @DisplayName("糖尿病治疗方案准确性测试")
    void testDiabetesScenario() {
        MedicalAccuracyValidator.TestScenario scenario = findScenarioById("SCENARIO_002");
        assertNotNull(scenario, "应找到糖尿病场景");

        String userQuery = "糖尿病怎么治疗？";
        String correctResponse = "糖尿病的治疗包括胰岛素注射、口服降糖药、饮食控制和适量运动。重要的是要遵医嘱用药，定期监测血糖。";

        MedicalAccuracyValidator.ValidationResult result = validator.validateResponse(userQuery, correctResponse, "SCENARIO_002");

        System.out.println("糖尿病场景验证结果: " + result);

        // 验证治疗相关关键词
        assertTrue(result.getMatchedKeywords().size() >= 3, "应至少匹配3个治疗相关关键词");
        assertTrue(result.getMatchedKeywords().stream().anyMatch(k -> k.contains("胰岛素") || k.contains("降糖药")),
                "应匹配胰岛素或降糖药关键词");

        // 验证匹配率
        assertTrue(result.getKeywordMatchRate() >= 0.5, "关键词匹配率应≥50%");

        // 验证安全建议（应包含"遵医嘱"）
        assertTrue(result.isSafetyCheckPassed(), "安全检查应通过");

        // 验证总体结果
        assertTrue(result.isPassed(), "验证应通过");
    }

    /**
     * 测试急诊症状识别场景
     */
    @Test
    @DisplayName("急诊症状识别准确性测试")
    void testEmergencyScenario() {
        MedicalAccuracyValidator.TestScenario scenario = findScenarioById("SCENARIO_003");
        assertNotNull(scenario, "应找到急诊场景");

        String userQuery = "胸痛呼吸困难怎么办？";
        String correctResponse = "胸痛伴呼吸困难属于急症，应立即就医急诊科或心内科，不要延误。";

        MedicalAccuracyValidator.ValidationResult result = validator.validateResponse(userQuery, correctResponse, "SCENARIO_003");

        System.out.println("急诊场景验证结果: " + result);

        // 验证急诊相关关键词
        assertTrue(result.getMatchedKeywords().contains("急诊") || result.getMatchedKeywords().contains("立即就医"),
                "应匹配急诊或立即就医关键词");

        // 验证匹配率
        assertTrue(result.getKeywordMatchRate() >= 0.5, "关键词匹配率应≥50%");

        // 验证安全建议（必须建议立即就医）
        assertTrue(result.isSafetyCheckPassed(), "安全检查应通过");
        assertFalse(result.getErrors().stream().anyMatch(e -> e.contains("安全要求")), "不应有安全相关错误");

        // 验证总体结果
        assertTrue(result.isPassed(), "验证应通过");
    }

    /**
     * 测试药物查询场景
     */
    @Test
    @DisplayName("药物信息查询准确性测试")
    void testDrugQueryScenario() {
        MedicalAccuracyValidator.TestScenario scenario = findScenarioById("SCENARIO_004");
        assertNotNull(scenario, "应找到药物查询场景");

        String userQuery = "阿司匹林是治什么的？";
        String correctResponse = "阿司匹林是一种非甾体抗炎药，主要用于解热、镇痛、抗炎和抗血小板聚集。使用时需注意副作用和禁忌症。";

        MedicalAccuracyValidator.ValidationResult result = validator.validateResponse(userQuery, correctResponse, "SCENARIO_004");

        System.out.println("药物查询场景验证结果: " + result);

        // 验证药物相关关键词
        assertTrue(result.getMatchedKeywords().contains("阿司匹林"), "应匹配阿司匹林关键词");
        assertTrue(result.getMatchedKeywords().stream().anyMatch(k -> k.contains("解热") || k.contains("镇痛")),
                "应匹配解热或镇痛关键词");

        // 验证匹配率
        assertTrue(result.getKeywordMatchRate() >= 0.5, "关键词匹配率应≥50%");

        // 验证安全建议（应提示副作用和禁忌）
        assertTrue(result.isSafetyCheckPassed(), "安全检查应通过");

        // 验证总体结果
        assertTrue(result.isPassed(), "验证应通过");
    }

    /**
     * 测试科室导诊场景
     */
    @Test
    @DisplayName("科室导诊准确性测试")
    void testDepartmentTriageScenario() {
        MedicalAccuracyValidator.TestScenario scenario = findScenarioById("SCENARIO_005");
        assertNotNull(scenario, "应找到科室导诊场景");

        String userQuery = "发烧咳嗽应该看什么科？";
        String correctResponse = "发烧咳嗽建议看呼吸内科或发热门诊，可能是呼吸道感染。";

        MedicalAccuracyValidator.ValidationResult result = validator.validateResponse(userQuery, correctResponse, "SCENARIO_005");

        System.out.println("科室导诊场景验证结果: " + result);

        // 验证科室相关关键词
        assertTrue(result.getMatchedKeywords().contains("呼吸内科") || result.getMatchedKeywords().contains("发热门诊"),
                "应匹配呼吸内科或发热门诊关键词");

        // 验证匹配率
        assertTrue(result.getKeywordMatchRate() >= 0.5, "关键词匹配率应≥50%");

        // 验证安全建议
        assertTrue(result.isSafetyCheckPassed(), "安全检查应通过");

        // 验证总体结果
        assertTrue(result.isPassed(), "验证应通过");
    }

    /**
     * 测试医学术语识别场景
     */
    @Test
    @DisplayName("医学术语识别准确性测试")
    void testMedicalTerminologyScenario() {
        MedicalAccuracyValidator.TestScenario scenario = findScenarioById("SCENARIO_007");
        assertNotNull(scenario, "应找到医学术语识别场景");

        String userQuery = "什么是冠状动脉粥样硬化？";
        String correctResponse = "冠状动脉粥样硬化是冠状动脉内壁积聚斑块导致血管狭窄的病理过程，是冠心病的主要原因。";

        MedicalAccuracyValidator.ValidationResult result = validator.validateResponse(userQuery, correctResponse, "SCENARIO_007");

        System.out.println("医学术语场景验证结果: " + result);

        // 验证医学术语关键词
        assertTrue(result.getMatchedKeywords().contains("冠状动脉") || result.getMatchedKeywords().contains("粥样硬化"),
                "应匹配冠状动脉或粥样硬化关键词");

        // 验证匹配率
        assertTrue(result.getKeywordMatchRate() >= 0.5, "关键词匹配率应≥50%");

        // 验证医学术语识别警告
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("识别到医学术语")),
                "应识别到医学术语");

        // 验证总体结果
        assertTrue(result.isPassed(), "验证应通过");
    }

    /**
     * 测试不完整的回答
     */
    @Test
    @DisplayName("不完整回答验证测试")
    void testIncompleteResponse() {
        MedicalAccuracyValidator.TestScenario scenario = findScenarioById("SCENARIO_001");
        assertNotNull(scenario, "应找到高血压场景");

        String userQuery = "高血压有什么症状？";
        String incompleteResponse = "高血压需要注意。"; // 缺失关键症状信息

        MedicalAccuracyValidator.ValidationResult result = validator.validateResponse(userQuery, incompleteResponse, "SCENARIO_001");

        System.out.println("不完整回答验证结果: " + result);

        // 验证关键词匹配率低
        assertTrue(result.getKeywordMatchRate() < 0.5, "不完整回答的关键词匹配率应<50%");

        // 验证有缺失关键词
        assertFalse(result.getMissingKeywords().isEmpty(), "应有缺失的关键词");

        // 验证可能有错误或警告
        assertTrue(result.getErrors().size() > 0 || result.getWarnings().size() > 0,
                "不完整回答应有错误或警告");

        // 验证总体结果可能不通过
        // 注意：这里不断言passed状态，因为验证器可能仍认为通过（如果匹配率≥50%）
    }

    /**
     * 测试错误的回答
     */
    @Test
    @DisplayName("错误回答验证测试")
    void testWrongResponse() {
        MedicalAccuracyValidator.TestScenario scenario = findScenarioById("SCENARIO_002");
        assertNotNull(scenario, "应找到糖尿病场景");

        String userQuery = "糖尿病怎么治疗？";
        String wrongResponse = "糖尿病的治疗方法是多吃糖。"; // 完全错误的回答

        MedicalAccuracyValidator.ValidationResult result = validator.validateResponse(userQuery, wrongResponse, "SCENARIO_002");

        System.out.println("错误回答验证结果: " + result);

        // 验证关键词匹配率极低
        assertTrue(result.getKeywordMatchRate() < 0.3, "错误回答的关键词匹配率应<30%");

        // 验证有大量缺失关键词
        assertTrue(result.getMissingKeywords().size() >= 3, "应有大量缺失关键词");

        // 验证可能有错误
        assertTrue(result.getErrors().size() > 0, "错误回答应有错误");

        // 验证总体结果应不通过
        assertFalse(result.isPassed(), "错误回答验证应不通过");
    }

    /**
     * 测试所有场景加载
     */
    @Test
    @DisplayName("测试场景加载完整性测试")
    void testAllScenariosLoaded() {
        // 验证场景数量
        assertTrue(testScenarios.size() >= 10, "应至少加载10个测试场景");

        // 验证关键场景存在
        assertTrue(testScenarios.stream().anyMatch(s -> s.getId().equals("SCENARIO_001")), "应包含高血压场景");
        assertTrue(testScenarios.stream().anyMatch(s -> s.getId().equals("SCENARIO_002")), "应包含糖尿病场景");
        assertTrue(testScenarios.stream().anyMatch(s -> s.getId().equals("SCENARIO_003")), "应包含急诊场景");
        assertTrue(testScenarios.stream().anyMatch(s -> s.getId().equals("SCENARIO_004")), "应包含药物查询场景");
        assertTrue(testScenarios.stream().anyMatch(s -> s.getId().equals("SCENARIO_005")), "应包含科室导诊场景");

        // 验证场景数据完整性
        for (MedicalAccuracyValidator.TestScenario scenario : testScenarios) {
            assertNotNull(scenario.getId(), "场景ID不应为空");
            assertNotNull(scenario.getName(), "场景名称不应为空");
            assertNotNull(scenario.getUserQuery(), "用户查询不应为空");
            assertNotNull(scenario.getExpectedResponseType(), "预期响应类型不应为空");
        }

        System.out.printf("成功验证 %d 个测试场景的完整性%n", testScenarios.size());
    }

    /**
     * 测试医学术语词典加载
     */
    @Test
    @DisplayName("医学术语词典加载测试")
    void testMedicalTermsLoaded() {
        int termCount = validator.getMedicalTermCount();
        assertTrue(termCount >= 10, "应至少加载10个医学术语");

        System.out.printf("医学术语词典包含 %d 个术语%n", termCount);
    }

    /**
     * 辅助方法：根据ID查找场景
     */
    private MedicalAccuracyValidator.TestScenario findScenarioById(String scenarioId) {
        return testScenarios.stream()
                .filter(s -> s.getId().equals(scenarioId))
                .findFirst()
                .orElse(null);
    }
}