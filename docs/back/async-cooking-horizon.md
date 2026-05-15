# FHIR 接口预留 + AI 填表助手 + 意图路由 实施方案

## Context

为后续对接 HIS 做准备，预留 FHIR R4 标准接口。同时实现 AI 填表助手和意图路由：用户输入先经路由判断，知识查询走 RAG 路径，数据采集走问卷路径，混合型走 RAG + 建议填表。采用"接口与实现分离"策略，通过 `imkqas.his.enabled` 开关控制数据源切换。

## 核心架构设计

### 意图路由（IntentRouter）

```
用户输入 → IntentRouter.classify(input)
  ├── KNOWLEDGE_QUERY → RAG 路径（现有 QaService，安全约束）
  ├── DATA_COLLECTION → 问卷路径（QuestionnaireService）
  └── MIXED → RAG 回答 + 建议填表（LLM 生成回答 + 尾部附加问卷推荐）
```

路由判断策略：
- LLM 分类（优先）：通过轻量 prompt 判断意图类别
- 关键词规则（兜底）：医疗问答关键词 → KNOWLEDGE；自评/症状描述 → DATA_COLLECTION

### 状态机（ConversationStateManager）

```
CHAT ──(AI建议+用户确认)──→ QUESTIONNAIRE
QUESTIONNAIRE ──(完成/中断)──→ CHAT
QUESTIONNAIRE ──(超时)──→ CHAT（自动恢复）
```

- 状态存储在 Redis（TTL 30分钟），支持分布式
- 中断恢复：用户返回时检查是否有未完成的问卷会话
- 每个会话绑定 conversationId，与现有对话系统无缝衔接

### AI 填表助手原则

- **按需触发**：只在意图路由判断为 DATA_COLLECTION 或 MIXED 时触发
- **用户主导**：AI 建议问卷 → 展示问卷信息 → 用户确认后才进入问答流程
- **不主动打断**：知识查询类不推送问卷

## 覆盖的 FHIR 资源（5 种）

| 资源 | 路径 | 建表 | 说明 |
|------|------|------|------|
| Patient | `/api/his/fhir/Patient` | fhir_patient_cache | 患者人口学信息 |
| Observation | `/api/his/fhir/Observation` | fhir_observation_cache | 生命体征、检验检查 |
| Condition | `/api/his/fhir/Condition` | fhir_condition_cache | 诊断/病情 |
| QuestionnaireResponse | `/api/his/fhir/QuestionnaireResponse` | fhir_questionnaire_response_cache | 问卷填写结果 |
| Bundle | `/api/his/fhir/Bundle` | 不建表（传输容器） | 统一写入入口，transaction/batch |

## 实施步骤

### 第 1 步：基础设施（可并行）

1. **pom.xml** — 添加 `hapi-fhir-base` + `hapi-fhir-structures-r4` (7.2.5)
2. **ErrorCode.java** — 添加 4 个错误码：50003, 60003, 70002, 20003
3. **V5__FHIR_cache_tables.sql** — 4 张缓存表 + 1 张问卷会话表
4. **HisConfigProperties.java** — `@ConfigurationProperties(prefix = "imkqas.his")`
5. **application.yml** — 添加 `imkqas.his` 配置段

### 第 2 步：数据层

6. **实体类** — FhirPatientCache, FhirObservationCache, FhirConditionCache, FhirQuestionnaireResponseCache
7. **Mapper** — 4 个 Mapper 继承 `BaseMapper<T>`

### 第 3 步：转换层

8. **FhirConverter.java** — 5 种 FHIR 资源 ↔ 实体双向转换 + JSON 序列化

### 第 4 步：FHIR 资源服务层

9. **Service 接口** — FhirPatientService, FhirObservationService, FhirConditionService, FhirQuestionnaireResponseService
10. **ServiceImpl** — 4 个实现类

### 第 5 步：Bundle 统一入口

11. **FhirBundleService** — 解析 Bundle → 按 type 分派 → transaction（@Transactional）或 batch（逐条独立）
12. **FhirBundleServiceImpl**

### 第 6 步：意图路由 + 状态机 + 问卷引擎

13. **IntentRouter.java** — 意图分类器：
    - `classify(String userInput)` → IntentType (KNOWLEDGE_QUERY / DATA_COLLECTION / MIXED)
    - LLM 分类 prompt + 关键词规则兜底
14. **IntentType.java** — 意图类型枚举
15. **ConversationStateManager.java** — 对话状态机：
    - CHAT ↔ QUESTIONNAIRE 切换
    - Redis 存储状态（TTL 30min）
    - `transition(state, event)` → new state
16. **ConversationState.java** — 状态枚举 (CHAT / QUESTIONNAIRE)
17. **InterviewEngine.java** — 填表引擎（原 QuestionnaireService 核心逻辑移入）：
    - `suggestQuestionnaire(userInput)` → 匹配问卷 + 返回建议
    - `startInterview(questionnaireId, userId, conversationId)` → 进入问卷模式
    - `answerQuestion(sessionId, answer)` → 记录答案 + 返回下一题或完成
    - `generateQuestionnaireResponse(sessionId)` → 生成 FHIR 资源
    - `getHistory(userId, questionnaireId)` → 历史对比
18. **QuestionnaireTemplate.java** — 问卷模板数据结构
19. **QuestionnaireRepository.java** — 内置标准量表库（PHQ-9, GAD-7, ISI 等）

### 第 7 步：集成到现有 QaService

20. **修改 QaServiceImpl.java** — 在 answer() 方法入口处集成意图路由：
    - `intent = intentRouter.classify(query)`
    - KNOWLEDGE_QUERY → 现有 RAG 流程
    - DATA_COLLECTION → 交给 InterviewEngine，返回问卷建议
    - MIXED → RAG 回答 + 尾部附加问卷推荐
21. **QaResponse.java** — 新增字段：`intentType`, `questionnaireSuggestion`

### 第 8 步：控制器层

22. **FhirPatientController**
23. **FhirObservationController**
24. **FhirConditionController**
25. **FhirQuestionnaireResponseController**
26. **FhirBundleController** — `POST /api/his/fhir/Bundle`
27. **InterviewController** — 填表助手 REST API：
    - `POST /api/his/interview/suggest` — AI 建议问卷
    - `POST /api/his/interview/start` — 用户确认，开始填表
    - `POST /api/his/interview/answer` — 提交答案
    - `GET /api/his/interview/{sessionId}/status` — 查询会话状态
    - `POST /api/his/interview/{sessionId}/resume` — 恢复中断的会话
    - `POST /api/his/interview/{sessionId}/cancel` — 取消填表
    - `GET /api/his/interview/history` — 历史填写记录
    - `GET /api/his/interview/history/{questionnaireId}/trend` — 评分趋势分析

### 第 9 步：测试

28. **FhirConverterTest** — 转换器往返一致性
29. **IntentRouterTest** — 意图分类测试（覆盖三类 + 边界）
30. **InterviewEngineTest** — 填表全流程 + 中断恢复
31. **FhirBundleServiceImplTest** — transaction 回滚 + batch 部分失败
32. **FhirBundleControllerTest** — 控制器集成测试

## 关键文件清单

### 新建（32 个）

```
# 配置
src/main/java/com/student/config/his/HisConfigProperties.java

# 实体 (4)
src/main/java/com/student/entity/his/FhirPatientCache.java
src/main/java/com/student/entity/his/FhirObservationCache.java
src/main/java/com/student/entity/his/FhirConditionCache.java
src/main/java/com/student/entity/his/FhirQuestionnaireResponseCache.java

# Mapper (4)
src/main/java/com/student/mapper/his/FhirPatientCacheMapper.java
src/main/java/com/student/mapper/his/FhirObservationCacheMapper.java
src/main/java/com/student/mapper/his/FhirConditionCacheMapper.java
src/main/java/com/student/mapper/his/FhirQuestionnaireResponseCacheMapper.java

# 转换层
src/main/java/com/student/dto/his/FhirConverter.java

# 意图路由 + 状态机 + 问卷引擎 (7)
src/main/java/com/student/service/his/IntentRouter.java
src/main/java/com/student/service/his/IntentType.java
src/main/java/com/student/service/his/ConversationStateManager.java
src/main/java/com/student/service/his/ConversationState.java
src/main/java/com/student/service/his/InterviewEngine.java
src/main/java/com/student/service/his/QuestionnaireTemplate.java
src/main/java/com/student/service/his/QuestionnaireRepository.java

# FHIR Service 接口 (5)
src/main/java/com/student/service/his/FhirPatientService.java
src/main/java/com/student/service/his/FhirObservationService.java
src/main/java/com/student/service/his/FhirConditionService.java
src/main/java/com/student/service/his/FhirQuestionnaireResponseService.java
src/main/java/com/student/service/his/FhirBundleService.java

# FHIR Service Impl (5)
src/main/java/com/student/service/his/impl/FhirPatientServiceImpl.java
src/main/java/com/student/service/his/impl/FhirObservationServiceImpl.java
src/main/java/com/student/service/his/impl/FhirConditionServiceImpl.java
src/main/java/com/student/service/his/impl/FhirQuestionnaireResponseServiceImpl.java
src/main/java/com/student/service/his/impl/FhirBundleServiceImpl.java

# 意图路由 + 状态机 + 问卷引擎 Impl (4)
src/main/java/com/student/service/his/impl/IntentRouterImpl.java
src/main/java/com/student/service/his/impl/ConversationStateManagerImpl.java
src/main/java/com/student/service/his/impl/InterviewEngineImpl.java
src/main/java/com/student/service/his/impl/QuestionnaireRepositoryImpl.java

# Controller (7)
src/main/java/com/student/controller/his/FhirPatientController.java
src/main/java/com/student/controller/his/FhirObservationController.java
src/main/java/com/student/controller/his/FhirConditionController.java
src/main/java/com/student/controller/his/FhirQuestionnaireResponseController.java
src/main/java/com/student/controller/his/FhirBundleController.java
src/main/java/com/student/controller/his/InterviewController.java

# 迁移
src/main/resources/db/migration/V5__FHIR_cache_tables.sql
```

### 修改（4 个）

```
pom.xml                    — 添加 2 个 HAPI FHIR 依赖
application.yml            — 添加 imkqas.his 配置段
ErrorCode.java             — 添加 4 个 FHIR 错误码
QaServiceImpl.java         — 集成意图路由入口
QaResponse.java            — 新增 intentType + questionnaireSuggestion 字段（需确认）
```

## 核心流程

### 意图路由分流

```
用户: "糖尿病患者应该怎么饮食？"
  → IntentRouter.classify() → KNOWLEDGE_QUERY
  → 走现有 RAG 路径，返回知识回答

用户: "最近一周情绪很低落，睡不好"
  → IntentRouter.classify() → DATA_COLLECTION
  → InterviewEngine.suggestQuestionnaire() → 匹配到 PHQ-9
  → 返回: "您描述的情况可能适合PHQ-9抑郁筛查量表，是否需要填写？（共9题，约3分钟）"

用户确认 "好的"
  → ConversationStateManager.transition(CHAT → QUESTIONNAIRE)
  → InterviewEngine.startInterview("PHQ-9", userId, conversationId)
  → 返回第1题...

用户: "糖尿病有什么症状？我最近总是口渴"
  → IntentRouter.classify() → MIXED
  → RAG 回答糖尿病症状知识
  → 尾部附加: "您提到口渴的症状，是否需要填写糖尿病风险自评表？"
```

### 状态机转换

```
CHAT
  │  AI建议问卷 + 用户确认
  ▼
QUESTIONNAIRE
  │  全部答完 → generateQuestionnaireResponse()
  │  用户取消 → cancel()
  │  超时30min → 自动恢复
  ▼
CHAT
```

## 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 路由分类 | LLM 分类 + 关键词兜底 | LLM 准确率高，关键词保证可用性 |
| 状态存储 | Redis（TTL 30min） | 分布式支持，自动过期清理 |
| 意图路由位置 | QaServiceImpl 入口处 | 与现有 RAG 流程无缝衔接，单点控制 |
| 填表触发 | 用户确认后才进入 | 用户主导，不主动打断知识问答 |
| 混合型处理 | RAG 回答 + 尾部附加建议 | 不影响知识问答体验 |

## 验证方式

1. `mvn compile` — 编译通过
2. `mvn test -Dtest="com.student.service.his.*,com.student.dto.his.*"` — 测试通过
3. Flyway 自动创建 V5 表，Swagger UI 可见 7 个控制器
4. 意图路由验证：三类输入各测 3 条
5. 填表全流程：建议 → 确认 → 逐一回答 → 生成 QuestionnaireResponse
6. 状态机：中断 → 恢复 → 超时自动清理
