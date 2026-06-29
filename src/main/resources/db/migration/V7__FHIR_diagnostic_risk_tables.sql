-- IMKQAS FHIR DiagnosticReport & RiskAssessment 缓存表
-- 版本: 7.0
-- 日期: 2026-06-29
-- 说明: 持久化访谈过程中生成的FHIR DiagnosticReport和RiskAssessment资源

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 1. FHIR 诊断报告资源缓存表 ====================
CREATE TABLE IF NOT EXISTS `fhir_diagnostic_report_cache` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键ID',
    `fhir_id` VARCHAR(100) NOT NULL COMMENT 'FHIR资源逻辑ID (DiagnosticReport.id)',
    `patient_fhir_id` VARCHAR(100) COMMENT '关联Patient的FHIR ID',
    `session_id` VARCHAR(50) NOT NULL COMMENT '关联访谈会话ID',
    `questionnaire_response_ref` VARCHAR(200) COMMENT '引用的QuestionnaireResponse引用',
    `status` VARCHAR(20) DEFAULT 'final' COMMENT '状态: registered/partial/preliminary/final',
    `code_system` VARCHAR(200) DEFAULT 'http://loinc.org' COMMENT '报告代码体系（如LOINC）',
    `code_value` VARCHAR(100) DEFAULT '86971-7' COMMENT '报告代码值',
    `code_display` VARCHAR(200) DEFAULT '心理健康评估报告' COMMENT '报告显示名称',
    `conclusion` TEXT COMMENT '诊断结论（来自AnalysisResult.summary）',
    `issued_date` TIMESTAMP COMMENT '报告发布时间',
    `resource_json` JSON NOT NULL COMMENT '完整FHIR DiagnosticReport JSON',
    `local_user_id` BIGINT COMMENT '关联本地users表ID',
    `conversation_id` BIGINT COMMENT '关联对话ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_fhir_id` (`fhir_id`),
    INDEX `idx_patient_fhir_id` (`patient_fhir_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_issued_date` (`issued_date`),
    INDEX `idx_local_user_id` (`local_user_id`),
    INDEX `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='FHIR诊断报告资源缓存表';

-- ==================== 2. FHIR 风险评估资源缓存表 ====================
CREATE TABLE IF NOT EXISTS `fhir_risk_assessment_cache` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键ID',
    `fhir_id` VARCHAR(100) NOT NULL COMMENT 'FHIR资源逻辑ID (RiskAssessment.id)',
    `patient_fhir_id` VARCHAR(100) COMMENT '关联Patient的FHIR ID',
    `session_id` VARCHAR(50) NOT NULL COMMENT '关联访谈会话ID',
    `questionnaire_response_ref` VARCHAR(200) COMMENT '引用的QuestionnaireResponse引用',
    `status` VARCHAR(20) DEFAULT 'final' COMMENT '状态: registered/preliminary/final',
    `occurrence_date` TIMESTAMP COMMENT '评估时间',
    `risk_level` VARCHAR(50) COMMENT '风险等级（如: 低/中/高/危急）',
    `risk_description` TEXT COMMENT '风险描述',
    `requires_urgent_attention` TINYINT DEFAULT 0 COMMENT '是否需要紧急关注: 0-否, 1-是',
    `resource_json` JSON NOT NULL COMMENT '完整FHIR RiskAssessment JSON',
    `local_user_id` BIGINT COMMENT '关联本地users表ID',
    `conversation_id` BIGINT COMMENT '关联对话ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_fhir_id` (`fhir_id`),
    INDEX `idx_patient_fhir_id` (`patient_fhir_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_occurrence_date` (`occurrence_date`),
    INDEX `idx_risk_level` (`risk_level`),
    INDEX `idx_local_user_id` (`local_user_id`),
    INDEX `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='FHIR风险评估资源缓存表';

SET FOREIGN_KEY_CHECKS = 1;
