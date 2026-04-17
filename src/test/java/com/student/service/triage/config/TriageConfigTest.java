package com.student.service.triage.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.student.model.triage.EmergencyLevel;

/**
 * TriageConfig 单元测试
 * 测试科室导诊引擎配置类的验证和规范化功能
 *
 * @author 系统生成
 * @version 1.0
 */
@DisplayName("科室导诊配置测试")
class TriageConfigTest {

    private TriageConfig config;

    @BeforeEach
    void setUp() {
        config = new TriageConfig();
    }

    @Test
    @DisplayName("默认配置有效性")
    void testDefaultConfigIsValid() {
        assertTrue(config.isValid());
    }

    @Test
    @DisplayName("配置有效性 - 规则引擎阈值超出范围")
    void testIsValid_RuleEngineThresholdOutOfRange() {
        config.setRuleEngineThreshold(1.5); // 大于1
        assertFalse(config.isValid());

        config.setRuleEngineThreshold(-0.1); // 小于0
        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("配置有效性 - LLM超时时间无效")
    void testIsValid_LlmTimeoutInvalid() {
        config.setLlmTimeout(0); // 必须大于0
        assertFalse(config.isValid());

        config.setLlmTimeout(-100);
        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("配置有效性 - 权重超出范围")
    void testIsValid_WeightsOutOfRange() {
        config.setRuleEngineWeight(1.5); // 大于1
        assertFalse(config.isValid());

        config.setLlmWeight(-0.1); // 小于0
        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("配置有效性 - 权重和不为1")
    void testIsValid_WeightsSumNotOne() {
        config.setRuleEngineWeight(0.3);
        config.setLlmWeight(0.3); // 和为0.6，不等于1
        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("配置有效性 - 权重和为1（允许微小误差）")
    void testIsValid_WeightsSumOneWithTolerance() {
        config.setRuleEngineWeight(0.7999);
        config.setLlmWeight(0.2001); // 和为1.0000，误差0.0002 < 0.001
        assertTrue(config.isValid());
    }

    @Test
    @DisplayName("权重规范化 - 正常权重")
    void testNormalizeWeights_NormalWeights() {
        config.setRuleEngineWeight(0.6);
        config.setLlmWeight(0.4);

        config.normalizeWeights();

        assertEquals(0.6, config.getRuleEngineWeight(), 0.0001);
        assertEquals(0.4, config.getLlmWeight(), 0.0001);
    }

    @Test
    @DisplayName("权重规范化 - 权重和不为1")
    void testNormalizeWeights_WeightsSumNotOne() {
        config.setRuleEngineWeight(0.8);
        config.setLlmWeight(0.1); // 和为0.9

        config.normalizeWeights();

        assertEquals(0.8 / 0.9, config.getRuleEngineWeight(), 0.0001);
        assertEquals(0.1 / 0.9, config.getLlmWeight(), 0.0001);
    }

    @Test
    @DisplayName("权重规范化 - 零权重")
    void testNormalizeWeights_ZeroWeights() {
        config.setRuleEngineWeight(0.0);
        config.setLlmWeight(0.0);

        config.normalizeWeights(); // 不应抛出异常

        assertEquals(0.0, config.getRuleEngineWeight(), 0.0001);
        assertEquals(0.0, config.getLlmWeight(), 0.0001);
    }

    @Test
    @DisplayName("获取急诊阈值 - 默认值")
    void testGetEmergencyThreshold_DefaultValues() {
        // 默认阈值配置
        Map<EmergencyLevel, Integer> thresholds = new HashMap<>();
        thresholds.put(EmergencyLevel.CRITICAL, 3);
        thresholds.put(EmergencyLevel.HIGH, 2);
        thresholds.put(EmergencyLevel.MEDIUM, 1);
        thresholds.put(EmergencyLevel.LOW, 0);
        config.setEmergencyThresholds(thresholds);

        assertEquals(3, config.getEmergencyThreshold(EmergencyLevel.CRITICAL));
        assertEquals(2, config.getEmergencyThreshold(EmergencyLevel.HIGH));
        assertEquals(1, config.getEmergencyThreshold(EmergencyLevel.MEDIUM));
        assertEquals(0, config.getEmergencyThreshold(EmergencyLevel.LOW));
    }

    @Test
    @DisplayName("获取急诊阈值 - 未配置的级别")
    void testGetEmergencyThreshold_UnconfiguredLevel() {
        // 设置部分阈值
        Map<EmergencyLevel, Integer> thresholds = new HashMap<>();
        thresholds.put(EmergencyLevel.CRITICAL, 3);
        config.setEmergencyThresholds(thresholds);

        // 未配置的级别应返回默认值0
        assertEquals(0, config.getEmergencyThreshold(EmergencyLevel.HIGH));
        assertEquals(0, config.getEmergencyThreshold(EmergencyLevel.MEDIUM));
        assertEquals(0, config.getEmergencyThreshold(EmergencyLevel.LOW));
    }

    @Test
    @DisplayName("配置属性默认值")
    void testDefaultPropertyValues() {
        assertEquals(0.6, config.getRuleEngineThreshold(), 0.0001);
        assertEquals(3000, config.getLlmTimeout());
        assertEquals(0.8, config.getRuleEngineWeight(), 0.0001);
        assertEquals(0.2, config.getLlmWeight(), 0.0001);
        assertTrue(config.isEnableFuzzyMatch());
        assertEquals(0.7, config.getFuzzyMatchThreshold(), 0.0001);
        assertEquals(0.3, config.getMinMatchScore(), 0.0001);
        assertEquals(3, config.getMaxRecommendations());
        assertTrue(config.isEnableEmergencyDetection());
        assertEquals(1000, config.getEmergencyDetectionTimeout());
        assertTrue(config.isEnableStats());
        assertEquals(30, config.getStatsRetentionDays());
        assertEquals(1.0, config.getStatsSamplingRate(), 0.0001);
        assertEquals(2, config.getThreadPoolCoreSize());
        assertEquals(4, config.getThreadPoolMaxSize());
        assertEquals(100, config.getThreadPoolQueueCapacity());
        assertEquals(60, config.getThreadPoolKeepAliveSeconds());
        assertEquals(20, config.getBatchProcessingMaxSize());
        assertEquals(10000, config.getBatchProcessingTimeout());
        assertTrue(config.isEnableAutoFallback());
        assertEquals("RULE_ENGINE", config.getLlmUnavailableFallback());
        assertEquals("FALLBACK", config.getRuleEngineUnavailableFallback());
        assertEquals("建议咨询医院导诊台或全科医学科", config.getFallbackAdvice());
    }

    @Test
    @DisplayName("配置属性设置和获取")
    void testPropertySettersAndGetters() {
        // 测试所有setter和getter
        config.setRuleEngineThreshold(0.7);
        assertEquals(0.7, config.getRuleEngineThreshold(), 0.0001);

        config.setLlmTimeout(5000);
        assertEquals(5000, config.getLlmTimeout());

        config.setRuleEngineWeight(0.9);
        assertEquals(0.9, config.getRuleEngineWeight(), 0.0001);

        config.setLlmWeight(0.1);
        assertEquals(0.1, config.getLlmWeight(), 0.0001);

        config.setEnableFuzzyMatch(false);
        assertFalse(config.isEnableFuzzyMatch());

        config.setFuzzyMatchThreshold(0.8);
        assertEquals(0.8, config.getFuzzyMatchThreshold(), 0.0001);

        config.setMinMatchScore(0.4);
        assertEquals(0.4, config.getMinMatchScore(), 0.0001);

        config.setMaxRecommendations(5);
        assertEquals(5, config.getMaxRecommendations());

        config.setEnableEmergencyDetection(false);
        assertFalse(config.isEnableEmergencyDetection());

        config.setEmergencyDetectionTimeout(2000);
        assertEquals(2000, config.getEmergencyDetectionTimeout());

        config.setEnableStats(false);
        assertFalse(config.isEnableStats());

        config.setStatsRetentionDays(7);
        assertEquals(7, config.getStatsRetentionDays());

        config.setStatsSamplingRate(0.5);
        assertEquals(0.5, config.getStatsSamplingRate(), 0.0001);

        config.setThreadPoolCoreSize(4);
        assertEquals(4, config.getThreadPoolCoreSize());

        config.setThreadPoolMaxSize(8);
        assertEquals(8, config.getThreadPoolMaxSize());

        config.setThreadPoolQueueCapacity(200);
        assertEquals(200, config.getThreadPoolQueueCapacity());

        config.setThreadPoolKeepAliveSeconds(30);
        assertEquals(30, config.getThreadPoolKeepAliveSeconds());

        config.setBatchProcessingMaxSize(50);
        assertEquals(50, config.getBatchProcessingMaxSize());

        config.setBatchProcessingTimeout(30000);
        assertEquals(30000, config.getBatchProcessingTimeout());

        config.setEnableAutoFallback(false);
        assertFalse(config.isEnableAutoFallback());

        config.setLlmUnavailableFallback("FALLBACK");
        assertEquals("FALLBACK", config.getLlmUnavailableFallback());

        config.setRuleEngineUnavailableFallback("LLM");
        assertEquals("LLM", config.getRuleEngineUnavailableFallback());

        config.setFallbackAdvice("请咨询医生");
        assertEquals("请咨询医生", config.getFallbackAdvice());
    }

    @Test
    @DisplayName("LLM提示词模板")
    void testLlmTriagePromptTemplate() {
        String template = """
            自定义提示词模板。
            症状：{symptoms}
            请分析并推荐科室。
            """;
        config.setLlmTriagePromptTemplate(template);

        assertEquals(template, config.getLlmTriagePromptTemplate());
    }

    @Test
    @DisplayName("急诊阈值映射")
    void testEmergencyThresholdsMapping() {
        Map<EmergencyLevel, Integer> thresholds = new HashMap<>();
        thresholds.put(EmergencyLevel.CRITICAL, 5);
        thresholds.put(EmergencyLevel.HIGH, 3);
        thresholds.put(EmergencyLevel.MEDIUM, 2);
        thresholds.put(EmergencyLevel.LOW, 1);

        config.setEmergencyThresholds(thresholds);

        Map<EmergencyLevel, Integer> retrieved = config.getEmergencyThresholds();
        assertNotNull(retrieved);
        assertEquals(5, retrieved.get(EmergencyLevel.CRITICAL));
        assertEquals(3, retrieved.get(EmergencyLevel.HIGH));
        assertEquals(2, retrieved.get(EmergencyLevel.MEDIUM));
        assertEquals(1, retrieved.get(EmergencyLevel.LOW));
    }

    @Test
    @DisplayName("配置有效性边界值")
    void testIsValid_BoundaryValues() {
        // 边界值测试
        config.setRuleEngineThreshold(0.0); // 最小边界
        config.setLlmTimeout(1); // 最小边界
        config.setRuleEngineWeight(0.0);
        config.setLlmWeight(1.0); // 和为1
        assertTrue(config.isValid());

        config.setRuleEngineThreshold(1.0); // 最大边界
        config.setRuleEngineWeight(1.0);
        config.setLlmWeight(0.0);
        assertTrue(config.isValid());
    }

    @Test
    @DisplayName("复杂配置有效性")
    void testIsValid_ComplexConfiguration() {
        // 有效复杂配置
        config.setRuleEngineThreshold(0.65);
        config.setLlmTimeout(4000);
        config.setRuleEngineWeight(0.75);
        config.setLlmWeight(0.25);
        config.setEnableFuzzyMatch(true);
        config.setFuzzyMatchThreshold(0.75);
        config.setMinMatchScore(0.35);
        config.setMaxRecommendations(4);
        config.setEnableEmergencyDetection(true);
        config.setEmergencyDetectionTimeout(1500);
        config.setEnableStats(true);
        config.setStatsRetentionDays(60);
        config.setStatsSamplingRate(0.8);
        config.setThreadPoolCoreSize(4);
        config.setThreadPoolMaxSize(10);
        config.setThreadPoolQueueCapacity(150);
        config.setThreadPoolKeepAliveSeconds(45);
        config.setBatchProcessingMaxSize(30);
        config.setBatchProcessingTimeout(20000);
        config.setEnableAutoFallback(true);
        config.setLlmUnavailableFallback("RULE_ENGINE");
        config.setRuleEngineUnavailableFallback("FALLBACK");
        config.setFallbackAdvice("请前往导诊台咨询");

        assertTrue(config.isValid());
    }
}