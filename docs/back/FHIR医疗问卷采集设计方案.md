# LLM驱动的FHIR医疗问卷采集系统——设计方案

## 上下文

### 问题
当前 `DATA_COLLECTION` 意图识别后仅返回问卷建议文本，用户需手动调用 `/api/his/interview/start` 等 API 逐题填表。缺失 LLM 驱动的自然对话采集、安全紧急中断、共情分析和 FHIR 资源自动生成能力。

### 目标
在现有 IMKQAS（Spring Boot + MyBatis Plus + LangChain4j + HAPI FHIR）基础上，嵌入实现设计文档中的三层架构，仅当意图识别为 `DATA_COLLECTION` 时触发。

### 现有可复用资产
- **意图路由**: `IntentRouterImpl.classify()` → `IntentType.DATA_COLLECTION`
- **填表引擎**: `InterviewEngine` / `InterviewEngineImpl`（手动填表骨架）
- **问卷模板库**: `QuestionnaireRepositoryImpl`（PHQ-9/GAD-7/ISI + 评分规则）
- **对话状态**: `ConversationStateManager`（CHAT/QUESTIONNAIRE + Redis）
- **FHIR 基础设施**: HAPI FHIR R4, `FhirConverter`, 4 个缓存实体 + Mapper + Controller
- **安全基础设施**: `SafetyGuardService`（急症预检三级关键词）
- **LLM 集成**: LangChain4j + DashScope qwen-plus + SSE 流式
- **前端**: `QaView.vue`（SSE 流式渲染 + v-html）

### 触发条件
仅当 `IntentRouter.classify(query) == DATA_COLLECTION` 时进入此流程。

---

## 实施分阶段计划

### 第一阶段：采集层核心（LLM 驱动自然对话）

**目标**：将 `DATA_COLLECTION` 分支从 "返回建议文本" 升级为 "LLM 驱动的自然对话采集"

**新增文件**：
1. `src/main/java/com/student/service/his/CollectionSubAgent.java` — 采集子 Agent 接口
   - `processUserInput(sessionId, userInput)` → 返回工具调用结果
2. `src/main/java/com/student/service/his/impl/CollectionSubAgentImpl.java` — LLM 交互核心
   - 系统提示词构建（角色定义 + 问卷结构注入 + 采集规则 + 安全规则 + 对话风格）
   - 工具定义（LangChain4j Tool）：`askQuestion`, `recordAnswer`, `clarifyQuestion`, `completeCollection`, `emergencyInterrupt`
   - 每个患者会话独立实例，状态隔离
   - **不负责 SSE 事件转换**——只返回工具调用结果给调用方
3. `src/main/java/com/student/service/his/CollectionSseEmitter.java` — SSE 事件适配器（新增）
   - 职责：将 `CollectionSubAgent` 的工具调用结果转换为 SSE 事件
   - `emitQuestion(AskQuestionOutput)` → SSE `{type: "question", ...}`
   - `emitCompletion(CompleteOutput)` → SSE `{type: "completion", ...}`
   - `emitSafetyAlert(EmergencyInterruptOutput)` → SSE `{type: "safety_alert", ...}`
   - `emitProgress(current, total)` → SSE `{type: "progress", ...}`
4. `src/main/java/com/student/service/his/CollectionSessionState.java` — 采集会话状态类
   - 扩展现有 `InterviewSession`，增加：原始表述记录、安全标记列表、会话状态枚举
5. `src/main/java/com/student/service/his/CollectionToolOutputs.java` — 工具调用输出类型定义
   - `AskQuestionOutput`（问题文本 + 选项列表 + 进度）
   - `RecordAnswerOutput`（已记录确认）
   - `CompleteOutput`（完成确认）
   - `EmergencyInterruptOutput`（安全提示）

**修改文件**：
6. `src/main/java/com/student/service/rag/impl/QaServiceImpl.java`
   - `answer()` 方法（行 101-111）：DATA_COLLECTION 分支改为：`suggestQuestionnaire()` → 匹配后直接 `startInterview()`（去掉确认环节）→ `CollectionSubAgent.processUserInput()`
   - `answerWithSources()` 方法（行 309-327）：同上
   - 新增 MIXED 分支：检测到 `forceQuestionnaire` 参数时，跳过意图识别直接进入采集流程
7. `src/main/java/com/student/service/his/impl/InterviewEngineImpl.java`
   - 将现有 `answerQuestion(sessionId, answer)` 方法统一重构为 `recordAnswer(sessionId, linkId, code, display, value, rawInput, source, confidence)`
   - 旧的两参数签名保留为 `@Deprecated` 委托方法
   - 增强 `buildCompletionResponse()`，末尾触发 `AnalysisAgent.analyze()`
8. `src/main/java/com/student/service/his/impl/ConversationStateManagerImpl.java`
   - **不复用新状态**——复用现有 `QUESTIONNAIRE` 状态
   - `InterviewSession` 新增 `collectionMode` 字段（`llm_driven` / `manual_form`）区分 LLM 模式还是手动模式
   - 两种模式的进入/退出条件相同，行为差异由 `collectionMode` 字段控制

**关键设计决策**：
- **LLM 工具调用使用 LangChain4j `@Tool` 注解**，与现有 `langchain4j` 依赖一致
- **工具参数枚举校验在方法体内手动实现**——LangChain4j `@Tool` 不支持注解级枚举校验，需在 `recordAnswer` 方法体开头 `if (!validCodes.contains(code)) throw`
- **状态外部化**：复用现有 Redis `SESSION_KEY_PREFIX = "his:interview:"` 存储 `CollectionSessionState`
- **安全检测**：每条用户消息在传入 LLM 前先经过 `SafetyGuardService.checkEmergency()` 关键词检测
- **不确认直接开始**：`suggestQuestionnaire()` 匹配成功后直接 `startInterview()`，用户的首条症状描述作为 LLM 的"开场白"注入首轮上下文，不等待用户二次确认

---

### 第二阶段：转换层增强（确定性管道）

**目标**：将现有 `buildCompletionResponse()` 增强为完整的三阶段管道

**新增文件**：
1. `src/main/java/com/student/service/his/TransformationPipeline.java` — 管道编排器
   - 阶段1：完整性校验（检查必填项齐全、编码合法、类型匹配）
   - 阶段2：规则计算引擎（评分 + 风险等级 + 组合逻辑标记）
   - 阶段3：FHIR 组装（QuestionnaireResponse + Observation）

**修改文件**：
2. `src/main/java/com/student/service/his/impl/InterviewEngineImpl.java`
   - `buildCompletionResponse()` 方法重构，调用 `TransformationPipeline.execute()`
   - 增加组合逻辑规则支持（如 PHQ-9 Q1≥2 且 Q2≥2 → "快感缺失伴情绪低落"）
3. `src/main/java/com/student/service/his/QuestionnaireTemplate.java`
   - `ScoringRule` 增加 `comboRules: List<ComboRule>` 字段
   - 新增 `ComboRule` 内部类（条件表达式 + 触发标记）
   - 新增 `safetyKeywords: List<String>` 字段
   - 新增 `requiredItems: List<String>` 字段
4. `src/main/java/com/student/service/his/impl/QuestionnaireRepositoryImpl.java`
   - 更新 PHQ-9/GAD-7/ISI 的内置配置，增加组合规则和安全关键词

---

### 第三阶段：分析层（LLM 主 Agent）

**目标**：生成共情分析和 FHIR DiagnosticReport / RiskAssessment

**新增文件**：
1. `src/main/java/com/student/service/his/AnalysisAgent.java` — 分析 Agent 接口
2. `src/main/java/com/student/service/his/impl/AnalysisAgentImpl.java` — 核心实现
   - 系统提示词：角色定义 + 输入说明 + 核心规则（禁重新计算、禁编造症状、禁下诊断）+ 分析框架 + 输出格式
   - 输入数据结构：脱敏后的患者信息 + 规则结果（得分/风险等级/标记） + 原始表述
   - 输出：结构化 JSON → 组装为 FHIR DiagnosticReport + RiskAssessment
3. `src/main/java/com/student/service/his/AnalysisResult.java` — 分析结果数据类
   - summary, riskLevel, riskLabel, recommendations（分层建议）, followUpAdvice

**修改文件**：
4. `src/main/java/com/student/dto/his/FhirConverter.java`
   - 新增 `toDiagnosticReport(AnalysisResult)` 转换方法
   - 新增 `toRiskAssessment(AnalysisResult)` 转换方法
5. `src/main/java/com/student/service/his/impl/InterviewEngineImpl.java`
   - `buildCompletionResponse()` 末尾触发 `AnalysisAgent.analyze()`

**数据脱敏**：分析层输入前通过脱敏函数替换患者姓名→哈希、精确年龄→年龄段。

---

### 第四阶段：前端改造

**目标**：QaView.vue 支持问卷交互 UI（选项按钮、进度条、安全提示）

**新增文件**：
1. `frontend/src/api/services/interview.service.ts` — 问卷采集 API 客户端
2. `frontend/src/api/types/interview.types.ts` — 问卷相关 TypeScript 类型

**修改文件**：
3. `frontend/src/views/QaView.vue`
   - 新增 `QuestionnaireMessage` 消息类型（替代纯文本渲染问卷内容）
   - 当 `streamChunk.type === 'question'` 时渲染：问题文本 + 选项按钮组 + 进度条
   - 当 `streamChunk.type === 'completion'` 时渲染：评分结果卡片 + 严重程度 + 建议
   - 当 `streamChunk.type === 'safety_alert'` 时渲染：红色警告横幅 + 求助热线
   - 保留现有 `text` 类型兼容
4. `frontend/src/api/types/qa.types.ts`
   - `QaStreamChunk` 的 `type` 增加 `'question' | 'completion' | 'safety_alert' | 'progress'`
   - 新增 `QuestionChunk` 接口（questionText, options[], currentIndex, totalQuestions）
   - 新增 `CompletionChunk` 接口（totalScore, maxScore, severity, interpretation）

**修改文件（后端 SSE 适配）**：
5. `src/main/java/com/student/controller/qa/QaController.java`
   - `stream()` 方法中增加对 `sseEmitter.send()` 新事件类型的支持
6. `src/main/java/com/student/service/his/CollectionSubAgent.java` 或专门的 SSE 适配器
   - 将工具调用输出转换为 SSE 事件（question/completion/safety_alert/progress）

---

### 第五阶段：安全增强与可观测性

**目标**：补齐三层安全检测、添加结构化日志、启用 HIS 配置

**修改文件**：
1. `src/main/java/com/student/service/SafetyGuardService.java` 接口（如不存在则新增方法）
   - 新增 `checkCollectionSafety(userMessage)` — 采集层专用安全检测
2. `src/main/java/com/student/config/his/HisConfigProperties.java`
   - 将 `enabled` 默认值改为 `true`（或通过 application.yml 配置控制）
3. `src/main/java/com/student/service/his/impl/CollectionSubAgentImpl.java`
   - 增加结构化日志（JSON 格式，含 trace_id/session_id/questionnaire_id）
   - 增加采集指标记录（对话轮次、完成率、中断率）
4. `src/main/resources/application.yml`
   - 添加 HIS 采集相关配置项（超时时间、安全关键词列表路径、降级策略配置）

---

## 架构设计：状态机 + LLM 混合方案

### 核心原则

状态机做"交警"——指挥流程往哪走；LLM 做"翻译官"——负责理解人话和说人话。二者分工明确，互不越界。

| 状态机负责 | LLM 负责 |
|-----------|---------|
| 流程控制：决定下一个问题是 Q1 还是 Q2 | 语义映射：将自然语言映射到 FHIR 标准编码 |
| 状态持久化：记录当前进度，支持断点续传 | 追问生成：当回答模糊时生成自然追问 |
| 事件触发：什么条件推进到下一题 | 问题润色：将标准问卷文本转为自然口语 |
| 中断处理：安全信号触发后立即跳转 | 隐晦风险识别：识别关键词无法覆盖的表达 |
| 完成判定：所有必答题是否已采集 | 共情回应：对情绪表达给予恰当反馈 |
| 编码校验：LLM 返回的 code 是否在枚举内 | 分析总结：采集完成后生成评估摘要 |

### 两大铁律

1. **状态机拥有流程的所有权**——LLM 只被调用，不调用状态机
2. **LLM 的输出必须经过状态机校验**——编码合法性、必填完整性都由代码把关

### 状态机状态定义

```
IDLE                  → 等待开始
QUESTIONING           → 正在提问，等待用户回答
  ├─ ANSWER_RECEIVED  → 收到回答，调用 LLM 提取编码
  ├─ CLARIFYING       → LLM 认为需要追问，等待用户补充
  └─ VALIDATING       → LLM 返回编码，状态机校验合法性
COMPLETED             → 全部问题采集完毕
INTERRUPTED           → 安全中断
EXPIRED               → 会话超时
CANCELLED             → 用户主动取消
```

### 交互握手协议

```
状态机: "当前在 Q3，问题='睡眠情况如何'，选项=[code:0 无困难 / code:1 有点困难 / code:2 非常困难 / code:3 无法入睡]"
  │
  ▼
LLM:   收到上下文 → 理解用户 "晚上躺床上脑子停不下来"
       → 返回 { intent: "answer", code: "2", display: "非常困难", value: 2, confidence: 0.88 }
  │
  ▼
状态机: code="2" 在 Q3 合法选项内 ✓ → 记录答案 → 推进到 Q4
```

事件类型：
- `ANSWER_EXTRACTED` → 正常推进
- `EMERGENCY_TRIGGERED` → 无论当前状态如何，立即跳转中断状态
- `CLARIFICATION_NEEDED` → 进入澄清子状态（状态机记录，避免 LLM 无限追问）

### LLM 上下文管理策略

| 内容 | 策略 | 说明 |
|------|------|------|
| 系统提示词 | 始终保留 | 问卷结构注入 + 安全规则 |
| 最近对话 | 滑动窗口（3-5 轮） | 保留原始表述 + LLM 理解结果 |
| 历史摘要 | 增量拼接 | `{linkId → code → display}` 映射表 + 逐题一句话总结 |

增量上下文摘要机制：每完成一题，LLM 返回附带 `context_summary` 字段。逐题拼接，恢复时无需重新调用 LLM。

---

## 降级策略表

### 故障分类与降级矩阵

| 故障类型 | 检测方式 | 降级策略 | 用户体验影响 |
|---------|---------|---------|------------|
| **LLM 超时**（>5s 无响应） | 调用方超时计时器 | 降级为规则解析器，关键词映射选项 | 回复变"生硬"，流程不中断 |
| **LLM 不可用**（连续 3 次失败） | 断路器熔断（30s 冷却） | 跳过 LLM，纯状态机模式：展示标准问题 + 选项编号，用户输入编号 | 退化为表单模式 |
| **LLM 返回格式异常**（JSON 解析失败） | 输出校验层 | 规则解析器兜底 + 发送标准澄清请求 | 用户感知一次"能再说一下吗？" |
| **LLM 返回 code 不合法** | 状态机枚举校验 | 拒绝编码，规则解析器重新提取；仍失败则发澄清 | 内部静默处理，用户无感知 |
| **LLM 置信度低**（<0.7） | 状态机校验 LLM 返回的 confidence 字段 | 置信度 <0.7 触发澄清；<0.5 发选项列表让用户自选 | 多一轮对话确认 |
| **Redis 不可用** | 异常捕获 | 降级为本地 ConcurrentHashMap + 异步写 MySQL | 无感知，但重启后会话丢失 |
| **LLM API 密钥/额度耗尽** | 特定错误码 | 立即切换纯状态机模式 + 后台告警 | 退化为表单模式 |

### 三层递进降级

```
层级 1: LLM 全功能模式
  │  理解语义 → 返回精准编码 → 生成共情追问
  │
  ├── 降级触发: LLM 超时 / 断路器熔断
  ▼
层级 2: 规则解析器模式
  │  纯代码做关键词 → 选项映射 + 固定追问模板
  │  - 正则匹配 "几乎每天" → code=3
  │  - 模糊匹配 "还行吧，偶尔" → code=2
  │  - 多候选 → 发澄清 "您是指 A) 偶尔 B) 经常 C) 几乎每天？"
  │
  ├── 降级触发: 规则解析也无法确定
  ▼
层级 3: 纯表单模式
  │  展示选项编号，用户输入数字
  │  "请选择：1-完全没有 2-有几天 3-一半以上天数 4-几乎每天"
  │  用户输入 "3" → 直接映射
```

降级是单向的：一次降级后不变更，等下一题再尝试恢复。断路器冷却 30 秒后新请求重新尝试 LLM。

### 规则解析器设计

规则解析器是降级链路的核心——确定性语义 → 编码映射器，输出格式与 LLM 完全一致：

```json
{
  "source": "rule_parser",
  "code": "3",
  "display": "几乎每天",
  "value": 3,
  "confidence": 0.82,
  "raw_input": "最近基本上天天都这样"
}
```

解析步骤：

| 步骤 | 方法 | 示例 |
|------|------|------|
| 1. 数值提取 | 正则匹配 "3分"、"大概2分"、"4级" | 直接映射到选项 value |
| 2. 频率词映射 | 标准频率词汇表 → code | "完全没有"→0, "偶尔"→1, "经常"→2, "几乎每天"→3 |
| 3. 相似度匹配 | Jaccard/编辑距离与选项文本对比 | "睡不太着" vs "入睡困难" |
| 4. 否定逻辑 | 识别否定前缀 | "没有不开心" → code=0 |
| 5. 置信度评估 | 候选编码数及分数差距 | 多候选接近 → 触发澄清 |

`source` 字段告知分析层答案来源——LLM 提取的可以更肯定表达，规则解析器提取的用更保守措辞。

---

## 错误恢复机制

### 会话外部存储结构

**Redis（热数据）**：

```
Key: his:interview:{sessionId}
Value: {
  "sessionId": "a1b2c3d4",
  "questionnaireId": "PHQ-9",
  "status": "QUESTIONING",
  "currentQuestionIndex": 4,
  "totalQuestions": 9,
  "answers": {
    "/q1": {"code": "2", "display": "一半以上天数", "value": 2, "raw": "大部分时间都不想动", "source": "llm", "confidence": 0.91},
    "/q2": {"code": "3", "display": "几乎每天",     "value": 3, "raw": "天天都觉得没希望",     "source": "llm", "confidence": 0.87}
  },
  "currentScore": 5,
  "safetyFlags": [],
  "degradationLevel": "llm",
  "consecutiveFailures": 0,
  "contextSummary": "前面已问 Q1-Q2，用户表示大部分时间缺乏兴趣、几乎每天情绪低落。",
  "createdAt": "2026-06-26T10:30:00",
  "lastActiveAt": "2026-06-26T10:33:15"
}
TTL: 1800 秒（每轮活动后自动续期）
```

**MySQL（冷数据，持久化保障）**：每采集完一题，异步写入 `fhir_questionnaire_response_cache` 表（已存在），即使 Redis 数据丢失，也可从 MySQL 重建核心答案数据。

### 恢复流程

```
用户重新进入聊天
  │
  ├── 步骤1: 查询活跃会话
  │     ConversationStateManager.getState(conversationId)
  │       → Redis 命中: QUESTIONNAIRE 状态 + pendingInterviewSessionId
  │       → Redis 未命中: 查 MySQL fhir_questionnaire_response_cache
  │                       (按 conversationId + status='in_progress')
  │
  ├── 步骤2: 加载会话状态
  │     loadSession(sessionId)
  │       → Redis 命中: 完整恢复（含 answers / contextSummary / degradationLevel）
  │       → Redis 未命中但 MySQL 有: 从 MySQL 重建 answers
  │         （丢失 raw_input 和 confidence，但不影响继续采集）
  │       → 两者都没有: 会话已彻底丢失，新建会话
  │
  ├── 步骤3: 状态机实例化
  │     根据 status + currentQuestionIndex 直接定位当前状态
  │     不重放历史，从当前状态开始
  │
  ├── 步骤4: 重建 LLM 上下文
  │     系统提示词（固定模板 + 问卷结构）✓ 每次重新注入
  │     历史摘要（contextSummary）✓ 增量拼接，无需 LLM 重新生成
  │
  ├── 步骤5: 确定降级层级
  │     检查断路器状态
  │       ├── 断路器关闭 → 尝试 LLM 模式
  │       ├── 断路器半开 → 尝试 LLM 模式，超时 3s
  │       └── 断路器打开 → 规则解析器模式，30s 后再试
  │
  └── 步骤6: 向用户发送恢复提示
       "欢迎回来！我们刚才进行到第 5 题：[当前问题]"
```

### 飞行中请求处理

如果用户在 LLM 调用期间断开：
- `lastActiveAt` < 10s 前 → 可能有飞行中的 LLM 请求
- 恢复时等待 3s 重试加载：
  - answers 中当前题已有记录 → LLM 完成了但用户没收到响应 → 重发当前题或下一题
  - answers 中无当前题记录 → LLM 未完成 → 重新发送当前问题

### 恢复用户体验分级

| 场景 | 中断时长 | 提示文案 |
|------|---------|---------|
| A | < 30 分钟 | "欢迎回来！我们继续刚才的话题……那么请问：[当前问题]" |
| B | 30 分钟 ~ 24 小时 | "欢迎回来！你之前在进行 PHQ-9 评估，已完成 4/9 题。需要继续吗？[继续] [重新开始]" |
| C | > 24 小时 | "你之前有一个未完成的评估，由于时间较久，建议重新开始。[重新开始]" |

### 幂等性保证

`applyAnswer(linkId, code, ...)` 操作幂等——同一 linkId 重复写入时覆盖前值。恢复后即使重放已记录但未推进状态机的答案，也不会导致数据错误。恢复不等于重置降级状态——`consecutiveFailures` 持久化到 Redis，不因会话恢复清零。

---

## 上下文压缩执行策略

### 核心原则

压缩只影响 LLM 的输入上下文，不影响业务数据完整性。完整答案始终保存在 `answers` 字段中，用于 FHIR 组装和分析层。

### 四种压缩时机

| 时机 | 触发点 | 执行动作 | 频率 | 延迟要求 |
|------|-------|---------|------|---------|
| **T1: 增量摘要** | 每完成一题后（`answerQuestion()` 保存答案时） | 生成该题摘要行，追加到 `contextSummary` | 每完成 1 题 1 次 | < 50ms |
| **T2: 预调用压缩** | 每次 LLM 调用前（`processUserInput()` 构建 prompt 前） | 检查 token 预算，超阈值则执行压缩 | 每次 LLM 调用前 | < 20ms |
| **T3: 会话恢复** | 从 Redis/MySQL 恢复会话时 | 如果 `contextSummary` 为空，从 `answers` 重建 | 每次恢复 1 次 | < 100ms |
| **T4: 分析层调用** | `TransformationPipeline` 完成后调用 `AnalysisAgent` 前 | 裁剪为只读事实包（不含单项得分） | 每次采集完成 1 次 | < 10ms |

### T1 时序

```
用户回答 → answerQuestion() 
    ├── 保存完整答案到 answers Map
    ├── 生成摘要行 → contextSummary += 摘要行
    └── 保存到 Redis（原子操作）
```

### T2 时序

```
processUserInput() 
    ├── loadSession() 
    ├── estimateTokens() → 预估总 token
    ├── [estimatedTokens > THRESHOLD] → compressContext()
    ├── buildPrompt()
    └── callLLM()
```

### 触发条件与阈值

#### T1（增量摘要）触发条件

| 条件 | 说明 | 是否必须 |
|------|------|---------|
| 答案已成功记录 | answerRecord 非空且编码合法 | ✅ 必须 |
| 当前题目非最后一题 | 最后一题完成后不需要为后续对话生成摘要 | 建议 |
| contextSummary 长度 < 500 字 | 防止摘要无限膨胀 | 建议 |


```java
// T1 触发判断
public boolean shouldGenerateSummary(CollectionSessionState session, AnswerRecord answer) {
    return answer != null 
        && answer.getCode() != null
        && session.getCurrentIndex() < session.getTotalQuestions()
        && session.getContextSummary().length() < 500;
}
```

#### T2（预调用压缩）触发条件

| 压缩等级 | 触发条件 | 压缩动作 |
|---------|---------|---------|
| 不压缩 | 预估 token < 2000 | 完整上下文 |
| 轻度压缩 | 预估 token ≥ 2000 | 启用增量摘要替代完整对话历史 |
| 深度压缩 | 预估 token ≥ 3000 | 摘要 + 滑动窗口（保留最近 3 轮） |
| 极限压缩 | 预估 token ≥ 4000 | 仅保留当前题目 + 最近 1 轮 + 结构化摘要 |

```java
// T2 触发判断
public CompressionLevel decideCompressionLevel(CollectionSessionState session, String userInput) {
    int estimated = estimateTokens(session, userInput);
    if (estimated < 2000)  return CompressionLevel.NONE;       // 不压缩
    if (estimated < 3000)  return CompressionLevel.LIGHT;      // 轻度压缩
    if (estimated < 4000)  return CompressionLevel.DEEP;       // 深度压缩
    return CompressionLevel.EMERGENCY;                          // 极限压缩
}
```

### 阈值设定依据

| 阈值 | 数值 | 依据 |
|------|------|------|
| TOKEN_BUDGET | 2000 | qwen-plus 上下文 32K，预留 buffer，2000 是安全且经济的调用尺寸 |
| SUMMARY_MAX_LENGTH | 500 字 | 9 题 × 平均 15 字/题 = 135 字，500 字可覆盖 30+ 题的长问卷 |
| SLIDING_WINDOW_SIZE | 3 轮 | 保留最近 3 轮完整对话足以维持对话连贯性 |
| EMERGENCY_MAX_TOKENS | 4000 | 超过此值强制极限压缩，防止超出模型上下文限制 |

### Token 估算公式

```
estimatedTokens = 
    SYSTEM_PROMPT_BASE(200)                    // 固定：精简后的系统提示
  + QUESTIONNAIRE_CONTEXT(150)                 // 固定：当前题目 + 选项
  + contextSummary.length() * 1.2              // 动态：历史摘要（中文 ≈1.2 token/字）
  + recentDialogTurns * 80                     // 动态：最近 N 轮完整对话（≈80 token/轮）
  + userInput.length() * 1.2                   // 动态：当前用户输入
  + TOOL_DEFINITIONS(100)                      // 固定：工具定义
```

#### 示例计算（PHQ-9 第 6 题，已完成 5 题）

```
estimatedTokens = 200 + 150 + (5×15×1.2) + (3×80) + (20×1.2) + 100
                = 200 + 150 + 90 + 240 + 24 + 100
                = 804  ← 低于 2000，无需压缩
```

#### 示例计算（PHQ-9 第 9 题，已完成 8 题，含追问）

```
estimatedTokens = 200 + 150 + (8×18×1.2) + (3×80) + (30×1.2) + 100
                = 200 + 150 + 173 + 240 + 36 + 100
                = 899  ← 仍低于 2000，但接近轻度压缩阈值
```

**结论**：PHQ-9（9 题）在正常对话下不会触发压缩。压缩主要针对：

- 长问卷（自定义 20+ 题问卷）
- 用户频繁追问/澄清导致对话轮次增加
- 会话恢复后 contextSummary 重建的初始加载

---

## FHIR 资源组装：从 answers 到 QuestionnaireResponse 的完整映射

### 现有实现的问题

当前 `InterviewEngineImpl.generateResponse()` 只做了最基础的映射——仅有 code 无 system、无 extension、无评分信息、display 可能为空。

### 目标结构（以 PHQ-9 为例）

```json
{
  "resourceType": "QuestionnaireResponse",
  "id": "qr-a1b2c3d4",
  "meta": {
    "profile": ["http://hl7.org/fhir/StructureDefinition/QuestionnaireResponse"],
    "tag": [{ "system": "http://imkqas.org/fhir/tags", "code": "llm-collected" }]
  },
  "extension": [
    { "url": "http://imkqas.org/fhir/StructureDefinition/qr-total-score", "valueInteger": 22 },
    { "url": "http://imkqas.org/fhir/StructureDefinition/qr-risk-level", "valueString": "重度抑郁" },
    { "url": "http://imkqas.org/fhir/StructureDefinition/qr-collection-mode", "valueString": "llm-driven" }
  ],
  "questionnaire": "http://imkqas.org/fhir/Questionnaire/PHQ-9",
  "status": "completed",
  "subject": { "reference": "Patient/pat-{userId}" },
  "authored": "2026-06-26T10:35:00+08:00",
  "author": { "reference": "Device/imkqas-collection-agent" },
  "item": [
    {
      "linkId": "/q1",
      "text": "做事时提不起劲或没有兴趣",
      "extension": [
        { "url": "http://imkqas.org/fhir/StructureDefinition/raw-input",
          "valueString": "大部分时间都不想动，对什么都提不起兴趣" },
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
    }
  ]
}
```

### 编码系统映射

标准问卷选项使用 LOINC Answer Codes：

| 频率选项 | LOINC Code | Display | Score |
|---------|-----------|---------|-------|
| 完全没有 | LA6568-5 | Not at all | 0 |
| 有几天 | LA6569-3 | Several days | 1 |
| 一半以上的天数 | LA6570-1 | More than half the days | 2 |
| 几乎每天 | LA6571-9 | Nearly every day | 3 |

不同问卷类型使用不同编码系统：

| 问卷类型 | 编码系统 | 示例 URI |
|---------|---------|---------|
| 标准化量表（PHQ-9/GAD-7/ISI） | LOINC Answer Codes | `http://loinc.org` |
| 自定义问卷 | 项目自定义编码系统 | `http://imkqas.org/fhir/CodeSystem/{questionnaireId}` |
| 数值型问题 | UCUM | `http://unitsofmeasure.org` |

编码系统通过 `QuestionnaireTemplate` 新增 `codeSystem` 字段配置，不在代码里硬编码。

### 自定义 Extension 定义

| Extension URL | 类型 | 放置位置 | 说明 |
|--------------|------|---------|------|
| `qr-total-score` | integer | 根 | 问卷总分 |
| `qr-risk-level` | string | 根 | 风险等级文本 |
| `qr-max-score` | integer | 根 | 满分 |
| `qr-collection-mode` | string | 根 | 采集模式：llm-driven / rule-fallback / manual-form |
| `qr-conversation-id` | string | 根 | 关联对话 ID |
| `combo-flag` | string | 根（可多个） | 触发的组合逻辑标记 |
| `raw-input` | string | 每个 item | 用户的原始自然语言表述 |
| `answer-source` | string | 每个 item | 答案提取来源：llm / rule_parser / direct_input |
| `answer-confidence` | decimal | 每个 item | 映射置信度 0.0-1.0 |
| `score` | integer | 每个 item.answer | 该选项对应的分数 |

### 映射规则

```
answers 中的 {linkId → answerRecord}
  │
  ├── linkId                → item.linkId
  ├── template.items[linkId].text → item.text
  ├── answerRecord.code     → item.answer[0].valueCoding.code
  ├── template.codeSystem   → item.answer[0].valueCoding.system
  ├── option.display        → item.answer[0].valueCoding.display
  ├── answerRecord.value    → item.answer[0].extension[score]
  ├── answerRecord.rawInput → item.extension[raw-input]
  ├── answerRecord.source   → item.extension[answer-source]
  ├── answerRecord.confidence → item.extension[answer-confidence]
  │
  └── 全局评分              → 根 extension[qr-total-score / qr-risk-level / ...]
```

### 数据来源字段（source）的传递链

| 采集场景 | source 值 | 含义 | 典型 confidence |
|---------|----------|------|----------------|
| LLM 正常提取 | `llm` | 高可信度映射 | 0.85-0.98 |
| 降级规则解析器 | `rule_parser` | 确定性规则匹配 | 0.55-0.85 |
| 用户直接选择 | `direct_input` | 表单模式手动选择 | 1.0 |
| 系统兜底 | `fallback` | 用户未回答，取默认值 | 0.3-0.5 |

`source` 和 `confidence` 从 answers → InterviewSession.provenance → AnalysisInput.RawInputItem → FHIR extension（answer-source/answer-confidence）→ AnalysisAgent 系统提示词（措辞确定度规则），贯穿全链路：

```
采集层: CollectionSubAgent 提取答案时打标签
  ├── LLM 路径: RecordAnswerOutput.source = "llm"
  ├── 规则降级: RecordAnswerOutput.source = "rule_parser"
  └── 手动填表: source = "direct_input"（归一化自 "manual"）
      │
      ▼
InterviewEngineImpl.recordAnswer(): 
  写入 session.provenance.put(linkId, Provenance{source, confidence})
      │
      ▼
转换层 TransformationPipeline:
  读取 session.provenance → FHIR extension（answer-source / answer-confidence）
      │
      ▼
分析层 InterviewEngineImpl.triggerAsyncAnalysis():
  读取 session.provenance → AnalysisInput.RawInputItem { source, confidence }
      │
      ▼
AnalysisAgent 系统提示词（第 7 条规则）:
  根据 source + confidence 调整措辞确定度
  "您明确表示..." / "您的表述倾向于..." / "您似乎提到..." / "您选择了..." / "关于这一点，您没有具体说明..."
```

---

## 分析层主 Agent 设计

### 核心约束："只读"的精确含义

分析层 Agent 收到的是一个**只读事实包**。关键不在"不让它算"，而在"让它没法算"——不传入可用于计算的数据。

```
传入分析层的：
  ✓ 已计算好的总分: 22
  ✓ 风险等级: "重度抑郁"
  ✓ 触发的标记: ["快感缺失伴情绪低落", "自杀意念阳性"]
  ✓ 各题用户原始表述: ["大部分时间不想动", "天天觉得没希望", ...]
  ✓ 患者年龄范围: "25-35岁"
  ✓ 历史趋势: "较上次上升3分"

不传入分析层的：
  ✗ 各题单项得分（不让它重新加减）
  ✗ 阈值配置（不让它质疑分级标准）
  ✗ 患者精确年龄、姓名、联系方式
  ✗ 评分规则配置
  ✗ 编码映射关系
```

### 输入数据结构

```json
{
  "meta": {
    "questionnaireTitle": "PHQ-9 抑郁症筛查量表",
    "collectionDate": "2026-06-26",
    "analysisId": "analysis-{uuid}",
    "collectionMode": "llm-driven"
  },
  "ruleResults": {
    "totalScore": 22,
    "maxScore": 27,
    "riskLevel": "重度抑郁",
    "riskLabel": "重度抑郁（建议尽快寻求专业帮助）",
    "flags": [
      { "type": "combo", "label": "快感缺失伴情绪低落", "condition": "Q1≥2 且 Q2≥2" },
      { "type": "safety", "label": "自杀意念阳性", "condition": "Q9≥1" }
    ]
  },
  "patientContext": {
    "ageRange": "25-35岁",
    "gender": "男",
    "assessmentCount": 3,
    "trend": {
      "direction": "上升",
      "delta": 3,
      "previousScore": 19,
      "previousDate": "2026-05-15",
      "description": "较上次评估上升3分，趋势值得关注"
    }
  },
  "rawInputs": [
    { "questionText": "做事时提不起劲或没有兴趣", "userSaid": "大部分时间都不想动，对什么都提不起兴趣",
      "source": "llm", "confidence": 0.91 },
    { "questionText": "感到情绪低落、沮丧或绝望", "userSaid": "天天都觉得没希望，早上醒来就难受",
      "source": "llm", "confidence": 0.87 },
    { "questionText": "感到心情低落、沮丧或绝望", "userSaid": "有时候会突然想哭，但也没发生什么特别的事",
      "source": "rule_parser", "confidence": 0.62 }
  ],
  "safetyStatus": {
    "hasEmergencyFlag": true,
    "triggeredKeywords": ["自杀意念"],
    "wasInterrupted": false
  }
}
```

### 系统提示词

```
你是心理健康评估分析专家。你的任务是：基于已完成的问卷评估结果和患者的原始表述，生成一份专业、共情、个性化的分析报告。

=== 你的输入 ===
1. ruleResults: 问卷的评分结果——这是已经由计算引擎确定的事实，你直接引用，不得重新计算、不得质疑其准确性
2. patientContext: 患者的基本信息和评估历史（均已脱敏）
3. rawInputs: 患者在回答每道题时的原始自然语言表述——这是你理解患者真实体验的主要依据。
   每项包含 source（答案来源：llm/rule_parser/direct_input/fallback）和 confidence（置信度0.0-1.0），
   用于指导你在引用该题目答案时的措辞确定度（参见第7条规则）。
4. safetyStatus: 安全标记状态——如果包含自杀意念标记，你必须在报告中包含危机提示

=== 核心规则（违反任一规则视为分析无效）===
1. 【禁止重新计算】你收到的分数和风险等级是最终结果，你只负责解读，不负责复核
2. 【禁止编造症状】只基于 rawInputs 中的原始表述来描述患者体验，不得添加患者未提及的症状
3. 【禁止下诊断】你是筛查工具。措辞用"提示存在...风险"、"建议进一步评估"，不得用"诊断为"、"你患有"
4. 【强制风险提示】如果 safetyStatus.hasEmergencyFlag 为 true，报告开头必须包含危机干预提示
5. 【保持专业边界】你的建议限于：生活方式调整、心理教育资源、寻求专业帮助的渠道。不得推荐具体药物、剂量、治疗方案
6. 【数据来源声明】分析中引用具体得分时，用"评估结果显示"而非"根据我的分析"
7. 【措辞确定度——溯源感知】rawInputs 中每题的 source 和 confidence 字段指示该答案的可靠性。
   你必须根据以下规则调整引用患者表述时的措辞确定度：

   | source       | confidence   | 措辞要求 |
   |-------------|-------------|---------|
   | llm         | >= 0.85     | 用"您明确表示..."、"您清楚描述了..."，语气肯定 |
   | llm         | 0.60-0.85   | 用"您的表述倾向于..."、"从您的描述来看..."，语气适度 |
   | llm         | < 0.60      | 用"您似乎提到..."，并在分析中原样引用 userSaid 中的原文 |
   | rule_parser | >= 0.70     | 用"您提到..."、"根据您的回答..." |
   | rule_parser | < 0.70      | 用"您似乎提到..."，并在分析中原样引用 userSaid 中的原文 |
   | direct_input| 任意        | 用"您选择了..."（用户直接点选的选项，确定度最高） |
   | fallback    | 任意        | 用"关于这一点，您没有具体说明..."，不得编造任何体验 |
   | 缺失        | 任意        | 等同于 fallback，采用最保守的措辞 |

   注意：这些措辞用于撰写分析时引用特定题目答案的方式。报告中不同题目的引用可以有不同的确定度。
   当多个答案描述同一主题时，优先采用确定度更高的措辞。

=== 分析框架 ===
1. 整体评估（2-3句）：概述评估结果 + 历史趋势
2. 细节理解：按主题聚类原始表述（情绪体验/生理症状/社会功能），找跨题目模式，不逐题罗列
3. 综合判断（1-2句）：基于规则结果+原始表述的整体印象
4. 建议分层：即时行动 → 短期方法 → 专业渠道

=== 共情表达指导 ===
- 用"你"而非"患者"
- 承认感受："你描述的...是很多人都会经历的困难"
- 给予希望："抑郁情绪是可治疗的，很多人都得到了显著改善"
- 避免空洞安慰：不说"一切都会好起来"，说"可以通过...来逐步改善"
```

### 输出 JSON 结构

```json
{
  "summary": "友好的评估摘要，200字以内",
  "riskAssessment": {
    "level": "重度抑郁（从 ruleResults 引用）",
    "description": "对风险等级的通俗解释，100字以内",
    "requiresUrgentAttention": true
  },
  "detailAnalysis": {
    "overview": "整体评估段落的 markdown",
    "patterns": ["模式1", "模式2"],
    "conclusion": "综合判断的 markdown"
  },
  "recommendations": {
    "immediate": [{"title": "...", "description": "..."}],
    "shortTerm": [{"title": "...", "description": "..."}],
    "professional": [{"title": "...", "description": "...", "resource": "电话或链接"}]
  },
  "followUp": {
    "suggestedDate": "2026-07-10",
    "rationale": "建议2周后重新评估以追踪变化"
  },
  "disclaimer": "本报告由AI辅助生成，仅供参考，不构成医疗诊断..."
}
```

### 输出→FHIR 映射

**DiagnosticReport**：

| 字段 | 来源 |
|------|------|
| `id` | `"dr-" + analysisId` |
| `status` | `"final"` |
| `code` | LOINC "心理健康评估报告" |
| `subject` | `Patient/pat-{userId}` |
| `conclusion` | `analysisResult.summary` |
| `extension[detail-analysis]` | `detailAnalysis` JSON |
| `extension[recommendations]` | `recommendations` JSON |
| `extension[follow-up]` | `followUp` JSON |
| `extension[disclaimer]` | `disclaimer` |

**RiskAssessment**：

| 字段 | 来源 |
|------|------|
| `id` | `"ra-" + analysisId` |
| `status` | `"final"` |
| `subject` | `Patient/pat-{userId}` |
| `basis` | 引用对应的 QuestionnaireResponse |
| `prediction[0].outcome` | `riskLevel` |
| `prediction[0].qualitativeRisk` | `riskLabel` |

### 调用时序

```
TransformationPipeline.execute(session) 完成
  │
  ├── [同步] FHIR QuestionnaireResponse 组装 & 保存
  ├── [同步] SSE 发送 {type: "completion", score, severity, interpretation}
  │      （用户立即看到评分结果，不等分析层）
  │
  └── [异步] AnalysisAgent.analyze()
        ├── LLM 调用（2-5s）
        ├── 组装 DiagnosticReport + RiskAssessment
        ├── FhirConverter 写入 MySQL
        └── SSE 发送 {type: "analysis_report", summary, recommendations}
```

用户首先拿到评分和风险等级（转换层输出），然后收到完整分析报告推送。分析层异步执行，失败不影响核心评分结果。

### FhirConverter 新增方法

```java
// 新增两个转换方法
public DiagnosticReport toDiagnosticReport(AnalysisResult result, String patientFhirId)
public RiskAssessment toRiskAssessment(AnalysisResult result, String patientFhirId, String qrReference)
```

---

## 关键数据流

### 路径 A：DATA_COLLECTION 意图（直接进入采集）

```
用户输入 "我最近两周睡眠很差，做什么都提不起劲"
  │
  ▼
QaController.stream() → QaServiceImpl.answerWithSources()
  │
  ├── 🔒 SafetyGuardService.checkEmergency(userInput)  ← 每轮必检
  │     ├── 三级关键词匹配
  │     └── 命中 → EMERGENCY_INTERRUPT → SSE {type: "safety_alert"}
  │
  ├── IntentRouterImpl.classify() → DATA_COLLECTION
  │
  ├── InterviewEngine.suggestQuestionnaire() → PHQ-9 匹配
  │     └── 匹配成功 → 去掉确认环节,直接 startInterview()
  │
  ├── InterviewEngine.startInterview("PHQ-9", userId, conversationId)
  │     ├── 创建 CollectionSessionState → 存入 Redis
  │     └── ConversationStateManager.transition(conversationId, QUESTIONNAIRE)
  │
  ├── CollectionSubAgent.processUserInput(sessionId, userInput)
  │     ├── 构建系统提示词（注入 PHQ-9 完整结构 + 用户开场白）
  │     ├── LLM 调用（LangChain4j + Tool 定义）
  │     ├── LLM 返回: askQuestion(linkId="/q1", text="...")
  │     └── 返回 AskQuestionOutput（不直接操作 SSE）
  │
  ├── CollectionSseEmitter.emitQuestion(output) → SSE {type: "question", ...}
  │
  ▼ (多轮对话循环)
  │
  ├── 用户回答 "几乎每天都有这种感觉"
  │     ├── 🔒 SafetyGuardService.checkEmergency(userInput)  ← 每轮必检
  │     │      ├── 三级关键词匹配（<1ms）
  │     │      └── 命中 → EMERGENCY_INTERRUPT
  │     │
  │     ├── [未命中] CollectionSubAgent.processUserInput(sessionId, userInput)
  │     │     ├── estimateTokens() → 检查是否需要压缩
  │     │     ├── 构建 prompt（含 contextSummary 增量摘要）
  │     │     ├── LLM 调用 → recordAnswer(linkId="/q1", code="3", ...)
  │     │     └── 返回 RecordAnswerOutput
  │     │
  │     ├── 🔒 状态机校验: code="3" 在 Q1 合法选项内 ✓
  │     │
  │     ├── InterviewEngine.recordAnswer(sessionId, linkId, code, ...)
  │     │     ├── 保存完整答案到 answers Map
  │     │     ├── 增量更新 contextSummary（T1 压缩）
  │     │     ├── 推进 currentQuestionIndex
  │     │     └── 保存到 Redis（原子操作）
  │     │
  │     ├── 未完成 → LLM 返回下一题 askQuestion(...)
  │     │     └── CollectionSseEmitter.emitQuestion() → SSE {type: "question"}
  │     │
  │     └── 已完成 → completeCollection()
  │
  └── (循环直到所有题目完成)
  │
  ▼ (全部题目完成后)
  │
  ├── TransformationPipeline.execute(session)
  │     ├── 阶段1: 完整性校验 → 9/9 通过
  │     ├── 阶段2: 规则计算引擎 → 总分 22, 风险等级: 重度
  │     │     └── 组合逻辑: Q1≥2 且 Q2≥2 → flag: "快感缺失伴情绪低落"
  │     └── 阶段3: FHIR 组装 → QuestionnaireResponse + Observation
  │
  ├── [同步] CollectionSseEmitter.emitCompletion() → SSE {type: "completion", ...}
  │      （用户立即看到评分结果）
  │
  ├── [异步] AnalysisAgent.analyze(ruleResult, rawInputs)
  │     ├── T4 压缩: 裁剪为只读事实包
  │     ├── LLM 分析（2-5s, 脱敏数据）
  │     ├── 组装 DiagnosticReport + RiskAssessment
  │     ├── FhirConverter → 写入 MySQL
  │     └── CollectionSseEmitter → SSE {type: "analysis_report", ...}
  │
  └── ConversationStateManager.clear(conversationId)
```

### 路径 B：MIXED 意图 → 用户点击推荐 → 进入采集

```
用户: "我最近压力很大，焦虑症该怎么缓解？"
  │
  ▼
IntentRouterImpl.classify() → MIXED
  │
  ├── RAG 管线完整执行 → 返回知识回答
  ├── InterviewEngine.suggestQuestionnaire() → GAD-7 匹配
  └── RAG 答案末尾附加: "根据你的描述，推荐填写 GAD-7 焦虑筛查量表。[点击开始评估]"
      （前端渲染为可点击按钮,携带 forceQuestionnaire="GAD-7"）

  ▼ (用户点击按钮)
  │
前端: POST /api/qa/stream
  Body: { query: "开始评估", userId, conversationId, forceQuestionnaire: "GAD-7" }

  ▼
QaServiceImpl.answerWithSources():
  │
  ├── 检测到 forceQuestionnaire="GAD-7" → 跳过 intentRouter.classify()
  ├── 直接进入 DATA_COLLECTION 分支
  ├── 🔒 SafetyGuardService.checkEmergency(query) → 通过
  ├── InterviewEngine.startInterview("GAD-7", userId, conversationId)
  │     └── 不复用 suggestQuestionnaire（用户已明确选择）
  ├── CollectionSubAgent.processUserInput(sessionId, "开始评估")
  │     └── LLM 首轮: askQuestion(linkId="/q1", ...)
  └── 此后与路径 A 的多轮循环完全一致
```

---

## 文件变更汇总

| 阶段 | 新增文件 | 修改文件 |
|------|---------|---------|
| 一 | `CollectionSubAgent.java`, `CollectionSubAgentImpl.java`, `CollectionSseEmitter.java`, `CollectionSessionState.java`, `CollectionToolOutputs.java` | `QaServiceImpl.java`（DATA_COLLECTION 分支 + MIXED forceQuestionnaire 路径）, `InterviewEngineImpl.java`（统一 recordAnswer 签名）, `ConversationStateManagerImpl.java`（InterviewSession 增加 collectionMode 字段）, `InterviewSession.java`（增加 collectionMode + rawInputs + safetyFlags） |
| 二 | `TransformationPipeline.java` | `InterviewEngineImpl.java`（重构 buildCompletionResponse）, `QuestionnaireTemplate.java`（增加 comboRules/safetyKeywords/requiredItems/codeSystem）, `QuestionnaireRepositoryImpl.java`（更新内置配置） |
| 三 | `AnalysisAgent.java`, `AnalysisAgentImpl.java`, `AnalysisResult.java` | `FhirConverter.java`（新增 toDiagnosticReport/toRiskAssessment）, `InterviewEngineImpl.java`（末尾异步触发分析） |
| 四 | `interview.service.ts`, `interview.types.ts` | `QaView.vue`（question/completion/safety_alert 组件 + MIXED 推荐按钮）, `qa.types.ts`（新增流块类型）, `QaController.java`（SSE 新事件类型 + forceQuestionnaire 参数） |
| 五 | — | `SafetyGuardService.java`, `HisConfigProperties.java`, `CollectionSubAgentImpl.java`（结构化日志 + 指标）, `application.yml` |

**总计：新增约 10 个文件，修改约 14 个文件**

---

## 验证方案

### 编译验证
```bash
mvn compile -DskipTests
cd frontend && npm run type-check
```

### 功能验证（端到端）
1. 启动服务后，通过聊天界面输入 "我最近两周情绪低落，睡不好觉"
2. 验证意图识别返回 `DATA_COLLECTION`
3. 验证系统自动匹配 PHQ-9 并启动 LLM 对话采集
4. 验证逐题自然对话完成全部 9 题
5. 验证完成后的评分结果和共情分析
6. 验证 FHIR QuestionnaireResponse 写入数据库
7. 输入危险关键词（如 "不想活了"），验证紧急中断触发

### 回归验证
- 确保 `KNOWLEDGE_QUERY` 和 `MIXED` 意图路径不受影响
- 确保现有 `/api/his/interview/*` 手动填表 API 仍正常工作
- 运行 `mvn test` 确保现有测试通过

### 工具调用正确性验证
- 日志中验证 LLM 返回的 `record_answer.code` 始终在预定义枚举范围内
- 验证评分计算结果与人工计算一致
