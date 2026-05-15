package com.student.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * 医疗安全兜底配置类
 * 配置急症关键词检测、检索置信度门控、回答安全校验等参数
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "imkqas.rag.safety")
@Data
@Validated
public class SafetyConfig {

    /** 总开关 */
    private boolean enabled = true;

    /** 急症预检配置 */
    private EmergencyConfig emergency = new EmergencyConfig();

    /** 置信度门控配置 */
    private ConfidenceConfig confidence = new ConfidenceConfig();

    /** 回答安全校验配置 */
    private AnswerSafetyConfig answerSafety = new AnswerSafetyConfig();

    // ========== 急症预检配置 ==========

    @Data
    public static class EmergencyConfig {
        /** 是否启用急症检测 */
        private boolean enabled = true;

        /**
         * 关键词分级库
         * 结构: { "CRITICAL": ["胸痛剧烈", ...], "HIGH": [...], "MEDIUM": [...] }
         */
        private Map<String, List<String>> keywords = Map.of();

        /**
         * 各级别回复模板
         * 结构: { "CRITICAL": "...", "HIGH": "...", "MEDIUM": "..." }
         */
        private Map<String, String> responseTemplates = Map.of();

        /** 是否启用整句匹配（关键词作为整体出现在 query 中） */
        private boolean matchFullSentence = true;
    }

    // ========== 置信度门控配置 ==========

    @Data
    public static class ConfidenceConfig {
        /** 是否启用置信度门控 */
        private boolean enabled = true;

        /** 硬阻断阈值：低于此值不调用 LLM，直接返回"无法找到可靠信息" */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minThreshold = 0.35;

        /** 警告阈值：低于此值 LLM 正常生成，但添加医疗免责声明 */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double warningThreshold = 0.6;

        /** 检索结果为空时的响应文本 */
        private String noRetrievalResponse = "抱歉，我无法从现有医学资料库中找到与您问题相关的信息。医疗建议需要基于确切的医学知识，建议您咨询专业医生获取准确的诊断和治疗方案。";

        /** 低于 minThreshold 时的响应文本 */
        private String lowConfidenceResponse = "抱歉，当前检索到的医学资料与您问题的相关性较低，无法生成可靠的回答。为了您的健康安全，建议您咨询专业医生获取准确信息。";
    }

    // ========== 回答安全校验配置 ==========

    @Data
    public static class AnswerSafetyConfig {
        /** 是否启用回答安全校验 */
        private boolean enabled = true;

        /** LLM 回答中禁止出现的模式（正则表达式） */
        private List<String> blockedPatterns = List.of();

        /** 命中 blockedPatterns 时的替换文本 */
        private String replacementText = "[请遵医嘱用药]";

        /** 免责声明模板（添加到所有回答末尾） */
        private String disclaimer = "\n\n---\n*免责声明：以上信息仅供参考，不能替代专业医疗建议。如有身体不适，请及时前往医疗机构就诊。*";

        /** 免责声明添加的置信度上限（置信度低于此值时添加） */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double disclaimerMaxConfidence = 0.8;
    }
}
