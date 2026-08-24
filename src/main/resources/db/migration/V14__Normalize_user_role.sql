-- IMKQAS users 表角色列规范化
-- 版本: 14.0
-- 说明: 初始 V1 将 role 定义为 ENUM('USER','ADMIN')，而 Java User.Role 枚举已扩展为
--       PATIENT/STUDENT/NURSE/DOCTOR/HEALTH_MANAGER/ADMIN。角色注册（PATIENT 等）
--       在旧 ENUM 列上无法写入，故将 role 列规范化为 VARCHAR(30)。
--       同时清理 V1 种子数据残留的 role='USER'（Java 枚举无此值，映射会抛异常）。
SET NAMES utf8mb4;

ALTER TABLE `users`
    MODIFY COLUMN `role` VARCHAR(30) NOT NULL DEFAULT 'PATIENT' COMMENT '用户角色';

UPDATE `users` SET `role` = 'PATIENT' WHERE `role` = 'USER';
