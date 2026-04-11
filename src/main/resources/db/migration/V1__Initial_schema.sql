-- IMKQAS 医疗知识问答系统数据库初始迁移脚本
-- 版本: 1.0
-- 日期: 2026-04-11
-- 说明: 创建初始数据库表结构和初始化数据

-- ==================== 会话设置 ====================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 用户表 ====================
-- 存储系统用户信息，包括管理员和普通用户
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    `phone` VARCHAR(20) UNIQUE NOT NULL COMMENT '手机号',
    `password` VARCHAR(255) COMMENT '密码（加密存储）',
    `role` ENUM('USER', 'ADMIN') DEFAULT 'USER' COMMENT '用户角色',
    `health_profile` JSON COMMENT 'Health profile JSON data',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    INDEX `idx_phone` (`phone`),
    INDEX `idx_username` (`username`),
    INDEX `idx_role` (`role`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='Users table';

-- ==================== 文档表 ====================
-- 存储上传的医学文档信息
CREATE TABLE IF NOT EXISTS `documents` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    `title` VARCHAR(255) NOT NULL COMMENT '文档标题',
    `category` VARCHAR(50) COMMENT 'Medical category',
    `file_path` VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    `chunk_count` INT DEFAULT 0 COMMENT '文档分块数量',
    `status` ENUM('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED') DEFAULT 'UPLOADED' COMMENT '文档处理状态',
    `uploaded_by` BIGINT NOT NULL COMMENT '上传者用户ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    FOREIGN KEY (`uploaded_by`) REFERENCES `users`(`id`) ON DELETE CASCADE,
    INDEX `idx_category` (`category`),
    INDEX `idx_status` (`status`),
    INDEX `idx_uploaded_by` (`uploaded_by`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_title` (`title`(100))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='Medical documents table';

-- ==================== 文档分块表 ====================
-- 存储文档的分块信息，用于向量化检索
CREATE TABLE IF NOT EXISTS `document_chunks` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分块ID',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `chunk_index` INT NOT NULL COMMENT '分块序号（从0开始）',
    `content` TEXT NOT NULL COMMENT '分块文本内容',
    `vector_id` VARCHAR(100) COMMENT 'Milvus向量ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    FOREIGN KEY (`document_id`) REFERENCES `documents`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_document_chunk` (`document_id`, `chunk_index`),
    INDEX `idx_document_id` (`document_id`),
    INDEX `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='Document chunks table';

-- ==================== 对话表 ====================
-- 存储用户与系统的对话会话
CREATE TABLE IF NOT EXISTS `conversations` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '对话ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(200) DEFAULT '新对话' COMMENT '对话标题',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='Conversations table';

-- ==================== 消息表 ====================
-- 存储对话中的消息记录
CREATE TABLE IF NOT EXISTS `messages` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` BIGINT NOT NULL COMMENT '对话ID',
    `role` ENUM('USER', 'ASSISTANT', 'SYSTEM') DEFAULT 'USER' COMMENT '消息角色',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `document_chunks` JSON COMMENT '引用的文档分块ID数组',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    FOREIGN KEY (`conversation_id`) REFERENCES `conversations`(`id`) ON DELETE CASCADE,
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_role` (`role`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='Messages table';

-- ==================== 验证码表 ====================
-- 存储手机验证码，用于登录验证
CREATE TABLE IF NOT EXISTS `verification_codes` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '验证码ID',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `code` VARCHAR(10) NOT NULL COMMENT '验证码',
    `type` ENUM('LOGIN', 'REGISTER', 'RESET_PASSWORD') DEFAULT 'LOGIN' COMMENT '验证码类型',
    `expires_at` TIMESTAMP NOT NULL COMMENT '过期时间',
    `used` TINYINT DEFAULT 0 COMMENT '是否已使用: 0-未使用, 1-已使用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_phone` (`phone`),
    INDEX `idx_expires_at` (`expires_at`),
    INDEX `idx_phone_type` (`phone`, `type`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='Verification codes table';

-- ==================== 系统配置表 ====================
-- 存储系统动态配置项
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    `config_key` VARCHAR(100) UNIQUE NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `config_type` ENUM('STRING', 'NUMBER', 'BOOLEAN', 'JSON', 'ARRAY') DEFAULT 'STRING' COMMENT '配置值类型',
    `description` VARCHAR(500) COMMENT '配置描述',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除, 1-已删除',
    INDEX `idx_config_key` (`config_key`),
    INDEX `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='System configuration table';

-- ==================== 初始化数据 ====================

-- 初始化管理员用户
-- 密码: admin123，使用MD5哈希存储: 0192023a7bbd73250516f069df18b500
-- 注意：系统支持MD5和BCrypt两种密码格式，新用户将使用BCrypt编码
INSERT IGNORE INTO `users` (`username`, `phone`, `password`, `role`, `health_profile`) VALUES
('admin', '13800000000', '0192023a7bbd73250516f069df18b500', 'ADMIN', NULL),
('testuser', '13900000000', '0192023a7bbd73250516f069df18b500', 'USER', '{"age": 30, "gender": "male", "allergies": ["青霉素"], "chronic_diseases": ["高血压"]}'),
('doctor_wang', '13700000000', '0192023a7bbd73250516f069df18b500', 'USER', '{"age": 45, "gender": "male", "allergies": [], "chronic_diseases": []}'),
('patient_li', '13600000000', '0192023a7bbd73250516f069df18b500', 'USER', '{"age": 65, "gender": "female", "allergies": ["青霉素", "头孢"], "chronic_diseases": ["糖尿病", "高血压", "冠心病"]}');

-- 初始化系统配置（示例）
INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `config_type`, `description`) VALUES
('system_name', 'IMKQAS医疗知识问答系统', 'STRING', '系统名称'),
('api_timeout', '30000', 'NUMBER', 'API超时时间（毫秒）'),
('max_upload_size', '10485760', 'NUMBER', '最大文件上传大小（字节）'),
('enable_registration', 'true', 'BOOLEAN', '是否开启用户注册'),
('milvus_collection_name', 'medical_documents', 'STRING', 'Milvus向量集合名称'),
('embedding_dimension', '768', 'NUMBER', '向量嵌入维度'),
('similarity_threshold', '0.75', 'NUMBER', '相似度阈值');

-- ==================== 外键检查恢复 ====================
SET FOREIGN_KEY_CHECKS = 1;