# MediRAG — 医疗知识智能问答系统规范文档

**版本**: 2.0
**日期**: 2026-07-18
**状态**: 已实施（持续迭代中）

---

## 项目概述

### 项目目标
构建基于 RAG（Retrieval-Augmented Generation）架构的医疗知识智能问答系统（IMKQAS），为医学生、基层医护、普通患者和健康管理师提供专业、准确、安全的医疗知识查询服务。

### 核心价值
- **准确性**: 基于权威医学文献双路检索，多因子重排序，避免大模型幻觉
- **安全性**: 三道医疗安全防线（急症预检、置信度门控、答案净化）
- **专业性**: 医疗术语规范化（三级同义词链 + SNOMED CT）、药物-人群禁忌标注
- **可视化**: RAG 检索链路全程可追踪，展示每步耗时与中间结果
- **易用性**: 对话式交互，SSE 流式输出体验
- **可评估**: 离线四维度评估 + 在线延迟/错误率监控

## 系统架构

### 五层架构（单体 Spring Boot 应用）
1. **展示层**: Vue 3 + TypeScript + Element Plus + ECharts（响应式前端）
2. **应用层**: Spring Boot 3.2.5 (Java 21) + LangChain4j 0.29.0（核心业务逻辑、JWT 鉴权、Redis 限流计数）
3. **RAG 引擎层**: 意图路由 + 双路召回 + RRF 融合 + 五维度多因子重排序 + 安全兜底（系统亮点）
4. **模型服务层**: 阿里云百炼 DashScope — qwen-plus（生成）+ text-embedding-v3（1024 维向量）+ gte-rerank-v2（交叉编码器精排）
5. **存储层**: MySQL + Milvus + Lucene + Redis + MinIO + RabbitMQ

## 核心功能模块

### 1. 智能问答模块（核心）
- **意图路由**: 6 层过滤架构（Caffeine 缓存 → 关键词/句式规则 → 正则 → Redis → LLM 2s 超时+断路器 → 兜底），识别 KNOWLEDGE_QUERY / DATA_COLLECTION / MIXED 三类意图
- **流式对话**: SSE 推送（`POST /api/qa/stream`），类似 ChatGPT 打字机效果
- **查询改写**: 三级漏斗——拼写纠正（THUOCL 医学词库 + SymSpell）→ 停用词简化 → 医疗术语化
- **同义词扩展**: 本地映射表 → SNOMED CT（Snowstorm FHIR 服务器）→ LLM 推断 + RabbitMQ 人工审核队列
- **来源溯源**: 每条回答标注引用的医学文献片段
- **多轮上下文**: Redis 存储会话上下文
- **医疗安全兜底**: 三道防线（详见"技术实现细节"）
- **药物查询**: 药品信息、别名与药物相互作用检查
- **检索可视化**: PipelineTrace 记录完整 RAG 链路每步耗时与结果

### 2. 知识库管理模块
- 文档上传（PDF / Word / TXT），MinIO 对象存储
- 文档解析流水线: MinerU 云端 API（数字原生 PDF）→ Tesseract OCR（chi_sim+eng，扫描件降级）→ PDFBox 兜底
- 医学结构感知分片: 章节层级识别、表格保护、ICD 编码检测、兄弟上下文合并、面包屑路径（分块 500 / 重叠 50）
- 药物-人群禁忌预标注: 分块入库时自动标注 ABSOLUTE / RELATIVE / CAUTION / UNCLEAR 四级禁忌与证据等级
- 双索引入库: Milvus 向量索引 + Lucene BM25 倒排索引
- 文档预览与切片浏览、索引重建

### 3. FHIR 问卷采集模块（HIS 对接）
- LLM 驱动的自适应访谈引擎，支持 PHQ-9 / GAD-7 / ISI 等标准量表
- 三级降级策略: LLM 解析 → 规则解析 → 手动表单
- 采集完成自动生成 FHIR DiagnosticReport / RiskAssessment
- FHIR R4 资源接口（HAPI FHIR 7.4.0）: Patient / Condition / Observation / Bundle / QuestionnaireResponse
- 会话状态机管理，热数据 Redis + 冷数据 MySQL 持久化

### 4. 用户管理模块
- 手机号 + 验证码登录（JWT 鉴权，24h 过期）
- 健康档案: 年龄/性别/过敏史/慢病史（JSON 字段），问答时注入 Prompt

### 5. 对话管理模块
- 多会话管理（类似 ChatGPT 侧边栏）
- 历史记录查询
- 会话导出（PDF / Markdown）

### 6. 评估与运营模块
- 离线评估: 检索 / 生成 / 融合 / 安全四维度评估器，评估数据集管理
- 在线监控: P99/P95/P50 延迟、错误率、未映射术语告警
- 管理后台: 语义缓存版本管理、统计监控、索引管理、禁忌规则 CRUD、同义词词条审核

## 技术实现细节

### RAG 流程（12 步管线）
```
用户提问 → ① 意图路由 → ② 查询改写+同义词扩展 → ③ 急症预检(安全兜底一)
        → ④⑤ 双路召回+RRF融合 → ⑥ 质量过滤 → ⑦ 矛盾检测
        → ⑧ 五维度多因子重排序 → ⑨ 置信度门控(安全兜底二)
        → ⑩ 语义缓存 → ⑪ LLM生成 → ⑫ 答案净化(安全兜底三) → 流式输出
```

### 多路检索策略
1. **向量检索**: Milvus + text-embedding-v3（1024 维），权重 0.6
2. **关键词检索**: Lucene 9.10.0 BM25 算法（含同义词扩展），权重 0.4
3. **RRF 融合**: Reciprocal Rank Fusion，k=10，双路各取 top 10
4. **精排**: gte-rerank-v2 交叉编码器语义评分，top 5 送入 LLM

### 五维度多因子重排序
| 维度 | 权重 | 说明 |
|------|------|------|
| 语义相似度 | 0.32 | 交叉编码器评分 |
| 权威性 | 0.27 | 证据金字塔（指南 > 荟萃分析 > RCT > 说明书 > 专家共识） |
| 意图匹配度 | 0.18 | 与查询意图的契合度 |
| 时效性 | 0.13 | 指数衰减 + 半衰期 |
| 层级相关性 | 0.10 | 文档章节层级 |

### 医疗安全兜底（三道防线）
1. **急症预检**: CRITICAL / HIGH / MEDIUM 三级急症关键词库，命中即阻断并提示就医
2. **置信度门控**: 检索置信度 < 0.35 硬阻断，< 0.6 附加警告
3. **答案净化**: 正则屏蔽药物剂量建议 + 自动追加免责声明

### 缓存体系
- **语义缓存链**: Caffeine L1（本地）→ Redis L2（分布式），分布式锁防缓存击穿
- **版本号失效**: 知识库更新后语义缓存自动失效
- **LLM 结果缓存**: 查询 MD5 归一化 + 上下文排序作为缓存键
- **意图路由缓存**: Caffeine + Redis 双级

### 数据库设计
- **MySQL**: 20+ 张表，Flyway 迁移脚本 V1~V10（用户、文档、分块、对话、消息、药品、禁忌规则、同义词映射、评估体系、FHIR 缓存、访谈持久化）
- **Milvus**: 文档向量存储（collection `medical_documents`，1024 维，text-embedding-v3）
- **Lucene**: 本地 BM25 倒排索引（`lucene-index/`）
- **Redis**: 查询缓存、会话上下文、分布式锁、限流计数
- **MinIO**: 医学文档文件存储（bucket `imkqas`）
- **RabbitMQ**: 未映射术语异步审核队列（确认模式 + 手动 ACK）

### 限流策略
基于 Redis 计数器实现用户级 API 限流（`RedisService.incrementRateLimitCount`），按用户与接口维度计数、可配置过期窗口。

## API 接口

### 主要端点
- `POST /api/qa/stream` — 流式问答（SSE）
- `POST /api/qa/ask` — 同步问答
- `GET /api/qa/intent` — 意图识别
- `GET /api/qa/drug` — 药物查询
- `GET /api/qa/health` — 健康检查
- `POST /api/rag/*` — RAG 检索与语义缓存管理
- `POST /api/documents` — 上传文档
- `GET /api/document-chunks/*` — 分块查询、重建索引
- `POST /api/auth/login` — 手机号登录
- `POST /api/his/interview/*` — FHIR 问卷访谈采集
- `GET|POST /api/his/fhir/{Patient|Condition|Observation|QuestionnaireResponse}` — FHIR R4 资源
- `POST /api/evaluation/*` — 评估数据集与评估运行
- `GET|POST /api/admin/*` — 缓存版本、统计监控、禁忌规则管理

完整 API 文档: springdoc-openapi (Swagger UI)。

## 部署要求

### 环境配置
| 服务 | 说明 |
|------|------|
| 应用服务器 | Spring Boot 应用（Docker 容器，多阶段构建） |
| MySQL 8.x | 结构化数据（systemd 系统进程） |
| Redis | 缓存与会话 |
| Milvus 2.3.x | 向量检索（Docker 容器） |
| MinIO | 文件存储 |
| RabbitMQ | 异步审核队列 |
| Snowstorm（可选） | SNOMED CT 术语服务 |

### Docker 部署
- `docker-compose.yml` — 完整服务编排
- `docker-compose-app-only.yml` — 仅应用容器（中间件独立部署）
- `docker-compose-snowstorm.yml` — SNOMED CT Snowstorm 服务

详见 `DEPLOYMENT.md`。

## 实施状态

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段 1 | 基础框架（Spring Boot、Flyway、基础 API） | ✅ 已完成 |
| 阶段 2 | RAG 核心（双路检索、RRF 融合、重排序、LLM 集成） | ✅ 已完成 |
| 阶段 3 | 业务模块（知识库、用户、对话管理） | ✅ 已完成 |
| 阶段 4 | 前端界面（Vue 3、流式对话、知识库管理） | ✅ 已完成 |
| 阶段 5 | 高级功能（检索可视化、药物查询、禁忌标注、意图路由） | ✅ 已完成 |
| 阶段 6 | FHIR 问卷采集（访谈引擎、FHIR R4 接口） | ✅ 已完成 |
| 阶段 7 | 评估体系（离线四维度评估、在线监控、调优脚本） | ✅ 已完成 |
| 持续迭代 | 文档切割优化、意图路由准确率、P99 延迟优化 | 🔄 进行中 |

## 质量保障

- **编译门禁**: 每次任务结束执行 `mvn compile` 验收
- **测试覆盖率**: JaCoCo 强制 LINE ≥ 80%、BRANCH ≥ 70%
- **评估脚本**: 意图准确率（150 条 + 60 条盲测）、P99 延迟（冷/热各 500 条）、幻觉检测、安全 A/B 测试、K 值与置信度阈值调优（`scripts/` 目录）

## 风险评估与缓解

| 风险 | 缓解措施 |
|------|----------|
| 医疗准确性风险 | 权威性重排序、来源标注、三道安全防线、矛盾检测 |
| LLM API 成本 | 多级语义缓存、意图路由前置过滤、Redis 限流计数 |
| 向量检索性能 | 双路召回互补、语义缓存、Milvus 索引优化 |
| LLM 服务不稳定 | 意图路由断路器 + 2s 超时、规则引擎降级、答案兜底 |
| 数据隐私安全 | JWT 鉴权、Spring Security 访问控制 |
| 术语覆盖不足 | SNOMED CT 集成 + LLM 推断 + RabbitMQ 人工审核闭环 |

---

## 详细设计文档

详细设计文档位于 `docs/back/` 目录:

- `IMKQAS系统设计方案.md` — 整体系统设计
- `RAG 检索管线.md` — RAG 检索管线设计
- `意图路由设计方案.md` — 意图路由器 6 层架构
- `医疗安全兜底模块技术方案.md` — 三道安全防线
- `医学文档结构感知分片方案.md` — 文档分片方案
- `FHIR医疗问卷采集设计方案.md` — 问卷采集设计
- `RAG链路评估体系建设计划.md` — 评估体系建设
- `docs/frontend/DESIGN.md` — 前端设计规范
