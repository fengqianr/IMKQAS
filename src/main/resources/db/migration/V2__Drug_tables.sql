-- IMKQAS 药品知识库数据库迁移脚本
-- 版本: 2.0
-- 日期: 2026-04-16
-- 说明: 创建药品知识库相关表结构，支持药物查询和相互作用检查

-- ==================== 会话设置 ====================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 药品信息表 ====================
-- 存储药品基本信息，用于药物查询功能
CREATE TABLE IF NOT EXISTS `drugs` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '药品ID',
    `generic_name` VARCHAR(200) NOT NULL COMMENT '药品通用名',
    `brand_name` VARCHAR(200) COMMENT '商品名',
    `english_name` VARCHAR(200) COMMENT '英文名',
    `drug_class` VARCHAR(100) COMMENT '药品分类（药理分类）',
    `dosage_form` VARCHAR(100) COMMENT '剂型（片剂、胶囊、注射液等）',
    `specification` VARCHAR(200) COMMENT '规格（如：0.5g*20片）',
    `manufacturer` VARCHAR(200) COMMENT '生产厂家',

    -- 主要信息字段
    `indications` JSON COMMENT '适应症（JSON数组）',
    `contraindications` JSON COMMENT '禁忌症（JSON数组）',
    `adverse_reactions` JSON COMMENT '不良反应（JSON数组）',
    `dosage` TEXT COMMENT '用法用量',
    `precautions` TEXT COMMENT '注意事项',
    `storage` VARCHAR(500) COMMENT '贮藏条件',
    `approval_number` VARCHAR(100) COMMENT '批准文号',

    -- 药物相互作用标记
    `has_interactions` TINYINT DEFAULT 0 COMMENT '是否有相互作用信息: 0-无, 1-有',

    -- 系统字段
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',

    INDEX `idx_generic_name` (`generic_name`),
    INDEX `idx_brand_name` (`brand_name`),
    INDEX `idx_drug_class` (`drug_class`),
    INDEX `idx_manufacturer` (`manufacturer`),
    INDEX `idx_has_interactions` (`has_interactions`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='药品信息表';

-- ==================== 药物相互作用表 ====================
-- 存储药品之间的相互作用信息
CREATE TABLE IF NOT EXISTS `drug_interactions` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '相互作用ID',
    `drug_a_id` BIGINT NOT NULL COMMENT '药品A ID',
    `drug_b_id` BIGINT NOT NULL COMMENT '药品B ID',
    `interaction_type` ENUM('CONTRAINDICATED', 'SEVERE', 'MODERATE', 'MILD', 'MONITOR', 'UNKNOWN') DEFAULT 'UNKNOWN' COMMENT '相互作用类型',
    `severity` ENUM('HIGH', 'MODERATE', 'LOW') DEFAULT 'MODERATE' COMMENT '严重程度',
    `description` TEXT COMMENT '相互作用描述',
    `mechanism` VARCHAR(500) COMMENT '作用机制',
    `recommendation` TEXT COMMENT '用药建议',

    -- 系统字段
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',

    FOREIGN KEY (`drug_a_id`) REFERENCES `drugs`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`drug_b_id`) REFERENCES `drugs`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_drug_interaction` (`drug_a_id`, `drug_b_id`),
    INDEX `idx_drug_a_id` (`drug_a_id`),
    INDEX `idx_drug_b_id` (`drug_b_id`),
    INDEX `idx_interaction_type` (`interaction_type`),
    INDEX `idx_severity` (`severity`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='药物相互作用表';

-- ==================== 药品别名表 ====================
-- 存储药品的别名、商品名、俗称等，便于查询
CREATE TABLE IF NOT EXISTS `drug_aliases` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '别名ID',
    `drug_id` BIGINT NOT NULL COMMENT '药品ID',
    `alias_type` ENUM('BRAND_NAME', 'COMMON_NAME', 'ABBREVIATION', 'TRADE_NAME', 'OTHER') DEFAULT 'OTHER' COMMENT '别名类型',
    `alias_name` VARCHAR(200) NOT NULL COMMENT '别名名称',

    -- 系统字段
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',

    FOREIGN KEY (`drug_id`) REFERENCES `drugs`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_drug_alias` (`drug_id`, `alias_name`),
    INDEX `idx_drug_id` (`drug_id`),
    INDEX `idx_alias_name` (`alias_name`),
    INDEX `idx_alias_type` (`alias_type`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='药品别名表';

-- ==================== 初始化药品数据（示例） ====================
-- 插入常见药品数据，用于开发和测试

-- 药品1: 阿司匹林
INSERT IGNORE INTO `drugs` (`generic_name`, `brand_name`, `drug_class`, `dosage_form`, `specification`, `manufacturer`, `indications`, `contraindications`, `adverse_reactions`, `dosage`, `precautions`, `storage`, `approval_number`, `has_interactions`) VALUES
('阿司匹林', '拜阿司匹灵', '非甾体抗炎药', '肠溶片', '100mg*30片', '拜耳医药保健有限公司',
 '["缓解轻度或中度疼痛，如头痛、牙痛、月经痛等", "用于感冒、流感等发热疾病的退热", "抗炎、抗风湿", "抗血栓"]',
 '["对阿司匹林或其他非甾体抗炎药过敏者禁用", "活动性消化道溃疡患者禁用", "严重肝肾功能不全者禁用", "妊娠期最后三个月禁用"]',
 '["胃肠道反应：恶心、呕吐、上腹部不适等", "过敏反应：皮疹、哮喘等", "肝肾功能损害", "出血倾向"]',
 '成人常用量：一次0.3～0.6g，一日3次。抗风湿：一日3～6g，分4次口服。\n儿童遵医嘱。',
 '1. 应与食物同服或用水冲服，以减少胃肠道刺激。\n2. 长期大量用药时应定期检查血象及肝肾功能。\n3. 年老体弱或体温在40℃以上者，解热时宜用小量。',
 '密封，在干燥处保存。',
 '国药准字H20065051',
 1);

-- 获取阿司匹林的ID
SET @aspirin_id = LAST_INSERT_ID();

-- 阿司匹林别名
INSERT IGNORE INTO `drug_aliases` (`drug_id`, `alias_type`, `alias_name`) VALUES
(@aspirin_id, 'BRAND_NAME', '拜阿司匹灵'),
(@aspirin_id, 'COMMON_NAME', '乙酰水杨酸'),
(@aspirin_id, 'ABBREVIATION', 'ASA');

-- 药品2: 青霉素
INSERT IGNORE INTO `drugs` (`generic_name`, `brand_name`, `drug_class`, `dosage_form`, `specification`, `manufacturer`, `indications`, `contraindications`, `adverse_reactions`, `dosage`, `precautions`, `storage`, `approval_number`, `has_interactions`) VALUES
('青霉素', '青霉素钠', 'β-内酰胺类抗生素', '注射用粉针', '80万单位*10支', '华北制药股份有限公司',
 '["敏感菌所致感染：如扁桃体炎、肺炎、猩红热、丹毒等", "梅毒", "钩端螺旋体病", "回归热"]',
 '["对青霉素类药物过敏者禁用", "有哮喘、湿疹、花粉症等过敏性疾病史者慎用"]',
 '["过敏反应：皮疹、药物热、过敏性休克等", "毒性反应：大剂量可致抽搐、昏迷等", "二重感染", "赫氏反应"]',
 '肌内注射：成人一日80万～200万单位，分3～4次给药；小儿每日按体重2.5万～5万单位/kg，分3～4次给药。\n静脉滴注：成人一日200万～2000万单位，分2～4次给药；小儿每日按体重5万～20万单位/kg，分2～4次给药。',
 '1. 用药前必须做青霉素皮肤试验，阳性反应者禁用。\n2. 交叉过敏反应：对一种青霉素过敏者可能对其他青霉素类也过敏。\n3. 肾功能减退者应调整剂量。',
 '密闭，在干燥处保存。',
 '国药准字H13020665',
 1);

-- 获取青霉素的ID
SET @penicillin_id = LAST_INSERT_ID();

-- 青霉素别名
INSERT IGNORE INTO `drug_aliases` (`drug_id`, `alias_type`, `alias_name`) VALUES
(@penicillin_id, 'BRAND_NAME', '青霉素钠'),
(@penicillin_id, 'COMMON_NAME', '苄青霉素'),
(@penicillin_id, 'ABBREVIATION', 'PCN');

-- 药品3: 华法林
INSERT IGNORE INTO `drugs` (`generic_name`, `brand_name`, `drug_class`, `dosage_form`, `specification`, `manufacturer`, `indications`, `contraindications`, `adverse_reactions`, `dosage`, `precautions`, `storage`, `approval_number`, `has_interactions`) VALUES
('华法林', '华法林钠', '抗凝血药', '片剂', '3mg*100片', '上海信谊药厂有限公司',
 '["预防和治疗血栓栓塞性疾病", "心房颤动伴血栓栓塞的预防", "心脏瓣膜置换术后抗凝"]',
 '["严重肝肾功能不全者禁用", "活动性出血或出血倾向者禁用", "妊娠期禁用"]',
 '["出血：皮肤黏膜出血、血尿、消化道出血等", "皮肤坏死", "紫趾综合征", "肝功能损害"]',
 '成人常用量：口服，第一日2.5～5mg，次日起用维持量，一日2.5～5mg。\n应根据凝血酶原时间（PT）或国际标准化比值（INR）调整剂量。',
 '1. 治疗期间应定期监测INR值，维持在2.0～3.0。\n2. 许多药物和食物可影响华法林的抗凝作用，使用时应特别注意。\n3. 老年患者剂量应适当减少。',
 '密封，在阴凉干燥处保存。',
 '国药准字H31022123',
 1);

-- 获取华法林的ID
SET @warfarin_id = LAST_INSERT_ID();

-- 华法林别名
INSERT IGNORE INTO `drug_aliases` (`drug_id`, `alias_type`, `alias_name`) VALUES
(@warfarin_id, 'BRAND_NAME', '华法林钠'),
(@warfarin_id, 'COMMON_NAME', '苄丙酮香豆素'),
(@warfarin_id, 'ABBREVIATION', 'WAR');

-- ==================== 初始化药物相互作用数据 ====================
-- 阿司匹林 + 华法林：增加出血风险
INSERT IGNORE INTO `drug_interactions` (`drug_a_id`, `drug_b_id`, `interaction_type`, `severity`, `description`, `mechanism`, `recommendation`) VALUES
(@aspirin_id, @warfarin_id, 'SEVERE', 'HIGH', '阿司匹林可增强华法林的抗凝作用，显著增加出血风险。', '阿司匹林抑制血小板聚集，与华法林协同增强抗凝效果。', '避免联合使用。如必须使用，应密切监测INR值，并考虑降低华法林剂量。');

-- 青霉素 + 华法林：可能增强抗凝作用
INSERT IGNORE INTO `drug_interactions` (`drug_a_id`, `drug_b_id`, `interaction_type`, `severity`, `description`, `mechanism`, `recommendation`) VALUES
(@penicillin_id, @warfarin_id, 'MODERATE', 'MODERATE', '大剂量青霉素可能增强华法林的抗凝作用，增加出血风险。', '青霉素可能影响肠道菌群，减少维生素K的合成，从而增强华法林作用。', '联合使用时监测INR值，必要时调整华法林剂量。');

-- ==================== 外键检查恢复 ====================
SET FOREIGN_KEY_CHECKS = 1;