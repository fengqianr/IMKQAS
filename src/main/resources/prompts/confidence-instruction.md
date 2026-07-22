# LLM 置信度输出指令

## 用途
在 LLM 生成医疗问答回答时，同步输出一个 confidence 分数（0.0~1.0），
系统根据该分数进行安全门控：低于阈值则阻断回答，偏低则添加免责声明。

## Prompt 片段（追加在回答要求之后）

```
【置信度评估】
在回答末尾，输出一个 confidence 分数（0.0~1.0），表示你对回答的把握程度：
输出格式：
confidence: 0.85
```

## 解析规则

- 正则: `confidence\s*[:：]\s*(0\.\d+|1\.0|1|0)`
- 大小写不敏感
- 未找到时默认返回 0.0（保守处理，触发阻断）
- 解析后从回答中移除 confidence 行，用户不可见

## 门控阈值

| 条件 | 行为 |
|------|------|
| confidence < 0.50 | 阻断，返回低置信度提示，不展示 LLM 回答 |
| 0.50 ≤ confidence < 0.55 | 放行，但追加医疗免责声明 |
| confidence ≥ 0.55 | 正常放行 |

## 配置位置

- Java 配置: `SafetyConfig.ConfidenceConfig`
- YAML 配置: `imkqas.rag.safety.confidence`
- 门控服务: `SafetyGuardService.assessConfidence(double)`
