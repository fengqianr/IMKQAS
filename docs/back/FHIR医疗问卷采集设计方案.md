# LLM驱动的FHIR医疗问卷采集系统——设计方案（已实现）

## 上下文

### 问题
当前 `DATA_COLLECTION` 意图识别后仅返回问卷建议文本，用户需手动调用 `/api/his/interview/start` 等 API 逐题填表。缺失 LLM 驱动的自然对话采集、安全紧急中断、共情分析和 FHIR 资源自动生成能力。

### 目标
在现有 IMKQAS（Spring Boot + MyBatis Plus + LangChain4j + HAPI FHIR）基础上，嵌入三层架构，仅当意图识别为 `DATA_COLLECTION` 时触发。

### 现有可复用资产
- **意图路由**: `IntentRouterImpl.classify()` → `IntentType.DATA_COLLECTION`
- **填表引擎**: `InterviewEngine` / `InterviewEngineImpl`
- **问卷模板库**: `QuestionnaireRepositoryImpl`（PHQ-9/GAD-7/ISI + 评分规则）
- **对话状态**: `ConversationStateManager`（CHAT/QUESTIONNAIRE + Redis）
- **FHIR 基础设施**: HAPI FHIR R4, `FhirConverter`, 缓存实体 + Mapper + Controller
- **安全基础设施**: `SafetyGuardService`（急症预检三级关键词）
- **LLM 集成**: LangChain4j + DashScope qwen-plus + SSE 流式
- **前端**: `QaView.vue`（SSE 流式渲染）

### 触发条件
仅当 `IntentRouter.classify(query) == DATA_COLLECTION` 时进入此流程。`MIXED` 意图走 RAG + 问卷建议的混合路径。

---

## 架构总览

### 核心分层

```
┌─────────────────────────────────────────────────────────┐
│ 前端 (QaView.vue)                                       │
│  问卷建议卡片 → 用户确认 → SSE 流式访谈                  │
└──────────────┬──────────────────────────────────────────┘
               │ POST /api/his/interview/start-llm (SSE)
               │ POST /api/his/interview/llm-answer (SSE)
               ▼
┌─────────────────────────────────────────────────────────┐
│ InterviewController (SSE 流式 API)                       │
│  分发 SSE 事件: question / completion / safety_alert     │
└──────────────┬──────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────┐
│ InterviewEngineImpl (核心编排引擎)                       │
│  ├─ 状态机: 严格握手协议（9状态，状态机独有题目推进权）    │
│  │   QUESTIONING → ANSWER_RECEIVED → VALIDATING →        │
│  │   COMPLETED / CLARIFYING，生命周期: PAUSED/EXPIRED/ABANDONED │
│  ├─ 三级降级: LLM → rule_parser → manual_form           │
│  └─ 三层存储: Redis(热) → ConcurrentHashMap(暖) → MySQL(冷) │
└───┬──────────┬──────────────┬───────────────────────────┘
    │          │              │
    ▼          ▼              ▼
┌────────┐ ┌───────────┐ ┌──────────────────────┐
│Collect │ │Transform  │ │AnalysisAgent         │
│SubAgent│ │Pipeline   │ │(LLM 共情分析)         │
│(LLM    │ │(确定性管道)│ │→ DiagnosticReport    │
│ 语义   │ │→ FHIR QR  │ │→ RiskAssessment      │
│ 理解)  │ │→ 评分规则 │ │→ AnalysisReport      │
└────────┘ └───────────┘ └──────────────────────┘
    │          │              │
    └──────────┴──────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────┐
│ InterviewPersistenceService (持久化协调层)                │
│  interview_sessions / messages / analysis_reports        │
│  fhir_questionnaire_response / diagnostic_report /       │
│  risk_assessment (缓存表)                                │
└─────────────────────────────────────────────────────────┘
```

### 与 QaServiceImpl 的关系

`QaServiceImpl` 负责意图路由和问卷建议，但**不直接启动访谈流程**。实际的数据采集由前端通过 `InterviewController` 的独立 SSE 端点驱动：

- `QaServiceImpl.answer()` → `DATA_COLLECTION` → 返回问卷建议文本 + `InterviewSuggestion`
- 前端渲染建议卡片，用户点击"开始评估" → 调用 `POST /api/his/interview/start-llm`（SSE）
- 后续多轮对话 → `POST /api/his/interview/llm-answer`（SSE）

### 核心组件清单

| 组件 | 类型 | 职责 |
|------|------|------|
| `InterviewEngine` / `Impl` | Service | 核心编排：状态机 + 降级 + 存储 + 完成触发 |
| `CollectionSubAgent` / `Impl` | Service | LLM 语义理解：自然语言→结构化答案 |
| `CollectionSseEmitter` | Adapter | AgentResult → SSE 事件转换 |
| `CollectionToolOutputs` | DTO | LLM 工具调用输出类型（5种） |
| `TransformationPipeline` | Service | 三阶段确定性管道（校验→评分→FHIR组装） |
| `AnalysisAgent` / `Impl` | Service | LLM 共情分析 → DiagnosticReport + RiskAssessment |
| `AnalysisInput` / `AnalysisResult` | DTO | 分析层输入/输出数据结构 |
| `InterviewPersistenceService` / `Impl` | Service | 持久化协调（3表 + FHIR缓存表） |
| `InterviewSession` | Model | 会话状态（含答案/溯源/降级/摘要） |
| `InterviewStatus` | Enum | 9状态枚举：QUESTIONING/ANSWER_RECEIVED/CLARIFYING/VALIDATING/COMPLETED + ACTIVE/PAUSED/EXPIRED/ABANDONED |
| `InterviewSessionCleanupTask` | Task | 定时扫描过期会话 @Scheduled |
| `BatchSubmitRequest` | DTO | 全量纯表单批量提交请求（linkId → code） |
| `BatchSubmitResponse` | DTO | 批量提交响应（总分 + 严重程度 + 分析摘要） |
| `FhirConverter` | Util | HAPI FHIR ↔ 缓存实体双向转换 |
| `IntentRouter` / `Impl` | Service | 意图分类：KNOWLEDGE_QUERY / DATA_COLLECTION / MIXED |

---

---

## 状态模型与会话管理

### InterviewSession（会话状态）

会话状态由 `InterviewSession` 统一管理，不再需要单独的 `CollectionSessionState` 类。所有采集相关的状态字段直接内嵌在 `InterviewSession` 中。

```
InterviewSession
├── sessionId / questionnaireId / questionnaireTitle  ← 会话标识
├── userId / conversationId                            ← 归属
├── currentQuestionIndex / totalQuestions              ← 进度
├── answers: Map<linkId, code>                         ← 已采集答案
├── currentScore / completed                           ← 评分状态
├── status: QUESTIONING / ANSWER_RECEIVED /            ← 题目级操作状态 + 生命周期
│          CLARIFYING / VALIDATING / COMPLETED /
│          ACTIVE / PAUSED / EXPIRED / ABANDONED
├── collectionMode: llm_driven / manual_form           ← 采集模式
├── degradationLevel: llm / rule_parser / manual_form  ← 当前降级层级
├── rawInputs: Map<linkId, 原始文本>                   ← 用户原始表述
├── safetyFlags: List<String>                          ← 触发的安全标记
├── contextSummary: String                             ← 增量上下文摘要（≤500字）
├── consecutiveFailures: int                           ← 断路器计数
├── clarificationCount: int                            ← 当前题追问次数（上限3）
├── provenance: Map<linkId, Provenance>                ← 溯源信息
│     └── Provenance { source, confidence }
├── createdAt / lastActiveAt                           ← 时间戳
```

### InterviewStatus（状态枚举）

```java
public enum InterviewStatus {
    // === 题目级操作状态（握手协议） ===
    QUESTIONING,      // 正在提问，等待用户回答
    ANSWER_RECEIVED,  // 收到用户回答，正在调用LLM提取编码
    CLARIFYING,       // LLM需要追问，等待用户补充说明
    VALIDATING,       // LLM已返回编码，状态机正在校验合法性
    COMPLETED,        // 全部题目采集完毕

    // === 会话生命周期状态 ===
    ACTIVE,           // 旧版"进行中"，保留兼容旧数据
    PAUSED,           // 暂停（关页面/刷新），可恢复
    EXPIRED,          // 超时过期，不可恢复
    ABANDONED;        // 用户主动放弃，已清理

    /** 判断是否为题目级操作状态（会话正在进行中） */
    public static boolean isQuestionLevelState(String status);

    /** 判断是否为活跃状态（可接受用户输入） */
    public static boolean isActiveState(String status);
}
```

状态由定时任务 `InterviewSessionCleanupTask` 自动推进：每5分钟扫描 `isActiveState()` 的会话中超过30分钟未活动的 → 标记为 `PAUSED`；`PAUSED` 超过30分钟 → `EXPIRED`。


### 数据存储三层架构

```
请求到达
  │
  ├── 1. 优先读 Redis（热数据）
  │     Key: his:interview:{sessionId}
  │     TTL: 1800秒（每次活动自动续期）
  │     Value: InterviewSession 完整 JSON
  │     命中率最高，延迟 < 1ms
  │
  ├── 2. Redis 未命中 → 读本地 ConcurrentHashMap（暖缓存）
  │     降级保障，进程重启后丢失
  │     延迟 < 0.1ms
  │
  └── 3. 两者均未命中 → 读 MySQL（冷数据）
        表: interview_sessions
        JSON 字段反序列化重建 InterviewSession
        延迟 ~5ms，用于跨进程共享 + 会话恢复
```

写入策略：
- 每次状态变更同步写 Redis + 本地缓存
- 异步写 MySQL（`CompletableFuture.runAsync`），不阻塞主流程
- 完成/暂停/放弃时同步写 MySQL 确保持久化

### 会话生命周期

```
创建: startInterview() / startLlmInterview()
  │  sessionId = UUID(12位), status = QUESTIONING
  │  写 Redis + MySQL, 转 ConversationState → QUESTIONNAIRE
  │
  ├── 填表中: 题目级操作状态循环
  │     QUESTIONING (发送题目，等待用户回答)
  │       │
  │       用户回答 → ANSWER_RECEIVED (调用LLM提取编码)
  │       │
  │       ├─ LLM返回有效编码 → VALIDATING
  │       │     ├─ linkId + code 校验通过 → recordAnswer() → 推进题号
  │       │     │   ├─ 还有下一题 → QUESTIONING (发送下一题)
  │       │     │   └─ 全部完成 → COMPLETED (触发分析+FHIR)
  │       │     └─ 校验失败 → CLARIFYING (追问用户)
  │       │           └─ 追问≥3次 → 强制 manual_form
  │       ├─ LLM要求追问 → CLARIFYING → 用户补充 → ANSWER_RECEIVED
  │       ├─ LLM检测危险 → ABANDONED (紧急中断)
  │       └─ LLM返回COMPLETE但未答完 → 忽略，继续QUESTIONING (防幻觉)
  │
  └── 生命周期（可中断任何题目级状态）:
        任何操作状态 --timeout--> PAUSED --timeout--> EXPIRED
        任何操作状态 --abandon--> ABANDONED
        PAUSED --resume--> QUESTIONING

  ├── 暂停: pauseInterview()
  │     status → PAUSED, lastActiveAt 更新
  │     清理 Redis（删 key）+ 本地缓存（remove）
  │     保留 MySQL 记录用于恢复
  │     ConversationState → CHAT
  │
  ├── 恢复: resumeInterview()
  │     status: PAUSED → QUESTIONING
  │     ConversationState → QUESTIONNAIRE
  │     从 MySQL 重建会话状态 → 重新写入 Redis
  │     不可恢复：EXPIRED / ABANDONED 状态
  │
  ├── 完成: 全部题目采集完毕
  │     completed = true, status = COMPLETED
  │     触发 TransformationPipeline + AnalysisAgent
  │     FHIR 资源写入 MySQL, ConversationState → CHAT
  │
  └── 放弃: abandonInterview()
        status → ABANDONED
        清理 Redis + 本地缓存
        异步逻辑删除 MySQL 记录
        ConversationState → CHAT
```

### 握手协议核心原则

**状态机独有题目推进权，LLM 仅负责语义提取（"翻译官"角色）。**

1. **LLM 不能推进题目**：LLM 不再返回 `ASK_QUESTION`，只能返回 `record_answer`/`clarify`/`complete`/`emergency_interrupt`。题目的发送（`ASK_QUESTION`）由状态机根据 `currentQuestionIndex` 自行构建。
2. **校验通过才能推进**：状态机收到 LLM 返回的 `record_answer` 后，必须校验：
   - `linkId` 匹配当前题目（`template.getItems().get(currentQuestionIndex).getLinkId()`）
   - `code` 在当前题目的 `options` 列表中存在
   - 两者均通过 → 调用 `recordAnswer()` 推进题号
   - 任一失败 → 进入 `CLARIFYING`，追问计数+1
3. **追问防死循环**：单题追问次数 `clarificationCount` 上限 3 次，超限强制 `manual_form` 降级。成功记录答案后重置为 0。
4. **防 LLM 幻觉**：LLM 返回 `complete` 时状态机校验 `currentQuestionIndex ≥ totalQuestions`，不满足则忽略并继续发送下一题。
5. **防重复提交**：若状态为 `ANSWER_RECEIVED` 或 `VALIDATING`（正在处理中）时收到新用户消息，直接返回"请稍候"。

### API 端点（InterviewController, /api/his/interview）

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/suggest` | AI 问卷建议（关键词匹配） |
| POST | `/start` | 开始手动填表 |
| POST | `/answer` | 提交单个选项型答案 |
| POST | `/start-llm` | **SSE 流式** 启动 LLM 驱动填表 |
| POST | `/llm-answer` | **SSE 流式** 处理 LLM 自然语言输入 |
| GET | `/{sessionId}/fhir` | 生成 FHIR QuestionnaireResponse |
| POST | `/{sessionId}/resume` | 恢复中断会话 |
| POST | `/{sessionId}/cancel` | 取消（委托 abandon） |
| POST | `/{sessionId}/pause` | 暂停（保留 MySQL） |
| POST | `/{sessionId}/abandon` | 放弃（清理全部数据） |
| POST | `/{sessionId}/batch-submit` | **全量纯表单模式**批量提交答案 |
| POST | `/{sessionId}/heartbeat` | 心跳保活 |
| GET | `/history` | 历史记录 |
| GET | `/trend` | 评分趋势 |
| GET | `/questionnaires` | 所有可用问卷列表 |
| GET | `/questionnaires/{id}` | 问卷详情 |
| GET | `/{sessionId}/messages` | 会话消息列表 |
| GET | `/{sessionId}/analysis` | AI 分析报告 |
| GET | `/by-conversation/{conversationId}` | 按对话查访谈 |
| GET | `/{sessionId}/fhir/diagnostic-report` | FHIR DiagnosticReport |
| GET | `/{sessionId}/fhir/risk-assessment` | FHIR RiskAssessment |

> **注意**：SSE 流式端点（`start-llm` / `llm-answer`）通过 `InterviewController` 独立提供，**不走** `QaController.stream()`。`QaController` 仅处理知识问答的 RAG 管线流式输出。问卷访谈和知识问答是两条独立的 SSE 通道。

---

---

## 降级策略

### 三级递进降级

降级由 `CollectionSubAgentImpl` 中的断路器控制。连续 3 次 LLM 失败触发熔断，30 秒冷却后自动尝试恢复。

```
层级 1: LLM 全功能模式 (degradationLevel = "llm")
  │  CollectionSubAgent 调用 LLM，返回结构化工具调用
  │
  ├── 降级触发: 连续 3 次 LLM 调用失败
  ▼
层级 2: 规则解析器模式 (degradationLevel = "rule_parser")
  │  纯代码关键词→选项映射，4步fallback：
  │  (1) 数值提取: 正则匹配 "3分"、"2级"
  │  (2) 频率词映射: "几乎没有"→0, "偶尔"→1, "经常"→2, "几乎每天"→3
  │  (3) 模糊匹配: 选项文本相似度
  │  (4) 多候选澄清: 发选项列表让用户选择
  │
  ├── 降级触发: 规则解析也无法确定 → 连续解析失败
  ▼
层级 3: 纯表单模式 (degradationLevel = "manual_form")
  │  展示选项编号，用户输入数字
  │  如用户输入 "3" → handleManualFormInput() 直接映射
  │
  └── 恢复: 纯表单模式成功记录一题后，自动尝试恢复 LLM 模式
       session.setDegradationLevel("llm")
       session.setConsecutiveFailures(0)
```

### 两种降级范围（Scope）

纯表单模式（层级 3）根据触发原因分为两种范围，通过 SSE `degradation` 事件的 `scope` 字段告知前端：

| Scope | 触发条件 | 行为 | 前端表现 |
|-------|---------|------|---------|
| **`single`** | 同一题追问 `clarificationCount ≥ 3`，LLM 始终无法映射 | 仅当前题目显示数字选项，下题自动恢复 LLM | 显示 `ElMessage.warning` 提示，SSE 流继续，题目卡片正常渲染 |
| **`all`** | `consecutiveFailures ≥ 3`（断路器熔断），LLM 彻底不可用 | 一次性展示所有剩余未答题，用户批量作答后统一提交 | 截断 SSE 流 → 渲染全量纯表单卡片 → 用户逐题点击选项 → 批量提交 |

**后端判断逻辑**（`InterviewController.emitAgentResult()`）：

```java
String scope = "single";
InterviewSession session = engine.resumeInterview(sessionId);
if (session != null && session.getConsecutiveFailures() >= 3) {
    scope = "all";
    reason = "LLM不可用，请点击问卷答案";
}
sseEmitter.sendDegradationNotice(emitter, level, reason, scope);
```

### 全量纯表单模式批量提交流程（scope: "all"）

当断路器熔断、LLM 彻底不可用时，前端切换至全量纯表单模式：

```
SSE degradation 事件 (scope: "all")
  │
  ▼
前端处理:
  ├── 显示横幅提示: "LLM不可用，请点击问卷答案"
  ├── 截断当前 SSE 流（不再等待后续事件）
  ├── 调用 GET /questionnaires/{id} 获取完整模板
  ├── 调用 GET /{sessionId}/messages 获取已答题 linkId 集合（排除已答题目）
  ├── 渲染全量纯表单卡片: 所有剩余题目 + 选项按钮 + 进度条
  └── 拦截 sendMessage(): 显示 "请先完成问卷填写后再发送消息"
  
  ▼ (用户逐题点击选项，所有题目选中后提交按钮激活)
  
前端: POST /api/his/interview/{sessionId}/batch-submit
  Body: { answers: { "/q5": "LA6570-2", "/q6": "LA6570-3", ... } }
  │
  ├── InterviewEngineImpl.submitBatch(sessionId, answers)
  │     ├── loadSession(sessionId) → 获取当前进度
  │     ├── 遍历 answers map:
  │     │     对每个 (linkId, code)，调用 recordAnswer() 逐题记录
  │     │     source 标记为 "manual_form"
  │     ├── 所有答案记录完毕后，调用 buildCompletionResponse()
  │     │     → TransformationPipeline → 评分 + FHIR 组装
  │     │     → [异步] AnalysisAgent → 共情分析
  │     └── 返回 BatchSubmitResponse { completed, totalScore, severity, interpretation, ... }
  │
  └── 前端渲染 completion 卡片（与 LLM 模式完成界面一致）
```

### 降级关键实现细节

- **断路器**：`CollectionSubAgentImpl` 内部维护 `consecutiveFailures` 计数，≥3 触发熔断
- **纯表单模式处理（scope: "single"）**：`InterviewEngineImpl.processLlmTurn()` 中检测 `degradationLevel == "manual_form"`，走 `handleManualFormInput()` 方法
- **纯表单提示构建**：`buildManualFormPrompt()` 将选项格式化为 "1. 选项A\n2. 选项B..." 的数字编号列表
- **批量提交（scope: "all"）**：`submitBatch()` 复用 `recordAnswer()` 逐题记录，然后调用 `buildCompletionResponse()` 统一评分。已答题目通过 `getSessionMessages()` 过滤，仅提交剩余未答题。
- **自动恢复**：scope "single" 成功记录一题后重置 `consecutiveFailures = 0` 并恢复 `llm` 模式；scope "all" 用户批量提交后即完成，下次新会话自动恢复 LLM。
- **source 标记**：规则解析器输出 `source = "rule_parser"`，纯表单输出 `source = "manual_form"`（归一化为 `direct_input`），区分于 LLM 的 `source = "llm"`

### LLM 返回校验（状态机执行）

状态机在 `handleRecordAnswerState()` 中执行两级校验，校验不通过不会推进题目：

1. **linkId 校验**：LLM 返回的 `linkId` 必须等于 `template.getItems().get(currentQuestionIndex).getLinkId()`
2. **code 合法性**：`code` 必须在当前题目的 `options` 列表内（stream 匹配）
3. **失败处理**：任一校验失败 → `handleValidationFailure()` → clarificationCount++ → CLARIFYING 或强制 manual_form（≥3次）
4. **defense-in-depth**：`recordAnswer()` 内部也有 linkId 校验（二次确认），防止状态机逻辑被绕过

与旧版区别：旧版 LLM 返回什么就记录什么（无条件信任），新版由状态机先校验再记录。

---

---

## 会话恢复与错误恢复

### 恢复流程

```
用户重新进入聊天 → 前端调用 GET /api/his/interview/by-conversation/{id}
  │
  ├── 步骤1: 查询活跃会话
  │     ConversationStateManager.getState(conversationId)
  │       → Redis 命中: QUESTIONNAIRE 状态 + pendingInterviewSessionId
  │       → Redis 未命中: 查 MySQL interview_sessions（按 conversationId）
  │
  ├── 步骤2: 加载会话状态
  │     InterviewEngineImpl.loadSession(sessionId)
  │       1. Redis: his:interview:{sessionId} → 完整 JSON 反序列化
  │       2. 本地缓存: localSessions.get(sessionId) → InterviewSession
  │       3. MySQL: persistenceService.loadSession(sessionId) → 重建
  │       三者均未命中 → 会话已彻底丢失
  │
  ├── 步骤3: 状态校验
  │     EXPIRED → 不可恢复（超过30分钟）
  │     ABANDONED → 不可恢复（已清理）
  │     PAUSED → 可恢复，status 改回 ACTIVE
  │
  ├── 步骤4: 上下文重建
  │     系统提示词（固定模板 + 问卷结构）✓ 每次重新注入
  │     contextSummary（增量摘要）✓ 从 InterviewSession 读取
  │     不需要重新调用 LLM 生成摘要
  │
  ├── 步骤5: 确定降级层级
  │     检查 consecutiveFailures
  │       < 3 → 尝试 LLM 模式
  │       >= 3 → 规则解析器/纯表单模式（断路器冷却中）
  │
  └── 步骤6: 恢复访谈
        前端调用 POST /{sessionId}/resume
        返回当前进度和当前题目
        前端渲染 resume 提示卡片
```

### 飞行中请求处理

如果用户在 LLM 调用期间断开：
- `lastActiveAt` < 10s → 可能有飞行中的 LLM 请求
- 恢复时检查 answers 中当前题是否有记录：
  - 有记录 → LLM 完成了但用户没收到响应 → 重新发送当前题
  - 无记录 → LLM 未完成 → 重新发送当前问题

### 幂等性保证

`recordAnswer(linkId, code, ...)` 操作幂等：同一 `linkId` 重复写入时覆盖前值。恢复后即使重放已记录但未推进状态机的答案，也不会导致数据错误。

### 会话清理定时任务

`InterviewSessionCleanupTask` 每 5 分钟执行一次：
```java
@Scheduled(fixedDelay = 300_000)
public void expirePausedSessions() {
    // PAUSED + lastActiveAt < 30分钟前 → 批量标记为 EXPIRED
}
```

---

## 上下文压缩策略

### 两种压缩时机

| 时机 | 触发点 | 执行动作 | 频率 |
|------|-------|---------|------|
| **T1: 增量摘要** | `recordAnswer()` 中每记录一题后 | 生成该题摘要行，追加到 `contextSummary` | 每完成 1 题 1 次 |
| **T2: 预调用压缩** | `CollectionSubAgentImpl.processUserInput()` 构建 prompt 前 | 估算 token，超阈值则压缩上下文 | 每次 LLM 调用前 |

### T1 增量摘要

在 `InterviewEngineImpl.appendContextSummary()` 中实现：
- LLM 返回 `contextSummary` 时优先使用 LLM 摘要
- LLM 未返回时使用规则模板生成：`"{题号}. 用户{display}({code}, score={value})"`
- 限制 `contextSummary` 最大 500 字符，超出时截断前半部分

### T2 Token 预算与压缩等级

```java
// 4级压缩，阈值: 2000 / 3000 / 4000 tokens
CompressionLevel level = decideCompressionLevel(session, userInput);
```

| 压缩等级 | 触发条件 (estimatedTokens) | 压缩动作 |
|---------|--------------------------|---------|
| NONE | < 2000 | 完整上下文 |
| LIGHT | ≥ 2000 | 启用增量摘要替代完整对话历史 |
| DEEP | ≥ 3000 | 摘要 + 滑动窗口（保留最近 3 轮） |
| EMERGENCY | ≥ 4000 | 仅保留当前题目 + 最近 1 轮 + 结构化摘要 |

### Token 估算公式（在 CollectionSubAgentImpl 中实现）

```
estimatedTokens = SYSTEM_PROMPT_BASE(200)
  + QUESTIONNAIRE_CONTEXT(150)
  + contextSummary.length() * 1.2
  + recentDialogTurns * 80
  + userInput.length() * 1.2
  + TOOL_DEFINITIONS(100)
```

### 实际表现

PHQ-9（9 题，每题约 15 字摘要）在正常对话下不会触发压缩（估算约 800-900 token）。压缩主要针对：
- 长问卷（20+ 题自定义问卷）
- 用户频繁追问/澄清导致对话轮次增加
- 会话恢复后 contextSummary 重建

---

---

## FHIR 资源组装

### 三阶段转换管道（TransformationPipeline）

采集完成后，`TransformationPipeline.execute(session, template)` 执行三阶段确定性转换：

```
阶段1: 完整性校验
  ├── 检查 requiredItems 是否全部有答案
  ├── 检查 answer code 是否在题目选项枚举内（非法编码拒绝）
  └── 失败 → 标记缺失题目，不生成 FHIR 资源

阶段2: 规则计算
  ├── 累加各题 score → totalScore
  ├── 根据 ScoringRule 匹配风险等级（minScore ≤ score ≤ maxScore）
  ├── 评估 ComboRule（如 PHQ-9 Q1≥2 且 Q2≥2 → "快感缺失伴情绪低落"）
  ├── 检查 safetyKeywords（如 Q9≥1 → "自杀意念阳性"）
  └── 输出: PipelineResult { totalScore, maxScore, riskLevel, flags }

阶段3: FHIR 资源组装
  └── 生成 HAPI FHIR R4 QuestionnaireResponse
        ├── 根 extension: total-score, risk-level, collection-mode, conversation-id, combo-flags
        └── 每个 item extension: raw-input, answer-source, answer-confidence, score
```

### FHIR QuestionnaireResponse 目标结构

```json
{
  "resourceType": "QuestionnaireResponse",
  "id": "qr-{sessionId}",
  "meta": {
    "profile": ["http://hl7.org/fhir/StructureDefinition/QuestionnaireResponse"],
    "tag": [{ "system": "http://imkqas.org/fhir/tags", "code": "llm-collected" }]
  },
  "extension": [
    { "url": "http://imkqas.org/fhir/StructureDefinition/qr-total-score", "valueInteger": 22 },
    { "url": "http://imkqas.org/fhir/StructureDefinition/qr-risk-level", "valueString": "重度抑郁" },
    { "url": "http://imkqas.org/fhir/StructureDefinition/qr-collection-mode", "valueString": "llm-driven" },
    { "url": "http://imkqas.org/fhir/StructureDefinition/qr-conversation-id", "valueString": "conv-123" }
  ],
  "questionnaire": "http://imkqas.org/fhir/Questionnaire/PHQ-9",
  "status": "completed",
  "subject": { "reference": "Patient/pat-{userId}" },
  "authored": "2026-06-26T10:35:00+08:00",
  "author": { "reference": "Device/imkqas-collection-agent" },
  "item": [{
    "linkId": "/q1",
    "text": "做事时提不起劲或没有兴趣",
    "extension": [
      { "url": "http://imkqas.org/fhir/StructureDefinition/raw-input",
        "valueString": "大部分时间都不想动" },
      { "url": "http://imkqas.org/fhir/StructureDefinition/answer-source",
        "valueString": "llm" },
      { "url": "http://imkqas.org/fhir/StructureDefinition/answer-confidence",
        "valueDecimal": 0.91 }
    ],
    "answer": [{
      "valueCoding": {
        "system": "http://loinc.org",
        "code": "LA6570-1",
        "display": "一半以上的天数"
      },
      "extension": [
        { "url": "http://imkqas.org/fhir/StructureDefinition/score", "valueInteger": 2 }
      ]
    }]
  }]
}
```

### 编码系统映射

通过 `QuestionnaireTemplate.codeSystem` 字段配置，不在代码里硬编码：

| 问卷类型 | 编码系统 |
|---------|---------|
| 标准化量表（PHQ-9/GAD-7） | `http://loinc.org` (LOINC) |
| 自定义量表（ISI） | `http://imkqas.org/fhir/CodeSystem/{id}` |
| 数值型问题 | `http://unitsofmeasure.org` (UCUM) |

### 降级组装

当管道组装失败时（如缺少必填项），`generateResponse()` 回退到基础降级组装：直接遍历 `answers` map 生成简化版 QuestionnaireResponse，不包含 extension 和评分。日志记录 `warn` 级别。

---

## 分析层设计

### 核心约束："只读"

分析层 Agent 收到的是**只读事实包**（`AnalysisInput`），不传入可用于重新计算的数据：

```
传入分析层的：
  ✓ 已计算好的总分、风险等级、标记
  ✓ 各题用户原始表述（含 source + confidence）
  ✓ 患者年龄范围、历史趋势

不传入分析层的：
  ✗ 各题单项得分（不让重新加减）
  ✗ 阈值配置、评分规则、编码映射
  ✗ 患者精确年龄、姓名
```

### 系统提示词核心规则

1. **禁止重新计算** — 分数和风险等级是最终结果，只负责解读
2. **禁止编造症状** — 只基于 rawInputs 中的原始表述
3. **禁止下诊断** — 用"提示存在...风险"，不用"诊断为"
4. **强制风险提示** — hasEmergencyFlag → 报告开头包含危机干预提示
5. **保持专业边界** — 建议限于生活方式、心理教育、专业渠道
6. **数据来源声明** — 用"评估结果显示"而非"根据我的分析"
7. **措辞确定度溯源感知** — 根据 source + confidence 调整措辞：

| source | confidence | 措辞要求 |
|--------|-----------|---------|
| llm | ≥ 0.85 | "您明确表示..." |
| llm | 0.60-0.85 | "您的表述倾向于..." |
| llm | < 0.60 | "您似乎提到..." |
| rule_parser | ≥ 0.70 | "您提到..." |
| rule_parser | < 0.70 | "您似乎提到..."（引用原文） |
| direct_input | 任意 | "您选择了..."（确定度最高） |
| fallback | 任意 | "关于这一点，您没有具体说明..." |

### 输出结构（AnalysisResult）

```json
{
  "summary": "友好的评估摘要，200字以内",
  "riskAssessment": { "level": "重度抑郁", "description": "...", "requiresUrgentAttention": true },
  "detailAnalysis": { "overview": "...", "patterns": ["..."], "conclusion": "..." },
  "recommendations": {
    "immediate": [{"title": "...", "description": "..."}],
    "shortTerm": [{"title": "...", "description": "..."}],
    "professional": [{"title": "...", "description": "...", "resource": "..."}]
  },
  "followUp": { "suggestedDate": "2026-07-10", "rationale": "..." },
  "disclaimer": "本报告由AI辅助生成，仅供参考..."
}
```

### 输出 → FHIR 映射

- **DiagnosticReport**: `FhirConverter.toDiagnosticReport(result, patientFhirId)` — conclusion 来自 summary，extension 包含 detailAnalysis/recommendations/followUp
- **RiskAssessment**: `FhirConverter.toRiskAssessment(result, patientFhirId, qrReference)` — prediction[0].outcome 来自 riskLevel

### 调用时序

```
TransformationPipeline.execute(session) 完成
  │
  ├── [同步] FHIR QuestionnaireResponse 组装 & 保存到 MySQL（fhir_questionnaire_response_cache）
  ├── [同步] CompletionSummary 返回（score, severity, interpretation）
  │      → SSE { type: "completion", totalScore, severity, ... }
  │      （用户立即看到评分，不等分析层）
  │
  └── [异步] CompletableFuture.runAsync → AnalysisAgent.analyze()
        ├── T4 压缩：裁剪为只读事实包
        ├── LLM 调用（2-5s）
        ├── 组装 DiagnosticReport + RiskAssessment
        ├── FhirConverter → 写入 MySQL（fhir_diagnostic_report_cache / fhir_risk_assessment_cache）
        ├── 写入 analysis_reports 表
        └── SSE { type: "analysis_complete", analysisId, summary }
```

分析层异步执行，失败不影响核心评分结果。用户先看到评分，再收到分析报告推送。

---

## 关键数据流

### 路径 A：DATA_COLLECTION 意图（返回建议 → 用户确认 → SSE 采集）

```
用户输入 "我最近两周睡眠很差，做什么都提不起劲"
  │
  ▼
QaServiceImpl.answer() / answerWithSources()
  │
  ├── IntentRouterImpl.classify() → DATA_COLLECTION
  │
  ├── InterviewEngine.suggestQuestionnaire() → PHQ-9 匹配
  │
  ├── 返回 QaResponse（含 suggestionText + InterviewSuggestion）
  │     answer: "根据您的描述，我推荐您填写「PHQ-9 抑郁症筛查量表」...是否需要现在填写？"
  │     metadata: { suggestion: { matched: true, questionnaire: {...}, confidence: 0.85 } }
  │
  └── ⚠ 注意：此时仅返回建议，不启动访谈
       QaServiceImpl 不直接调用 startInterview() 或 CollectionSubAgent

  ▼ (前端渲染建议卡片 → 用户点击"开始评估")

前端: POST /api/his/interview/start-llm (SSE)
  Body: { questionnaireId: "PHQ-9", userId, conversationId }
  │
  ├── InterviewEngineImpl.startLlmInterview("PHQ-9", userId, conversationId)
  │     ├── 创建 InterviewSession（collectionMode = "llm_driven"）
  │     ├── 保存 Redis + MySQL
  │     ├── ConversationStateManager → QUESTIONNAIRE
  │     └── 返回首题信息 + session

  ▼ (SSE 事件流)
  │
  ├── SSE { type: "question", linkId: "/q1", text: "做事时提不起劲...", options: [...] }
  │
  ▼ (多轮对话循环)

  用户回答 "几乎每天都有这种感觉"
  │
  前端: POST /api/his/interview/llm-answer (SSE)
    Body: { sessionId, answer: "几乎每天都有这种感觉", conversationId }
  │
  ├── InterviewEngineImpl.processLlmTurn(sessionId, userInput, conversationId)
  │     ├── 【入站校验】只接受 QUESTIONING/CLARIFYING 状态
  │     │     ANSWER_RECEIVED/VALIDATING → 拒绝重复提交
  │     │     其他生命周期状态 → 抛出异常
  │     ├── 持久化用户消息（异步）
  │     ├── 状态转换: → ANSWER_RECEIVED
  │     ├── 调用 LLM:
  │     │     manual_form → handleManualFormInput()
  │     │     其他 → CollectionSubAgent.processUserInput(session, userInput, template)
  │     │
  │     ├── 【状态机分支处理】根据 AgentResult.type:
  │     │     RECORD_ANSWER → handleRecordAnswerState()
  │     │       ├── 校验 linkId 匹配当前题目
  │     │       ├── 校验 code 在选项列表中
  │     │       ├── 通过 → VALIDATING → recordAnswer() → 推进题号
  │     │       │   ├── 未完成 → QUESTIONING → buildNextQuestionResult()
  │     │       │   │     → SSE { type: "question" }（状态机自行构建）
  │     │       │   └── 已完成 → COMPLETED → TransformationPipeline
  │     │       │         → SSE { type: "completion" }
  │     │       └── 失败 → handleValidationFailure()
  │     │             → clarificationCount++ → CLARIFYING 或强制 manual_form
  │     │     CLARIFY → clarificationCount++ → CLARIFYING
  │     │             → ≥3次 → 强制 manual_form (buildManualFormPrompt)
  │     │             → SSE { type: "clarify" }
  │     │     COMPLETE → 防幻觉校验:
  │     │             → 未答完 → 忽略，buildNextQuestionResult()
  │     │             → 已答完 → COMPLETED + buildCompletionResponse()
  │     │     EMERGENCY_INTERRUPT → ABANDONED
  │     │             → SSE { type: "safety_alert" }
  │     │
  │     └── persistAgentResultMessage() → 异步写 MySQL
  │
  └── SSE 事件通过 CollectionSseEmitter 发送

  ▼ (全部题目完成后)

  ├── TransformationPipeline.execute(session, template)
  │     ├── 阶段1: 完整性校验
  │     ├── 阶段2: 规则计算（总分 + 风险等级 + 组合标记）
  │     └── 阶段3: FHIR QuestionnaireResponse 组装
  │
  ├── SSE { type: "completion", totalScore, maxScore, severity, interpretation }
  │
  ├── [异步] AnalysisAgent.analyze(analysisInput)
  │     ├── DiagnosticReport + RiskAssessment → MySQL
  │     ├── AnalysisReportEntity → analysis_reports 表
  │     └── SSE { type: "analysis_complete", analysisId, summary }
  │
  └── ConversationStateManager.clear(conversationId)
```

#### 子路径 A-1：全量纯表单降级（scope: "all"）

当 LLM 连续失败 ≥3 次（断路器熔断）时，从路径 A 的 SSE 循环中分叉：

```
SSE degradation 事件 (scope: "all", reason: "LLM不可用，请点击问卷答案")
  │
  ▼
前端截断 SSE 流，切换到全量纯表单:
  ├── 横幅: "LLM不可用，请点击问卷答案"
  ├── 进度条: X/N 题已选
  ├── 所有剩余题目卡片 + 可点击选项按钮
  └── "提交所有答案" 按钮（全部选完后激活）

  ▼ (用户逐题点击，全部完成后提交)

POST /api/his/interview/{sessionId}/batch-submit
  Body: { answers: { "/q5": "LA6570-2", "/q6": "LA6570-3", ... } }
  │
  ├── InterviewEngineImpl.submitBatch(sessionId, answers)
  │     ├── 遍历 answers → recordAnswer() 逐题记录 (source="manual_form")
  │     └── buildCompletionResponse() → 评分 + FHIR 组装
  │
  └── 返回 BatchSubmitResponse → 前端渲染 completion 卡片
```

> **注意**：scope "single" 的降级（追问超限）不截断 SSE 流，仅通过 `ElMessage.warning` 提示用户。下一题自动恢复 LLM 模式。

### 路径 B：MIXED 意图（RAG + 问卷推荐）

```
用户: "我最近压力很大，焦虑症该怎么缓解？"
  │
  ▼
IntentRouterImpl.classify() → MIXED
  │
  ├── RAG 管线完整执行 → 返回知识回答（SSE 流式）
  ├── InterviewEngine.suggestQuestionnaire() → GAD-7 匹配
  └── QaResponse 同时包含:
        answer (RAG 回答) + metadata.suggestion = GAD-7 InterviewSuggestion

  ▼ (前端渲染 RAG 答案 + "填写 GAD-7 焦虑筛查量表" 推荐按钮)

用户点击 → 与路径 A 完全一致: POST /api/his/interview/start-llm (SSE)
```

> **关键区别**：设计文档最初规划的 "不确认直接开始" 和 "forceQuestionnaire 参数绕过意图路由" **未实施**。实际实现将数据采集和知识问答分为两条独立通道：
> - 知识问答 → `QaController.stream()` → RAG 管线
> - 问卷采集 → `InterviewController` SSE 端点 → 独立访谈流程
> 
> 两者通过 `suggestion` 元数据在前端衔接，由用户主动触发切换。

---

## 数据库迁移

### 迁移脚本清单

| 脚本 | 说明 | 表 |
|------|------|-----|
| V5__FHIR_cache_tables.sql | FHIR 资源本地缓存（4张） | fhir_patient_cache, fhir_observation_cache, fhir_condition_cache, fhir_questionnaire_response_cache |
| V6__Interview_persistence.sql | 访谈采集持久化（3张） | interview_sessions, interview_messages, analysis_reports |
| V7__FHIR_diagnostic_risk_tables.sql | FHIR 分析输出缓存（2张） | fhir_diagnostic_report_cache, fhir_risk_assessment_cache |
| V9__Interview_status.sql | interview_sessions 增加 status 列 | ALTER TABLE interview_sessions ADD COLUMN status VARCHAR(20) DEFAULT 'QUESTIONING' |
| V10__Interview_status_refined.sql | 状态精细化 + 追问计数 | MODIFY status → 9状态枚举 + ADD clarification_count INT |

### 核心表结构

**interview_sessions**（会话主表）：
- 业务字段：session_id, questionnaire_id, user_id, conversation_id
- 状态字段：status (QUESTIONING/ANSWER_RECEIVED/CLARIFYING/VALIDATING/COMPLETED/ACTIVE/PAUSED/EXPIRED/ABANDONED), current_question_index, total_questions, completed
- JSON 字段：answers, raw_inputs, safety_flags, provenance
- 采集字段：collection_mode, degradation_level, consecutive_failures, clarification_count, context_summary, current_score

**interview_messages**（消息记录）：
- 按 session_id + sequence_num 排序
- message_type: suggestion / question / completion / safety_alert / progress / clarify / user_message
- message_data: 完整 JSON（供前端重建卡片）

**analysis_reports**（分析报告）：
- JSON 字段：risk_assessment, detail_analysis, recommendations, follow_up
- 业务字段：total_score, max_score, severity, interpretation, summary, disclaimer

---

## 文件清单

### 新增文件（19 个）

| 文件 | 层 | 说明 |
|------|-----|------|
| `service/his/CollectionSubAgent.java` | 采集层接口 | 采集子 Agent 接口 |
| `service/his/impl/CollectionSubAgentImpl.java` | 采集层实现 | LLM 语义理解（纯编码提取器）+ 断路器 + 规则解析器降级 (~570行) |
| `service/his/CollectionSseEmitter.java` | SSE 适配 | AgentResult → SSE 事件转换（含降级通知） |
| `service/his/CollectionToolOutputs.java` | DTO | 5 种工具调用输出类型 |
| `service/his/BatchSubmitRequest.java` | DTO | 全量纯表单批量提交请求（linkId → code） |
| `service/his/BatchSubmitResponse.java` | DTO | 批量提交响应（总分 + 严重程度 + 分析摘要） |
| `service/his/TransformationPipeline.java` | 转换层 | 三阶段确定性管道 |
| `service/his/AnalysisAgent.java` | 分析层接口 | 分析 Agent 接口 |
| `service/his/impl/AnalysisAgentImpl.java` | 分析层实现 | LLM 共情分析 |
| `service/his/AnalysisInput.java` | DTO | 只读事实包 |
| `service/his/AnalysisResult.java` | DTO | 结构化分析结果 |
| `service/his/InterviewPersistenceService.java` | 持久层接口 | 持久化协调 |
| `service/his/impl/InterviewPersistenceServiceImpl.java` | 持久层实现 | 3 表 + FHIR 缓存表协调 |
| `service/his/InterviewStatus.java` | 枚举 | ACTIVE/PAUSED/EXPIRED/ABANDONED |
| `service/his/impl/InterviewSessionCleanupTask.java` | 定时任务 | PAUSED 超时 → EXPIRED |
| `entity/his/InterviewSessionEntity.java` | 实体 | interview_sessions 表 |
| `entity/his/InterviewMessageEntity.java` | 实体 | interview_messages 表 |
| `entity/his/AnalysisReportEntity.java` | 实体 | analysis_reports 表 |
| `mapper/his/*` (3个) | Mapper | MyBatis Plus BaseMapper |
| `frontend/src/api/services/interview.service.ts` | 前端 API | SSE 流式访谈 + `batchSubmit()` + pause/abandon/resume/heartbeat |
| `frontend/src/api/types/interview.types.ts` | 前端类型 | 问卷相关 TypeScript 类型（含 `BatchSubmitRequest/Response`、`DegradationEvent.scope`） |

### 修改文件（9 个）

| 文件 | 修改内容 |
|------|---------|
| `InterviewSession.java` | 增加 collectionMode, rawInputs, safetyFlags, contextSummary, degradationLevel, consecutiveFailures, clarificationCount, provenance 字段；默认 status 改为 QUESTIONING |
| `InterviewEngine.java` | 增加 startLlmInterview, processLlmTurn, recordAnswer(9参数,含linkId校验), submitBatch, resume/pause/abandon/heartbeat, getInterviewMessages, getAnalysisReport, getInterviewsByConversation |
| `InterviewController.java` | 新增 `POST /{sessionId}/batch-submit` 端点；`emitAgentResult()` 根据 `consecutiveFailures ≥ 3` 判断降级 scope 传 "all" |
| `CollectionSseEmitter.java` | `sendDegradationNotice()` 新增 `scope` 参数（"single" / "all"），区分单题降级与全量降级 |
| `InterviewEngineImpl.java` | 核心实现 (~1520行): 严格握手协议状态机 + 三层存储 + 降级调度 + `submitBatch()` 批量提交 + 增量摘要 + 完成管道 |
| `InterviewPersistenceServiceImpl.java` | 状态默认值 ACTIVE → QUESTIONING，新增 clarificationCount 映射 |
| `CollectionSubAgentImpl.java` | 移除 ask_question 工具定义，LLM 退化为纯编码提取器 |
| `QuestionnaireTemplate.java` | 增加 comboRules, safetyKeywords, requiredItems, codeSystem |
| `QuestionnaireRepositoryImpl.java` | 更新 PHQ-9/GAD-7/ISI 内置配置（组合规则 + 安全关键词 + LOINC 编码） |
| `FhirConverter.java` | 新增 toDiagnosticReport, toRiskAssessment，增强 QuestionnaireResponse 组装（含 extension） |
| `QaView.vue` | 问卷建议卡片 → SSE 流式访谈 → question/completion/safety_alert 组件 + 全量纯表单卡片（manual_form）+ scope 区分处理 + `sendMessage` 拦截 |

---

## 验证方案

### 编译验证
```bash
mvn compile -DskipTests
cd frontend && npm run type-check
```

### 功能验证（端到端）
1. 通过聊天界面输入 "我最近两周情绪低落，睡不好觉"
2. 验证意图识别返回 `DATA_COLLECTION`，前端渲染问卷建议卡片
3. 用户点击"开始评估"，验证 `POST /api/his/interview/start-llm` SSE 流式返回首题
4. 逐轮输入自然语言回答，验证 `POST /api/his/interview/llm-answer` SSE 正常推进
5. 全部 9 题完成后，验证 completion 事件（总分 + 严重程度）
6. 验证 analysis_complete 事件（AI 分析报告）
7. 输入危险关键词（如 "不想活了"），验证 safety_alert 事件
8. 验证 MySQL 中 interview_sessions / messages / analysis_reports / fhir_* 表有数据

### 降级验证
- 模拟同一题追问 ≥3 次 → 验证 scope "single" 降级（仅该题纯表单，下题恢复 LLM）
- 模拟 LLM 连续失败 ≥3 次 → 验证 scope "all" 断路器熔断 → 全量纯表单卡片 → 批量提交 → completion
- 验证全量纯表单模式中已答题过滤正确（通过 getSessionMessages 排除）
- 模拟 Redis 不可用 → 验证本地缓存/MySQL 降级

### 回归验证
- 确保 `KNOWLEDGE_QUERY` 意图路径不受影响
- 确保手动填表 API（`/start` + `/answer`）仍正常工作
- 确保 `MIXED` 意图的 RAG 回答 + 问卷推荐正常
