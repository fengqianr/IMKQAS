-- IMKQAS users 表新增个人身份信息 JSON 列
-- 版本: 12.0
-- 说明: 与 health_profile 平行存储患者本人身份信息（姓名/性别/出生日期/证件号/住址），
--       保存后同步 fhir_patient_cache，供医生端按姓名/证件号/手机号检索。
SET NAMES utf8mb4;

ALTER TABLE `users`
    ADD COLUMN `identity` JSON NULL
        COMMENT '个人身份信息JSON: {name, gender, birthDate, idCard, address}'
        AFTER `health_profile`;
