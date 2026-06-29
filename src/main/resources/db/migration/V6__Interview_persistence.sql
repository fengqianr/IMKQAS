-- IMKQAS 访谈问卷采集持久化表
-- 版本: 6.0
-- 日期: 2026-06-29
-- 说明: 将LLM驱动的FHIR问卷采集对话持久化到MySQL，配合Redis实现热冷数据分层

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 1. 访谈会话持久化表 ====================
CREATE TABLE IF NOT EXISTS `interview_sessions` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键',
    `session_id` VARCHAR(50) NOT NULL COMMENT '会话唯一标识（12位UUID）',
    `questionnaire_id` VARCHAR(200) NOT NULL COMMENT '问卷标识（如PHQ-9/GAD-7/ISI）',
    `questionnaire_title` VARCHAR(200) COMMENT '问卷标题',
    `user_id` BIGINT COMMENT '用户ID',
    `conversation_id` BIGINT COMMENT '关联对话ID（conversations表）',
    `current_question_index` INT DEFAULT 0 COMMENT '当前题号（从0开始）',
    `total_questions` INT DEFAULT 0 COMMENT '总题数',
    `answers` JSON COMMENT '已收集答案: {linkId: answerCode}',
    `current_score` INT DEFAULT 0 COMMENT '当前累计总分',
    `completed` TINYINT DEFAULT 0 COMMENT '是否已完成: 0-进行中, 1-已完成',
    `collection_mode` VARCHAR(20) DEFAULT 'manual_form' COMMENT '采集模式: llm_driven/manual_form',
    `degradation_level` VARCHAR(20) DEFAULT 'llm' COMMENT '降级层级: llm/rule_parser/manual_form',
    `raw_inputs` JSON COMMENT '用户原始自然语言表述: {linkId: rawText}',
    `safety_flags` JSON COMMENT '已触发的安全标记列表',
    `context_summary` TEXT COMMENT '增量上下文摘要',
    `provenance` JSON COMMENT '溯源信息: {linkId: {source, confidence}}',
    `consecutive_failures` INT DEFAULT 0 COMMENT '连续LLM失败次数（断路器用）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `last_active_at` TIMESTAMP COMMENT '最后活动时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_session_id` (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_questionnaire_id` (`questionnaire_id`),
    INDEX `idx_completed` (`completed`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='访谈会话持久化表';

-- ==================== 2. 访谈对话消息持久化表 ====================
CREATE TABLE IF NOT EXISTS `interview_messages` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键',
    `conversation_id` BIGINT COMMENT '关联对话ID',
    `session_id` VARCHAR(50) NOT NULL COMMENT '关联会话ID',
    `user_id` BIGINT COMMENT '用户ID',
    `message_type` VARCHAR(30) NOT NULL COMMENT '消息类型: suggestion/question/completion/safety_alert/progress/clarify',
    `sequence_num` INT DEFAULT 0 COMMENT '同一会话内的消息序号',
    `questionnaire_id` VARCHAR(200) COMMENT '问卷标识',
    `questionnaire_title` VARCHAR(200) COMMENT '问卷标题',
    `message_data` JSON NOT NULL COMMENT '完整消息数据（与SSE事件格式一致的JSON）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_message_type` (`message_type`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='访谈对话消息持久化表';

-- ==================== 3. AI分析报告持久化表 ====================
CREATE TABLE IF NOT EXISTS `analysis_reports` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '本地主键',
    `analysis_id` VARCHAR(100) NOT NULL COMMENT '分析唯一标识（analysis-{sessionId}）',
    `session_id` VARCHAR(50) NOT NULL COMMENT '关联会话ID',
    `questionnaire_id` VARCHAR(200) COMMENT '问卷标识',
    `questionnaire_title` VARCHAR(200) COMMENT '问卷标题',
    `user_id` BIGINT COMMENT '用户ID',
    `conversation_id` BIGINT COMMENT '关联对话ID',
    `total_score` INT COMMENT '问卷总分',
    `max_score` INT COMMENT '问卷满分',
    `severity` VARCHAR(50) COMMENT '严重程度等级（如: 轻度/中度/重度）',
    `interpretation` VARCHAR(200) COMMENT '评分解释（临床意义说明）',
    `summary` VARCHAR(500) COMMENT '友好评估摘要（200字以内）',
    `risk_assessment` JSON COMMENT '风险评估: {level, description, requiresUrgentAttention}',
    `detail_analysis` JSON COMMENT '详细分析: {overview, patterns[], conclusion}',
    `recommendations` JSON COMMENT '分层建议: {immediate[], shortTerm[], professional[]}',
    `follow_up` JSON COMMENT '随访建议: {suggestedDate, rationale}',
    `disclaimer` VARCHAR(500) COMMENT '免责声明',
    `latency_ms` BIGINT COMMENT '分析耗时（毫秒）',
    `raw_llm_response` MEDIUMTEXT COMMENT '原始LLM响应（仅用于排查）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY `uk_analysis_id` (`analysis_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_questionnaire_id` (`questionnaire_id`),
    INDEX `idx_severity` (`severity`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='AI分析报告持久化表';

SET FOREIGN_KEY_CHECKS = 1;
