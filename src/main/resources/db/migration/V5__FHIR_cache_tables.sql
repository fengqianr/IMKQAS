-- IMKQAS FHIR 资源本地缓存表
-- 版本: 5.0
-- 日期: 2026-05-13
-- 说明: 预留 FHIR 接口，本地缓存 Patient/Observation/Condition/QuestionnaireResponse 资源，为后续 HIS 对接做准备

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 1. FHIR 患者资源缓存表 ====================
CREATE TABLE IF NOT EXISTS `fhir_patient_cache` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键ID',
    `fhir_id` VARCHAR(100) NOT NULL COMMENT 'FHIR资源逻辑ID (Patient.id)',
    `identifier_system` VARCHAR(200) COMMENT '身份证号/病历号等标识体系',
    `identifier_value` VARCHAR(100) COMMENT '标识值（如身份证号码）',
    `family_name` VARCHAR(100) COMMENT '姓',
    `given_name` VARCHAR(100) COMMENT '名',
    `gender` VARCHAR(20) COMMENT '性别: male/female/other/unknown',
    `birth_date` DATE COMMENT '出生日期',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `address_text` VARCHAR(500) COMMENT '地址文本',
    `marital_status` VARCHAR(50) COMMENT '婚姻状况',
    `resource_json` JSON NOT NULL COMMENT '完整FHIR Patient JSON',
    `version_id` VARCHAR(50) COMMENT 'FHIR资源版本号',
    `last_updated` TIMESTAMP COMMENT 'FHIR资源最后更新时间',
    `source` VARCHAR(50) DEFAULT 'LOCAL' COMMENT '数据来源: LOCAL/HIS_SYNC/MANUAL',
    `local_user_id` BIGINT COMMENT '关联本地users表ID（如有）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_fhir_id` (`fhir_id`),
    UNIQUE KEY `uk_identifier` (`identifier_system`, `identifier_value`),
    INDEX `idx_family_name` (`family_name`),
    INDEX `idx_given_name` (`given_name`),
    INDEX `idx_gender` (`gender`),
    INDEX `idx_birth_date` (`birth_date`),
    INDEX `idx_phone` (`phone`),
    INDEX `idx_local_user_id` (`local_user_id`),
    INDEX `idx_source` (`source`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='FHIR患者资源本地缓存表';

-- ==================== 2. FHIR 观察资源缓存表 ====================
CREATE TABLE IF NOT EXISTS `fhir_observation_cache` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键ID',
    `fhir_id` VARCHAR(100) NOT NULL COMMENT 'FHIR资源逻辑ID (Observation.id)',
    `patient_fhir_id` VARCHAR(100) NOT NULL COMMENT '关联Patient的FHIR ID',
    `code_system` VARCHAR(200) COMMENT '观察代码体系（如LOINC）',
    `code_value` VARCHAR(100) COMMENT '观察代码值（如8480-6表示收缩压）',
    `code_display` VARCHAR(200) COMMENT '观察项目显示名称（如"Systolic blood pressure"）',
    `category` VARCHAR(100) COMMENT '分类: vital-signs/laboratory/imaging/survey',
    `value_type` VARCHAR(50) COMMENT '值类型: Quantity/CodeableConcept/string/boolean',
    `value_quantity` DOUBLE COMMENT '数值型结果',
    `value_unit` VARCHAR(50) COMMENT '数值单位（如mmHg）',
    `value_string` VARCHAR(500) COMMENT '字符串型结果',
    `value_code` VARCHAR(100) COMMENT '编码型结果',
    `effective_date_time` TIMESTAMP COMMENT '观察有效时间',
    `status` VARCHAR(20) DEFAULT 'final' COMMENT '状态: registered/preliminary/final/amended',
    `resource_json` JSON NOT NULL COMMENT '完整FHIR Observation JSON',
    `version_id` VARCHAR(50) COMMENT 'FHIR资源版本号',
    `last_updated` TIMESTAMP COMMENT 'FHIR资源最后更新时间',
    `source` VARCHAR(50) DEFAULT 'LOCAL' COMMENT '数据来源: LOCAL/HIS_SYNC/MANUAL',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_fhir_id` (`fhir_id`),
    INDEX `idx_patient_fhir_id` (`patient_fhir_id`),
    INDEX `idx_code` (`code_system`, `code_value`),
    INDEX `idx_category` (`category`),
    INDEX `idx_effective_date` (`effective_date_time`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='FHIR观察资源本地缓存表';

-- ==================== 3. FHIR 病情资源缓存表 ====================
CREATE TABLE IF NOT EXISTS `fhir_condition_cache` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键ID',
    `fhir_id` VARCHAR(100) NOT NULL COMMENT 'FHIR资源逻辑ID (Condition.id)',
    `patient_fhir_id` VARCHAR(100) NOT NULL COMMENT '关联Patient的FHIR ID',
    `code_system` VARCHAR(200) COMMENT '诊断代码体系（如SNOMED CT/ICD-10）',
    `code_value` VARCHAR(100) COMMENT '诊断代码值',
    `code_display` VARCHAR(500) COMMENT '诊断名称',
    `category` VARCHAR(100) COMMENT '分类: problem-list-item/encounter-diagnosis/health-concern',
    `clinical_status` VARCHAR(50) COMMENT '临床状态: active/recurrence/relapse/inactive/remission/resolved',
    `verification_status` VARCHAR(50) COMMENT '验证状态: unconfirmed/provisional/differential/confirmed',
    `severity` VARCHAR(50) COMMENT '严重程度: mild/moderate/severe',
    `onset_date_time` TIMESTAMP COMMENT '发病时间',
    `abatement_date_time` TIMESTAMP COMMENT '缓解/痊愈时间',
    `recorded_date` TIMESTAMP COMMENT '记录时间',
    `resource_json` JSON NOT NULL COMMENT '完整FHIR Condition JSON',
    `version_id` VARCHAR(50) COMMENT 'FHIR资源版本号',
    `last_updated` TIMESTAMP COMMENT 'FHIR资源最后更新时间',
    `source` VARCHAR(50) DEFAULT 'LOCAL' COMMENT '数据来源: LOCAL/HIS_SYNC/MANUAL',
    `notes` TEXT COMMENT '备注',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_fhir_id` (`fhir_id`),
    INDEX `idx_patient_fhir_id` (`patient_fhir_id`),
    INDEX `idx_code` (`code_system`, `code_value`),
    INDEX `idx_category` (`category`),
    INDEX `idx_clinical_status` (`clinical_status`),
    INDEX `idx_onset_date` (`onset_date_time`),
    INDEX `idx_recorded_date` (`recorded_date`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='FHIR病情资源本地缓存表';

-- ==================== 4. FHIR 问卷回答资源缓存表 ====================
CREATE TABLE IF NOT EXISTS `fhir_questionnaire_response_cache` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键ID',
    `fhir_id` VARCHAR(100) NOT NULL COMMENT 'FHIR资源逻辑ID (QuestionnaireResponse.id)',
    `patient_fhir_id` VARCHAR(100) COMMENT '关联Patient的FHIR ID',
    `questionnaire_id` VARCHAR(200) COMMENT '问卷标识（如PHQ-9/GAD-7/ISI）',
    `questionnaire_title` VARCHAR(200) COMMENT '问卷标题',
    `status` VARCHAR(20) DEFAULT 'completed' COMMENT '状态: in-progress/completed/amended/stopped',
    `authored_date` TIMESTAMP COMMENT '填写时间',
    `total_score` DOUBLE COMMENT '问卷总分',
    `score_interpretation` VARCHAR(200) COMMENT '评分解释（如"中度抑郁"）',
    `item_count` INT COMMENT '问题数量',
    `answered_count` INT COMMENT '已回答数量',
    `resource_json` JSON NOT NULL COMMENT '完整FHIR QuestionnaireResponse JSON',
    `version_id` VARCHAR(50) COMMENT 'FHIR资源版本号',
    `last_updated` TIMESTAMP COMMENT 'FHIR资源最后更新时间',
    `source` VARCHAR(50) DEFAULT 'LOCAL' COMMENT '数据来源: LOCAL/HIS_SYNC/MANUAL',
    `local_user_id` BIGINT COMMENT '关联本地users表ID',
    `conversation_id` BIGINT COMMENT '关联对话ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_fhir_id` (`fhir_id`),
    INDEX `idx_patient_fhir_id` (`patient_fhir_id`),
    INDEX `idx_questionnaire_id` (`questionnaire_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_authored_date` (`authored_date`),
    INDEX `idx_local_user_id` (`local_user_id`),
    INDEX `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='FHIR问卷回答资源本地缓存表';

SET FOREIGN_KEY_CHECKS = 1;
