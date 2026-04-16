package com.student.service.triage.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import com.student.config.DepartmentKnowledgeConfig;
import com.student.service.LlmService;
import com.student.service.triage.TriageService;
import com.student.service.triage.impl.EmergencySymptomDetector;
import com.student.service.triage.impl.HybridTriageServiceImpl;
import com.student.service.triage.impl.LlmTriageAdapter;
import com.student.service.triage.impl.RuleBasedTriageEngine;
import com.student.service.triage.impl.SymptomNormalizer;
import com.student.service.triage.stats.TriageStatsCollector;

/**
 * TriageEngineConfig 单元测试
 * 测试科室导诊引擎配置类的Bean创建和配置验证
 *
 * @author 系统生成
 * @version 1.0
 */
@SpringBootTest(classes = TriageEngineConfig.class)
@EnableConfigurationProperties(TriageConfig.class)
@TestPropertySource(properties = {
    "imkqas.triage.engine.rule-engine-threshold=0.65",
    "imkqas.triage.engine.llm-timeout=4000",
    "imkqas.triage.engine.rule-engine-weight=0.7",
    "imkqas.triage.engine.llm-weight=0.3",
    "imkqas.triage.engine.enable-fuzzy-match=true",
    "imkqas.triage.engine.fuzzy-match-threshold=0.75",
    "imkqas.triage.engine.min-match-score=0.35",
    "imkqas.triage.engine.max-recommendations=4",
    "imkqas.triage.engine.enable-emergency-detection=true",
    "imkqas.triage.engine.enable-stats=true",
    "imkqas.triage.engine.thread-pool-core-size=2",
    "imkqas.triage.engine.thread-pool-max-size=4",
    "imkqas.triage.engine.thread-pool-queue-capacity=100",
    "imkqas.triage.engine.batch-processing-max-size=20"
})
@DisplayName("科室导诊引擎配置测试")
class TriageEngineConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TriageConfig triageConfig;

    @MockBean
    private DepartmentKnowledgeConfig departmentKnowledgeConfig;

    @MockBean
    private LlmService llmService;

    @Test
    @DisplayName("配置类加载")
    void testConfigurationClassLoaded() {
        assertNotNull(applicationContext);
        assertNotNull(triageConfig);
    }

    @Test
    @DisplayName("TriageConfig Bean创建和属性绑定")
    void testTriageConfigBean() {
        assertNotNull(triageConfig);
        assertEquals(0.65, triageConfig.getRuleEngineThreshold(), 0.001);
        assertEquals(4000, triageConfig.getLlmTimeout());
        assertEquals(0.7, triageConfig.getRuleEngineWeight(), 0.001);
        assertEquals(0.3, triageConfig.getLlmWeight(), 0.001);
        assertTrue(triageConfig.isEnableFuzzyMatch());
        assertEquals(0.75, triageConfig.getFuzzyMatchThreshold(), 0.001);
        assertEquals(0.35, triageConfig.getMinMatchScore(), 0.001);
        assertEquals(4, triageConfig.getMaxRecommendations());
        assertTrue(triageConfig.isEnableEmergencyDetection());
        assertTrue(triageConfig.isEnableStats());
        assertEquals(2, triageConfig.getThreadPoolCoreSize());
        assertEquals(4, triageConfig.getThreadPoolMaxSize());
        assertEquals(100, triageConfig.getThreadPoolQueueCapacity());
        assertEquals(20, triageConfig.getBatchProcessingMaxSize());
    }

    @Test
    @DisplayName("ExecutorService Bean创建")
    void testExecutorServiceBean() {
        ExecutorService executorService = applicationContext.getBean(ExecutorService.class);
        assertNotNull(executorService);
        // ExecutorService应该是可用的
        assertFalse(executorService.isShutdown());
        assertFalse(executorService.isTerminated());
    }

    @Test
    @DisplayName("SymptomNormalizer Bean创建")
    void testSymptomNormalizerBean() {
        SymptomNormalizer normalizer = applicationContext.getBean(SymptomNormalizer.class);
        assertNotNull(normalizer);
    }

    @Test
    @DisplayName("RuleBasedTriageEngine Bean创建")
    void testRuleBasedTriageEngineBean() {
        RuleBasedTriageEngine engine = applicationContext.getBean(RuleBasedTriageEngine.class);
        assertNotNull(engine);
    }

    @Test
    @DisplayName("EmergencySymptomDetector Bean创建")
    void testEmergencySymptomDetectorBean() {
        EmergencySymptomDetector detector = applicationContext.getBean(EmergencySymptomDetector.class);
        assertNotNull(detector);
    }

    @Test
    @DisplayName("LlmTriageAdapter Bean创建")
    void testLlmTriageAdapterBean() {
        LlmTriageAdapter adapter = applicationContext.getBean(LlmTriageAdapter.class);
        assertNotNull(adapter);
    }

    @Test
    @DisplayName("TriageStatsCollector Bean创建")
    void testTriageStatsCollectorBean() {
        TriageStatsCollector collector = applicationContext.getBean(TriageStatsCollector.class);
        assertNotNull(collector);
    }

    @Test
    @DisplayName("TriageService Bean创建")
    void testTriageServiceBean() {
        TriageService service = applicationContext.getBean(TriageService.class);
        assertNotNull(service);
        assertTrue(service instanceof HybridTriageServiceImpl);
    }

    @Test
    @DisplayName("配置验证Bean")
    void testValidateTriageConfigBean() {
        // validateTriageConfig方法在配置类中定义，返回boolean
        // 它会在应用启动时执行，这里我们验证配置有效
        assertTrue(triageConfig.isValid());
    }

    @Test
    @DisplayName("配置验证 - 有效配置")
    void testConfigurationValidation_ValidConfig() {
        assertTrue(triageConfig.isValid());
    }

    @Test
    @DisplayName("Bean依赖注入验证")
    void testBeanDependencyInjection() {
        // 获取TriageService并验证其依赖已注入
        HybridTriageServiceImpl service = (HybridTriageServiceImpl) applicationContext.getBean(TriageService.class);
        assertNotNull(service);

        // 验证关键依赖不为null
        // 由于是集成测试，实际依赖由Spring注入
        // 我们可以验证Bean存在
        assertNotNull(applicationContext.getBean(SymptomNormalizer.class));
        assertNotNull(applicationContext.getBean(RuleBasedTriageEngine.class));
        assertNotNull(applicationContext.getBean(EmergencySymptomDetector.class));
        assertNotNull(applicationContext.getBean(LlmTriageAdapter.class));
        assertNotNull(applicationContext.getBean(TriageStatsCollector.class));
        assertNotNull(applicationContext.getBean(ExecutorService.class));
    }

    @Test
    @DisplayName("线程池配置")
    void testThreadPoolConfiguration() {
        ExecutorService executorService = applicationContext.getBean(ExecutorService.class);
        assertNotNull(executorService);

        // 验证线程池配置通过属性绑定
        assertEquals(2, triageConfig.getThreadPoolCoreSize());
        assertEquals(4, triageConfig.getThreadPoolMaxSize());
        assertEquals(100, triageConfig.getThreadPoolQueueCapacity());
    }

    @Test
    @DisplayName("权重规范化")
    void testWeightNormalization() {
        // 验证权重规范化逻辑
        triageConfig.normalizeWeights();
        double totalWeight = triageConfig.getRuleEngineWeight() + triageConfig.getLlmWeight();
        assertEquals(1.0, totalWeight, 0.001);
    }

    @Test
    @DisplayName("配置属性覆盖")
    void testConfigurationPropertyOverride() {
        // 验证@TestPropertySource中的属性覆盖了默认值
        assertEquals(0.65, triageConfig.getRuleEngineThreshold(), 0.001);
        assertEquals(4000, triageConfig.getLlmTimeout());
        assertEquals(4, triageConfig.getMaxRecommendations());
    }

    @Test
    @DisplayName("默认值回退")
    void testDefaultValueFallback() {
        // 测试未在@TestPropertySource中设置的属性使用默认值
        assertEquals(30, triageConfig.getStatsRetentionDays());
        assertEquals(1.0, triageConfig.getStatsSamplingRate(), 0.001);
        assertEquals(60, triageConfig.getThreadPoolKeepAliveSeconds());
        assertEquals(10000, triageConfig.getBatchProcessingTimeout());
        assertTrue(triageConfig.isEnableAutoFallback());
        assertEquals("RULE_ENGINE", triageConfig.getLlmUnavailableFallback());
        assertEquals("FALLBACK", triageConfig.getRuleEngineUnavailableFallback());
        assertEquals("建议咨询医院导诊台或全科医学科", triageConfig.getFallbackAdvice());
    }
}