package com.student.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 评估系统配置类
 * 集中管理离线评估和在线监控的参数
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "imkqas.evaluation")
@Data
@Validated
@Slf4j
public class EvaluationConfig {

    /** 评估系统总开关 */
    private boolean enabled = true;

    /** 离线评估配置 */
    private OfflineConfig offline = new OfflineConfig();

    /** 在线监控配置 */
    private OnlineConfig online = new OnlineConfig();

    /** 报告配置 */
    private ReportConfig report = new ReportConfig();

    // ========== 离线评估配置 ==========

    @Data
    public static class OfflineConfig {
        /** 最大并发评估数 */
        @Min(1)
        private int maxConcurrency = 4;

        /** 默认评估K值列表 */
        private List<Integer> defaultKValues = List.of(1, 5, 10, 20, 30);

        /** LLM评判配置 */
        private LlmJudgeConfig llmJudge = new LlmJudgeConfig();
    }

    @Data
    public static class LlmJudgeConfig {
        /** 是否启用LLM-as-Judge */
        private boolean enabled = true;

        /** 评判模型 */
        @NotBlank
        private String model = "qwen-plus";

        /** 最大重试次数 */
        @Min(1)
        private int maxRetries = 3;

        /** 超时时间（秒） */
        @Min(5)
        private int timeout = 30;

        /** 是否缓存LLM评判结果 */
        private boolean cacheEnabled = true;
    }

    // ========== 在线监控配置 ==========

    @Data
    public static class OnlineConfig {
        /** 是否启用在线指标采集 */
        private boolean enabled = true;

        /** 指标采集间隔（秒） */
        @Min(10)
        private int collectionInterval = 60;

        /** 指标数据保留天数 */
        @Min(1)
        private int retentionDays = 30;

        /** 是否启用P95/P99百分位计算 */
        private boolean percentileEnabled = true;

        /** 采样窗口大小（用于百分位计算） */
        @Min(10)
        private int percentileWindowSize = 100;

        /** 未映射术语率告警阈值 */
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        private double unmappedTermAlertThreshold = 5.0;

        /** 慢查询阈值（毫秒），超过此值记录警告 */
        @Min(100)
        private int slowQueryThresholdMs = 5000;

        /** 错误率告警阈值（%） */
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        private double errorRateAlertThreshold = 5.0;
    }

    // ========== 报告配置 ==========

    @Data
    public static class ReportConfig {
        /** 报告输出目录 */
        private String outputDir = "./evaluation-reports";

        /** 默认报告格式 */
        private String defaultFormat = "BOTH";

        /** 报告保留天数 */
        @Min(1)
        private int retentionDays = 90;

        /** 是否自动生成基线报告 */
        private boolean autoBaseline = true;
    }

    /** 报告格式常量 */
    public static final String FORMAT_CONSOLE = "CONSOLE";
    public static final String FORMAT_JSON = "JSON";
    public static final String FORMAT_HTML = "HTML";
    public static final String FORMAT_BOTH = "BOTH";

    @PostConstruct
    public void init() {
        log.info("评估系统配置初始化完成");
        log.info("离线评估: 并发数={}, LLM评判={}",
                offline.getMaxConcurrency(), offline.getLlmJudge().isEnabled() ? "启用" : "禁用");
        log.info("在线监控: 采集间隔={}s, 保留={}天, 慢查询阈值={}ms",
                online.getCollectionInterval(), online.getRetentionDays(), online.getSlowQueryThresholdMs());
        log.info("报告: 输出目录={}, 格式={}", report.getOutputDir(), report.getDefaultFormat());
    }
}
