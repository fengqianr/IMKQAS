package com.student.service.triage.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.student.model.triage.EmergencyLevel;

import lombok.Data;

/**
 * 科室导诊引擎配置类
 * 管理分流引擎的所有参数和阈值配置
 *
 * @author 系统生成
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "imkqas.triage.engine")
public class TriageConfig {

    // ========== 顺序执行配置 ==========

    /**
     * 规则引擎置信度阈值
     * 当规则引擎置信度低于此值时触发LLM分析
     */
    private double ruleEngineThreshold = 0.6;

    /**
     * LLM调用超时时间（毫秒）
     */
    private int llmTimeout = 3000;

    // ========== 结果融合权重配置 ==========

    /**
     * 规则引擎结果权重
     */
    private double ruleEngineWeight = 0.8;

    /**
     * LLM结果权重
     */
    private double llmWeight = 0.2;

    // ========== 规则引擎配置 ==========

    /**
     * 是否启用模糊匹配
     */
    private boolean enableFuzzyMatch = true;

    /**
     * 模糊匹配阈值
     */
    private double fuzzyMatchThreshold = 0.7;

    /**
     * 最小匹配分数阈值
     */
    private double minMatchScore = 0.3;

    /**
     * 最大推荐科室数量
     */
    private int maxRecommendations = 3;

    // ========== LLM适配器配置 ==========

    /**
     * LLM分流提示词模板
     */
    private String llmTriagePromptTemplate = """
        你是一个医疗分诊专家。请根据以下症状描述，推荐最合适的就诊科室。
        症状：{symptoms}

        请按以下格式回复：
        1. 主要推荐科室：[科室名称]
        2. 置信度：[0.0-1.0之间的浮点数]
        3. 理由：[简要说明推荐理由]
        4. 备选科室：[科室1, 科室2...]（可选）
        5. 紧急程度：[CRITICAL/HIGH/MEDIUM/LOW]（可选）
        """;

    /**
     * 是否启用LLM缓存
     */
    private boolean enableLlmCache = true;

    /**
     * LLM缓存过期时间（秒）
     */
    private int llmCacheTtl = 3600;

    // ========== 急诊检测配置 ==========

    /**
     * 急诊症状检测阈值配置
     * key: 紧急级别，value: 最小匹配症状数量
     */
    private Map<EmergencyLevel, Integer> emergencyThresholds = Map.of(
        EmergencyLevel.CRITICAL, 3,
        EmergencyLevel.HIGH, 2,
        EmergencyLevel.MEDIUM, 1,
        EmergencyLevel.LOW, 0
    );

    /**
     * 是否启用急诊症状检测
     */
    private boolean enableEmergencyDetection = true;

    /**
     * 急诊检测超时时间（毫秒）
     */
    private int emergencyDetectionTimeout = 1000;

    // ========== 统计监控配置 ==========

    /**
     * 是否启用统计收集
     */
    private boolean enableStats = true;

    /**
     * 统计数据保留天数
     */
    private int statsRetentionDays = 30;

    /**
     * 统计采样率（0.0-1.0）
     */
    private double statsSamplingRate = 1.0;

    // ========== 性能配置 ==========

    /**
     * 线程池核心线程数
     */
    private int threadPoolCoreSize = 2;

    /**
     * 线程池最大线程数
     */
    private int threadPoolMaxSize = 4;

    /**
     * 线程池队列容量
     */
    private int threadPoolQueueCapacity = 100;

    /**
     * 线程池空闲线程存活时间（秒）
     */
    private int threadPoolKeepAliveSeconds = 60;

    /**
     * 批量处理最大大小
     */
    private int batchProcessingMaxSize = 20;

    /**
     * 批量处理超时时间（毫秒）
     */
    private int batchProcessingTimeout = 10000;

    // ========== 降级配置 ==========

    /**
     * 是否启用自动降级
     */
    private boolean enableAutoFallback = true;

    /**
     * LLM不可用时的降级模式
     */
    private String llmUnavailableFallback = "RULE_ENGINE";

    /**
     * 规则引擎不可用时的降级模式
     */
    private String ruleEngineUnavailableFallback = "FALLBACK";

    /**
     * 兜底建议文本
     */
    private String fallbackAdvice = "建议咨询医院导诊台或全科医学科";

    // ========== 验证方法 ==========

    /**
     * 验证配置有效性
     *
     * @return 配置是否有效
     */
    public boolean isValid() {
        if (ruleEngineThreshold < 0 || ruleEngineThreshold > 1) {
            return false;
        }
        if (llmTimeout <= 0) {
            return false;
        }
        if (ruleEngineWeight < 0 || ruleEngineWeight > 1) {
            return false;
        }
        if (llmWeight < 0 || llmWeight > 1) {
            return false;
        }
        if (Math.abs(ruleEngineWeight + llmWeight - 1.0) > 0.001) {
            return false;
        }
        return true;
    }

    /**
     * 获取规范化权重（确保权重和为1）
     */
    public void normalizeWeights() {
        double total = ruleEngineWeight + llmWeight;
        if (total != 0) {
            ruleEngineWeight = ruleEngineWeight / total;
            llmWeight = llmWeight / total;
        }
    }

    /**
     * 获取急诊检测阈值
     *
     * @param level 紧急级别
     * @return 对应的阈值
     */
    public int getEmergencyThreshold(EmergencyLevel level) {
        return emergencyThresholds.getOrDefault(level, 0);
    }
}