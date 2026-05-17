# RAG链路评估体系建设计划

## 背景

IMKQAS的RAG管线已发展到v3.0，包含13个步骤（查询预处理→安全兜底→双路召回→RRF融合→质量过滤→矛盾检测→多因子重排序→置信度门控→语义缓存→LLM生成→答案净化→响应）。目前缺乏系统化的质量评估体系，无法量化各环节的表现和优化效果。

需要在RAG管线的每个环节建立评估指标，形成**离线评估（基于标注数据集）+ 在线监控（运行时指标采集）**双轨评估体系。

## 评估维度（按RAG管线步骤组织）

### 离线评估（需标注数据集）
| 维度 | 对应管线步骤 | 核心指标 |
|------|-------------|---------|
| 检索质量 | [4] 双路召回 | Recall@K, Precision@K, MRR, NDCG@K, Hit Rate |
| 融合质量 | [5] RRF融合 | 融合前后MRR对比, 双路互补性分析 |
| 过滤质量 | [6][7] 质量过滤+矛盾检测 | 过滤准确率/召回率, 误杀率 |
| 重排序质量 | [8] 多因子重排序 | 重排前后MRR对比, Top-K准确率变化 |
| 生成质量 | [11] LLM生成 | Faithfulness, Answer Relevance, Context Relevance (LLM-as-Judge) |
| 安全质量 | [3][9][12] 三层安全兜底 | 紧急检测准确率, 置信度门控有效率, 误阻断率 |

### 在线评估（运行时采集）
| 维度 | 对应管线步骤 | 核心指标 |
|------|-------------|---------|
| 管线健康度 | 全步骤 | 各阶段耗时分布(P50/P95/P99), 缓存命中率 |
| 端到端质量 | [1]→[13] | 成功率, 平均延迟, 置信度分布 |
| 预处理质量 | [2] 查询预处理 | 实体识别数, 同义词覆盖率, 未映射术语率 |

## 架构设计

```
┌─────────────────────────────────────────────────────┐
│                   EvaluationController               │
│         /api/evaluation/datasets|runs|online|report  │
├─────────────────────────────────────────────────────┤
│  EvaluationService  │  OnlineMetricsCollector        │
│  (离线评估编排)      │  (@Scheduled 定时采集)         │
├─────────────────────────────────────────────────────┤
│  RetrievalEvaluator │ FusionEvaluator │ SafetyEval   │
│  GenerationEvaluator│ (LLM-as-Judge)                 │
├─────────────────────────────────────────────────────┤
│  MetricsCalculator  │  PipelineTraceContext           │
│  (纯函数指标计算)    │  (ThreadLocal管线步骤追踪)      │
├─────────────────────────────────────────────────────┤
│  6张评估表 (eval_dataset, eval_run, eval_query_result│
│  eval_pipeline_snapshot, online_metrics_snapshot...)  │
└─────────────────────────────────────────────────────┘
```

## 新增文件清单

### 数据层（entity + mapper）
- `entity/evaluation/EvalDataset.java` — 评估数据集实体
- `entity/evaluation/EvalDatasetItem.java` — 评估数据项实体（标注query + ground_truth）
- `entity/evaluation/EvalRun.java` — 评估运行记录实体（汇总指标）
- `entity/evaluation/EvalQueryResult.java` — 逐查询评估结果实体
- `entity/evaluation/EvalPipelineSnapshot.java` — 管线步骤快照实体
- `entity/evaluation/OnlineMetricsSnapshot.java` — 在线指标快照实体
- `mapper/evaluation/EvalDatasetMapper.java` 等6个Mapper

### 服务层（接口 + 实现）
- `service/evaluation/EvaluationService.java` + `impl/` — 离线评估主流程
- `service/evaluation/RetrievalEvaluator.java` + `impl/` — 检索质量评估
- `service/evaluation/FusionEvaluator.java` + `impl/` — 融合质量评估
- `service/evaluation/GenerationEvaluator.java` + `impl/` — 生成质量评估(LLM-as-Judge)
- `service/evaluation/SafetyEvaluator.java` + `impl/` — 安全质量评估
- `service/evaluation/OnlineMetricsCollector.java` + `impl/` — 在线指标采集(@Scheduled)
- `service/evaluation/DatasetService.java` + `impl/` — 数据集管理
- `service/evaluation/ReportService.java` + `impl/` — 报告生成(Console/JSON/HTML)

### 工具层
- `utils/evaluation/MetricsCalculator.java` — 所有评估指标的纯函数实现
- `utils/evaluation/PipelineTraceContext.java` — ThreadLocal管线步骤追踪
- `utils/evaluation/DatasetImporter.java` — 数据集导入(JSON/CSV)
- `utils/evaluation/ReportTemplates.java` — HTML报告模板

### 配置/迁移/控制器
- `config/EvaluationConfig.java` — 评估系统配置
- `controller/evaluation/EvaluationController.java` — 评估管理REST API
- `resources/db/migration/V4__Evaluation_tables.sql` — 6张表的DDL

### 现有文件修改
- `QaServiceImpl.java` — 在answer()的13个步骤插入PipelineTraceContext桩点
- `MultiRetrievalService.java` + `Impl` — 新增hybridRetrievalDetail()暴露中间检索结果
- `application.yml` — 新增imkqas.evaluation配置段

## 核心指标公式

```
Recall@K    = |R_k ∩ G| / |G|
Precision@K = |R_k ∩ G| / K
MRR         = (1/N) * Σ(1/rank_i)
NDCG@K      = DCG@K / IDCG@K, 其中 DCG@K = Σ(2^rel_j - 1)/log₂(j+1)
互补度      = |R_v ∪ R_k| / max(|R_v|, |R_k|)
Faithfulness = LLM_Judge(answer, context) → 0-1 分数
```

## 实现分步

### 第1步：基础框架（数据模型+Mapper+配置）
- Flyway建表迁移 V4
- 6个实体类 + 6个Mapper接口
- EvaluationConfig 配置类
- application.yml 评估配置段

### 第2步：指标计算+数据集管理
- MetricsCalculator 工具类（所有公式的纯函数实现）
- DatasetService + Impl（CRUD + JSON导入）
- DatasetImporter（从现有test_scenarios.json导入初始数据）

### 第3步：离线评估引擎
- RetrievalEvaluator + Impl
- FusionEvaluator + Impl
- SafetyEvaluator + Impl
- EvaluationService + Impl（主流程编排）
- EvaluationController（API端点）

### 第4步：管线插桩+在线采集
- PipelineTraceContext 工具类
- 修改QaServiceImpl.answer()插入13步trace桩点
- 修改MultiRetrievalServiceImpl暴露hybridRetrievalDetail()
- OnlineMetricsCollector + Impl（@Scheduled定时采集）

### 第5步：生成评估+报告
- GenerationEvaluator + Impl（LLM-as-Judge调用阿里云百炼）
- ReportService + Impl（Console/JSON/HTML）
- ReportTemplates（HTML模板）
- 生成基线评估报告

### 第6步：集成测试
- MetricsCalculatorTest 单元测试
- EvaluationSystemTest 集成测试（端到端评估流程）
- 从现有test_scenarios.json导入首批评估数据集

## 验证方式

1. **编译验证**：`mvn compile` 确保所有新增代码编译通过
2. **单元测试**：`mvn test -Dtest=MetricsCalculatorTest` 验证指标计算正确性
3. **集成测试**：`mvn test -Dtest=EvaluationSystemTest` 验证端到端评估流程
4. **API测试**：通过EvaluationController的/swagger-ui.html验证API可用
5. **基线报告**：用现有15个医疗场景运行一次完整评估，输出基线报告
6. **在线采集**：启动应用后等待60秒，验证online_metrics_snapshot表有数据写入
