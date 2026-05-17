# RAG 检索管线 — 代码链路全追踪

> 最后更新: 2026-05-10  
> 追踪版本: QaServiceImpl v3.0 + QueryRewrite v3.0  
> 本文档以代码执行路径为主线，合并所有 RAG 相关文档内容

---

## 一、完整链路一览

```
用户查询: "扑热息痛对小孩发烧有用吗"
  │
  ├─[1] QaServiceImpl.answer()                              ← 入口
  │
  ├─[2] QueryRewriteService.rewrite()                       ← 查询预处理
  │     ├─ 拼写纠正 (correctSpelling)
  │     ├─ 停用词过滤 (simplify, 保留否定词)
  │     ├─ 医学实体识别 (MedicalEntityRecognitionService)
  │     ├─ 同义词扩展 (SynonymExpansionService, 3级级联)
  │     ├─ 意图扩展 (expand)
  │     └─ 医学术语标准化 (medicalize → SNOMED CT)
  │
  ├─[3] SafetyGuardService.checkEmergency()                 ← 安全兜底①: 急症预检
  │     └─ 命中高危词 → 阻断, 返回就医建议
  │
  ├─[4] MultiRetrievalService.hybridRetrieval()             ← 双路召回
  │     ├─ vectorRetrieval()  ─→ EmbeddingService ─→ Milvus (K=30)
  │     └─ keywordRetrieval() ─→ KeywordRetrievalService ─→ Lucene BM25 (K=30)
  │
  ├─[5] RrfFusionService.fuseVectorAndKeyword()             ← RRF融合
  │     └─ score(d) = Σ w_i × 1/(60 + rank_i(d))
  │
  ├─[6] QualityFilterService.filter()                       ← 质量过滤 (4层)
  │     ├─ 片段长度 < 20字 → 丢弃
  │     ├─ 黑名单域名 + 短文本 → 丢弃
  │     └─ 纯免责声明无医学实质 → 丢弃
  │
  ├─[7] QualityFilterService.detectContradictions()         ← 矛盾检测
  │     └─ 药物-人群对正反断言冲突 + 权威性 > 0.7 → 阻断
  │
  ├─[8] MultiFactorRerankService.rerank()                   ← 多因子重排序
  │     └─ final = 0.4*authority + 0.2*timeliness + 0.4*semantic
  │
  ├─[9] SafetyGuardService.assessConfidence()               ← 安全兜底②: 置信度门控
  │     └─ maxScore < 阈值 → 阻断, 返回"当前知识不足"
  │
  ├─[10] SemanticCacheService.get()                         ← 语义缓存链
  │      ├─ 命中 → 直接返回 (延迟 < 5ms)
  │      └─ 未命中 → 分布式锁 → LLM生成 → 写入缓存
  │
  ├─[11] LlmService.generateAnswer()                        ← LLM生成
  │      ├─ buildMedicalPrompt() → 强制约束系统指令
  │      └─ callLlmApi() → 阿里云百炼 DashScope
  │
  ├─[12] SafetyGuardService.sanitizeAnswer()                ← 安全兜底③: 回答校验
  │      ├─ 替换危险用药模式
  │      └─ 低置信度追加免责声明
  │
  └─[13] 构建 QaResponse → 返回                             ← 出口
```

**关键数字速记**：

| 环节 | 关键参数 |
|------|----------|
| 双路召回 | BM25:30 + Vector:30, 并行 CompletableFuture |
| RRF融合 | k=60, 向量:0.6 + 关键词:0.4 |
| 质量过滤 | 4层规则, 黑名单6域名 |
| 矛盾检测 | 正反断言, 权威性阈值 0.7 |
| 重排序 | 权威性0.4 + 时效性0.2 + 语义0.4, 最终topK=5 |
| 语义缓存 | Redis TTL=7200s, 分布式锁TTL=30s |
| LLM | 阿里云百炼, OkHttp连接池, Caffeine本地缓存10000条 |

---

## 二、逐步代码追踪

### 2.1 入口 — `QaServiceImpl.answer()`

- **文件**: `src/main/java/com/student/service/rag/impl/QaServiceImpl.java:74`
- **方法签名**: `public QaResponse answer(String query, Long userId, Long conversationId)`

```java
// QaServiceImpl.java:74-201
public QaResponse answer(String query, Long userId, Long conversationId) {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    // [2] 查询预处理
    String processedQuery = queryRewriteService.rewrite(query, userId, conversationId);

    // [3] 急症预检
    SafetyDecision emergencyDecision = safetyGuardService.checkEmergency(processedQuery);
    if (emergencyDecision.isBlocked()) return buildEmergencyResponse(...);

    // [4][5] 双路召回 + RRF融合
    List<RetrievalResult> retrievalResults = retrieveDocuments(processedQuery);

    // [6] 质量过滤
    FilterResult filterResult = qualityFilterService.filter(retrievalResults);
    List<RetrievalResult> filteredResults = filterResult.getPassed();

    // [7] 矛盾检测
    ContradictionResult contradiction = qualityFilterService.detectContradictions(filteredResults, query);
    if (contradiction.isHasContradiction()) return buildContradictionResponse(...);

    // [8] 多因子重排序
    List<RetrievalResult> rerankedResults = rerankDocuments(processedQuery, filteredResults);

    // [9] 置信度门控
    ConfidenceDecision confidenceDecision = safetyGuardService.assessConfidence(rerankedResults);
    if (confidenceDecision.isBlocked()) return buildLowConfidenceResponse(...);

    // [10] 语义缓存链
    String normalizedQuery = queryRewriteService.normalize(query);
    List<Long> sortedFragmentIds = extractSortedFragmentIds(rerankedResults);
    CachedAnswer cached = semanticCacheService.get(normalizedQuery, sortedFragmentIds);
    if (cached != null) {
        answer = safetyGuardService.sanitizeAnswer(cached.getAnswer(), 0.8);  // [12]
    } else {
        // 分布式锁 → LLM → 写缓存 [11]
        boolean lockAcquired = tryAcquireRebuildLock(...);
        if (lockAcquired) {
            answer = generateAnswer(query, context);
            answer = safetyGuardService.sanitizeAnswer(answer, calculateConfidence(...));
            semanticCacheService.put(normalizedQuery, sortedFragmentIds, answer, sources);
            releaseRebuildLock(...);
        } else {
            Thread.sleep(200);  // 等待锁持有者
            cached = semanticCacheService.get(normalizedQuery, sortedFragmentIds);
            if (cached != null) answer = cached.getAnswer();
            else answer = generateAnswer(query, context);  // 直接LLM, 不写缓存
        }
    }

    // [13] 构建响应
    return new QaResponse(query, answer, sources, confidence, processingTime, modelUsed);
}
```

---

### 2.2 查询预处理 — `QueryRewriteService.rewrite()`

- **文件**: `src/main/java/com/student/service/rag/impl/QueryRewriteServiceImplRefactored.java:343`
- **版本**: v3.0（重构版）
- **依赖**: RedisService, SnomedTerminologyService, MedicalEntityRecognitionService, SynonymExpansionService

**处理流程**（6步）：

```
原始查询 "扑热息痛对小孩发烧有用吗"
  │
  ├─ ① correctSpelling()  ─→ 拼写纠正
  │    "感冐"→"感冒", "高血圧"→"高血压"
  │    规则: 10组常见错别字映射
  │    规划: symspell-java 智能纠正（待依赖解决后启用）
  │
  ├─ ② simplify()  ─→ 停用词过滤
  │    移除: "的", "了", "是", "我", "吗", "呢", "请问"...
  │    保留: 所有否定词 ("不", "没", "无", "未", "非", "否")
  │
  ├─ ③ entityRecognitionService.recognize()  ─→ 医学实体识别
  │    引擎: HanLP + THUOCL 医学词库
  │    输出: List<MedicalEntity>
  │      [扑热息痛:DRUG(0.95)], [小孩:POPULATION(0.90)], [发烧:SYMPTOM(0.98)]
  │
  │    实体类型 (8类):
  │    DRUG(药品), DISEASE(疾病), SYMPTOM(症状), POPULATION(人群),
  │    BODY_PART(身体部位), EXAMINATION(检查), TREATMENT(治疗), OTHER(其他)
  │
  │    THUOCL词库管理:
  │    - 每10分钟自动检查文件更新并热加载
  │    - 词库不可用时降级使用内置50+核心医学术语
  │
  ├─ ④ synonymExpansionService.expand()  ─→ 同义词扩展 (3级级联)
  │    第1级: 本地映射表 (LOCAL, 延迟<1ms, 置信度1.0)
  │      "扑热息痛" → "对乙酰氨基酚"
  │      "发烧"     → "发热"
  │    第2级: SNOMED CT Snowstorm FHIR API (延迟50-200ms, 置信度0.85-0.95)
  │      "小孩" → "儿童"
  │    第3级: LLM 临时推断 (兜底, 置信度≥0.7才采纳, 不写入映射表)
  │
  │    兜底: 未映射词条 → 人工审核队列 → 频次≥3推送审核 → 专家标注 → 回写映射表
  │    告警: 未映射率 > 5% 触发告警
  │
  ├─ ⑤ expand()  ─→ 意图扩展
  │    根据 classifyIntent() 识别的意图追加相关术语
  │    意图类型 (10种):
  │    DISEASE_QUERY, DRUG_QUERY, SYMPTOM_QUERY, DEPARTMENT_GUIDANCE,
  │    TREATMENT_QUERY, PREVENTION_QUERY, EXAMINATION_QUERY,
  │    EMERGENCY_QUERY, GENERAL_HEALTH, OTHER
  │    → "对乙酰氨基酚 儿童 发热 治疗 用法"
  │
  └─ ⑥ medicalize()  ─→ SNOMED CT 术语标准化
       优先: SNOMED CT 服务
       降级: 本地口语→术语映射表
       "脑袋疼"→"头痛", "拉肚子"→"腹泻", "睡不着"→"失眠"
```

**关键接口**：

| 接口/类 | 文件 | 核心方法 |
|----------|------|----------|
| `MedicalEntityRecognitionService` | `rag/MedicalEntityRecognitionService.java` | `recognize(query)`, `segment(query)` |
| `SynonymExpansionService` | `rag/SynonymExpansionService.java` | `expand(query, entities)`, `lookupTerm(term)`, `approveMapping(...)` |
| `SnomedTerminologyService` | `snomed/SnomedTerminologyService.java` | `medicalize(query)`, `searchConcept(term)` |

---

### 2.3 安全兜底①：急症预检 — `SafetyGuardService.checkEmergency()`

- **文件**: `src/main/java/com/student/service/rag/SafetyGuardService.java:48`

```
配置中定义三级急症关键词 (CRITICAL / HIGH / MEDIUM)
  CRITICAL: "胸痛"、"大出血"、"窒息"、"意识丧失"...
  HIGH:     "骨折"、"烧伤"、"中毒"...
  MEDIUM:   "高烧不退"、"持续呕吐"...

查询命中 → SafetyDecision.block() → 返回就医建议, 不进入检索
查询未命中 → SafetyDecision.pass() → 继续管线
```

---

### 2.4 双路召回 — `MultiRetrievalService.hybridRetrieval()`

- **文件**: `src/main/java/com/student/service/rag/impl/MultiRetrievalServiceImpl.java:159`

```
┌─ CompletableFuture (并行, 2线程池) ─┐
│                                    │
│  向量检索 (vectorRetrieval)         │  关键词检索 (keywordRetrieval)
│  ├─ EmbeddingService.embed()       │  ├─ KeywordRetrievalService
│  │   阿里云 text-embedding-v3      │  │   .searchWithSynonyms()
│  │   输出: 1024维向量              │  │   Lucene BM25 + 同义词
│  ├─ MilvusService.searchSimilar()  │  │   搜索所有已索引文档片段
│  │   Milvus 向量数据库             │  │
│  │   Collection: document_chunks   │  │
│  └─ 返回 topK=30                   │  └─ 返回 topK=30
│                                    │
└──────────┬──────────────────┬──────┘
           │                  │
    List<RetrievalResult>  List<RetrievalResult>
    (source=VECTOR)        (source=KEYWORD)
           │                  │
           └────────┬─────────┘
                    ▼
         [5] RrfFusionService.fuseVectorAndKeyword()
```

**检索模式**（通过 `ragConfig.getRetrieval().getMode()` 配置）:

| 模式 | 枚举值 | 说明 |
|------|--------|------|
| HYBRID | 默认 | 双路并行召回 + RRF融合 |
| VECTOR | VECTOR | 仅向量检索 |
| KEYWORD | KEYWORD | 仅关键词检索 |

**降级策略**：混合检索超时或异常时 → `fallbackRetrieval()` → 按权重偏好选择单路检索

---

### 2.5 RRF融合 — `RrfFusionService.fuseVectorAndKeyword()`

- **文件**: `src/main/java/com/student/service/rag/impl/RrfFusionServiceImpl.java:101`

**标准 RRF 公式**：

```
score(d) = Σ w_i × 1 / (k + rank_i(d))

其中:
  k = 60 (可配置 rrf-k)
  w_vector = 0.6, w_keyword = 0.4 (可配置)
  rank_i(d) = 文档d在第i路检索中的排名 (从1开始)
```

**核心实现** (`applyRrfFusion()`, 行174):

```java
for (int rank = 0; rank < results.size(); rank++) {
    double rrfScore = 1.0 / (rrfK + (rank + 1));
    double weightedScore = rrfScore * weight;
    // 同一 chunkId 的分数累加（去重）
    fusedResult.addScore(weightedScore);
}
// 按总分降序 → 取 topK → 输出 HYBRID 来源的结果
```

去重以 chunkId 为键，同一个片段在双路中都出现时 RRF 分数累加。

---

### 2.6 质量过滤 — `QualityFilterService.filter()`

- **文件**: `src/main/java/com/student/service/rag/impl/QualityFilterServiceImpl.java:75`

**4层过滤规则**：

| 层 | 规则 | 条件 | 动作 |
|----|------|------|------|
| 1 | 片段太短 | `content.length() < 20` | 丢弃 |
| 2 | 黑名单来源 + 短文本 | 域名在 `BLACKLIST_DOMAINS` 中 且 `content.length() < 100` | 丢弃 |
| 3 | 纯免责声明 | 含免责词 且 无医学实质词 | 丢弃 |
| 4 | 矛盾检测 | 见 2.7 节 | 阻断管线 |

**黑名单域名**（硬编码，可扩展为配置驱动）：

```
zhidao.baidu.com, baike.baidu.com, zhihu.com, tieba.baidu.com,
haodf.com, xywy.com
```

**免责声明特征词**：`"仅供参考"、"不能替代"、"请咨询"、"不构成医疗建议"` 等

**医学实质特征词**：`"适应症"、"用法用量"、"不良反应"、"诊断"、"治疗"、"临床"` 等

---

### 2.7 矛盾检测 — `QualityFilterService.detectContradictions()`

- **文件**: `src/main/java/com/student/service/rag/impl/QualityFilterServiceImpl.java:109`

**触发条件**：
1. 查询中同时包含药物实体 (DRUG) 和人群实体 (POPULATION)
2. 检索结果中同时存在正面断言和负面断言
3. 双方权威性均 > 0.7

**检测逻辑**：

```
对于每个 药物-人群对:
  在所有检索结果中扫描:
    正向断言匹配: "可用", "安全", "适用", "推荐", "首选", "一线", "有效"...
    负向断言匹配: "禁用", "禁忌", "不宜", "避免", "慎用", "不推荐"...

  双方都存在 且 权威性均 > 0.7 → ContradictionResult.conflict()
  → 阻断LLM调用 → 返回矛盾提示响应
```

**示例**：查询"阿司匹林对孕妇"，若检索到片段A说"阿司匹林孕妇可用"（权威性0.8），片段B说"阿司匹林孕妇禁用"（权威性0.9），则触发阻断。

---

### 2.8 多因子重排序 — `MultiFactorRerankService.rerank()`

- **文件**: `src/main/java/com/student/service/rag/impl/MultiFactorRerankServiceImpl.java:55`

**三因子综合公式**：

```
final = 0.4 × authority + 0.2 × timeliness + 0.4 × semantic
```

#### 2.8.1 权威性 (authority, 权重 0.4)

**证据金字塔 8 级**（`AuthorityLevel` 枚举）：

| 等级 | 来源 | 分数 |
|------|------|------|
| 1 | 系统评价/Meta分析 (systematic_review, meta_analysis) | 1.0 |
| 2 | 药品说明书/临床指南 (drug_label, clinical_guideline) | 0.95 |
| 3 | 随机对照试验 (rct) | 0.9 |
| 4 | 队列研究/病例对照 (cohort_study, case_control) | 0.7 |
| 5 | 医院科普/教科书 (hospital_science, medical_encyclopedia) | 0.6 |
| 6 | 专家意见/病例报告 (expert_opinion, case_report) | 0.5 |
| 7 | 一般健康网站 (general_website, self_media) | 0.3 |
| 8 | 论坛/问答 (forum, qa) | 0.1 |

**获取优先级**：metadata.authority → metadata.source_type 映射 → 内容推断（关键词匹配）

#### 2.8.2 时效性 (timeliness, 权重 0.2)

**指数衰减公式**：`timeliness = e^(-λ × ΔY)`，其中 `λ = ln(2) / 半衰期`

| 知识类型 | 半衰期(年) | 示例 |
|----------|------------|------|
| TREATMENT (治疗方案) | 3.0 | 药物疗法、手术方式 |
| PUBLIC_HEALTH (公共卫生) | 4.0 | 防疫政策、流行病 |
| DEVICE (医疗器械) | 5.0 | 设备、耗材 |
| DIAGNOSIS (诊断方法) | 6.0 | 检查手段、诊断标准 |
| BASIC_SCIENCE (基础科学) | 18.0 | 解剖、生理、生化 |
| CLASSIC_RESEARCH (经典研究) | 100.0 | 里程碑式发现 |

**年份提取**：metadata.publish_year → metadata.year → 内容正则 `\b(19\d{2}|20\d{2})\s*年`

#### 2.8.3 语义相似度 (semantic, 权重 0.4)

通过阿里云 DashScope `gte-rerank-v2` Cross-encoder API 计算 query 与每个片段的语义相关性分数。

**降级**：Cross-encoder 调用失败时 → 使用向量检索的原始相似度分数

---

### 2.9 安全兜底②：置信度门控 — `SafetyGuardService.assessConfidence()`

- **文件**: `src/main/java/com/student/service/rag/SafetyGuardService.java:91`

```
重排序后的 maxScore:
  < minThreshold    → ConfidenceDecision.block() → 返回"当前知识不足，建议咨询医生"
  < warningThreshold → ConfidenceDecision.warning() → 放行但后续追加免责声明
  ≥ warningThreshold → ConfidenceDecision.pass() → 正常放行
```

阈值通过 `SafetyConfig`（`application.yml` 中的 `imkqas.safety.confidence`）配置。

---

### 2.10 语义缓存链 — `SemanticCacheService`

- **文件**: `src/main/java/com/student/service/rag/impl/SemanticCacheServiceImpl.java`

**背诵版**：问题归一化 + 排序无关缓存键 + 版本控制 → 命中返回 / 未命中分布式锁 → LLM生成

#### 缓存键设计

```
sem:cache:{MD5(normalizedQuery + sortedFragmentIds)}_v{version}
```

**排序无关**：fragmentIds 在哈希前排序，确保相同语义内容不因检索结果顺序变化而缓存穿透。

#### 缓存值结构

```json
{
  "answer": "对乙酰氨基酚是儿童常用的解热镇痛药...",
  "sources": ["《儿科学》第9版", "儿童发热诊疗指南2023"],
  "timestamp": 1715352000000,
  "query": "对乙酰氨基酚 儿童 发热"
}
```

**存储时带版本号**：`redisService.setWithVersion(key, value, version, ttl)`

**读取时校验版本号**：`redisService.getWithVersion(key, expectedVersion)` — 版本不匹配则返回 null

#### 分布式锁懒加载

```
缓存未命中
  → SETNX sem:lock:{MD5(query+sortedIds)}_v{version} (TTL=30s)
     ├─ 获取成功 → LLM生成 → 写缓存 → 释放锁
     └─ 获取失败 → sleep(200ms) → 重试读缓存
          ├─ 命中 → 返回
          └─ 未命中 → 直接LLM (不写缓存, 避免并发写)
```

#### 版本控制

- 版本 key: `sem:version:knowledge`
- 递增方式: `redisService.increment("sem:version:knowledge", 1)`
- 递增触发: 知识库更新 / 术语映射变更 / 人工全量失效
- 旧版本缓存: 不主动删除, 由 Redis TTL (7200s) 自动清理

#### 术语变更失效

三种触发方式：

| 方式 | 实现 | 延迟 |
|------|------|------|
| 人工触发 | API 调用 `manualInvalidate(term)` | 实时 |
| 消息订阅 | `onTermChanged(event)` 预留MQ接口 | 实时 |
| 定时兜底 | `scheduledVersionCheck()` 定时对比术语 Hash | ≤ 1小时 |

---

### 2.11 LLM生成 — `LlmService.generateAnswer()`

- **文件**: `src/main/java/com/student/service/rag/impl/LlmServiceImpl.java:130`

#### 调用链路

```
generateAnswer(query, context)
  ├─ normalizeQuery(query) → 用于缓存键
  ├─ 检查本地 Caffeine 缓存 (10000条, TTL=10min)
  ├─ 检查 Redis 缓存
  ├─ buildMedicalPrompt(query, context) → 构建 Prompt
  ├─ callLlmApi(prompt) → 调用阿里云百炼 API
  ├─ extractAnswerFromResponse(response) → 提取回答
  └─ 回写 Caffeine + Redis 缓存
```

#### Prompt 结构

```
【系统指令】
你是一个严格的医疗知识助手，请严格遵守以下规则：

1. 只基于下方提供的「参考知识片段」回答，不要使用你自己的内部知识或训练数据中的信息。
2. 如果不同片段之间存在矛盾，优先采信权威性高（等级数值更高）且发布时间新的片段。
3. 如果提供的片段无法回答用户问题，请直接说「当前知识不足，建议咨询医生」，
   不要编造或推测任何信息。
4. 回答应专业、准确、简洁，使用中文。
5. 严禁给出具体的药物剂量、用法建议。

【参考知识片段】
--- 片段 1 ---
[内容...]
--- 片段 2 ---
[内容...]

【用户问题】
[用户query]

【回答要求】
请基于以上参考知识片段回答。如果无法回答，务必说「当前知识不足，建议咨询医生」。
涉及诊断或治疗时，必须强调「此信息仅供参考，不能替代专业医疗建议」。

回答：
```

**长度控制**：context 总长度接近 `maxTokens * 3` 时截断最长的片段

#### API 调用

- API: 阿里云百炼 (DashScope)，兼容 OpenAI 格式
- HTTP客户端: OkHttp (连接池5, 超时可配置)
- 异步线程池: ThreadPoolExecutor (核心10, 最大50, 队列200, CallerRunsPolicy)

---

### 2.12 安全兜底③：回答安全校验 — `SafetyGuardService.sanitizeAnswer()`

- **文件**: `src/main/java/com/student/service/rag/SafetyGuardService.java:138`

```
sanitizeAnswer(answer, confidence)
  ├─ replaceBlockedPatterns(answer)
  │   将危险用药模式替换为 "[具体用药请咨询医生]"
  │   例如: "每次服用Xmg" → "[具体用药请咨询医生]"
  └─ 置信度 < disclaimerMaxConfidence → 追加免责声明
      "（此信息仅供参考，不能替代专业医疗建议。）"
```

---

### 2.13 出口 — 构建 `QaResponse`

- **文件**: `src/main/java/com/student/service/rag/QaService.java` (接口中的内部类)

```java
QaResponse {
    query,              // 原始查询
    answer,             // 最终回答
    retrievedContext,   // 引用的知识片段 (top 3)
    confidence,         // 综合置信度
    processingTime,     // 总耗时(ms)
    modelUsed           // LLM模型名称
}
```

---

## 三、依赖组件速查表

| 组件 | 类 | 文件 | 职责 |
|------|-----|------|------|
| 查询改写 | `QueryRewriteServiceImplRefactored` | `rag/impl/` | 6步查询预处理管线 |
| 实体识别 | `MedicalEntityRecognitionServiceImpl` | `rag/impl/` | HanLP + THUOCL 医学术语抽取 |
| 同义词扩展 | `SynonymExpansionServiceImpl` | `rag/impl/` | 3级级联：本地→SNOMED CT→LLM |
| 术语标准化 | `SnomedTerminologyService` | `snomed/` | SNOMED CT Snowstorm FHIR API |
| 安全兜底 | `SafetyGuardService` | `rag/` | 急症预检 + 置信度门控 + 回答净化 |
| 向量嵌入 | `EmbeddingServiceImpl` | `rag/impl/` | 阿里云 text-embedding-v3 (1024维) |
| 向量存储 | `MilvusService` | `dataBase/` | Milvus 2.3.6 向量相似搜索 |
| 关键词检索 | `KeywordRetrievalServiceImpl` | `rag/impl/` | Lucene BM25 + 同义词扩展 |
| RRF融合 | `RrfFusionServiceImpl` | `rag/impl/` | 标准RRF: score=1/(k+rank), k=60 |
| 质量过滤 | `QualityFilterServiceImpl` | `rag/impl/` | 4层规则过滤 + 矛盾检测 |
| 交叉编码器 | `CrossEncoderRerankServiceImpl` | `rag/impl/` | 阿里云 gte-rerank-v2 |
| 多因子重排序 | `MultiFactorRerankServiceImpl` | `rag/impl/` | 权威性+时效性+语义三维排序 |
| 语义缓存 | `SemanticCacheServiceImpl` | `rag/impl/` | 排序无关缓存键+版本+分布式锁 |
| LLM调用 | `LlmServiceImpl` | `rag/impl/` | 阿里云百炼，Caffeine+Redis二级缓存 |
| 术语变更 | `TermChangeListenerImpl` | `rag/impl/` | 定时Hash对比+人工/事件触发失效 |
| Redis工具 | `RedisService` | `service/` | increment/acquireLock/releaseLock/setWithVersion |

---

## 四、配置项速查

```yaml
imkqas:
  rag:
    retrieval:
      initial-top-k: 30        # 双路各召回数
      rrf-k: 60                 # RRF平滑参数
      weights: {vector: 0.6, keyword: 0.4}
      mode: HYBRID              # VECTOR / KEYWORD / HYBRID
    quality-filter:
      min-fragment-length: 20
      blacklist-domains: [zhidao.baidu.com, baike.baidu.com, ...]
      contradiction-authority-threshold: 0.7
    multi-factor-rerank:
      weights: {authority: 0.4, timeliness: 0.2, semantic: 0.4}
      half-lives: {treatment: 3.0, diagnosis: 6.0, basic-science: 18.0, ...}
    semantic-cache:
      enabled: true
      ttl: 7200                 # 缓存过期时间(秒)
      lock-ttl: 30              # 分布式锁过期时间(秒)
      version-check-interval: 3600  # 定时版本检查间隔(秒)
      knowledge-version: 1
    llm:
      model: qwen-turbo
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      max-tokens: 2000
      timeout: 30               # API超时(秒)
    cache.llm:
      enabled: true
      ttl: 3600                 # LLM回答缓存(秒)
  query-rewrite:
    thuocl-dict-path: /data/dict/thuocl_medical.txt
    enable-spell-checker: true
  safety:
    enabled: true
    emergency.enabled: true     # 急症预检开关
    confidence.enabled: true    # 置信度门控开关
    answer-safety.enabled: true # 回答安全校验开关
```

---

## 五、异常降级路径

| 环节 | 异常场景 | 降级行为 |
|------|----------|----------|
| 查询预处理 | 整体 rewrite 异常 | 返回原始 query |
| 实体识别 | HanLP/THUOCL 不可用 | 降级为简单规则分词 |
| 同义词扩展 | SNOMED CT 超时/不可用 | 跳过第2级，进入 LLM 兜底 |
| 急症预检 | 配置未加载 | SafetyDecision.pass() 放行 |
| 双路检索 | 整体超时/异常 | 按权重偏好选择单路检索 |
| 向量检索 | Milvus 不可用 | 返回空列表 |
| 关键词检索 | Lucene 索引异常 | 返回空列表 |
| RRF融合 | 输入为空 | 返回空列表 |
| 交叉编码器 | DashScope API 失败 | 使用向量相似度分数 |
| 语义缓存 | Redis 不可用 | 跳过缓存，直接 LLM |
| LLM调用 | API超时/限流/认证失败 | 返回降级回答 |
| 回答净化 | 配置未加载 | 返回原始回答 |
| 整体管线 | 未捕获异常 | `getFallbackResponse()` 通用降级 |

---

*本文档基于 IMKQAS 项目 2026-05-10 代码状态编写，覆盖 QaServiceImpl v3.0 完整代码链路。*
