-- IMKQAS 移除逻辑删除字段
-- 版本: 13.0
-- 说明: 实体层已去除 @TableLogic 注解，改为物理删除；同步移除各表的 deleted 列。


SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `users` DROP COLUMN `deleted`;
ALTER TABLE `documents` DROP COLUMN `deleted`;
ALTER TABLE `document_chunks` DROP COLUMN `deleted`;
ALTER TABLE `messages` DROP COLUMN `deleted`;

ALTER TABLE `medical_synonym_mapping` DROP COLUMN `deleted`;
ALTER TABLE `unmapped_term_queue` DROP COLUMN `deleted`;

ALTER TABLE `eval_dataset` DROP COLUMN `deleted`;
ALTER TABLE `eval_dataset_item` DROP COLUMN `deleted`;
ALTER TABLE `eval_run` DROP COLUMN `deleted`;

ALTER TABLE `fhir_patient_cache` DROP COLUMN `deleted`;
ALTER TABLE `fhir_observation_cache` DROP COLUMN `deleted`;
ALTER TABLE `fhir_condition_cache` DROP COLUMN `deleted`;
ALTER TABLE `fhir_questionnaire_response_cache` DROP COLUMN `deleted`;

ALTER TABLE `interview_sessions` DROP COLUMN `deleted`;
ALTER TABLE `interview_messages` DROP COLUMN `deleted`;
ALTER TABLE `analysis_reports` DROP COLUMN `deleted`;

ALTER TABLE `fhir_diagnostic_report_cache` DROP COLUMN `deleted`;
ALTER TABLE `fhir_risk_assessment_cache` DROP COLUMN `deleted`;

ALTER TABLE `drug_population_contraindication` DROP COLUMN `deleted`;
ALTER TABLE `drugs` DROP COLUMN `deleted`;
ALTER TABLE `drug_interactions` DROP COLUMN `deleted`;
ALTER TABLE `drug_aliases` DROP COLUMN `deleted`;

SET FOREIGN_KEY_CHECKS = 1;
