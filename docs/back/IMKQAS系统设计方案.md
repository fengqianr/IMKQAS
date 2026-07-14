# IMKQAS 系统设计方案

**版本**: 2.0
**日期**: 2026-07-14


---

## 1. 项目概述

IMKQAS（Intelligent Medical Knowledge Q&A System）是一个基于检索增强生成（RAG）的医疗知识智能问答系统。系统面向医学生、基层医护、普通患者和健康管理师，提供智能医学问答、知识库管理、AI 辅助量表填写、科室分诊、药物查询等完整功能。

### 1.1 核心能力矩阵

| 能力域 | 目标用户 | 核心功能 |
|--------|---------|---------|
| 智能问答 | 全部用户 | 12 步 RAG 管线，流式输出，来源追溯，多轮对话 |
| 意图路由 | 全部用户 | 六层过滤架构，三类意图分流（知识查询/数据采集/混合型） |
| AI 量表填写 | 患者 | 双模采集（LLM 驱动 + 纯表单），PHQ-9/GAD-7/ISI 标准量表 |
| FHIR 标准接口 | 系统对接 | HL7 FHIR R4，7 种资源，为 HIS 预留 |
| 知识库管理 | 管理员 | 文档上传 → PDF 解析 → 语义分块 → 向量化 |
| 医学同义词 | 系统内部 | 三级链标准化（本地 → SNOMED CT → LLM），含审核工作流 |
| 禁忌检测 | 全部用户 | 药物-人群禁忌表，知识片段自动标注，安全提示追加 |
| 离线评估 | 开发者 | 评估数据集管理，多维度评估，管线快照追踪 |
| 在线监控 | 运维 | 60 秒间隔指标采集，30 天保留，多维度统计 |

### 1.2 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java + TypeScript | 21 / 5.4 |
| 后端框架 | Spring Boot + MyBatis Plus | 3.2.5 / 3.5.7 |
| 前端框架 | Vue 3 + Vite + Pinia + Element Plus | 3.4 / 5.0 |
| 关系数据库 | MySQL + Flyway 迁移 | 8.0 |
| 向量数据库 | Milvus | 2.3.6 |
| 缓存 | Redis | 7.x |
| 全文搜索 | Apache Lucene | 9.10.0 |
| 对象存储 | MinIO | 8.5.10 |
| 消息队列 | RabbitMQ | — |
| LLM 编排 | LangChain4j + DashScope API | 0.29.0 |
| 医疗标准 | HAPI FHIR R4 + SNOMED CT (Snowstorm) | 7.4.0 |
| 文档处理 | PDFBox + Tesseract OCR + MinerU API | 3.0.2 |
| 安全 | Spring Security + JWT | — |

### 1.3 项目规模

| 维度 | 数量 |
|------|------|
| Java 源文件 | 274 |
| Service 接口/实现类 | 40 |
| REST Controller | 21 |
| 数据库表 | 29 |
| Flyway 迁移 | 10 (V1-V10) |
| REST API 端点 | ~130 |
| Vue 视图文件 | 9 |
| TypeScript API 服务 | 8 |

## 2. 系统架构

### 2.1 分层架构图

```
┌──────────────────────────────────────────────────────┐
│              前端展示层 (Vue 3 + TypeScript)           │
│  QaView │ KnowledgeView │ Dashboard │ TermReview │   │
│  ContraindicationRules │ Login │ Register            │
├──────────────────────────────────────────────────────┤
│              网关/过滤器层                             │
│  JWT 认证 │ 限流 (10 req/s) │ 全局异常处理 │ CORS     │
├──────────────────────────────────────────────────────┤
│            Controller 层 (21 个控制器, ~130 端点)      │
│  Auth │ User │ Doc │ Qa │ RAG │ Triage │ HIS │ Eval  │
├──────────────────────────────────────────────────────┤
│             Service 层 (40 个服务接口/类)              │
│  ┌────────┬────────┬────────┬────────┬──────────┐   │
│  │ Common │  RAG   │  HIS   │ Triage │Evaluation│   │
│  │ Drug   │ Export │ SNOMED │Document│ Database │   │
│  └────────┴────────┴────────┴────────┴──────────┘   │
├──────────────────────────────────────────────────────┤
│          Mapper 层 (26 个 MyBatis Plus Mapper)        │
├──────────────────────────────────────────────────────┤
│                   基础设施层                           │
│  MySQL (29 表) │ Redis │ Milvus │ MinIO │ RabbitMQ   │
└──────────────────────────────────────────────────────┘
```

### 2.2 包结构总览

```
com.student/
├── config/              (14 文件) — Spring 配置
│   ├── RagConfig, SecurityConfig, MilvusConfig
│   ├── MinioConfig, RedisConfig, MyBatisPlusConfig
│   ├── RabbitMQConfig, WebConfig, SafetyConfig
│   ├── OpenApiConfig, EvaluationConfig
│   ├── DepartmentKnowledgeConfig, MyMetaObjectHandler
│   └── his/HisConfigProperties
├── controller/          (21 文件) — REST 控制器
│   ├── Auth, User, Message, Document, DocumentChunk, Conversation
│   ├── drug/DrugController
│   ├── evaluation/EvaluationController
│   ├── his/ (6): FhirPatient/Observation/Condition/
│   │       QuestionnaireResponse/Bundle + InterviewController
│   ├── qa/QaController
│   ├── rag/ (5): Rag, RetrievalTest, SynonymTest,
│   │       Admin, Contraindication
│   └── triage/DepartmentTriageController
├── dto/                 (24 文件)
│   ├── ApiResponse, LoginRequest/Response, RegisterRequest, UserDTO
│   ├── his/FhirConverter, qa/RetrievalPathDto
│   ├── rag/(7): AdminStatsVO, UnmappedTermVO 等
│   ├── triage/(4): TriageRequest, DepartmentKnowledgeBase 等
│   └── user/HealthProfileRequest, UserUpdateRequest
├── entity/              (26 文件) — MyBatis Plus 实体
│   ├── User, Document, DocumentChunk, Conversation, Message
│   ├── contraindication/DrugPopulationContraindication
│   ├── drug/Drug, DrugAlias, DrugInteraction
│   ├── evaluation/ (6): EvalDataset, EvalRun 等
│   ├── his/ (9): 6×Fhir*Cache + InterviewSession/Message
│   │       + AnalysisReport
│   └── synonym/MedicalSynonymMapping, UnmappedTermRecord
├── exception/           (7 文件) — BaseException, ErrorCode 等
├── filter/              JwtAuthenticationFilter
├── handler/             GlobalExceptionHandler, ResponseAdvice
├── mapper/              (26 文件) — 全部继承 BaseMapper<T>
│   ├── evaluation/ (6) + his/ (9) + 根目录 (11)
├── model/triage/        (6 文件) — 分诊领域模型
├── service/             (129 文件) — 业务核心，见第 3 节
└── utils/               (17 文件)
    ├── JwtUtil, PdfToMarkdownConverter, MedicalDocumentSplitter
    ├── TextCleaner, RecursiveTextSplitter
    └── evaluation/ (4): PipelineTraceContext, MetricsCalculator 等
```

## 3. 服务层架构

### 3.1 服务模块总览

系统共有 40 个服务组件，按 10 个模块组织。其中 32 个为接口+实现分离模式，8 个为直接 `@Service` 类。

| 模块 | 包路径 | 接口 | 实现 | 职责 |
|------|--------|------|------|------|
| Common | `service.common` | 3 | 4 | 用户/认证/对话/消息/验证码 |
| RAG | `service.rag` | 14 | 17 | RAG 管线核心（检索/融合/重排序/生成/安全） |
| HIS | `service.his` | 6 | 13 | FHIR 资源管理/意图路由/填表引擎/分析 |
| Evaluation | `service.evaluation` | 3 | 8 | 离线评估+在线监控+报告生成 |
| Document | `service.document` | 2 | 3 | 文档/分块管理+文档处理 |
| Drug | `service.drug` | 1 | 1 | 药物查询/相互作用检查 |
| Export | `service.export` | 1 | 2 | 对话导出(PDF/Markdown) |
| Database | `service.dataBase` | 0 | 2 | Milvus 向量操作+MinIO 文件存储 |
| SNOMED | `service.snomed` | 0 | 1 | SNOMED CT 术语标准化 |
| Root | `service` | 1 | 1 | LlmService + RedisService |

### 3.2 RAG 模块服务详解

RAG 模块是系统核心，包含 15 个服务组件。下面是每个服务的职责和关键方法。

#### 3.2.1 核心管线服务

**QaService** (接口 + QaServiceImpl)
- `answer(query, userId, conversationId)` → QaResponse — 完整 12 步同步管线
- `answerAsync(...)` → CompletableFuture — 异步问答
- `answerWithSources(...)` → QaResponseWithSources — 带引用+管线追踪
- `answerBatch(...)` → List — 批量问答
- 内部类：QaResponse, QaResponseWithSources, SourceCitation, QaStats
- QaResponse 字段：query, answer, retrievedContext, confidence, processingTime, modelUsed, intentType, questionnaireSuggestion

**MultiRetrievalService** (接口 + Impl)
- `vectorRetrieval(query, topK)` — Milvus 向量检索
- `keywordRetrieval(query, topK)` — Lucene 关键词检索
- `hybridRetrieval(query, topK)` — 双路并行 + RRF 融合
- `hybridRetrievalWithPerSideK(...)` — 可调权重融合
- 内部类：RetrievalResult (chunkId, documentId, content, score, vectorScore, keywordScore, source)

**RrfFusionService** (接口 + Impl)
- `fuse(resultLists, weights, topK)` — Reciprocal Rank Fusion 算法
- `fuseVectorAndKeyword(...)` — 向量+关键词专用融合
- 内部类：FusionStats

**CrossEncoderRerankService** (接口 + Impl)
- `rerank(query, results, topK)` — Cross-Encoder 语义重排序 (gte-rerank-v2)
- `batchRerank(...)` — 批量重排序
- 内部类：RerankStats

**MultiFactorRerankService** (接口 + Impl)
- `rerank(query, results, topK)` — 多因子加权重排序
- 权重配置：权威性 0.4 / 时效性 0.2 / 语义相似度 0.4
- 内部类：KnowledgeType 枚举, AuthorityLevel 枚举

#### 3.2.2 查询增强与安全服务

**QueryRewriteService** (接口 + Impl)
- `rewrite(originalQuery, userId, conversationId)` — 三级链改写
- `simplify(query)` / `correctSpelling(query)` / `medicalize(query)`
- 三级链：THUOCL 词表 → 医学实体识别 → 同义词扩展 → 拼写纠正

**SynonymExpansionService** (接口 + Impl)
- `expand(query, entities)` → ExpansionResult — 同义词查询扩展
- `lookupTerm(colloquialTerm)` — 三级查找（本地 → SNOMED CT → LLM）
- `lookupTermForceLlm(term, contextQuery)` — 强制 LLM 推理
- `approveMapping(...)` / `batchUpsertMappings(...)` — 审核管理

**MedicalEntityRecognitionService** (接口 + Impl)
- `recognize(query)` → List<MedicalEntity> — 混合模式实体识别
- 实体类型：DRUG, DISEASE, SYMPTOM, POPULATION, BODY_PART, EXAMINATION, TREATMENT, OTHER

**SafetyGuardService** (直接 `@Service`)
- `checkEmergency(query)` → SafetyDecision — 急症关键词三级检测
- `assessConfidence(results)` → ConfidenceDecision — 置信度门控
- `sanitizeAnswer(answer, confidence)` — 答案安全净化

**ContraindicationDetectionService** (接口 + Impl)
- `annotateChunks(chunks)` — 知识片段自动标注禁忌信息
- `detectChunk(chunkContent)` → List<ContraindicationMatch> — 禁忌检测
- `buildSafetyNote(query)` — 构建安全提示文本
- 规则表：drug_population_contraindication (28 条种子规则)

#### 3.2.3 缓存与嵌入服务

**SemanticCacheService** (接口 + Impl)
- `get(normalizedQuery, fragmentIds)` → CachedAnswer — 语义缓存读取
- `put(...)` — 缓存写入（Redis, TTL 7200s）
- 分布式锁防止缓存击穿，版本管理支持增量更新

**EmbeddingService** (接口 + Impl)
- `embed(text)` → List<Float> — 文本向量化 (text-embedding-v3, 1024 维)
- `embedBatch(texts)` → List — 批量向量化
- 部署类型检测 (local/remote/hybrid)

**KeywordRetrievalService** (接口 + Impl)
- `buildIndex()` — 构建 Lucene 索引
- `search(query, topK)` — 关键词检索，支持同义词扩展
- `addToIndex(chunk)` / `addBatchToIndex(chunks)` — 增量索引

**QualityFilterService** (接口 + Impl)
- `filter(results)` → FilterResult — 质量过滤（黑名单域名/文本长度/冗余去重）
- `detectContradictions(results, query)` → ContradictionResult — 矛盾检测与裁决
- 裁决策略：权威性差异 → 以高权威源为准；权威性相当 → 交 LLM 判断

### 3.3 LLM 服务

**LlmService** (接口，实现：LlmServiceImpl)
- `generateAnswer(query, context)` — 标准 RAG 生成
- `generateAnswerWithCitations(query, contextWithSources)` — 带引用的生成
- `generateAnswerDirect(prompt)` — 直接生成（绕过缓存）
- 内部类：ContextWithSource, AnswerWithCitations, Citation, ModelInfo, LlmStats
- 生产环境使用 DashScope qwen-plus，超时 60s

### 3.4 Redis 服务

**RedisService** (直接 `@Service`)
- 基础操作：set/get/delete/exists/expire/increment
- 业务操作：setUserSession/getUserSession, setQueryResult/getQueryResult
- 缓存操作：setMedicalTerm/getMedicalTerm, setDrugInteraction/getDrugInteraction
- 限流操作：setRateLimitCount/getRateLimitCount/incrementRateLimitCount
- 分布式锁：acquireLock/releaseLock (TTL)
- 版本化缓存：setWithVersion/getWithVersion

## 4. RAG 问答管线

### 4.1 完整管线流程图

```
用户 Query
  │
  [1] 意图路由 (IntentRouter.classify)
  │   ├── KNOWLEDGE_QUERY → 继续 RAG 管线
  │   ├── DATA_COLLECTION → 直接返回问卷建议
  │   └── MIXED → 继续 RAG + 尾部附加问卷推荐
  │
  [2] 查询预处理 (QueryRewriteService.rewrite)
  │   三级链：THUOCL词表 → 医学实体识别 → 同义词扩展 → 拼写纠正
  │
  [3] 安全预检① — 急症检测 (SafetyGuardService.checkEmergency)
  │   急症关键词三级匹配，命中则阻断并返回就医指导
  │
  [4] 向量检索 (MultiRetrievalService.vectorRetrieval)
  │   Milvus 查询，1024 维 embedding，top-K 可配置
  │
  [5] 关键词检索 (MultiRetrievalService.keywordRetrieval)
  │   Lucene 倒排索引，支持同义词扩展
  │
  [5] RRF 融合 (RrfFusionService.fuse)
  │   Reciprocal Rank Fusion, k=60, 向量权重 0.6, 关键词权重 0.4
  │
  [6] 质量过滤 (QualityFilterService.filter)
  │   黑名单域名 → 文本长度 → 重复检测 → 低质信息过滤
  │
  [7] 矛盾检测 (QualityFilterService.detectContradictions)
  │   药物-人群矛盾对检测 → 权威性裁决或交 LLM 判断
  │
  [8] 多因子重排序 (MultiFactorRerankService.rerank)
  │   权威性(0.4) + 时效性(0.2) + 语义相似度(0.4)
  │
  [9] 安全预检② — 置信度门控 (SafetyGuardService.assessConfidence)
  │   检索最高分低于阈值 → 阻断，返回低置信度提示
  │
  [10] 语义缓存 (SemanticCacheService.get)
  │   Redis 缓存命中 → 直接返回；未命中 → 分布式锁 → LLM 生成 → 回写
  │
  [11] LLM 生成 (LlmService.generateAnswer)
  │   Prompt 模板注入检索上下文 + 用户健康档案
  │
  [12] 安全净化③ (SafetyGuardService.sanitizeAnswer)
  │   答案审查 → 移除不安全内容 → 追加禁忌安全提示 + 矛盾裁决提示
  │
  ▼
QaResponse（含答案、来源、置信度、意图类型、管线追踪数据）
```

### 4.2 管线关键配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| rag.retrieval.mode | HYBRID | 检索模式 (VECTOR/KEYWORD/HYBRID) |
| rag.retrieval.initialTopK | 10 | 初始检索数量 |
| rag.retrieval.rerankTopK | 5 | 重排序后保留数量 |
| rag.retrieval.rrf.k | 60 | RRF 融合参数 |
| rag.retrieval.vectorWeight | 0.6 | 向量检索权重 |
| rag.retrieval.keywordWeight | 0.4 | 关键词检索权重 |
| rag.reranker | gte-rerank-v2 | Cross-Encoder 模型 |
| rag.embedding | text-embedding-v3 (1024维) | Embedding 模型 |
| rag.llm | qwen-plus (60s超时) | LLM 模型 |
| rag.quality-filter.minFragmentLength | 20 | 最小片段长度（字符） |
| rag.semantic-cache.ttl | 7200s | 语义缓存过期时间 |

## 5. HIS/FHIR 模块

### 5.1 模块总览

HIS 模块为后续对接医院信息系统预留 FHIR R4 标准接口，同时实现了完整的 AI 填表助手功能。通过 `imkqas.his.enabled` 开关控制（当前为 false）。

### 5.2 意图路由 (IntentRouter)

**六层过滤架构：**

```
用户输入
  → [0] Caffeine 本地缓存 (10,000条, 5min TTL) → 命中返回
  → [1] 关键词匹配 (16 知识关键词 + 8 数据采集关键词)
      DATA关键词 + 问卷匹配 → DATA_COLLECTION
      知识关键词 + 无DATA关键词 + 无问卷 → KNOWLEDGE_QUERY
  → [2] 正则匹配 (补充关键词未覆盖的边界 case)
  → [3] Redis 分布式缓存 (2h TTL, 断路器保护)
  → [4] LLM 分类 (2s 超时 + 断路器: 连续5次失败熔断30s)
  → [5] 兜底: KNOWLEDGE_QUERY (保证可用性)
```

**断路器机制：**
- LLM 断路器：连续失败 5 次 → 熔断 30s → 半开试探
- Redis 断路器：第 1 次超时即熔断 30s → 保守策略快速保护下游
- Redis 写入异步化，失败不影响主链路

### 5.3 覆盖的 FHIR 资源 (7 种)

| 资源 | REST 路径 | 建表 | 说明 |
|------|----------|------|------|
| Patient | `/api/his/fhir/Patient` | fhir_patient_cache | 患者人口学信息 |
| Observation | `/api/his/fhir/Observation` | fhir_observation_cache | 生命体征、检验检查 |
| Condition | `/api/his/fhir/Condition` | fhir_condition_cache | 诊断/病情 |
| QuestionnaireResponse | `/api/his/fhir/QuestionnaireResponse` | fhir_questionnaire_response_cache | 问卷填写结果 |
| Bundle | `/api/his/fhir/Bundle` | 不建表（传输容器） | 统一写入入口，transaction/batch |
| DiagnosticReport | 内部使用 | fhir_diagnostic_report_cache | AI 分析报告 |
| RiskAssessment | 内部使用 | fhir_risk_assessment_cache | AI 风险评估 |

### 5.4 对话状态机

```
CHAT ──(AI建议+用户确认)──→ QUESTIONNAIRE
QUESTIONNAIRE ──(完成/中断)──→ CHAT
QUESTIONNAIRE ──(超时30min)──→ CHAT（自动恢复）

会话生命周期:
ACTIVE → QUESTIONING → ANSWER_RECEIVED → (CLARIFYING → ANSWER_RECEIVED)*
  → COMPLETED / PAUSED / EXPIRED / ABANDONED
```

- 状态存储：Redis 热状态 (TTL 30min) + MySQL 冷持久化 + 本地 Map 兜底
- 定时清理：InterviewSessionCleanupTask 每 5 分钟将过期 PAUSED 标记为 EXPIRED
- 心跳保活：前端定期调用 `/heartbeat` 防止会话过期

### 5.5 双模采集架构

**LLM 驱动模式：**
```
用户自然语言输入
  → CollectionSubAgent 语义理解
  → 匹配到具体选项（含置信度）
  → 置信度高 → RecordAnswer → 下一题
  → 置信度低 → Clarify 追问 (最多5次)
  → 追问超限 → 降级为规则解析器 → 再超限 → 纯表单模式
```

**纯表单模式：**
```
前端展示题目 + 选项
  → 用户点击选项 → answerQuestion()
  → 或一次性填写所有剩余题目 → submitBatch()
```

**三阶段确定性管线 (TransformationPipeline)：**
```
阶段1: 完整性校验 → 必答题检查、答案合法性验证
阶段2: 规则计算 → 评分累加、风险等级判定、组合逻辑触发
阶段3: FHIR 组装 → QuestionnaireResponse + DiagnosticReport + RiskAssessment
```

### 5.6 分析报告

**AnalysisAgent** 通过 LLM 生成结构化报告：
- 风险等级评估 + 总分/最大值/严重程度
- 详细分析 + 建议 + 随访计划 + 免责声明
- 输出作为 FHIR DiagnosticReport + RiskAssessment 存储

## 6. 业务模块详解

### 6.1 通用模块 (Common)

**AuthService** — 认证服务
- `sendLoginCode(phone)` — 发送短信验证码
- `loginWithCode(request)` → LoginResponse — 手机号+验证码登录，返回 JWT
- `register(request)` — 新用户注册
- `refreshToken(oldToken)` / `logout(token)` / `validateToken(token)`
- JWT 配置：密钥 `imkqas-medical-rag-secret-key-2026`，过期 24h

**CodeService** — 验证码管理
- `sendCode(phone)` / `verifyCode(phone, code)` — 验证码发送与校验
- `cleanupExpiredCodes()` — 过期验证码清理
- `canSendCode(phone)` / `getRemainingTime(phone)` — 发送频率控制

**UserService** — 用户管理 (继承 IService<User>)
- 支持手机号/用户名查询，健康档案 JSON 存储
- 角色枚举：PATIENT, STUDENT, NURSE, DOCTOR, HEALTH_MANAGER, ADMIN
- 实现 Spring Security UserDetails 接口

**ConversationService** — 对话管理
- 软删除回收站：`listDeleted(userId)` / `restoreConversation(id)`
- 支持分页查询、标题更新、按用户过滤

**MessageService** — 消息管理
- 按对话 ID 查询消息列表（时间升序）
- 支持关键词搜索、分角色过滤


### 6.3 药物查询模块 (Drug)

**DrugQueryService** (接口 + Impl)
- `searchDrugsByName(drugName)` → List<Drug> — 通用名/品牌名/别名搜索
- `getDrugById(id)` → Drug — 药品详情（含适应症、禁忌症、不良反应 JSON）
- `checkDrugInteraction(drugAId, drugBId)` → DrugInteraction — 单对相互作用
- `checkMultipleDrugInteractions(drugIds)` → List — 批量相互作用检查
- `generateDrugReminders(drugId, healthProfile)` → List<String> — 个性化用药提醒
- `getDrugClasses()` / `getDrugsByClass(drugClass)` — 药品分类浏览

**药物相互作用类型枚举：** CONTRAINDICATED / SEVERE / MODERATE / MILD / MONITOR / UNKNOWN
**严重程度：** HIGH / MODERATE / LOW

### 6.4 SNOMED CT 术语模块

**SnomedTerminologyService** (直接 `@Service`)
- `medicalize(query)` — 查询文本的术语标准化
- `lookupByTerm(term)` → SnomedTermResponse — 按术语查询
- `lookupByConceptId(conceptId)` — 按概念 ID 查询
- `lookupBatch(terms)` → Map — 批量查询
- 对接 Snowstorm 术语服务器 (localhost:8082)，支持本地降级

**同义词三级链标准化：**
```
口语化术语
  → LOCAL: MedicalSynonymMapping 表 (已审核映射)
  → SNOMED_CT: Snowstorm 术语服务器
  → LLM: LangChain4j 语义推理 (结果入队待审核)
```

### 6.5 文档处理模块

**DocumentService** (继承 IService<Document>)
- 文档状态：UPLOADED → PROCESSING → COMPLETED / FAILED

**DocumentChunkService** (继承 IService<DocumentChunk>)
- `clearAllChunks()` — 清除所有分块 + Milvus 向量

**DocumentProcessorService** (接口 + Impl)
- `processDocument(documentId)` — 单文档异步处理
- `processDocuments(documentIds)` → int — 批量处理
- 处理管线：PDF 提取 → 文本清洗 → 语义分块 (500字/50重叠) → 向量化 (1024维) → Milvus 入库 + Lucene 索引

**PDF 处理技术栈：**
- MinerU API (优先)：高精度 PDF 解析
- PDFBox 3.0.2：纯 Java PDF 提取
- Tesseract OCR：扫描件文字识别 (降级)
- MedicalDocumentSplitter：医学文档专用分割器
- PageLevelClassifier：页面级分类（正文/目录/参考文献）

### 6.6 对话导出模块

**ConversationExportService** (接口，两个实现)
- `PdfExportServiceImpl` — PDF 格式导出
- `MarkdownExportServiceImpl` — Markdown 格式导出
- 支持格式：PDF, MARKDOWN, TXT
- 可选参数：includeTimestamps, includeMetadata

### 6.7 数据库服务

**MilvusService** — 向量数据库操作
- `insertVector(chunkId, documentId, content, embedding, metadata)` → Long
- `batchInsertVectors(dataList)` → List<Long>
- `searchSimilarVectors(queryEmbedding, topK)` → List<SearchResult>
- `deleteByChunkId(id)` / `deleteByDocumentId(id)` / `deleteByExpression(expr)`
- `loadCollection()` / `flushCollection()` / `releaseCollection()`
- Milvus 集合：medical_documents，维度 1024，内积相似度

**MinioService** — 对象存储操作
- `uploadFile(file, objectName)` → String (URL)
- `getFileUrl(objectName, expiry)` / `downloadFile(objectName)` → InputStream
- `deleteFile(objectName)` / `deleteFiles(objectNames)`
- `fileExists(objectName)` / `getFileInfo(objectName)` / `listFiles(prefix, recursive)`
- `copyFile(source, target)` / `getBucketUsage()` → BucketUsageInfo
- Dev bucket: `imkqas`, Prod bucket: `medical-documents`

### 6.8 评估模块 (Evaluation)

**DatasetService** (接口 + Impl) — 评估数据集管理
- `createDataset(dataset)` / `listDatasets(page, size, domain, status)` / `getDataset(id)`
- `addItem(datasetId, item)` / `batchImportItems(datasetId, items)` → int
- `listItems(datasetId, page, size)` / `getAllItems(datasetId)`
- `importFromJson(jsonContent, name)` / `exportToJson(datasetId)`
- 数据集评估维度标注：groundTruthChunkIds, relevanceLabels, safetyLevel, expectedResponseType

**EvaluationService** (接口 + Impl) — 评估执行引擎
- `startEvaluation(datasetId, runName, evalDimensions, skipLlmEval, sampleSize)` → Long
- `getRunStatus(runId)` / `getRunSummary(runId)` → EvalRun
- `stopEvaluation(runId)` / `listRuns(page, size)`
- 评估维度：检索质量(recall@k, precision@k, MRR, NDCG)、融合质量、重排序质量、生成质量(faithfulness, relevance)、安全质量
- 管线快照追踪：`getPipelineSnapshot(runId, queryResultId)`

**ReportService** (接口 + Impl) — 评估报告生成
- `generateConsoleReport(runId)` / `generateJsonReport(runId)` / `generateHtmlReport(runId)`
- `generateComparisonReport(runIds)` — 多运行对比报告

**OnlineMetricsCollector** — 在线监控
- 采集间隔 60s，数据保留 30 天
- 监控维度：请求量、延迟分位数、缓存命中率、同义词覆盖率、安全阻断率、置信度分布桶、检索模式分布、质量过滤统计、LLM Token 统计
- 存储表：online_metrics_snapshot (40+ 指标字段)

## 7. API 接口设计

### 7.1 控制器总览

系统共有 21 个控制器，分布为 7 个 API 组。

| 控制器 | 基础路径 | 端点数 | 认证 |
|--------|---------|--------|------|
| AuthController | `/api/auth` | 7 | 部分公开 |
| UserController | `/api/users` | 9 | JWT |
| MessageController | `/api/messages` | 7 | JWT |
| DocumentController | `/api/documents` | 8 | JWT |
| DocumentChunkController | `/api/document-chunks` | 8 | JWT |
| ConversationController | `/api/conversations` | 10 | JWT |
| DrugController | `/api/drugs` | 7 | JWT |
| QaController | `/api/qa` | 5 | JWT |
| DepartmentTriageController | `/api/triage` | 4 | JWT |
| RagController | `/api/rag` | 4 | ADMIN |
| RetrievalTestController | `/api/rag/retrieval-test` | 5 | ADMIN |
| SynonymTestController | `/api/rag/synonym-test` | 3 | ADMIN |
| AdminController | `/api/admin` | 8 | ADMIN |
| ContraindicationController | `/api/admin/contraindications` | 7 | ADMIN |
| EvaluationController | `/api/evaluation` | 13 | ADMIN |
| FhirPatientController | `/api/his/fhir/Patient` | 7 | JWT |
| FhirObservationController | `/api/his/fhir/Observation` | 7 | JWT |
| FhirConditionController | `/api/his/fhir/Condition` | 6 | JWT |
| FhirQuestionnaireResponseController | `/api/his/fhir/QuestionnaireResponse` | 6 | JWT |
| FhirBundleController | `/api/his/fhir/Bundle` | 3 | JWT |
| InterviewController | `/api/his/interview` | 21 | JWT |

总计约 **130 个端点**。

### 7.2 核心 API 详解

#### 智能问答 API

```http
POST /api/qa/ask
Content-Type: application/json
Authorization: Bearer {token}

{
  "query": "高血压患者应该注意什么？",
  "userId": 1,
  "conversationId": 10
}

Response 200:
{
  "success": true,
  "data": {
    "query": "高血压患者应该注意什么？",
    "answer": "高血压患者应注意...(包含来源引用)",
    "retrievedContext": ["文档1片段...", "文档2片段..."],
    "confidence": 0.87,
    "processingTime": 1234,
    "modelUsed": "qwen-plus",
    "intentType": "KNOWLEDGE_QUERY",
    "questionnaireSuggestion": null
  }
}
```

#### SSE 流式问答

```http
POST /api/qa/stream
Content-Type: application/x-www-form-urlencoded

query=高血压患者应该注意什么&userId=1&conversationId=10

Response (text/event-stream):
event: chunk
data: {"content": "高"}
event: chunk
data: {"content": "血压"}
...
event: retrievalPath
data: {"steps": [...], "totalDurationMs": 1234, "cacheHit": false, "intentType": "KNOWLEDGE_QUERY"}
event: done
data: {"type": "complete"}
```

#### 填表助手 API

```http
POST /api/his/interview/suggest
{"userInput": "最近一周情绪很低落，睡不好"}

Response:
{
  "matched": true,
  "questionnaire": { "id": "PHQ-9", "title": "PHQ-9 抑郁筛查量表", ... },
  "suggestionText": "根据您的描述，我推荐您填写「PHQ-9 抑郁筛查量表」...",
  "confidence": 0.85
}

POST /api/his/interview/start
{"questionnaireId": "PHQ-9", "userId": 1, "conversationId": 10}

POST /api/his/interview/start-llm        (SSE 流式)
POST /api/his/interview/llm-answer       (SSE 流式)
POST /api/his/interview/{sessionId}/batch-submit
```

### 7.3 通用 API 响应格式

```java
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    private LocalDateTime timestamp;

    static <T> ApiResponse<T> success(T data);
    static <T> ApiResponse<T> error(String message);
    static <T> ApiResponse<Pagination<T>> pagination(List<T>, long total, int page, int size);

    class Pagination<T> {
        List<T> data; long total; int page; int size; int totalPages;
    }
}
```

## 8. 数据库设计

### 8.1 数据库总览

系统使用 MySQL 8.0，通过 10 个 Flyway 迁移脚本管理 29 张表。

| 迁移 | 表数 | 表名 |
|------|------|------|
| V1 | 7 | users, documents, document_chunks, conversations, messages, verification_codes, system_config |
| V2 | 3 | drugs, drug_interactions, drug_aliases |
| V3 | 3 | medical_synonym_mapping, unmapped_term_queue, synonym_expansion_stats |
| V4 | 6 | eval_dataset, eval_dataset_item, eval_run, eval_query_result, eval_pipeline_snapshot, online_metrics_snapshot |
| V5 | 4 | fhir_patient_cache, fhir_observation_cache, fhir_condition_cache, fhir_questionnaire_response_cache |
| V6 | 3 | interview_sessions, interview_messages, analysis_reports |
| V7 | 2 | fhir_diagnostic_report_cache, fhir_risk_assessment_cache |
| V8 | 1 | drug_population_contraindication (含 28 条种子规则) |
| V9 | — | ALTER: interview_sessions 添加 status 列 |
| V10 | — | ALTER: 细化 status 默认值 + 添加 clarification_count 列 |

### 8.2 核心业务表

#### users — 用户表

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 雪花 ID |
| username | VARCHAR(50) UNIQUE | 用户名 |
| phone | VARCHAR(20) UNIQUE | 手机号 |
| password | VARCHAR(255) | BCrypt 加密 |
| role | ENUM | PATIENT/STUDENT/NURSE/DOCTOR/HEALTH_MANAGER/ADMIN |
| health_profile | JSON | 健康档案：{age, gender, allergies, chronicDiseases, ...} |
| created_at, updated_at | TIMESTAMP | 时间戳 |
| deleted | TINYINT | 逻辑删除 (0/1) |

#### documents — 文档表

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 雪花 ID |
| title | VARCHAR(255) | 文档标题 |
| category | VARCHAR(50) | 医学分类 |
| file_path | VARCHAR(500) | MinIO 存储路径 |
| chunk_count | INT | 分块数量 |
| status | ENUM | UPLOADED/PROCESSING/COMPLETED/FAILED |
| uploaded_by | BIGINT FK | 上传者 |

#### document_chunks — 知识片段表

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 雪花 ID |
| document_id | BIGINT FK | 所属文档 |
| chunk_index | INT | 片段序号 |
| content | TEXT | 文本内容 |
| metadata | JSON | {page, section, ...} |
| vector_id | VARCHAR(100) | Milvus 向量 ID |
| has_contraindication | TINYINT | 含禁忌信息标记 (V8) |
| contraindication_info | TEXT | 禁忌详细信息 (V8) |

#### conversations — 对话表

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 雪花 ID |
| user_id | BIGINT FK | 用户 |
| title | VARCHAR(100) | 对话标题 |
| created_at, updated_at | TIMESTAMP | 时间戳 |
| deleted | TINYINT | 逻辑删除 |

#### messages — 消息表

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 雪花 ID |
| conversation_id | BIGINT FK | 所属对话 |
| role | ENUM | USER/ASSISTANT |
| content | TEXT | 消息内容 |
| source_references | JSON | 引用的文档片段 ID 列表 |
| created_at, updated_at | TIMESTAMP | 时间戳 |
| deleted | TINYINT | 逻辑删除 |

#### drugs — 药品表

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 雪花 ID |
| generic_name | VARCHAR(255) | 通用名 |
| brand_name | VARCHAR(255) | 商品名 |
| english_name | VARCHAR(255) | 英文名 |
| drug_class | VARCHAR(100) | 药品分类 |
| indications | JSON | 适应症列表 |
| contraindications | JSON | 禁忌症列表 |
| adverse_reactions | JSON | 不良反应列表 |
| dosage | TEXT | 用法用量 |
| has_interactions | TINYINT | 存在相互作用记录 |

#### drug_interactions — 药物相互作用表

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 雪花 ID |
| drug_a_id, drug_b_id | BIGINT FK | 两种药品 |
| interaction_type | ENUM | CONTRAINDICATED/SEVERE/MODERATE/MILD/MONITOR/UNKNOWN |
| severity | ENUM | HIGH/MODERATE/LOW |
| description | TEXT | 机制描述 |
| recommendation | TEXT | 用药建议 |

### 8.3 FHIR 缓存表

所有 FHIR 缓存表采用统一结构：结构化字段（用于查询） + `resource_json` JSON 字段（完整 FHIR 数据） + `source` 来源标识。

| 来源值 | 说明 |
|--------|------|
| LOCAL | 本地生成（AI 填表） |
| HIS_SYNC | HIS 系统同步 |
| MANUAL | 手动录入 |

### 8.4 面试持久化表

#### interview_sessions

| 关键列 | 类型 | 说明 |
|--------|------|------|
| session_id | VARCHAR(64) UNIQUE | UUID 会话标识 |
| questionnaire_id | VARCHAR(64) | 问卷模板 ID |
| answers | JSON | 已答题答案 {linkId: {code, display, value}} |
| current_score | INT | 当前累计分数 |
| status | VARCHAR(20) | 生命周期状态 |
| collection_mode | VARCHAR(20) | llm_driven / manual_form |
| degradation_level | VARCHAR(20) | 降级级别 |
| safety_flags | JSON | 安全标记 |
| provenance | JSON | 溯源信息 (答案来源+置信度) |
| consecutive_failures | INT | LLM 连续失败次数 |
| clarification_count | INT | 追问次数 |

#### analysis_reports

| 关键列 | 类型 | 说明 |
|--------|------|------|
| analysis_id | VARCHAR(64) UNIQUE | 分析报告 UUID |
| total_score, max_score | INT | 评分 |
| severity | VARCHAR(20) | 严重程度 |
| interpretation | TEXT | 评分解读 |
| risk_assessment | JSON | 风险评估结构化数据 |
| detail_analysis | JSON | 详细分析 |
| recommendations | JSON | 建议列表 |
| follow_up | JSON | 随访计划 |
| disclaimer | TEXT | 免责声明 |
| raw_llm_response | MEDIUMTEXT | LLM 原始响应 |

### 8.5 同义词与审核表

#### medical_synonym_mapping

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK AUTO | 自增 ID |
| colloquial_term | VARCHAR(200) UNIQUE | 口语化术语 |
| standard_term | VARCHAR(200) | 标准术语 |
| snomed_concept_id | VARCHAR(50) | SNOMED CT 编码 |
| entity_type | VARCHAR(50) | DRUG/DISEASE/SYMPTOM/... |
| source | VARCHAR(50) | MANUAL/HISTORY_LOG/PUBLIC_DATASET/LLM_INFERRED |
| status | VARCHAR(20) | PENDING/APPROVED/REJECTED |
| confidence | DOUBLE | 映射置信度 |
| usage_count | INT | 使用次数 |

#### drug_population_contraindication

| 列 | 类型 | 说明 |
|----|------|------|
| drug_name + population_name | VARCHAR(100) UNIQUE | 药品+人群联合唯一 |
| contraindication_type | ENUM | ABSOLUTE/RELATIVE/CAUTION/UNCLEAR |
| evidence_level | ENUM | GUIDELINE/DRUG_LABEL/RCT/META_ANALYSIS/EXPERT_CONSENSUS/CASE_REPORT |
| is_active | TINYINT | 启用/禁用 |

## 9. 前端架构

### 9.1 技术栈与项目结构

```
frontend/
├── src/
│   ├── api/
│   │   ├── config.ts                    — API 基础配置
│   │   ├── interceptors/auth.interceptor.ts — JWT 拦截器 + 自动刷新
│   │   ├── services/                    — 8 个 API 服务
│   │   │   ├── auth.service.ts          — 登录/注册/Token 管理
│   │   │   ├── qa.service.ts            — 问答/SSE 流式/分诊/药物
│   │   │   ├── conversation.service.ts  — 对话/消息 CRUD + 导出
│   │   │   ├── document.service.ts      — 文档上传/搜索/预览
│   │   │   ├── document-chunk.service.ts— 分块管理
│   │   │   ├── interview.service.ts     — AI 填表 (SSE 流式)
│   │   │   ├── adminService.ts          — 同义词审核
│   │   │   └── contraindicationService.ts— 禁忌规则管理
│   │   └── types/                       — TypeScript 类型定义
│   ├── assets/styles/                   — 20 个 CSS 文件
│   │   ├── variables.css                — CSS 变量 (颜色/间距/阴影)
│   │   ├── design-system.css            — 设计系统基础
│   │   ├── brand-colors.css             — 品牌颜色类
│   │   ├── element-plus-overrides.css   — Element Plus 覆盖
│   │   └── custom-*.css                 — 各模块定制样式
│   ├── components/
│   │   ├── layout/MainLayout.vue        — 主布局 (导航栏+用户菜单)
│   │   └── TermReviewPanel.vue          — 术语审核面板组件
│   ├── router/
│   │   ├── index.ts                     — Vue Router 配置
│   │   ├── routes.ts                    — 路由定义 (10 条)
│   │   └── guards.ts                    — 导航守卫 (认证/权限)
│   ├── stores/auth.store.ts             — Pinia 认证状态管理
│   └── views/                           — 9 个视图文件
│       ├── QaView.vue                   — 智能问答 (2213行)
│       ├── KnowledgeView.vue            — 知识库管理 (1012行)
│       ├── ContraindicationRules.vue    — 禁忌规则管理 (1283行)
│       ├── TermReview.vue               — 词条审核 (1072行)
│       ├── DashboardView.vue            — 仪表板 (459行)
│       ├── LoginView.vue / RegisterView.vue / ForgotPasswordView.vue
│       └── NotFoundView.vue             — 404 页面
```

### 9.2 路由设计

| 路径 | 组件 | Meta | 说明 |
|------|------|------|------|
| `/` | 重定向 | requiresAuth | → `/dashboard` |
| `/login` | LoginView | guestOnly | 登录页 |
| `/register` | RegisterView | guestOnly | 注册页 |
| `/forgot-password` | ForgotPasswordView | guestOnly | 忘记密码 |
| `/dashboard` | DashboardView | requiresAuth | 仪表板 |
| `/qa` | QaView | noLayout | 智能问答（无外壳布局） |
| `/knowledge` | KnowledgeView | noLayout | 知识库管理 |
| `/contraindication-rules` | ContraindicationRules | requiresAuth, noLayout | 禁忌规则 |
| `/term-review` | TermReview | requiresAuth, noLayout | 词条审核 |
| `/:pathMatch(.*)*` | NotFoundView | — | 404 |

**路由守卫逻辑：**
1. `requiresAuth` + 未登录 → 重定向到 `/login?redirect=原路径`
2. 已登录访问 `/login` 或 `guestOnly` → 重定向到 `/`
3. 所有页面标题设为 `"{title} - IMKQAS"`

### 9.3 设计系统

**颜色变量：** 主色 `#005eb8`，基于 Material Design 3 扩展规范
**圆角：** 正方形风格 (2px-12px 梯度)
**字体：** 标题 Manrope，正文 Inter
**阴影层级：** soft(0.08) → medium(0.12) → hard(0.16) → glow(0.2)
**品牌颜色已从 Tailwind 迁移至自定义 CSS 类**（`text-brand`, `bg-brand` 等）

### 9.4 核心前端功能

**QaView (2213 行)** — 最复杂的视图：
- 左侧对话列表（新建/切换/删除/导出）
- 中间聊天区域（Markdown 渲染 + SSE 流式输出）
- 检索过程可视化面板（管线步骤 + 延迟 + 状态）
- 问卷建议弹窗（AI 填表集成）
- 右侧健康档案快速查看

**KnowledgeView (1012 行)**：
- 拖拽上传 PDF 文档
- 分类侧边栏 + 文档搜索
- 文档处理状态实时追踪
- 文档预览 (PDF 内嵌 + 文本提取)

**InterviewService（前端 SSE 处理）**：
- 通用 SSE 流读取器
- LLM 驱动填表事件处理（ASK_QUESTION/RECORD_ANSWER/CLARIFY/COMPLETE/EMERGENCY_INTERRUPT）
- 降级通知处理

### 9.5 Vite 配置

```typescript
export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': 'src' } },
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
  },
  build: { outDir: 'dist', sourcemap: false }
})
```

## 10. 安全设计

### 10.1 认证体系

- **JWT 认证**：RSA 256 签名，Token 过期 24h
- **Token 刷新**：前端拦截器自动处理 401 → 尝试刷新 → 失败跳登录
- **手机验证码登录**：60s 发送间隔，5 分钟有效期

### 10.2 三层安全兜底

| 层级 | 检查点 | 机制 |
|------|--------|------|
| 第 1 层 | 查询阶段 | 急症关键词三级匹配 → 命中阻断，返回就医指导 |
| 第 2 层 | 检索阶段 | 置信度门控 → 最高分低于阈值时阻断 |
| 第 3 层 | 生成阶段 | 答案安全净化 → 移除风险内容 |
| 追加 | 答案末尾 | 禁忌安全提示 + 矛盾裁决提示 |

### 10.3 限流策略

| 角色 | QPS | 突发 |
|------|-----|------|
| 普通用户 | 10/s | 20 |
| 管理员 | 50/s | 100 |
| 文档上传 | 2/s | — |
| 批量操作 | 5/s | — |

### 10.4 数据访问控制

- Repository 层通过 MyBatis Plus `@TableLogic` 实现逻辑删除
- Service 层区分用户角色权限
- 对话隔离：用户只能访问自己的对话和消息
- API 层通过 `@PreAuthorize` 和 `@RolesAllowed` 控制管理端点

## 11. 部署架构

### 11.1 服务组件拓扑

```
                    ┌──────────────┐
                    │   Nginx 80   │  ← 反向代理 + 静态资源
                    └──────┬───────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
     ┌────────────┐ ┌────────────┐ ┌────────────┐
     │ Spring Boot│ │  Milvus    │ │  MinIO     │
     │ :8080      │ │  :19530    │ │  :9000     │
     └─────┬──────┘ └────────────┘ └────────────┘
           │
     ┌─────┼──────────────┐
     ▼     ▼              ▼
  ┌──────┐ ┌────────┐ ┌──────────┐
  │MySQL │ │ Redis  │ │RabbitMQ  │
  │:3306 │ │ :6379  │ │ :5672    │
  └──────┘ └────────┘ └──────────┘
```

### 11.2 环境依赖

| 组件 | 版本 | 用途 | 端口 |
|------|------|------|------|
| JDK | 21 | Java 运行时 | — |
| MySQL | 8.0 | 主数据库 (29 表) | 3306 |
| Redis | 7.x | 缓存 / 限流 / 会话 / 分布式锁 | 6379 |
| Milvus | 2.3.x | 向量存储 (1024 维) | 19530 |
| MinIO | latest | 文档对象存储 | 9000/9001 |
| RabbitMQ | 3.x | 同义词审核异步队列 | 5672/15672 |
| Snowstorm | — | SNOMED CT 术语服务 (可选) | 8082 |
| Nginx | — | 反向代理 | 80/443 |

### 11.3 JVM 启动参数

```bash
java -Xms512m -Xmx2048m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dspring.profiles.active=prod \
  -jar imkqas.jar
```

### 11.4 外部服务容灾配置

| 服务 | 超时 | 重试 | 降级策略 |
|------|------|------|----------|
| Milvus | 10s | 1 | 回退到纯关键词检索 |
| LLM (DashScope) | 60s | 0 | 断路器触发后拒绝请求 |
| 意图路由 LLM | 2s | 0 | 降级至规则解析器 |
| Snowstorm | 10s | 3 | 本地映射表兜底 |
| Redis | 2s | — | 直接穿透到数据库 |
| RabbitMQ | 5s | 3 (指数退避) | 跳过消息发送 |

### 11.5 关键配置摘要

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `server.port` | 8080 | 应用端口 |
| `spring.datasource.hikari.maximum-pool-size` | 10 | 数据库连接池 |
| `spring.datasource.hikari.max-lifetime` | 240000 (4min) | 小于 MySQL wait_timeout |
| `spring.cache.redis.time-to-live` | 3600000 (1h) | Redis 缓存 TTL |
| `imkqas.rag.retrieval.mode` | hybrid | 混合检索模式 |
| `imkqas.rag.retrieval.weights` | 0.6 / 0.4 | 向量 / 关键词权重 |
| `imkqas.rag.retrieval.rerank-top-k` | 5 | 送入 LLM 的片段数 |
| `imkqas.rag.llm.model` | qwen-plus | 大语言模型 |
| `imkqas.rag.safety.confidence.min-threshold` | 0.35 | 置信度硬阻断阈值 |
| `imkqas.security.jwt.expiration` | 86400000 (24h) | Token 过期时间 |
| `imkqas.rag.document.chunk.size` | 500 | 文档分块大小 |
| `imkqas.rag.document.chunk.overlap` | 50 | 分块重叠 |

## 12. 开发规划

### 12.1 已完成阶段

| 阶段 | 内容 | 核心交付 |
|------|------|----------|
| **Phase 1 — 基础架构** | 项目骨架、认证、用户管理 | Spring Boot 项目、JWT 认证、6 种角色体系 |
| **Phase 2 — 文档处理** | PDF 解析、分块、向量化 | MinerU 集成、语义分块、Milvus 存储 |
| **Phase 3 — RAG 管线** | 检索-融合-重排-生成 | 12 步管线、双路混合检索、RRF 融合、多因子重排 |
| **Phase 4 — 安全兜底** | 三层安全体系 | 急症预检、置信度门控、回答安全净化、禁忌检测 |
| **Phase 5 — 问答增强** | 查询改写、同义词扩展 | 医学实体识别、口语→标准术语映射、RabbitMQ 审核队列 |
| **Phase 6 — 科室分诊** | 症状→科室推荐 | 混合引擎（规则 + LLM）、急诊检测 |
| **Phase 7 — 智能填表** | FHIR 问卷采集 | 7 种 FHIR 资源、双模式采集、意图路由、分析报告 |
| **Phase 8 — 药物互斥** | 药物-人群禁忌检测 | 28 条种子规则、禁忌标注管线 |
| **Phase 9 — 评估体系** | 离线评估 + 在线监控 | 5 维评估指标、40+ 在线指标、管线快照追踪 |

### 12.2 规划中功能

| 优先级 | 功能 | 说明 |
|--------|------|------|
| P0 | HIS 系统对接 | `imkqas.his.enabled=true` 后对接真实 HIS FHIR 接口 |
| P1 | 多语言支持 | 英文医学术语检索与生成 |
| P1 | 图片问答 | 上传检查报告图片，OCR + RAG 问答 |
| P2 | 知识图谱集成 | 疾病-症状-药品关系图谱辅助推理 |
| P2 | 实时语音问答 | WebSocket + TTS/STT 集成 |
| P3 | 多租户支持 | SaaS 化部署，租户数据隔离 |

## 13. 风险评估与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| LLM 生成错误医疗建议 | **严重** | 中 | 三层安全兜底 + 置信度门控 + 免责声明强制追加 |
| 向量检索召回不相关文档 | 高 | 中 | 双路混合检索 + RRF 融合 + 质量过滤 + 矛盾检测 |
| LLM API 不可用 | 高 | 低 | 断路器 (3 次失败触发) + 意图路由回退 + 规则引擎降级 |
| 数据库连接池耗尽 | 高 | 低 | HikariCP max-lifetime < MySQL wait_timeout + 连接验证 |
| Redis 不可用 | 中 | 低 | 缓存穿透到数据库 + 限流器本地降级 |
| Milvus 不可用 | 中 | 低 | 自动回退到纯关键词检索 (Lucene) |
| 文档解析质量差 | 中 | 中 | MinerU + Tesseract OCR 双通道 + PDF 预检查分类 |
| 同义词映射覆盖率不足 | 中 | 中 | 三级链 (本地→Snowstorm→LLM) + 未映射告警 + RabbitMQ 审核 |
| 高并发下响应延迟 | 中 | 低 | 语义缓存 + Redis 结果缓存 + 限流策略 |
| 用户隐私数据泄露 | **严重** | 低 | JWT 认证 + 对话隔离 + 角色权限控制 + HTTPS |

## 附录

### A. 术语表

| 术语 | 说明 |
|------|------|
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| RRF | Reciprocal Rank Fusion，倒数排名融合算法 |
| FHIR | Fast Healthcare Interoperability Resources，HL7 医疗数据交换标准 |
| SNOMED CT | Systematized Nomenclature of Medicine Clinical Terms，临床医学术语标准 |
| SSE | Server-Sent Events，服务器推送事件（单向流式传输） |
| Milvus | 开源向量数据库，用于存储和检索文档嵌入向量 |
| MinerU | PDF 内容提取工具，将 PDF 转换为结构化 Markdown |
| DashScope | 阿里云百炼 AI 服务平台（提供 LLM / Embedding / Rerank API） |
| MinIO | 开源对象存储，兼容 S3 协议 |
| RabbitMQ | 开源消息队列，用于同义词审核异步处理 |
| Snowstorm | SNOMED CT 术语服务器，提供 FHIR API 查询标准化术语 |
| Flyway | 数据库版本迁移工具，通过 SQL 脚本管理表结构变更 |
| MyBatis Plus | MyBatis 增强工具，提供 CRUD 封装、分页、逻辑删除等 |
| HikariCP | 高性能 JDBC 连接池 |
| 断路器 | 熔断保护模式，连续失败后自动拒绝请求，防止级联故障 |

### B. 参考文献

1. Lewis, P., et al. "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks." NeurIPS 2020.
2. HL7 FHIR R4 Specification. https://hl7.org/fhir/R4/
3. SNOMED CT International. https://www.snomed.org/
4. Cormack, G.V., et al. "Reciprocal Rank Fusion Outperforms Condorcet and Individual Rank Learning Methods." SIGIR 2009.
5. DashScope API 文档. https://help.aliyun.com/document_detail/dashscope.html
6. Milvus 向量数据库文档. https://milvus.io/docs/
7. MyBatis Plus 官方文档. https://baomidou.com/
8. Spring Boot 3.2.x Reference. https://docs.spring.io/spring-boot/

### C. 修订记录

| 日期 | 版本 | 修订人 | 说明 |
|------|------|--------|------|
| 2026-04-04 | v0.1 | 系统 | 初始版本，基础系统设计 |
| 2026-07-14 | v1.0 | feng | 根据实际代码全面重写：更新文件数 (274)、新增 FHIR 模块、意图路由、双模式采集、SSE 流式、评估系统、前端架构 |

---

*本文档由 IMKQAS 开发团队维护，随代码同步更新。最后更新：2026-07-14*