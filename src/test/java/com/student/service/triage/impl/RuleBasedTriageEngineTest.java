package com.student.service.triage.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.student.dto.triage.DepartmentKnowledgeBase;
import com.student.model.triage.DepartmentKnowledge;
import com.student.model.triage.DepartmentRecommendation;
import com.student.model.triage.DepartmentTriageResult;
import com.student.service.triage.config.TriageConfig;

/**
 * RuleBasedTriageEngine 单元测试
 * 测试规则引擎的科室匹配和推荐功能
 *
 * @author 系统生成
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("规则引擎测试")
class RuleBasedTriageEngineTest {

    @Mock
    private DepartmentKnowledgeBase knowledgeBase;

    @Mock
    private SymptomNormalizer symptomNormalizer;

    @Mock
    private TriageConfig config;

    private RuleBasedTriageEngine ruleEngine;

    @BeforeEach
    void setUp() {
        // 配置模拟对象，使用lenient避免UnnecessaryStubbingException
        lenient().when(config.isEnableFuzzyMatch()).thenReturn(true);
        lenient().when(config.getFuzzyMatchThreshold()).thenReturn(0.7);
        lenient().when(config.getMinMatchScore()).thenReturn(0.3);
        lenient().when(config.getMaxRecommendations()).thenReturn(3);

        ruleEngine = new RuleBasedTriageEngine(knowledgeBase, symptomNormalizer, config);
    }

    @Test
    @DisplayName("测试分析症状 - 基本匹配")
    void testAnalyze_BasicMatch() {
        // Arrange
        String symptoms = "发烧咳嗽";
        String normalizedSymptoms = "发烧 咳嗽";

        List<String> symptomList = Arrays.asList("发烧", "咳嗽");

        // 创建模拟科室知识
        DepartmentKnowledge dept1 = new DepartmentKnowledge();
        dept1.setId("dept1");
        dept1.setName("呼吸内科");
        dept1.setSymptoms(Arrays.asList("发烧", "咳嗽", "喉咙痛"));
        dept1.setPriority(1);

        DepartmentKnowledge dept2 = new DepartmentKnowledge();
        dept2.setId("dept2");
        dept2.setName("发热门诊");
        dept2.setSymptoms(Arrays.asList("发烧", "畏寒"));
        dept2.setPriority(2);

        List<DepartmentKnowledge> departments = Arrays.asList(dept1, dept2);

        // 设置模拟行为
        when(symptomNormalizer.normalize(symptoms)).thenReturn(normalizedSymptoms);
        when(symptomNormalizer.splitSymptoms(normalizedSymptoms)).thenReturn(symptomList);
        when(knowledgeBase.getDepartments()).thenReturn(departments);

        // Act
        DepartmentTriageResult result = ruleEngine.analyze(symptoms);

        // Assert
        assertNotNull(result);
        assertEquals(normalizedSymptoms, result.getSymptoms()); // 引擎返回标准化后的症状
        assertNotNull(result.getRecommendations());
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0);
    }

    @Test
    @DisplayName("测试分析症状 - 无匹配科室")
    void testAnalyze_NoMatch() {
        // Arrange
        String symptoms = "罕见症状";
        String normalizedSymptoms = "罕见症状";
        List<String> symptomList = Arrays.asList("罕见症状");

        // 设置模拟行为
        when(symptomNormalizer.normalize(symptoms)).thenReturn(normalizedSymptoms);
        lenient().when(symptomNormalizer.splitSymptoms(normalizedSymptoms)).thenReturn(symptomList);
        when(knowledgeBase.getDepartments()).thenReturn(Arrays.asList());

        // Act
        DepartmentTriageResult result = ruleEngine.analyze(symptoms);

        // Assert
        assertNotNull(result);
        assertEquals(normalizedSymptoms, result.getSymptoms()); // 引擎返回标准化后的症状
        assertTrue(result.getRecommendations().isEmpty());
        assertEquals(0.0, result.getConfidence(), 0.01);
    }

    @Test
    @DisplayName("测试分析症状 - 空症状")
    void testAnalyze_EmptySymptoms() {
        // Arrange
        String symptoms = "";
        String normalizedSymptoms = "";

        // 设置模拟行为
        when(symptomNormalizer.normalize(symptoms)).thenReturn(normalizedSymptoms);
        lenient().when(symptomNormalizer.splitSymptoms(normalizedSymptoms)).thenReturn(Arrays.asList());

        // Act
        DepartmentTriageResult result = ruleEngine.analyze(symptoms);

        // Assert
        assertNotNull(result);
        assertEquals(symptoms, result.getSymptoms());
        assertTrue(result.getRecommendations().isEmpty());
        assertEquals(0.0, result.getConfidence(), 0.01);
    }

    @Test
    @DisplayName("测试分析症状 - null症状")
    void testAnalyze_NullSymptoms() {
        // Arrange
        String symptoms = null;
        String normalizedSymptoms = "";

        // 设置模拟行为
        when(symptomNormalizer.normalize(symptoms)).thenReturn(normalizedSymptoms);
        lenient().when(symptomNormalizer.splitSymptoms(normalizedSymptoms)).thenReturn(Arrays.asList());

        // Act
        DepartmentTriageResult result = ruleEngine.analyze(symptoms);

        // Assert
        assertNotNull(result);
        assertEquals("", result.getSymptoms()); // 引擎返回标准化后的空字符串
        assertTrue(result.getRecommendations().isEmpty());
        assertEquals(0.0, result.getConfidence(), 0.01);
    }

    @Test
    @DisplayName("测试分析症状 - 急诊科室优先")
    void testAnalyze_EmergencyPriority() {
        // Arrange
        String symptoms = "胸痛呼吸困难";
        String normalizedSymptoms = "胸痛 呼吸困难";
        List<String> symptomList = Arrays.asList("胸痛", "呼吸困难");

        // 创建模拟科室知识
        DepartmentKnowledge emergencyDept = new DepartmentKnowledge();
        emergencyDept.setId("emergency");
        emergencyDept.setName("急诊科");
        emergencyDept.setSymptoms(Arrays.asList("胸痛", "呼吸困难", "意识丧失"));
        emergencyDept.setPriority(1);
        emergencyDept.setEmergency(true);
        emergencyDept.setEmergencyLevel("HIGH");

        DepartmentKnowledge regularDept = new DepartmentKnowledge();
        regularDept.setId("cardio");
        regularDept.setName("心内科");
        regularDept.setSymptoms(Arrays.asList("胸痛", "心悸"));
        regularDept.setPriority(2);
        regularDept.setEmergency(false);

        List<DepartmentKnowledge> departments = Arrays.asList(emergencyDept, regularDept);

        // 设置模拟行为
        when(symptomNormalizer.normalize(symptoms)).thenReturn(normalizedSymptoms);
        when(symptomNormalizer.splitSymptoms(normalizedSymptoms)).thenReturn(symptomList);
        when(knowledgeBase.getDepartments()).thenReturn(departments);

        // Act
        DepartmentTriageResult result = ruleEngine.analyze(symptoms);

        // Assert
        assertNotNull(result);
        assertFalse(result.getRecommendations().isEmpty());
        // 急诊科应该优先推荐
        boolean hasEmergencyDept = result.getRecommendations().stream()
            .anyMatch(rec -> "急诊科".equals(rec.getDepartmentName()));
        assertTrue(hasEmergencyDept);
    }

    @Test
    @DisplayName("测试置信度计算")
    void testConfidenceCalculation() {
        // 注意：此测试验证置信度计算逻辑
        // 由于实际计算在引擎内部，我们通过模拟确保方法被调用
        String symptoms = "测试症状";
        String normalizedSymptoms = "测试症状";
        List<String> symptomList = Arrays.asList("测试症状");

        DepartmentKnowledge dept = new DepartmentKnowledge();
        dept.setId("test");
        dept.setName("测试科室");
        dept.setSymptoms(Arrays.asList("测试症状"));
        dept.setPriority(1);

        // 设置模拟行为
        when(symptomNormalizer.normalize(symptoms)).thenReturn(normalizedSymptoms);
        when(symptomNormalizer.splitSymptoms(normalizedSymptoms)).thenReturn(symptomList);
        when(knowledgeBase.getDepartments()).thenReturn(Arrays.asList(dept));

        // Act
        DepartmentTriageResult result = ruleEngine.analyze(symptoms);

        // Assert
        assertNotNull(result);
        // 置信度应该在合理范围内
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0);
    }

    @Test
    @DisplayName("测试推荐数量限制")
    void testRecommendationLimit() {
        // Arrange
        String symptoms = "多种症状";
        String normalizedSymptoms = "多种症状";
        List<String> symptomList = Arrays.asList("多种症状");

        // 创建多个科室
        List<DepartmentKnowledge> departments = Arrays.asList(
            createDepartment("dept1", "科室1", Arrays.asList("多种症状")),
            createDepartment("dept2", "科室2", Arrays.asList("多种症状")),
            createDepartment("dept3", "科室3", Arrays.asList("多种症状")),
            createDepartment("dept4", "科室4", Arrays.asList("多种症状")),
            createDepartment("dept5", "科室5", Arrays.asList("多种症状"))
        );

        // 设置模拟行为
        when(symptomNormalizer.normalize(symptoms)).thenReturn(normalizedSymptoms);
        when(symptomNormalizer.splitSymptoms(normalizedSymptoms)).thenReturn(symptomList);
        when(knowledgeBase.getDepartments()).thenReturn(departments);
        lenient().when(config.getMaxRecommendations()).thenReturn(3); // 限制为3个

        // Act
        DepartmentTriageResult result = ruleEngine.analyze(symptoms);

        // Assert
        assertNotNull(result);
        assertTrue(result.getRecommendations().size() <= 3);
    }

    private DepartmentKnowledge createDepartment(String id, String name, List<String> symptoms) {
        DepartmentKnowledge dept = new DepartmentKnowledge();
        dept.setId(id);
        dept.setName(name);
        dept.setSymptoms(symptoms);
        dept.setPriority(1);
        return dept;
    }
}