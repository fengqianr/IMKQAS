package com.student.service.triage.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.student.service.LlmService;
import com.student.service.triage.TriageService;
import com.student.service.triage.impl.EmergencySymptomDetector;
import com.student.service.triage.impl.HybridTriageServiceImpl;
import com.student.service.triage.impl.LlmTriageAdapter;
import com.student.service.triage.impl.RuleBasedTriageEngine;
import com.student.service.triage.impl.SymptomNormalizer;
import com.student.service.triage.stats.TriageStatsCollector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 科室导诊引擎配置类
 * 配置所有引擎组件的Spring Bean
 *
 * @author 系统生成
 * @version 1.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TriageConfig.class)
public class TriageEngineConfig {

    private final TriageConfig triageConfig;
    private final com.student.config.DepartmentKnowledgeConfig departmentKnowledgeConfig;
    private final LlmService llmService;

    /**
     * 创建线程池ExecutorService
     * 用于异步执行LLM分析和批量处理
     */
    @Bean
    public ExecutorService triageExecutorService() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            triageConfig.getThreadPoolCoreSize(),
            triageConfig.getThreadPoolMaxSize(),
            triageConfig.getThreadPoolKeepAliveSeconds(),
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(triageConfig.getThreadPoolQueueCapacity()),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("创建科室导诊线程池: coreSize={}, maxSize={}, queueCapacity={}",
            triageConfig.getThreadPoolCoreSize(),
            triageConfig.getThreadPoolMaxSize(),
            triageConfig.getThreadPoolQueueCapacity());

        return executor;
    }

    /**
     * 创建症状标准化器Bean
     */
    @Bean
    public SymptomNormalizer symptomNormalizer() {
        return new SymptomNormalizer(
            departmentKnowledgeConfig.symptomSynonyms(),
            triageConfig.isEnableFuzzyMatch(),
            triageConfig.getFuzzyMatchThreshold()
        );
    }

    /**
     * 创建规则引擎Bean
     */
    @Bean
    public RuleBasedTriageEngine ruleBasedTriageEngine() {
        return new RuleBasedTriageEngine(
            departmentKnowledgeConfig.departmentKnowledgeBase(),
            symptomNormalizer(),
            triageConfig
        );
    }

    /**
     * 创建急诊症状检测器Bean
     */
    @Bean
    public EmergencySymptomDetector emergencySymptomDetector() {
        return new EmergencySymptomDetector(
            departmentKnowledgeConfig.departmentKnowledgeBase(),
            triageConfig
        );
    }

    /**
     * 创建LLM分流适配器Bean
     */
    @Bean
    public LlmTriageAdapter llmTriageAdapter() {
        return new LlmTriageAdapter(
            llmService,
            triageConfig,
            triageExecutorService()
        );
    }

    /**
     * 创建统计收集器Bean
     */
    @Bean
    public TriageStatsCollector triageStatsCollector() {
        return new TriageStatsCollector(triageConfig);
    }

    /**
     * 创建混合分流服务实现Bean
     */
    @Bean
    public TriageService triageService() {
        return new HybridTriageServiceImpl(
            symptomNormalizer(),
            ruleBasedTriageEngine(),
            emergencySymptomDetector(),
            llmTriageAdapter(),
            triageConfig,
            triageExecutorService(),
            triageStatsCollector()
        );
    }

    /**
     * 配置验证
     * 在应用启动时验证所有配置
     */
    @Bean
    public boolean validateTriageConfig() {
        if (!triageConfig.isValid()) {
            log.error("科室导诊引擎配置无效，请检查配置参数");
            throw new IllegalArgumentException("科室导诊引擎配置无效");
        }

        log.info("科室导诊引擎配置验证通过");
        log.info("规则引擎阈值: {}", triageConfig.getRuleEngineThreshold());
        log.info("LLM超时时间: {}ms", triageConfig.getLlmTimeout());
        log.info("规则引擎权重: {}, LLM权重: {}",
            triageConfig.getRuleEngineWeight(), triageConfig.getLlmWeight());
        log.info("启用急诊检测: {}", triageConfig.isEnableEmergencyDetection());
        log.info("启用统计收集: {}", triageConfig.isEnableStats());

        return true;
    }
}