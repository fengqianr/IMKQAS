-- IMKQAS 问卷回答缓存表补充 session_id
-- 版本: 11.0
-- 日期: 2026-08-13
-- 说明: 为 fhir_questionnaire_response_cache 表补充 session_id 列，
--       用于 history 接口返回会话ID，支持前端「查看详情」跳转在线分析报告。
--       历史数据为 NULL，前端需做容错处理（无 sessionId 时不显示详情入口）。

SET NAMES utf8mb4;

ALTER TABLE `fhir_questionnaire_response_cache`
    ADD COLUMN `session_id` VARCHAR(64) NULL COMMENT '访谈会话ID（InterviewSession.sessionId）' AFTER `conversation_id`;

-- 为 session_id 添加索引，便于按会话查询分析报告
ALTER TABLE `fhir_questionnaire_response_cache`
    ADD INDEX `idx_session_id` (`session_id`);
