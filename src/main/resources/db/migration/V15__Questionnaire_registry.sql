-- IMKQAS 问卷注册表
-- 版本: 15.0
-- 日期: 2026-08-23
-- 说明: 问卷注册表承载匹配关键词与FHIR访问路径，实现匹配与动态加载解耦。
--       关键词归属注册表（运营在本地维护），问卷完整内容由FHIR资源承载。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 问卷注册表 ====================
CREATE TABLE IF NOT EXISTS `questionnaire_registry` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键ID',
    `questionnaire_id` VARCHAR(64) NOT NULL COMMENT '问卷唯一标识（如PHQ-9/GAD-7/ISI）',
    `title` VARCHAR(200) NOT NULL COMMENT '问卷标题',
    `category` VARCHAR(64) COMMENT '分类: mental_health/diabetes/sleep/pain/general',
    `keywords` JSON COMMENT '触发关键词列表（JSON数组，用于匹配用户输入）',
    `fhir_path` VARCHAR(255) COMMENT 'FHIR访问路径（如Questionnaire/PHQ-9）',
    `priority` INT DEFAULT 0 COMMENT '优先级（命中数相同时，值越小越优先）',
    `status` VARCHAR(16) DEFAULT 'active' COMMENT '状态: active/retired',
    `item_count` INT COMMENT '题目数',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_questionnaire_id` (`questionnaire_id`),
    INDEX `idx_category` (`category`),
    INDEX `idx_status` (`status`),
    INDEX `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='问卷注册表';

-- ==================== 初始数据 ====================
INSERT INTO `questionnaire_registry`
    (`questionnaire_id`, `title`, `category`, `keywords`, `fhir_path`, `priority`, `status`, `item_count`)
VALUES
    ('PHQ-9', 'PHQ-9 抑郁症筛查量表', 'mental_health',
     '["情绪低落","抑郁","不开心","没兴趣","沮丧","提不起精神","不想动","心情差","消沉","快乐不起来"]',
     'Questionnaire/PHQ-9', 0, 'active', 9),
    ('GAD-7', 'GAD-7 广泛性焦虑障碍量表', 'mental_health',
     '["焦虑","紧张","担心","不安","心慌","坐立不安","烦躁","害怕","恐慌","惴惴不安"]',
     'Questionnaire/GAD-7', 1, 'active', 7),
    ('ISI', 'ISI 失眠严重指数量表', 'sleep',
     '["失眠","睡不着","睡不好","入睡困难","早醒","多梦","睡眠差","熬夜","半夜醒"]',
     'Questionnaire/ISI', 2, 'active', 7);

SET FOREIGN_KEY_CHECKS = 1;
