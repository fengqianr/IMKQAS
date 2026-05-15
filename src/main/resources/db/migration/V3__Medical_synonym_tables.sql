-- IMKQAS 医学同义词映射与未匹配词条审核队列
-- 版本: 3.0
-- 日期: 2026-05-10
-- 说明: 支持查询预处理中的同义词扩展、人工审核队列和监控统计

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 医学同义词映射表 ====================
-- 存储口语表达到标准医学术语的映射关系
-- 初始数据来源：医学书籍、历史问答日志、公开数据集（中文症状库）
CREATE TABLE IF NOT EXISTS `medical_synonym_mapping` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `colloquial_term` VARCHAR(200) NOT NULL COMMENT '口语化表达（如：扑热息痛）',
    `standard_term` VARCHAR(200) NOT NULL COMMENT '标准医学术语（如：对乙酰氨基酚）',
    `snomed_concept_id` VARCHAR(100) COMMENT 'SNOMED CT概念ID',
    `entity_type` VARCHAR(50) COMMENT '实体类型：DRUG/DISEASE/SYMPTOM/POPULATION/BODY_PART/EXAMINATION/TREATMENT',
    `source` VARCHAR(50) DEFAULT 'MANUAL' COMMENT '来源：MANUAL/HISTORY_LOG/PUBLIC_DATASET/LLM_INFERRED',
    `status` VARCHAR(20) DEFAULT 'APPROVED' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
    `reviewer` VARCHAR(100) COMMENT '审核人',
    `confidence` DOUBLE DEFAULT 1.0 COMMENT '置信度 0.0-1.0',
    `usage_count` INT DEFAULT 0 COMMENT '使用次数（用于统计热度）',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记',

    UNIQUE KEY `uk_colloquial_term` (`colloquial_term`),
    INDEX `idx_standard_term` (`standard_term`),
    INDEX `idx_entity_type` (`entity_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_source` (`source`),
    INDEX `idx_usage_count` (`usage_count`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='医学同义词映射表';

-- ==================== 未映射词条审核队列表 ====================
-- 存储SNOMED CT也无法匹配的口语表达，等待人工审核
CREATE TABLE IF NOT EXISTS `unmapped_term_queue` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `term` VARCHAR(200) NOT NULL COMMENT '未映射的词条',
    `context_query` TEXT COMMENT '出现的上下文（原始查询）',
    `guessed_entity_type` VARCHAR(50) COMMENT '推测的实体类型',
    `llm_guess` VARCHAR(200) COMMENT 'LLM临时推断结果',
    `llm_confidence` DOUBLE COMMENT 'LLM推断置信度',
    `occurrence_count` INT DEFAULT 1 COMMENT '出现次数',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED/AUTO_RESOLVED',
    `reviewer` VARCHAR(100) COMMENT '审核人',
    `review_note` TEXT COMMENT '审核备注',

    `first_seen_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '首次出现时间',
    `last_seen_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近出现时间',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记',

    UNIQUE KEY `uk_term_pending` (`term`, `status`),
    INDEX `idx_status` (`status`),
    INDEX `idx_occurrence_count` (`occurrence_count`),
    INDEX `idx_last_seen_at` (`last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='未映射词条审核队列表';

-- ==================== 同义词扩展统计表 ====================
-- 记录每日同义词扩展的命中率统计，用于监控告警
CREATE TABLE IF NOT EXISTS `synonym_expansion_stats` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `stat_date` DATE NOT NULL COMMENT '统计日期',
    `total_terms` INT DEFAULT 0 COMMENT '总处理词条数',
    `local_hits` INT DEFAULT 0 COMMENT '本地映射命中数',
    `snomed_hits` INT DEFAULT 0 COMMENT 'SNOMED CT命中数',
    `llm_hits` INT DEFAULT 0 COMMENT 'LLM临时推断命中数',
    `unmapped_count` INT DEFAULT 0 COMMENT '未映射数',
    `unmapped_rate` DOUBLE DEFAULT 0.0 COMMENT '未映射率(%)',
    `pending_review_count` INT DEFAULT 0 COMMENT '待审核词条数',
    `avg_processing_time_ms` DOUBLE DEFAULT 0.0 COMMENT '平均处理时间(ms)',
    `alert_triggered` TINYINT DEFAULT 0 COMMENT '是否触发告警',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY `uk_stat_date` (`stat_date`),
    INDEX `idx_unmapped_rate` (`unmapped_rate`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='同义词扩展统计表';

-- ==================== 初始化口语→标准术语映射数据 ====================
-- 常见药品口语映射
INSERT IGNORE INTO `medical_synonym_mapping` (`colloquial_term`, `standard_term`, `entity_type`, `source`, `status`, `confidence`) VALUES
('扑热息痛', '对乙酰氨基酚', 'DRUG', 'PUBLIC_DATASET', 'APPROVED', 1.0),
('必理通', '对乙酰氨基酚', 'DRUG', 'PUBLIC_DATASET', 'APPROVED', 0.95),
('芬必得', '布洛芬', 'DRUG', 'PUBLIC_DATASET', 'APPROVED', 1.0),
('消心痛', '硝酸异山梨酯', 'DRUG', 'PUBLIC_DATASET', 'APPROVED', 0.95),
('心得安', '普萘洛尔', 'DRUG', 'PUBLIC_DATASET', 'APPROVED', 0.95),
('降糖灵', '苯乙双胍', 'DRUG', 'PUBLIC_DATASET', 'APPROVED', 0.9),
('降压灵', '利血平', 'DRUG', 'PUBLIC_DATASET', 'APPROVED', 0.9),
('先锋霉素', '头孢菌素', 'DRUG', 'PUBLIC_DATASET', 'APPROVED', 0.9),

-- 常见症状口语映射
('脑袋疼', '头痛', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 1.0),
('肚子疼', '腹痛', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 1.0),
('拉肚子', '腹泻', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 1.0),
('跑肚', '腹泻', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 0.95),
('心慌', '心悸', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 1.0),
('心口疼', '胸痛', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 0.95),
('喘不上气', '呼吸困难', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 1.0),
('气短', '呼吸困难', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 0.95),
('睡不着', '失眠', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 1.0),
('没胃口', '食欲不振', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 1.0),
('发高烧', '高热', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 0.95),
('干咳', '咳嗽', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 0.9),
('咳痰', '咳嗽', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 0.9),
('打喷嚏', '喷嚏', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 0.9),
('流鼻涕', '鼻漏', 'SYMPTOM', 'HISTORY_LOG', 'APPROVED', 0.9),

-- 常见疾病口语映射
('伤风', '上呼吸道感染', 'DISEASE', 'PUBLIC_DATASET', 'APPROVED', 0.95),
('消渴症', '糖尿病', 'DISEASE', 'PUBLIC_DATASET', 'APPROVED', 0.9),
('血压高', '高血压', 'DISEASE', 'PUBLIC_DATASET', 'APPROVED', 0.95),
('血糖高', '糖尿病', 'DISEASE', 'HISTORY_LOG', 'APPROVED', 0.85),
('血稠', '高脂血症', 'DISEASE', 'HISTORY_LOG', 'APPROVED', 0.85),
('中风', '脑卒中', 'DISEASE', 'PUBLIC_DATASET', 'APPROVED', 1.0),
('半身不遂', '偏瘫', 'DISEASE', 'PUBLIC_DATASET', 'APPROVED', 0.95),
('冠心病', '冠状动脉粥样硬化性心脏病', 'DISEASE', 'PUBLIC_DATASET', 'APPROVED', 1.0),
('心梗', '心肌梗死', 'DISEASE', 'HISTORY_LOG', 'APPROVED', 1.0),
('甲亢', '甲状腺功能亢进症', 'DISEASE', 'PUBLIC_DATASET', 'APPROVED', 1.0),
('甲减', '甲状腺功能减退症', 'DISEASE', 'PUBLIC_DATASET', 'APPROVED', 1.0);

SET FOREIGN_KEY_CHECKS = 1;
