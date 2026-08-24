-- IMKQAS 消息检索路径持久化
-- 版本: 14.0
-- 说明: messages 表新增 retrieval_path 列，用于持久化 Agent 工具调用过程 / RAG 检索路径，
--       使对话历史重载后仍可还原「知识检索路径」可视化。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `messages`
    ADD COLUMN `retrieval_path` JSON NULL COMMENT '检索路径/Agent工具调用过程JSON' AFTER `source_references`;

SET FOREIGN_KEY_CHECKS = 1;
