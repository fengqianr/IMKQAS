# 医疗安全兜底模块技术方案

## 一、背景与必要性

RAG 系统在医疗场景下的核心风险：当检索不到可靠信息时，LLM 可能基于不相关或错误上下文生成看似合理但实际危险的回答。此外，用户可能询问急症症状，系统若按常规流程回答会延误救治。

**必要性（应对面试追问）：**

- *问：医疗 RAG 为什么需要专门的兜底模块？* 答：通用 RAG 的置信度计算通常在 LLM 生成之后，但医疗场景需要在 LLM 生成**之前**就阻断低质量检索——因为一旦 LLM 开始生成，即使答案质量差，用户也可能采信。同时，急症检测必须在检索之前完成，零延迟响应。
- *问：阈值为什么设成 0.35/0.6？* 答：Cross-encoder 重排序分数在 [0,1] 区间，< 0.3 通常表示语义不相关，0.3-0.6 为弱相关，> 0.6 为可靠相关。医疗场景取保守值 0.35 作为硬阻断线（留 0.05 缓冲），0.6 作为免责提示线。这些值应在上线后通过 A/B 测试调优。
- *问：会不会误判？* 答：会，任何基于关键词/阈值的系统都有误判可能。设计上做了三层缓冲：(1) 急症检测仅阻断明确的高危关键词，不阻断模糊描述；(2) 低置信度阻断仅在 top-1 分数 < 0.35 时生效，保留 LLM 在弱相关时生成带免责提示的回答的权利；(3) 所有阻断返回友好就医建议而非报错。上线后通过误判率监控持续调优阈值。

---

## 二、模块架构设计

```
用户查询
    │
    ▼
┌─────────────────────┐
│  ① 急症预检          │  ← 新增：EmergencyChecker
│  (关键词匹配)        │     命中最危关键词 → 立即返回就医建议
└────────┬────────────┘         旁路整个 RAG 链路
         │ 非急症
         ▼
┌─────────────────────┐
│  ② 多路检索 + 重排序  │  ← 现有流程
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  ③ 置信度门控        │  ← 新增：ConfidenceGate
│  基于最大重排序分数   │     低于 min-threshold → 返回"无法找到可靠资料"
└────────┬────────────┘     低于 warning-threshold → LLM生成 + 添加免责声明
         │ 通过
         ▼
┌─────────────────────┐
│  ④ LLM 生成回答      │  ← 现有流程
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  ⑤ 回答安全校验      │  ← 新增：AnswerSafetyCheck
│  屏蔽危险模式        │     替换危险内容 + 添加医疗免责声明
└────────┬────────────┘
         │
         ▼
      返回响应
```

**三个插入点对应 QaServiceImpl.answer() 方法中的三个位置：**

| 插入点 | 位置 | 作用 | 可用数据 |
|--------|------|------|----------|
| A | 第 70 行 try 块开始，检索之前 | 急症关键词检测 | 仅 query 字符串 |
| B | 第 76 行重排序之后，第 82 行 LLM 之前 | 检索质量门控 | rerankedResults (score/vectorScore/keywordScore) |
| C | 第 82 行 LLM 之后，第 85 行置信度之前 | 回答安全校验 | answer 字符串 + confidence |

---

## 三、配置文件设计（application.yml）

在 `imkqas.rag.safety` 下新增配置，遵循 RagConfig 的 `@ConfigurationProperties` 模式：

```yaml
imkqas:
  rag:
    safety:
      # 总开关
      enabled: true

      # ─── ① 急症预检 ───
      emergency:
        enabled: true
        # 关键词分级库
        keywords:
          CRITICAL:
            - "胸痛剧烈"
            - "呼吸困难"
            - "大出血"
            - "昏迷不醒"
            - "心跳停止"
            - "窒息"
            - "严重过敏反应"
            - "药物中毒"
            - "农药中毒"
            - "意识丧失"
            - "抽搐不止"
            - "剧烈头痛伴呕吐"
          HIGH:
            - "高烧不退"
            - "持续呕吐"
            - "严重外伤"
            - "开放性骨折"
            - "大面积烧伤"
            - "吐血"
            - "便血"
            - "剧烈腹痛"
            - "抽搐"
            - "言语不清"
            - "半身麻木"
            - "口眼歪斜"
          MEDIUM:
            - "持续腹泻"
            - "严重皮疹"
            - "持续头晕"
            - "视力模糊"
            - "持续发热"
        # 各级别回复模板
        response-templates:
          CRITICAL: "⚠️ 您描述的症状可能属于医疗急症，请立即停止线上咨询，拨打120急救电话或前往最近医院的急诊科就诊！这是紧急情况，请务必尽快就医！"
          HIGH: "⚠️ 您描述的症状较为严重，建议尽快就医。请立即前往医院急诊科或相关专科就诊，不要延误。"
          MEDIUM: "⚠️ 您描述的症状需要引起注意，建议尽快就医。如症状持续或加重，请及时到医疗机构就诊。"
        # 同时匹配用户问题中的连续句子（不仅单个关键词）
        match-full-sentence: true

      # ─── ② 置信度门控 ───
      confidence:
        enabled: true
        # 硬阻断阈值：低于此值 → 不调用 LLM，直接返回"无法找到可靠信息"
        min-threshold: 0.35
        # 警告阈值：低于此值 → LLM 正常生成，但添加医疗免责声明
        warning-threshold: 0.6
        # 检索结果为空时的响应文本
        no-retrieval-response: "抱歉，我无法从现有医学资料库中找到与您问题相关的信息。医疗建议需要基于确切的医学知识，建议您咨询专业医生获取准确的诊断和治疗方案。"
        # 低于 min-threshold 时的响应文本
        low-confidence-response: "抱歉，当前检索到的医学资料与您问题的相关性较低，无法生成可靠的回答。为了您的健康安全，建议您咨询专业医生获取准确信息。"

      # ─── ③ 回答安全校验 ───
      answer-safety:
        enabled: true
        # LLM 回答中禁止出现的模式（正则表达式）
        blocked-patterns:
          - "建议(服用|使用|口服|注射).*药物"
          - "推荐.*(剂量|用量|用法)"
          - "请服用"
          - "处方药.*[剂量用量]"
        # 替换文本（当命中 blocked-patterns 时）
        replacement-text: "[请遵医嘱用药]"
        # 免责声明模板（添加到所有回答末尾）
        disclaimer: "\n\n---\n*免责声明：以上信息仅供参考，不能替代专业医疗建议。如有身体不适，请及时前往医疗机构就诊。*"
        # 免责声明添加的置信度上限
        disclaimer-max-confidence: 0.8
```

---

## 四、核心代码设计

### 4.1 SafetyConfig（配置类）

```java
// 文件: src/main/java/com/student/config/SafetyConfig.java
package com.student.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "imkqas.rag.safety")
@Data
@Validated
public class SafetyConfig {

    private boolean enabled = true;

    private EmergencyConfig emergency = new EmergencyConfig();
    private ConfidenceConfig confidence = new ConfidenceConfig();
    private AnswerSafetyConfig answerSafety = new AnswerSafetyConfig();

    @Data
    public static class EmergencyConfig {
        private boolean enabled = true;
        // 关键词分级: CRITICAL -> ["胸痛剧烈", ...], HIGH -> [...], MEDIUM -> [...]
        private Map<String, List<String>> keywords = Map.of();
        // 各级别回复模板
        private Map<String, String> responseTemplates = Map.of();
        private boolean matchFullSentence = true;
    }

    @Data
    public static class ConfidenceConfig {
        private boolean enabled = true;

        @DecimalMin("0.0") @DecimalMax("1.0")
        private double minThreshold = 0.35;

        @DecimalMin("0.0") @DecimalMax("1.0")
        private double warningThreshold = 0.6;

        @NotBlank
        private String noRetrievalResponse = "抱歉，我无法从现有医学资料库中找到与您问题相关的信息...";

        @NotBlank
        private String lowConfidenceResponse = "抱歉，当前检索到的医学资料与您问题的相关性较低...";
    }

    @Data
    public static class AnswerSafetyConfig {
        private boolean enabled = true;
        private List<String> blockedPatterns = List.of();
        private String replacementText = "[请遵医嘱用药]";
        private String disclaimer = "\n\n---\n*免责声明：以上信息仅供参考...*";

        @DecimalMin("0.0") @DecimalMax("1.0")
        private double disclaimerMaxConfidence = 0.8;
    }
}
```

### 4.2 SafetyGuardService（核心服务）

```java
// 文件: src/main/java/com/student/service/rag/SafetyGuardService.java
package com.student.service.rag;

import com.student.config.SafetyConfig;
import com.student.service.rag.MultiRetrievalService.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyGuardService {

    private final SafetyConfig safetyConfig;

    // 急症关键词匹配统计（用于监控误判率）
    private final AtomicInteger emergencyBlockCount = new AtomicInteger(0);
    private final AtomicInteger confidenceBlockCount = new AtomicInteger(0);
    private final AtomicInteger safetyCheckCount = new AtomicInteger(0);

    // 缓存已编译的正则模式
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    // ==================== ① 急症预检 ====================

    /**
     * 急症关键词检测
     * @param query 用户原始查询
     * @return SafetyDecision：PASS → 放行, BLOCK → 阻断并返回就医建议
     */
    public SafetyDecision checkEmergency(String query) {
        if (!safetyConfig.isEnabled() || !safetyConfig.getEmergency().isEnabled()) {
            return SafetyDecision.pass();
        }
        if (query == null || query.trim().isEmpty()) {
            return SafetyDecision.pass();
        }

        Map<String, List<String>> keywords = safetyConfig.getEmergency().getKeywords();
        Map<String, String> templates = safetyConfig.getEmergency().getResponseTemplates();
        boolean matchFullSentence = safetyConfig.getEmergency().isMatchFullSentence();

        // 按 severity 从高到低遍历
        String[] levels = {"CRITICAL", "HIGH", "MEDIUM"};
        for (String level : levels) {
            List<String> levelKeywords = keywords.getOrDefault(level, List.of());
            String template = templates.getOrDefault(level, "建议尽快就医。");

            for (String keyword : levelKeywords) {
                boolean matched;
                if (matchFullSentence) {
                    // 整句匹配：关键词作为一个整体出现在 query 中
                    matched = query.contains(keyword);
                } else {
                    // 子串匹配
                    matched = query.contains(keyword);
                }

                if (matched) {
                    log.warn("[安全兜底] 急症关键词命中: level={}, keyword='{}', query='{}'",
                            level, keyword, truncate(query, 80));
                    emergencyBlockCount.incrementAndGet();
                    return SafetyDecision.block(
                            "EMERGENCY_" + level,
                            template,
                            level
                    );
                }
            }
        }

        return SafetyDecision.pass();
    }

    // ==================== ② 置信度门控 ====================

    /**
     * 评估检索结果置信度
     * @param results 重排序后的检索结果
     * @return ConfidenceDecision：PASS / WARNING / BLOCK
     */
    public ConfidenceDecision assessConfidence(List<RetrievalResult> results) {
        if (!safetyConfig.isEnabled() || !safetyConfig.getConfidence().isEnabled()) {
            return ConfidenceDecision.pass(1.0);
        }
        if (results == null || results.isEmpty()) {
            confidenceBlockCount.incrementAndGet();
            return ConfidenceDecision.block(0.0, safetyConfig.getConfidence().getNoRetrievalResponse());
        }

        double maxScore = results.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .max()
                .orElse(0.0);

        double minThreshold = safetyConfig.getConfidence().getMinThreshold();
        double warningThreshold = safetyConfig.getConfidence().getWarningThreshold();

        if (maxScore < minThreshold) {
            log.warn("[安全兜底] 置信度过低: maxScore={}, threshold={}", maxScore, minThreshold);
            confidenceBlockCount.incrementAndGet();
            return ConfidenceDecision.block(maxScore, safetyConfig.getConfidence().getLowConfidenceResponse());
        } else if (maxScore < warningThreshold) {
            log.info("[安全兜底] 置信度偏低: maxScore={}, warningThreshold={}", maxScore, warningThreshold);
            return ConfidenceDecision.warning(maxScore, safetyConfig.getConfidence().getDisclaimer());
        }

        return ConfidenceDecision.pass(maxScore);
    }

    // ==================== ③ 回答安全校验 ====================

    /**
     * 校验并净化 LLM 回答
     * @param answer LLM 原始回答
     * @param confidence 当前置信度
     * @return 净化后的安全回答
     */
    public String sanitizeAnswer(String answer, double confidence) {
        if (!safetyConfig.isEnabled() || !safetyConfig.getAnswerSafety().isEnabled()) {
            return answer;
        }
        if (answer == null || answer.trim().isEmpty()) {
            return answer;
        }

        safetyCheckCount.incrementAndGet();

        // 3.1 替换危险模式
        String sanitized = replaceBlockedPatterns(answer);

        // 3.2 添加免责声明（如果置信度低于阈值）
        double disclaimerMaxConf = safetyConfig.getAnswerSafety().getDisclaimerMaxConfidence();
        if (confidence < disclaimerMaxConf) {
            String disclaimer = safetyConfig.getAnswerSafety().getDisclaimer();
            if (!sanitized.endsWith(disclaimer)) {
                sanitized = sanitized + disclaimer;
            }
        }

        if (!sanitized.equals(answer)) {
            log.info("[安全兜底] 回答已净化: originalLength={}, sanitizedLength={}",
                    answer.length(), sanitized.length());
        }

        return sanitized;
    }

    /**
     * 替换回答中的危险模式
     */
    private String replaceBlockedPatterns(String answer) {
        List<String> patterns = safetyConfig.getAnswerSafety().getBlockedPatterns();
        String replacement = safetyConfig.getAnswerSafety().getReplacementText();

        for (String patternStr : patterns) {
            Pattern pattern = compiledPatterns.computeIfAbsent(patternStr, Pattern::compile);
            if (pattern.matcher(answer).find()) {
                answer = pattern.matcher(answer).replaceAll(replacement);
            }
        }
        return answer;
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
```

### 4.3 SafetyDecision & ConfidenceDecision（值对象）

```java
// 文件: src/main/java/com/student/service/rag/SafetyDecision.java
package com.student.service.rag;

import lombok.Getter;

@Getter
public class SafetyDecision {
    private final boolean blocked;
    private final String reasonCode;   // EMERGENCY_CRITICAL, EMERGENCY_HIGH, EMERGENCY_MEDIUM
    private final String adviceMessage; // 就医建议文本
    private final String severityLevel; // CRITICAL / HIGH / MEDIUM

    private SafetyDecision(boolean blocked, String reasonCode, String adviceMessage, String severityLevel) {
        this.blocked = blocked;
        this.reasonCode = reasonCode;
        this.adviceMessage = adviceMessage;
        this.severityLevel = severityLevel;
    }

    public static SafetyDecision pass() {
        return new SafetyDecision(false, null, null, null);
    }

    public static SafetyDecision block(String reasonCode, String adviceMessage, String severityLevel) {
        return new SafetyDecision(true, reasonCode, adviceMessage, severityLevel);
    }
}

// 文件: src/main/java/com/student/service/rag/ConfidenceDecision.java
package com.student.service.rag;

import lombok.Getter;

@Getter
public class ConfidenceDecision {
    private final boolean blocked;
    private final boolean warning;
    private final double maxScore;
    private final String message; // 阻断时的回复文本 / 警告时的免责声明

    private ConfidenceDecision(boolean blocked, boolean warning, double maxScore, String message) {
        this.blocked = blocked;
        this.warning = warning;
        this.maxScore = maxScore;
        this.message = message;
    }

    public static ConfidenceDecision pass(double maxScore) {
        return new ConfidenceDecision(false, false, maxScore, null);
    }

    public static ConfidenceDecision warning(double maxScore, String disclaimer) {
        return new ConfidenceDecision(false, true, maxScore, disclaimer);
    }

    public static ConfidenceDecision block(double maxScore, String response) {
        return new ConfidenceDecision(true, false, maxScore, response);
    }
}
```

---

## 五、与现有 RAG 链路的集成

修改 `QaServiceImpl.answer()` 方法，在三个关键点插入安全兜底逻辑：

```java
// 在 QaServiceImpl 中新增依赖
private final SafetyGuardService safetyGuardService;

// 修改 answer() 方法
public QaResponse answer(String query, Long userId, Long conversationId) {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    // ==== 缓存检查（不变）====
    // ... (省略, 与现有代码相同)

    try {
        // ═══ 插入点 A：急症预检 ═══
        SafetyDecision emergencyDecision = safetyGuardService.checkEmergency(query);
        if (emergencyDecision.isBlocked()) {
            log.info("[安全兜底] 急症阻断: query={}, level={}", query, emergencyDecision.getSeverityLevel());
            return buildEmergencyResponse(query, emergencyDecision, startTime);
        }

        // 1. 检索文档（不变）
        List<RetrievalResult> retrievalResults = retrieveDocuments(query);
        totalRetrievedDocuments.addAndGet(retrievalResults.size());

        // 2. 重排序（不变）
        List<RetrievalResult> rerankedResults = rerankDocuments(query, retrievalResults);

        // ═══ 插入点 B：置信度门控 ═══
        ConfidenceDecision confidenceDecision = safetyGuardService.assessConfidence(rerankedResults);
        if (confidenceDecision.isBlocked()) {
            log.info("[安全兜底] 置信度阻断: maxScore={}", confidenceDecision.getMaxScore());
            return buildLowConfidenceResponse(query, confidenceDecision, startTime);
        }

        // 3. 提取上下文（不变）
        List<String> context = extractContext(rerankedResults);

        // 4. 生成回答（不变）
        String answer = generateAnswer(query, context);

        // 5. 计算置信度（不变）
        double confidence = calculateConfidence(rerankedResults, answer);

        // ═══ 插入点 C：回答安全校验 ═══
        answer = safetyGuardService.sanitizeAnswer(answer, confidence);

        // 6. 构建响应（不变）
        // ... (省略, 与现有代码相同)

    } catch (Exception e) {
        // 异常处理（不变）
        // ...
    }
}

// 新增辅助方法
private QaResponse buildEmergencyResponse(String query, SafetyDecision decision, long startTime) {
    long processingTime = System.currentTimeMillis() - startTime;
    return new QaResponse(
            query,
            decision.getAdviceMessage(),
            Collections.emptyList(),
            0.0,  // 急症阻断，置信度为 0
            processingTime,
            "safety-guard"
    );
}

private QaResponse buildLowConfidenceResponse(String query, ConfidenceDecision decision, long startTime) {
    long processingTime = System.currentTimeMillis() - startTime;
    return new QaResponse(
            query,
            decision.getMessage(),
            Collections.emptyList(),
            decision.getMaxScore(),
            processingTime,
            "safety-guard"
    );
}
```

对于 `answerWithSources()` 方法，做同样的三处集成。

---

## 六、急症关键词库设计

### 6.1 关键词组织原则

- **CRITICAL**：直接威胁生命，必须立即 120 / 急诊。匹配到这些词 → 100% 阻断 RAG。
- **HIGH**：可能迅速恶化，建议尽快就医。匹配到这些词 → 阻断 RAG，返回就医建议。
- **MEDIUM**：需要引起注意，但可等待常规就诊。匹配到这些词 → 阻断 RAG，返回就医建议。

### 6.2 关键词示例（完整列表约 60 个，按三类分）

**CRITICAL（20 个）**：
胸痛剧烈、呼吸困难、大出血、昏迷不醒、心跳停止、窒息、严重过敏反应、药物中毒、农药中毒、一氧化碳中毒、意识丧失、抽搐不止、剧烈头痛伴呕吐、溺水、电击伤、严重烧伤、气道异物、自杀倾向、严重低血糖昏迷、高血压危象

**HIGH（25 个）**：
高烧不退、持续呕吐、严重外伤、开放性骨折、大面积烧伤、吐血、便血、剧烈腹痛、抽搐、言语不清、半身麻木、口眼歪斜、严重烧伤、动物咬伤、毒蛇咬伤、高空坠落、车祸外伤、剧烈头痛、视力突然下降、呼吸困难加重、胸痛胸闷、心跳过快、意识模糊、严重脱水、过敏反应全身皮疹

**MEDIUM（15 个）**：
持续腹泻、严重皮疹、持续头晕、视力模糊、持续发热、轻度腹痛、关节肿痛、严重咳嗽、吞咽困难、耳鸣、皮肤溃烂、长期不愈伤口、体重不明原因下降、夜间盗汗、持续乏力

---

## 七、阈值推荐与面试追问应对

| 阈值 | 值 | 作用 | 依据 |
|------|-----|------|---------|
| `min-threshold` | 0.35 | 硬阻断线，低于此值不调用 LLM | Cross-encoder 分数 < 0.3 通常为"不相关"，取 0.35 作为保守边界 |
| `warning-threshold` | 0.60 | 软提示线，添加免责声明 | Cross-encoder 分数 > 0.6 通常为"可靠相关" |
| `disclaimer-max-confidence` | 0.80 | 置信度 > 0.8 时不显示免责声明 | 高分回答已有充分证据，可减少对用户的干扰 |

**可配置化**：所有阈值在 `application.yml` 中可调，通过配置中心可动态下发。

**面试追问应对**：

- *问：0.35 和 0.6 是根据什么定的？有没有数据支撑？*
  答：初始阈值基于 cross-encoder reranker 的典型分数分布。Cross-encoder 的输出是 query-document 对的语义相似度，经验分布为：< 0.2 完全不相关，0.2-0.4 弱相关，0.4-0.6 部分相关，> 0.6 可靠相关。医疗场景取保守值。上线后我们会在生产环境收集分数分布数据，通过 ROC 曲线分析找到最优 cutoff。这也是为什么阈值设计为可配置的。

- *问：如果误判了（比如用户问"什么是胸痛"不是真的胸痛），会怎么样？*
  答：这种误判是已知的局限。当前方案采用了关键词精确匹配而非语义理解，所以"什么是胸痛"不会命中（因为触发词是"胸痛剧烈"而不是"胸痛"）。关键词的措辞特意加了程度限定词（剧烈、严重、持续等），大幅降低误判率。未来可引入 NER + 意图分类来进一步区分"询问症状"和"表达症状"。

- *问：有没有考虑过用分类模型代替关键词匹配？*
  答：考虑过。分类模型（例如 BERT-based 紧急分类器）准确率更高，但有三个问题：(1) 增加推理延迟和成本；(2) 需要标注数据训练；(3) 模型可能在某些边缘 case 上产生置信度错误的预测。关键词匹配虽然简单，但在医疗急症场景下，**宁可误判不可漏判**，关键词匹配的假阴性率更低。当前方案是"关键词+规则"作为第一道防线，未来可以叠加分类模型作为第二道防线。

---

## 八、测试用例

### 8.1 急症预检测试

```java
@ExtendWith(MockitoExtension.class)
class SafetyGuardServiceTest {

    @Mock
    private SafetyConfig safetyConfig;
    @Mock
    private SafetyConfig.EmergencyConfig emergencyConfig;
    @Mock
    private SafetyConfig.ConfidenceConfig confidenceConfig;
    @Mock
    private SafetyConfig.AnswerSafetyConfig answerSafetyConfig;

    private SafetyGuardService safetyGuardService;

    @BeforeEach
    void setUp() {
        // 配置急症检测启用
        when(safetyConfig.isEnabled()).thenReturn(true);
        when(safetyConfig.getEmergency()).thenReturn(emergencyConfig);
        when(emergencyConfig.isEnabled()).thenReturn(true);
        when(emergencyConfig.isMatchFullSentence()).thenReturn(true);

        // 配置关键词
        Map<String, List<String>> keywords = Map.of(
            "CRITICAL", List.of("胸痛剧烈", "呼吸困难", "大出血"),
            "HIGH", List.of("高烧不退", "剧烈腹痛"),
            "MEDIUM", List.of("持续腹泻")
        );
        when(emergencyConfig.getKeywords()).thenReturn(keywords);

        // 配置回复模板
        Map<String, String> templates = Map.of(
            "CRITICAL", "紧急情况！请立即拨打120！",
            "HIGH", "请尽快就医！",
            "MEDIUM", "建议及时就医。"
        );
        when(emergencyConfig.getResponseTemplates()).thenReturn(templates);

        safetyGuardService = new SafetyGuardService(safetyConfig);
    }

    @Test
    void testEmergencyCritical_shouldBlock() {
        SafetyDecision result = safetyGuardService.checkEmergency("我胸口剧烈疼痛，喘不上气");
        assertTrue(result.isBlocked());
        assertEquals("EMERGENCY_CRITICAL", result.getReasonCode());
        assertTrue(result.getAdviceMessage().contains("120"));
    }

    @Test
    void testNormalQuery_shouldPass() {
        SafetyDecision result = safetyGuardService.checkEmergency("小儿肺炎吃什么药");
        assertFalse(result.isBlocked());
    }

    @Test
    void testQueryContainsOnlySymptomName_shouldNotBlock() {
        // "胸痛" 不含 "剧烈"，不应命中 CRITICAL
        SafetyDecision result = safetyGuardService.checkEmergency("胸痛是什么原因");
        assertFalse(result.isBlocked());
    }

    @Test
    void testEmptyQuery_shouldPass() {
        SafetyDecision result = safetyGuardService.checkEmergency("");
        assertFalse(result.isBlocked());
    }
}
```

### 8.2 置信度门控测试

```java
@Test
void testLowConfidence_shouldBlock() {
    when(safetyConfig.getConfidence()).thenReturn(confidenceConfig);
    when(confidenceConfig.isEnabled()).thenReturn(true);
    when(confidenceConfig.getMinThreshold()).thenReturn(0.35);
    when(confidenceConfig.getWarningThreshold()).thenReturn(0.6);
    when(confidenceConfig.getLowConfidenceResponse()).thenReturn("无法找到可靠信息");
    when(confidenceConfig.getNoRetrievalResponse()).thenReturn("未检索到资料");

    // maxScore = 0.12 < 0.35
    List<RetrievalResult> results = List.of(
        createResult(0.12)
    );
    ConfidenceDecision decision = safetyGuardService.assessConfidence(results);
    assertTrue(decision.isBlocked());
}

@Test
void testMediumConfidence_shouldWarn() {
    when(safetyConfig.getConfidence()).thenReturn(confidenceConfig);
    when(confidenceConfig.isEnabled()).thenReturn(true);
    when(confidenceConfig.getMinThreshold()).thenReturn(0.35);
    when(confidenceConfig.getWarningThreshold()).thenReturn(0.6);

    // maxScore = 0.45, 在 [0.35, 0.6) 之间
    List<RetrievalResult> results = List.of(
        createResult(0.45)
    );
    ConfidenceDecision decision = safetyGuardService.assessConfidence(results);
    assertFalse(decision.isBlocked());
    assertTrue(decision.isWarning());
}

@Test
void testHighConfidence_shouldPass() {
    when(safetyConfig.getConfidence()).thenReturn(confidenceConfig);
    when(confidenceConfig.isEnabled()).thenReturn(true);
    when(confidenceConfig.getMinThreshold()).thenReturn(0.35);
    when(confidenceConfig.getWarningThreshold()).thenReturn(0.6);

    // maxScore = 0.85 > 0.6
    List<RetrievalResult> results = List.of(
        createResult(0.85)
    );
    ConfidenceDecision decision = safetyGuardService.assessConfidence(results);
    assertFalse(decision.isBlocked());
    assertFalse(decision.isWarning());
}

private RetrievalResult createResult(double score) {
    RetrievalResult result = mock(RetrievalResult.class);
    when(result.getScore()).thenReturn(score);
    when(result.getContent()).thenReturn("测试内容");
    return result;
}
```

### 8.3 集成测试（验证 QaServiceImpl 集成）

```java
@SpringBootTest
@AutoConfigureMockMvc
class SafetyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testEmergencyQuery_shouldReturnEmergencyAdvice() throws Exception {
        String requestJson = """
            {"question": "我突然胸痛剧烈，喘不上气", "userId": 1}
            """;

        mockMvc.perform(post("/api/qa/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(containsString("120")))
                .andExpect(jsonPath("$.data.confidence").value(0.0))
                .andExpect(jsonPath("$.data.modelUsed").value("safety-guard"));
    }
}
```

---

## 九、集成步骤（按顺序执行）

1. **创建文件**：SafetyConfig.java（配置类，遵循 RagConfig 模式）
2. **创建文件**：SafetyDecision.java、ConfidenceDecision.java（值对象）
3. **创建文件**：SafetyGuardService.java（核心服务）
4. **修改文件**：application.yml（追加 safety 配置段）
5. **修改文件**：QaServiceImpl.java（三处集成点 + buildEmergencyResponse/buildLowConfidenceResponse 方法）
6. **修改文件**：QaService.java（QaResponse 继承体系无需改动——answer 字段已存在）
7. **验证**：运行测试用例
8. **验证**：运行 mvn compile 确保编译通过
9. **验证**：启动应用，用 curl 测试急症查询和正常查询

---

## 十、验证方案

| 测试场景 | 输入 | 预期输出 |
|----------|------|----------|
| 急症阻断 | "我突然胸痛剧烈，怎么办" | 返回就医建议，modelUsed="safety-guard"，confidence=0.0 |
| 急症不误判 | "胸痛是什么原因引起的" | 正常走 RAG 流程（不含"剧烈"） |
| 急症不误判 | "我想了解关于呼吸困难的知识" | 正常走 RAG 流程 |
| 低置信度阻断 | 无相关文档的问题 | 返回"无法找到可靠医学资料"，confidence<0.35 |
| 中等置信度 | 部分相关问题 | LLM 正常回答，末尾附加免责声明 |
| 正常回答 | 有可靠文档的问题 | LLM 正常回答，无免责声明（confidence>0.8） |
| 回答安全校验 | LLM 回答包含"建议服用XXX药物" | "建议服用"被替换为"[请遵医嘱用药]" |
