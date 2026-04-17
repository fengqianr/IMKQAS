package com.student.service.triage.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.student.dto.triage.DepartmentKnowledgeBase;
import com.student.model.triage.DepartmentKnowledge;
import com.student.model.triage.EmergencyCheckResult;
import com.student.model.triage.EmergencyLevel;
import com.student.service.triage.config.TriageConfig;

/**
 * EmergencySymptomDetector 单元测试
 * 测试急诊症状检测器的核心功能，包括症状匹配、紧急级别判定和异步检测
 *
 * @author 系统生成
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("急诊症状检测器测试")
class EmergencySymptomDetectorTest {

    @Mock
    private DepartmentKnowledgeBase knowledgeBase;

    @Mock
    private TriageConfig config;


    private EmergencySymptomDetector emergencyDetector;

    private DepartmentKnowledge emergencyDept;
    private DepartmentKnowledge nonEmergencyDept;

    @BeforeEach
    void setUp() {
        // 初始化检测器
        emergencyDetector = new EmergencySymptomDetector(knowledgeBase, config);

        // 准备急诊科室
        emergencyDept = new DepartmentKnowledge();
        emergencyDept.setId("emergency");
        emergencyDept.setName("急诊科");
        emergencyDept.setSymptoms(Arrays.asList("胸痛", "呼吸困难", "意识丧失"));
        emergencyDept.setEmergency(true);
        emergencyDept.setEmergencyLevel("HIGH");
        emergencyDept.setPriority(1);

        // 准备非急诊科室
        nonEmergencyDept = new DepartmentKnowledge();
        nonEmergencyDept.setId("cardio");
        nonEmergencyDept.setName("心内科");
        nonEmergencyDept.setSymptoms(Arrays.asList("胸痛", "心悸"));
        nonEmergencyDept.setEmergency(false);
        nonEmergencyDept.setPriority(2);

        // 默认配置 - 使用lenient避免UnnecessaryStubbingException
        lenient().when(config.isEnableEmergencyDetection()).thenReturn(true);
        lenient().when(config.getEmergencyDetectionTimeout()).thenReturn(1000);
        lenient().when(config.isEnableFuzzyMatch()).thenReturn(true);
        lenient().when(config.getFuzzyMatchThreshold()).thenReturn(0.7);
        lenient().when(config.getEmergencyThreshold(EmergencyLevel.CRITICAL)).thenReturn(3);
        lenient().when(config.getEmergencyThreshold(EmergencyLevel.HIGH)).thenReturn(2);
        lenient().when(config.getEmergencyThreshold(EmergencyLevel.MEDIUM)).thenReturn(1);
        lenient().when(config.getEmergencyThreshold(EmergencyLevel.LOW)).thenReturn(0);
    }

    @Test
    @DisplayName("检测急诊症状 - 发现匹配症状")
    void testDetect_EmergencySymptomsFound() {
        // 模拟知识库返回急诊科室
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));
        System.out.println("Dept emergencyLevel: " + emergencyDept.getEmergencyLevel());
        System.out.println("Dept symptoms: " + emergencyDept.getSymptoms());
        System.out.println("EmergencyLevel.fromString(HIGH): " + EmergencyLevel.fromString("HIGH"));

        // 执行检测 - 使用有分隔符的症状以确保正确拆分
        EmergencyCheckResult result = emergencyDetector.detect("胸痛，呼吸困难");

        // 调试输出
        System.out.println("Result: isEmergency=" + result.isEmergency() +
                         ", level=" + result.getEmergencyLevel() +
                         ", symptoms=" + result.getEmergencySymptoms());

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isEmergency());
        assertEquals(EmergencyLevel.HIGH, result.getEmergencyLevel());
        assertFalse(result.getEmergencySymptoms().isEmpty());
        assertTrue(result.getAdvice().contains("严重症状"));
        assertTrue(result.getEmergencySymptoms().contains("胸痛") ||
                   result.getEmergencySymptoms().contains("呼吸困难"));
    }

    @Test
    @DisplayName("检测急诊症状 - 无匹配症状")
    void testDetect_NoEmergencySymptoms() {
        // 模拟知识库返回急诊科室
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        // 执行检测（症状不匹配）
        EmergencyCheckResult result = emergencyDetector.detect("轻微头痛");

        // 验证结果
        assertNotNull(result);
        assertFalse(result.isEmergency());
        assertEquals(EmergencyLevel.LOW, result.getEmergencyLevel());
        assertTrue(result.getEmergencySymptoms().isEmpty());
        assertTrue(result.getAdvice().contains("未检测到需要紧急处理的症状"));
    }

    @Test
    @DisplayName("检测急诊症状 - 急诊检测禁用")
    void testDetect_EmergencyDetectionDisabled() {
        // 配置禁用急诊检测
        when(config.isEnableEmergencyDetection()).thenReturn(false);

        EmergencyCheckResult result = emergencyDetector.detect("胸痛呼吸困难");

        // 验证返回无急诊结果
        assertNotNull(result);
        assertFalse(result.isEmergency());
        assertEquals(EmergencyLevel.LOW, result.getEmergencyLevel());
    }

    @Test
    @DisplayName("检测急诊症状 - 空症状")
    void testDetect_EmptySymptoms() {
        // 禁用急诊检测，避免不必要的知识库调用
        when(config.isEnableEmergencyDetection()).thenReturn(false);

        EmergencyCheckResult result = emergencyDetector.detect("");

        assertNotNull(result);
        assertFalse(result.isEmergency());
    }

    @Test
    @DisplayName("检测急诊症状 - null症状")
    void testDetect_NullSymptoms() {
        // 禁用急诊检测，避免不必要的知识库调用
        when(config.isEnableEmergencyDetection()).thenReturn(false);

        EmergencyCheckResult result = emergencyDetector.detect(null);

        assertNotNull(result);
        assertFalse(result.isEmergency());
    }

    @Test
    @DisplayName("检测急诊症状 - 知识库中没有急诊科室")
    void testDetect_NoEmergencyDepartmentsInKnowledgeBase() {
        // 模拟知识库返回空列表
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList());

        EmergencyCheckResult result = emergencyDetector.detect("胸痛");

        assertNotNull(result);
        assertFalse(result.isEmergency());
    }

    @Test
    @DisplayName("检测急诊症状 - 异步执行")
    void testDetect_AsyncExecution() throws Exception {
        // 模拟知识库返回急诊科室
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        // 使用真实的单线程执行器，而不是模拟
        java.util.concurrent.ExecutorService realExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            // 执行异步检测
            EmergencyCheckResult result = emergencyDetector.detect("胸痛", realExecutor);

            assertNotNull(result);
            assertTrue(result.isEmergency());
        } finally {
            realExecutor.shutdown();
        }
    }

    @Test
    @DisplayName("检测急诊症状 - 异步执行超时")
    void testDetect_AsyncTimeout() throws InterruptedException {
        // 模拟知识库返回急诊科室，但让调用睡眠以触发超时
        when(knowledgeBase.getEmergencyDepartments()).thenAnswer(invocation -> {
            // 睡眠时间超过配置的超时时间(1000ms)
            Thread.sleep(2000);
            return Arrays.asList(emergencyDept);
        });

        // 使用真实执行器
        java.util.concurrent.ExecutorService realExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            // 执行异步检测（应返回无急诊结果）
            EmergencyCheckResult result = emergencyDetector.detect("胸痛", realExecutor);

            assertNotNull(result);
            assertFalse(result.isEmergency());
        } finally {
            realExecutor.shutdown();
        }
    }

    @Test
    @DisplayName("症状匹配 - 完全匹配")
    void testSymptomMatching_ExactMatch() {
        // 模拟知识库返回急诊科室
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        EmergencyCheckResult result = emergencyDetector.detect("胸痛");

        // 胸痛完全匹配急诊科室的症状列表
        assertTrue(result.isEmergency());
        assertTrue(result.getEmergencySymptoms().contains("胸痛"));
    }

    @Test
    @DisplayName("症状匹配 - 包含匹配")
    void testSymptomMatching_ContainsMatch() {
        // 模拟知识库返回急诊科室
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        // "剧烈胸痛"包含"胸痛"
        EmergencyCheckResult result = emergencyDetector.detect("剧烈胸痛");

        assertTrue(result.isEmergency());
    }

    @Test
    @DisplayName("紧急级别判定 - 根据科室预设级别")
    void testEmergencyLevelDetermination_FromDepartmentPreset() {
        // 设置科室预设紧急级别为CRITICAL
        emergencyDept.setEmergencyLevel("CRITICAL");
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        // 调试输出
        System.out.println("科室 emergencyLevel: " + emergencyDept.getEmergencyLevel());
        System.out.println("科室 isEmergency: " + emergencyDept.isEmergency());
        System.out.println("科室 symptoms: " + emergencyDept.getSymptoms());
        System.out.println("EmergencyLevel.fromString(\"CRITICAL\"): " + EmergencyLevel.fromString("CRITICAL"));
        System.out.println("EmergencyLevel.fromString(\"HIGH\"): " + EmergencyLevel.fromString("HIGH"));

        EmergencyCheckResult result = emergencyDetector.detect("胸痛");

        System.out.println("结果 emergencyLevel: " + result.getEmergencyLevel());
        System.out.println("结果 isEmergency: " + result.isEmergency());

        assertEquals(EmergencyLevel.CRITICAL, result.getEmergencyLevel());
    }

    @Test
    @DisplayName("紧急级别判定 - 根据匹配症状数量")
    void testEmergencyLevelDetermination_FromMatchedSymptomsCount() {
        // 科室没有预设级别，使用匹配数量
        emergencyDept.setEmergencyLevel(null);
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        // 匹配2个症状 -> HIGH级别（使用分隔符确保正确拆分）
        EmergencyCheckResult result = emergencyDetector.detect("胸痛，呼吸困难");

        assertEquals(EmergencyLevel.HIGH, result.getEmergencyLevel());
    }

    @Test
    @DisplayName("批量检测 - 成功")
    void testBatchDetect_Success() {
        // 模拟知识库返回急诊科室
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        List<String> symptomsList = Arrays.asList("胸痛", "头痛", "呼吸困难");
        List<EmergencyCheckResult> results = emergencyDetector.batchDetect(symptomsList);

        assertNotNull(results);
        assertEquals(3, results.size());
        // 胸痛和呼吸困难应检测为急诊
        assertTrue(results.get(0).isEmergency()); // 胸痛
        assertFalse(results.get(1).isEmergency()); // 头痛（不匹配）
        assertTrue(results.get(2).isEmergency()); // 呼吸困难
    }

    @Test
    @DisplayName("获取急诊科室列表")
    void testGetEmergencyDepartments() {
        // 模拟知识库返回急诊科室列表
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        List<DepartmentKnowledge> emergencyDepts = emergencyDetector.getEmergencyDepartments();

        assertNotNull(emergencyDepts);
        assertEquals(1, emergencyDepts.size());
        assertEquals("急诊科", emergencyDepts.get(0).getName());
    }

    @Test
    @DisplayName("检查急诊检测是否启用")
    void testIsEmergencyDetectionEnabled() {
        when(config.isEnableEmergencyDetection()).thenReturn(true);
        assertTrue(emergencyDetector.isEmergencyDetectionEnabled());

        when(config.isEnableEmergencyDetection()).thenReturn(false);
        assertFalse(emergencyDetector.isEmergencyDetectionEnabled());
    }

    @Test
    @DisplayName("获取急诊阈值")
    void testGetEmergencyThreshold() {
        when(config.getEmergencyThreshold(EmergencyLevel.HIGH)).thenReturn(2);
        int threshold = emergencyDetector.getEmergencyThreshold(EmergencyLevel.HIGH);
        assertEquals(2, threshold);
    }

    @Test
    @DisplayName("模糊匹配启用")
    void testFuzzyMatchingEnabled() {
        // 启用模糊匹配
        when(config.isEnableFuzzyMatch()).thenReturn(true);
        when(config.getFuzzyMatchThreshold()).thenReturn(0.7);
        when(knowledgeBase.getEmergencyDepartments()).thenReturn(Arrays.asList(emergencyDept));

        // 模糊匹配测试
        EmergencyCheckResult result = emergencyDetector.detect("胸部疼痛"); // 近似"胸痛"

        // 由于模糊匹配实现依赖SymptomNormalizer，这里主要验证流程不异常
        assertNotNull(result);
    }

    private EmergencyCheckResult createEmergencyResult() {
        EmergencyCheckResult result = new EmergencyCheckResult();
        result.setEmergency(true);
        result.setEmergencyLevel(EmergencyLevel.HIGH);
        result.setEmergencySymptoms(Arrays.asList("胸痛"));
        result.setAdvice("检测到严重症状: 胸痛。建议立即就医，优先考虑急诊科。");
        return result;
    }
}