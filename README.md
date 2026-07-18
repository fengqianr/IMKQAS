# IMKQAS 医疗知识问答系统

**I**ntelligent **M**edical **K**nowledge **Q**uestion **A**nswering **S**ystem —— 基于 RAG（检索增强生成）架构的智能医疗知识问答系统，提供医学知识问答、药物查询、FHIR 问卷采集等医疗垂直领域能力，并内置多层医疗安全兜底机制。

---

## 目录

- [系统架构](#系统架构)
- [核心功能](#核心功能)
- [RAG 问答管线](#rag-问答管线)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [API 概览](#api-概览)
- [数据库设计](#数据库设计)
- [评估与测试体系](#评估与测试体系)
- [部署](#部署)
- [相关文档](#相关文档)

---

## 系统架构

```
                        ┌─────────────────────────────┐
                        │   前端 Vue 3 + Element Plus  │
                        │  问答 / 知识库 / 审核 / 管理   │
                        └──────────────┬──────────────┘
                                       │ REST / SSE
                        ┌──────────────▼──────────────┐
                        │   Spring Boot 3.2.5 (Java 21)│
                        │                              │
        ┌───────────────┼──────────────────────────────┤
        │               │                              │
   ┌────▼────┐    ┌─────▼─────┐                 ┌──────▼─────┐
   │ 意图路由 │    │ RAG 管线   │                 │ FHIR 问卷  │
   │ 6 层过滤 │    │ 12 步链路  │                 │ 访谈引擎   │
   └─────────┘    └─────┬─────┘                 └────────────┘
                        │
     ┌──────────┬───────┼────────┬───────────┬──────────┐
     │          │       │        │           │          │
┌────▼───┐ ┌────▼───┐ ┌─▼────┐ ┌─▼───────┐ ┌─▼──────┐ ┌─▼────────┐
│ MySQL  │ │ Milvus │ │Lucene│ │  Redis  │ │ MinIO  │ │ RabbitMQ │
│ 业务数据│ │ 向量库  │ │ BM25 │ │ 缓存/锁 │ │ 文件存储│ │ 审核队列  │
└────────┘ └────────┘ └──────┘ └─────────┘ └────────┘ └──────────┘
                        │
                  ┌─────▼──────────────────┐
                  │ 阿里云百炼 DashScope     │
                  │ qwen-plus / embedding-v3│
                  │ gte-rerank-v2           │
                  └────────────────────────┘
```

## 核心功能

### 智能问答（RAG）
- **双路召回 + RRF 融合**：Milvus 向量检索（权重 0.6）+ Lucene BM25 关键词检索（权重 0.4），RRF（k=10）融合排序
- **三级漏斗查询改写**：拼写纠正（THUOCL 医学词库 + SymSpell）→ 停用词简化 → 医疗术语化
- **同义词扩展三级链**：本地映射表 → SNOMED CT（Snowstorm FHIR 服务器）→ LLM 推断 + RabbitMQ 人工审核队列
- **五维度多因子重排序**：权威性（证据金字塔 0.27）/ 时效性（指数衰减 0.13）/ 语义相似度（交叉编码器 0.32）/ 意图匹配（0.18）/ 层级相关性（0.10）
- **语义缓存链**：Caffeine L1 + Redis L2，分布式锁防缓存击穿，知识库版本号自动失效
- **SSE 流式输出**：支持流式问答响应

### 医疗安全兜底（三道防线）
1. **急症预检**：CRITICAL / HIGH / MEDIUM 三级急症关键词库，命中即阻断并返回就医警告
2. **置信度门控**：检索置信度 < 0.35 硬阻断，< 0.6 附加警告
3. **答案净化**：正则屏蔽药物剂量建议 + 自动追加免责声明

### 意图路由（6 层架构）
Caffeine 缓存 → 关键词/句式规则 → 正则匹配 → Redis 缓存 → LLM 分类（2s 超时 + 断路器）→ 默认兜底，识别 `KNOWLEDGE_QUERY` / `DATA_COLLECTION` / `MIXED` 三类意图。

### 文档处理流水线
- **PDF 解析**：MinerU 云端 API（数字原生 PDF）→ Tesseract OCR（chi_sim+eng，扫描件降级）→ PDFBox 兜底
- **医学结构感知分片**：识别章节层级、表格保护、ICD 编码检测、兄弟上下文合并、面包屑路径（分块大小 500 / 重叠 50）
- **药物-人群禁忌标注**：文档分块预处理时自动标注禁忌信息，支持 ABSOLUTE / RELATIVE / CAUTION / UNCLEAR 四级禁忌与证据等级（指南/说明书/RCT/荟萃分析/专家共识）

### 医疗垂直能力
- **药物查询**：药品信息、别名、药物相互作用查询
- **FHIR 问卷采集**：LLM 驱动的自适应访谈引擎，支持 PHQ-9 / GAD-7 / ISI 等量表，三级降级（LLM → 规则解析 → 手动表单），采集完成后自动生成 FHIR DiagnosticReport / RiskAssessment
- **FHIR R4 接口**：Patient / Condition / Observation / Bundle / QuestionnaireResponse 资源接口（HAPI FHIR 7.4.0），预留 HIS 对接

### 运营与评估
- **离线评估体系**：检索 / 生成 / 融合 / 安全四维度评估器，评估数据集管理
- **在线监控**：P99/P95/P50 延迟、错误率、未映射术语告警
- **RAG 链路可视化**：全链路追踪每步耗时与中间结果
- **对话导出**：Markdown / PDF 格式导出

## RAG 问答管线

`QaServiceImpl.answer()` 实现的 12 步完整链路：

| 步骤 | 环节 | 核心实现 |
|------|------|----------|
| 1 | 意图路由 | `IntentRouterImpl`（6 层过滤，DATA_COLLECTION 直接转问卷） |
| 2 | 查询预处理 | `QueryRewriteServiceImpl`（三级漏斗）+ `SynonymExpansionServiceImpl` |
| 3 | 安全兜底 ①：急症预检 | `SafetyGuardService`（三级关键词库） |
| 4-5 | 双路召回 + RRF 融合 | `MultiRetrievalServiceImpl` + `RrfFusionServiceImpl` |
| 6 | 质量过滤 | `QualityFilterService`（片段长度、黑名单域名） |
| 7 | 矛盾检测 | `QualityFilterService`（权威性裁决） |
| 8 | 多因子重排序 | `MultiFactorRerankServiceImpl` + `CrossEncoderRerankServiceImpl`（gte-rerank-v2） |
| 9 | 安全兜底 ②：置信度门控 | 硬阻断 < 0.35，警告 < 0.6 |
| 10 | 语义缓存 | `SemanticCacheServiceImpl`（Caffeine L1 → Redis L2） |
| 11 | LLM 生成 | `LlmServiceImpl`（qwen-plus，多级缓存） |
| 12 | 安全兜底 ③：答案净化 | 屏蔽剂量建议 + 免责声明 |

## 技术栈

### 后端

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 / 框架 | Java / Spring Boot | 21 / 3.2.5 |
| ORM | MyBatis Plus | 3.5.5 |
| 数据库 | MySQL | 8.x（Connector/J 8.3.0） |
| 向量数据库 | Milvus SDK Java | 2.3.6 |
| 全文检索 | Lucene（BM25） | 9.10.0 |
| 缓存 | Redis + Caffeine | - / 3.1.8 |
| 消息队列 | RabbitMQ（Spring AMQP） | managed |
| 对象存储 | MinIO | 8.5.10 |
| LLM 集成 | LangChain4j（OpenAI / HuggingFace 适配器） | 0.29.0 |
| 大模型 | 阿里云百炼 qwen-plus / text-embedding-v3（1024 维）/ gte-rerank-v2 | - |
| 安全 | Spring Security + JJWT | - / 0.11.5 |
| FHIR | HAPI FHIR（R4） | 7.4.0 |
| PDF / OCR | PDFBox / Tess4J | 3.0.2 / 5.9.0 |
| Office 解析 | Apache POI | 5.2.5 |
| NLP | HanLP | 1.8.4 |
| 数据库迁移 | Flyway | managed |
| API 文档 | springdoc-openapi（Swagger UI） | 2.5.0 |
| 测试覆盖率 | JaCoCo（LINE ≥ 80%，BRANCH ≥ 70%） | 0.8.12 |

### 前端

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | Vue 3 + TypeScript | 3.4 / 5.4 |
| 构建 | Vite | 5.x |
| UI 组件 | Element Plus | 2.4 |
| 状态管理 | Pinia | 2.1 |
| HTTP | Axios（JWT 拦截器 + 401 自动刷新） | - |
| 样式 | Tailwind CSS（Material Design 3 扩展色板） | - |
| 图表 | ECharts | 5.4 |

## 项目结构

```
IMKQAS/
├── pom.xml                          # Maven 配置
├── Dockerfile                       # 多阶段构建
├── docker-compose.yml               # 完整服务编排
├── docker-compose-app-only.yml      # 仅应用容器
├── docker-compose-snowstorm.yml     # SNOMED CT Snowstorm 服务
├── DEPLOYMENT.md                    # 部署指南
├── src/main/java/com/imkqas/
│   ├── ImkqasApplication.java       # 启动类
│   ├── controller/                  # 20 个 Controller
│   │   ├── qa/                      # 问答入口（ask / intent / SSE / 药物）
│   │   ├── rag/                     # RAG 检索、管理后台、禁忌规则、检索测试
│   │   ├── his/                     # FHIR 资源 + 问卷访谈
│   │   ├── drug/                    # 药品查询
│   │   └── evaluation/              # 评估管理
│   ├── service/
│   │   ├── rag/                     # RAG 核心（检索/融合/重排/缓存/安全/LLM）
│   │   ├── his/                     # 意图路由、访谈引擎、FHIR 服务、分析 Agent
│   │   ├── document/                # 文档处理流水线
│   │   ├── drug/                    # 药品服务
│   │   ├── evaluation/              # 四维度评估器 + 在线监控
│   │   ├── export/                  # 对话导出（Markdown/PDF）
│   │   ├── snomed/                  # SNOMED CT 术语服务
│   │   ├── dataBase/                # Milvus / MinIO 封装
│   │   └── common/                  # 用户、认证、对话、消息
│   ├── entity/                      # 实体类（drug/contraindication/synonym/evaluation/his）
│   ├── mapper/                      # 28 个 MyBatis Plus Mapper
│   ├── config/                      # 17 个配置类
│   └── utils/                       # 医学分片器、PDF 转换、JWT 等工具
├── src/main/resources/
│   ├── application.yml              # 应用配置
│   └── db/migration/                # Flyway 迁移脚本 V1~V10
├── frontend/                        # Vue 3 前端
│   └── src/
│       ├── api/services/            # 8 个业务 API Service
│       ├── views/                   # 9 个页面
│       ├── router/                  # 路由 + 认证守卫
│       └── stores/                  # Pinia 状态
├── scripts/                         # Python 评估测试脚本及结果
└── docs/                            # 设计文档（后端/前端）
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8.x、Redis、RabbitMQ、MinIO、Milvus 2.3.x（可通过 docker-compose 启动）
- 阿里云百炼 DashScope API Key（LLM / Embedding / Rerank）

### 后端启动

```bash
# 1. 配置 src/main/resources/application.yml 中的数据源与中间件连接信息
#    （MySQL / Redis / RabbitMQ / MinIO / Milvus / DashScope API Key）

# 2. 编译
mvn compile

# 3. 启动（Flyway 自动执行数据库迁移）
mvn spring-boot:run

# 打包
mvn package -DskipTests
```

启动后访问：
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 健康检查：`http://localhost:8080/api/qa/health`

### 前端启动

```bash
cd frontend
npm install
npm run dev          # 开发服务器
npm run build        # 生产构建
npm run type-check   # 类型检查
```

主要页面路由：

| 路由 | 页面 |
|------|------|
| `/dashboard` | 仪表板 |
| `/qa` | 智能问答 |
| `/knowledge` | 知识库管理 |
| `/contraindication-rules` | 药物-人群禁忌规则管理 |
| `/term-review` | 同义词词条审核 |

### 测试

```bash
mvn test                      # 全部测试
mvn test -Dtest=测试类名       # 单个测试类
```

## API 概览

| 模块 | 路径前缀 | 说明 |
|------|----------|------|
| 认证 | `/api/auth` | 登录、注册、验证码、密码重置 |
| 用户 | `/api/users` | 用户资料、健康档案 |
| 问答 | `/api/qa` | 提问、意图识别、SSE 流式、药物查询 |
| RAG | `/api/rag` | 检索 API、语义缓存管理、检索/同义词测试 |
| 对话 | `/api/conversations` | 会话 CRUD、导出 |
| 消息 | `/api/messages` | 消息记录 |
| 文档 | `/api/documents` | 上传、下载、删除、列表 |
| 文档分块 | `/api/document-chunks` | 分块查询、重建索引 |
| 药品 | `/api/drugs` | 药品信息、相互作用 |
| 评估 | `/api/evaluation` | 数据集管理、评估运行 |
| 管理 | `/api/admin` | 缓存版本、统计监控、索引管理、禁忌规则 |
| FHIR | `/api/his/fhir/*` | Patient / Condition / Observation / Bundle / QuestionnaireResponse |
| 访谈 | `/api/his/interview` | FHIR 问卷访谈采集 |

完整 API 文档见 Swagger UI（`springdoc-openapi 2.5.0`）。

## 数据库设计

Flyway 迁移脚本（`src/main/resources/db/migration/`）共 10 个版本，覆盖 20+ 张表：

| 版本 | 内容 |
|------|------|
| V1 | 基础表：用户、文档、分块、对话、消息、验证码、系统配置 |
| V2 | 药品知识库：drugs / drug_aliases / drug_interactions |
| V3 | 同义词映射 + 未映射词条审核队列 |
| V4 | 评估体系：数据集、评估运行、结果、管线快照、在线指标 |
| V5 | FHIR 资源缓存：Patient / Observation / Condition / QuestionnaireResponse |
| V6 | 问卷访谈持久化：sessions / messages |
| V7 | FHIR DiagnosticReport / RiskAssessment 缓存 |
| V8 | 药物-人群禁忌规则 + 分块禁忌标注字段 |
| V9-V10 | 访谈会话状态机细化 |

## 评估与测试体系

`scripts/` 目录提供完整的 Python 评估脚本：

| 脚本 | 用途 |
|------|------|
| `intent_accuracy_test.py` | 意图识别准确率（150 条测试集 + 60 条盲测集） |
| `p99_latency_test.py` | P99 响应延迟（冷/热启动各 500 条对比） |
| `ab_safety_test.py` | 安全兜底 A/B 测试 |
| `test_rerank_thresholds.py` | 重排序阈值调优 |
| `hallucination_results/` | 幻觉检测测试 |
| `k_tuning_results/` | 检索 K 值调优报告 |
| `confidence_tuning_results/` | 置信度阈值调优报告 |
| `batch_infer_terms.py` / `batch_write_mappings.py` | 同义词批量推断与写入 |

## 部署

支持 Docker 化部署，详见 [DEPLOYMENT.md](DEPLOYMENT.md)：

```bash
# 完整服务编排（含中间件）
docker-compose up -d

# 仅应用容器（中间件独立部署）
docker-compose -f docker-compose-app-only.yml up -d

# SNOMED CT Snowstorm 术语服务（可选）
docker-compose -f docker-compose-snowstorm.yml up -d
```

## 相关文档

| 文档 | 位置 |
|------|------|
| 系统设计方案 | `docs/back/IMKQAS系统设计方案.md` |
| RAG 检索管线 | `docs/back/RAG 检索管线.md` |
| 意图路由设计 | `docs/back/意图路由设计方案.md` |
| 医疗安全兜底方案 | `docs/back/医疗安全兜底模块技术方案.md` |
| 医学文档分片方案 | `docs/back/医学文档结构感知分片方案.md` |
| FHIR 问卷采集设计 | `docs/back/FHIR医疗问卷采集设计方案.md` |
| 评估体系建设计划 | `docs/back/RAG链路评估体系建设计划.md` |
| 前端设计规范 | `docs/frontend/DESIGN.md` |
| 部署指南 | `DEPLOYMENT.md` |

---

## 免责声明

本系统提供的医疗信息仅供参考，不能替代专业医生的诊断和治疗建议。如有身体不适，请及时就医。
