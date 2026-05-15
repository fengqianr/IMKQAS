-- IMKQAS RAG评估体系数据表
-- 版本: 4.0
-- 日期: 2026-05-10
-- 说明: 支持离线评估（标注数据集+评估运行）和在线监控（指标快照）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 评估数据集主表 ====================
CREATE TABLE IF NOT EXISTS `eval_dataset` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(200) NOT NULL COMMENT '数据集名称',
    `version` VARCHAR(50) DEFAULT '1.0' COMMENT '版本号',
    `description` TEXT COMMENT '描述',
    `domain` VARCHAR(50) COMMENT '医学领域（内科/外科/儿科等）',
    `difficulty` VARCHAR(20) COMMENT '难度分布（simple/medium/hard/mixed）',
    `total_items` INT DEFAULT 0 COMMENT '标注数据条数',
    `source` VARCHAR(50) COMMENT '来源（MANUAL_ANNOTATION/HISTORY_IMPORT/LLM_GENERATED）',
    `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/REVIEWED/APPROVED/ARCHIVED）',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',

    UNIQUE KEY `uk_name_version` (`name`, `version`),
    INDEX `idx_domain` (`domain`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='评估数据集主表';

-- ==================== 评估数据项表 ====================
CREATE TABLE IF NOT EXISTS `eval_dataset_item` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `dataset_id` BIGINT NOT NULL COMMENT '所属数据集ID',
    `item_index` INT NOT NULL COMMENT '数据项序号',
    `query` TEXT NOT NULL COMMENT '用户查询',
    `ground_truth_chunk_ids` JSON COMMENT '标注相关片段ID列表',
    `ground_truth_doc_ids` JSON COMMENT '标注相关文档ID列表',
    `ground_truth_answer` TEXT COMMENT '参考标准答案',
    `relevance_labels` JSON COMMENT '片段级相关性标注 {chunkId: score}',
    `query_type` VARCHAR(50) COMMENT '查询类型（SYMPTOM_INQUIRY/DRUG_QUERY/DISEASE_INFO等）',
    `difficulty` VARCHAR(20) COMMENT '难度（simple/medium/hard）',
    `safety_level` VARCHAR(20) COMMENT '安全等级（SAFE/NEEDS_DISCLAIMER/EMERGENCY/BLOCKED）',
    `should_trigger_emergency` TINYINT DEFAULT 0 COMMENT '是否应触发紧急阻断',
    `should_trigger_confidence_block` TINYINT DEFAULT 0 COMMENT '是否应触发置信度阻断',
    `expected_response_type` VARCHAR(50) COMMENT '期望响应类型',
    `expected_keywords` JSON COMMENT '期望包含关键词',
    `prohibited_keywords` JSON COMMENT '禁止出现关键词',
    `metadata` JSON COMMENT '扩展元数据',
    `annotator` VARCHAR(100) COMMENT '标注人',
    `review_status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '审核状态',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',

    INDEX `idx_dataset_id` (`dataset_id`),
    INDEX `idx_query_type` (`query_type`),
    INDEX `idx_difficulty` (`difficulty`),
    INDEX `idx_safety_level` (`safety_level`),
    CONSTRAINT `fk_item_dataset` FOREIGN KEY (`dataset_id`) REFERENCES `eval_dataset`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='评估数据项表';

-- ==================== 评估运行记录表 ====================
CREATE TABLE IF NOT EXISTS `eval_run` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `dataset_id` BIGINT NOT NULL COMMENT '使用的数据集ID',
    `run_name` VARCHAR(200) COMMENT '运行名称',
    `status` VARCHAR(20) DEFAULT 'RUNNING' COMMENT '状态（RUNNING/COMPLETED/FAILED）',
    `started_at` TIMESTAMP NULL COMMENT '开始时间',
    `completed_at` TIMESTAMP NULL COMMENT '完成时间',
    `total_queries` INT DEFAULT 0 COMMENT '评估查询总数',
    `evaluated_queries` INT DEFAULT 0 COMMENT '已完成评估数',
    `eval_dimensions` JSON COMMENT '评估维度列表',

    -- 检索指标摘要
    `recall_at_1` DOUBLE COMMENT 'Recall@1',
    `recall_at_5` DOUBLE COMMENT 'Recall@5',
    `recall_at_10` DOUBLE COMMENT 'Recall@10',
    `recall_at_20` DOUBLE COMMENT 'Recall@20',
    `precision_at_5` DOUBLE COMMENT 'Precision@5',
    `precision_at_10` DOUBLE COMMENT 'Precision@10',
    `mrr` DOUBLE COMMENT 'MRR',
    `ndcg_at_10` DOUBLE COMMENT 'NDCG@10',
    `hit_rate_at_5` DOUBLE COMMENT 'HitRate@5',

    -- 融合指标摘要
    `mrr_vector_only` DOUBLE COMMENT '仅向量检索MRR',
    `mrr_keyword_only` DOUBLE COMMENT '仅关键词检索MRR',
    `mrr_fused` DOUBLE COMMENT 'RRF融合后MRR',
    `complementarity_score` DOUBLE COMMENT '互补度',

    -- 过滤指标摘要
    `filter_precision` DOUBLE COMMENT '过滤准确率',
    `filter_recall` DOUBLE COMMENT '过滤召回率',
    `blacklist_hit_rate` DOUBLE COMMENT '黑名单命中率',

    -- 重排序指标摘要
    `mrr_before_rerank` DOUBLE COMMENT '重排序前MRR',
    `mrr_after_rerank` DOUBLE COMMENT '重排序后MRR',

    -- 生成质量摘要
    `avg_faithfulness` DOUBLE COMMENT '平均忠实度',
    `avg_answer_relevance` DOUBLE COMMENT '平均答案相关性',
    `avg_context_relevance` DOUBLE COMMENT '平均上下文相关性',

    -- 安全质量摘要
    `emergency_detection_accuracy` DOUBLE COMMENT '紧急检测准确率',
    `safety_block_rate` DOUBLE COMMENT '安全兜底触发率',

    -- 管线耗时摘要
    `avg_total_time_ms` DOUBLE COMMENT '平均总耗时（ms）',
    `avg_retrieval_time_ms` DOUBLE COMMENT '平均检索耗时（ms）',
    `avg_llm_time_ms` DOUBLE COMMENT '平均LLM耗时（ms）',
    `cache_hit_rate` DOUBLE COMMENT '缓存命中率',

    `config_snapshot` JSON COMMENT '评估时的配置快照',
    `error_log` TEXT COMMENT '错误日志',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',

    INDEX `idx_dataset_id` (`dataset_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='评估运行记录表';

-- ==================== 查询级评估结果表 ====================
CREATE TABLE IF NOT EXISTS `eval_query_result` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `run_id` BIGINT NOT NULL COMMENT '所属运行ID',
    `dataset_item_id` BIGINT NOT NULL COMMENT '对应数据项ID',
    `query` TEXT COMMENT '查询文本',

    -- 检索结果
    `retrieved_chunk_ids` JSON COMMENT '最终检索到的chunkId列表（有序）',
    `vector_chunk_ids` JSON COMMENT '向量检索chunkId列表',
    `keyword_chunk_ids` JSON COMMENT '关键词检索chunkId列表',
    `hit_ground_truth` TINYINT COMMENT '是否命中ground truth',
    `first_relevant_rank` INT COMMENT '第一个相关结果排名（1-based，无命中为-1）',

    -- 过滤结果
    `before_filter_count` INT COMMENT '过滤前文档数',
    `after_filter_count` INT COMMENT '过滤后文档数',
    `discarded_reasons` JSON COMMENT '丢弃原因分布',

    -- 生成结果
    `generated_answer` TEXT COMMENT '生成的回答',
    `faithfulness_score` DOUBLE COMMENT '忠实度分数',
    `answer_relevance_score` DOUBLE COMMENT '答案相关性分数',
    `context_relevance_score` DOUBLE COMMENT '上下文相关性分数',

    -- 安全结果
    `emergency_triggered` TINYINT COMMENT '紧急检测是否触发',
    `confidence_blocked` TINYINT COMMENT '置信度是否阻断',
    `safety_sanitized` TINYINT COMMENT '回答是否被净化',
    `final_confidence` DOUBLE COMMENT '最终置信度',

    -- 耗时明细
    `total_time_ms` BIGINT COMMENT '总耗时（ms）',
    `retrieval_time_ms` BIGINT COMMENT '检索耗时（ms）',
    `rerank_time_ms` BIGINT COMMENT '重排序耗时（ms）',
    `llm_time_ms` BIGINT COMMENT 'LLM生成耗时（ms）',
    `preprocessing_time_ms` BIGINT COMMENT '预处理耗时（ms）',

    -- 缓存
    `cache_hit` TINYINT DEFAULT 0 COMMENT '语义缓存是否命中',

    -- 同义词扩展
    `entities_recognized` INT DEFAULT 0 COMMENT '识别实体数',
    `entities_mapped` INT DEFAULT 0 COMMENT '成功映射实体数',

    `error_message` TEXT COMMENT '错误信息',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX `idx_run_id` (`run_id`),
    INDEX `idx_dataset_item_id` (`dataset_item_id`),
    CONSTRAINT `fk_result_run` FOREIGN KEY (`run_id`) REFERENCES `eval_run`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_result_item` FOREIGN KEY (`dataset_item_id`) REFERENCES `eval_dataset_item`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='查询级评估结果表';

-- ==================== 管线快照表 ====================
CREATE TABLE IF NOT EXISTS `eval_pipeline_snapshot` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `run_id` BIGINT NOT NULL COMMENT '所属运行ID',
    `query_result_id` BIGINT NOT NULL COMMENT '所属查询结果ID',
    `step_name` VARCHAR(100) NOT NULL COMMENT '步骤名称',
    `step_order` INT NOT NULL COMMENT '步骤顺序（1-13）',
    `duration_ms` BIGINT COMMENT '本步骤耗时（ms）',
    `input_count` INT COMMENT '输入数据量',
    `output_count` INT COMMENT '输出数据量',
    `intermediate_data` JSON COMMENT '中间状态数据',
    `status` VARCHAR(20) COMMENT '步骤状态（SUCCESS/SKIPPED/FAILED/BLOCKED）',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX `idx_run_id` (`run_id`),
    INDEX `idx_query_result_id` (`query_result_id`),
    INDEX `idx_step_order` (`step_order`),
    CONSTRAINT `fk_snapshot_run` FOREIGN KEY (`run_id`) REFERENCES `eval_run`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_snapshot_result` FOREIGN KEY (`query_result_id`) REFERENCES `eval_query_result`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='管线快照表（按步骤存储）';

-- ==================== 在线指标快照表 ====================
CREATE TABLE IF NOT EXISTS `online_metrics_snapshot` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `snapshot_time` TIMESTAMP NOT NULL COMMENT '快照时间',
    `window_seconds` INT DEFAULT 300 COMMENT '采样窗口（秒）',

    -- 请求量
    `total_requests` INT DEFAULT 0 COMMENT '总请求数',
    `successful_requests` INT DEFAULT 0 COMMENT '成功请求数',
    `failed_requests` INT DEFAULT 0 COMMENT '失败请求数',
    `success_rate` DOUBLE DEFAULT 0.0 COMMENT '成功率',

    -- 延迟
    `avg_total_ms` DOUBLE COMMENT '平均总耗时',
    `p50_total_ms` DOUBLE COMMENT 'P50延迟',
    `p95_total_ms` DOUBLE COMMENT 'P95延迟',
    `p99_total_ms` DOUBLE COMMENT 'P99延迟',
    `avg_retrieval_ms` DOUBLE COMMENT '平均检索耗时',
    `avg_llm_ms` DOUBLE COMMENT '平均LLM耗时',

    -- 缓存
    `semantic_cache_hits` INT DEFAULT 0 COMMENT '语义缓存命中数',
    `semantic_cache_misses` INT DEFAULT 0 COMMENT '语义缓存未命中数',
    `cache_hit_rate` DOUBLE DEFAULT 0.0 COMMENT '缓存命中率',

    -- 同义词
    `avg_entities_per_query` DOUBLE COMMENT '平均每查询实体数',
    `synonym_coverage_rate` DOUBLE COMMENT '同义词扩展覆盖率',
    `unmapped_term_rate` DOUBLE COMMENT '未映射术语率',

    -- 安全
    `emergency_block_count` INT DEFAULT 0 COMMENT '紧急阻断数',
    `confidence_block_count` INT DEFAULT 0 COMMENT '置信度阻断数',
    `safety_check_count` INT DEFAULT 0 COMMENT '安全检查总数',
    `safety_block_rate` DOUBLE DEFAULT 0.0 COMMENT '安全阻断率',

    -- 置信度分布
    `conf_bucket_0_35` INT DEFAULT 0 COMMENT '置信度[0, 0.35)',
    `conf_bucket_35_60` INT DEFAULT 0 COMMENT '置信度[0.35, 0.6)',
    `conf_bucket_60_80` INT DEFAULT 0 COMMENT '置信度[0.6, 0.8)',
    `conf_bucket_80_100` INT DEFAULT 0 COMMENT '置信度[0.8, 1.0]',
    `avg_confidence` DOUBLE DEFAULT 0.0 COMMENT '平均置信度',

    -- 检索模式分布
    `vector_retrieval_count` INT DEFAULT 0 COMMENT '向量检索次数',
    `keyword_retrieval_count` INT DEFAULT 0 COMMENT '关键词检索次数',
    `hybrid_retrieval_count` INT DEFAULT 0 COMMENT '混合检索次数',

    -- 质量过滤
    `avg_input_docs` DOUBLE COMMENT '平均过滤前文档数',
    `avg_output_docs` DOUBLE COMMENT '平均过滤后文档数',
    `avg_discard_rate` DOUBLE COMMENT '平均丢弃率',

    -- LLM
    `total_tokens_generated` BIGINT DEFAULT 0 COMMENT '总生成token数',
    `avg_tokens_per_response` DOUBLE COMMENT '平均每回答token数',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX `idx_snapshot_time` (`snapshot_time`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='在线指标快照表';

SET FOREIGN_KEY_CHECKS = 1;
