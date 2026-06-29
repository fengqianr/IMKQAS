package com.student.service.his.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.service.LlmService;
import com.student.service.his.AnalysisAgent;
import com.student.service.his.AnalysisInput;
import com.student.service.his.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 分析Agent实现 —— 基于规则结果和原始表述生成共情分析报告
 * 严格"只读"：不传入可用于重新计算的数据，LLM只负责解读
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
public class AnalysisAgentImpl implements AnalysisAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public AnalysisAgentImpl(LlmService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AnalysisResult analyze(AnalysisInput input) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("分析Agent开始: analysisId={}, questionnaire={}, riskLevel={}",
                    input.getMeta().getAnalysisId(), input.getMeta().getQuestionnaireTitle(),
                    input.getRuleResults().getRiskLevel());

            String prompt = buildAnalysisPrompt(input);
            log.info("分析Agent Prompt构建完成: analysisId={}, promptLen={}",
                    input.getMeta().getAnalysisId(), prompt.length());

            String response = llmService.generateAnswerDirect(prompt);
            long latency = System.currentTimeMillis() - startTime;
            log.info("分析Agent LLM响应接收: analysisId={}, latency={}ms, responseLen={}",
                    input.getMeta().getAnalysisId(), latency, response.length());

            AnalysisResult result = parseAnalysisResponse(response);
            result.setLatencyMs(latency);
            result.setRawLlmResponse(response);

            log.info("分析Agent完成: analysisId={}, riskLevel={}, summary={}, latency={}ms",
                    input.getMeta().getAnalysisId(),
                    input.getRuleResults().getRiskLevel(),
                    result.getSummary() != null
                            ? result.getSummary().substring(0, Math.min(80, result.getSummary().length()))
                            : "无",
                    latency);

            return result;

        } catch (Exception e) {
            log.error("分析Agent调用失败，使用降级分析: analysisId={}, error={}",
                    input.getMeta().getAnalysisId(), e.getMessage());
            return buildFallbackAnalysis(input, System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public CompletableFuture<AnalysisResult> analyzeAsync(AnalysisInput input) {
        return CompletableFuture.supplyAsync(() -> analyze(input));
    }

    @Override
    public boolean isAvailable() {
        return llmService != null && llmService.isAvailable();
    }

    // ==================== Prompt构建 ====================

    private String buildAnalysisPrompt(AnalysisInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildSystemPrompt());
        sb.append("\n\n");
        sb.append(buildInputBlock(input));
        sb.append("\n\n");
        sb.append("请基于以上输入数据，按照系统提示词中定义的JSON格式输出分析结果。只输出JSON，不要输出其他文本。");
        return sb.toString();
    }

    private String buildSystemPrompt() {
        return """
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
                2. 【禁止编造症状】只基于rawInputs中的原始表述来描述患者体验，不得添加患者未提及的症状
                3. 【禁止下诊断】你是筛查工具。措辞用"提示存在...风险"、"建议进一步评估"，不得用"诊断为"、"你患有"
                4. 【强制风险提示】如果safetyStatus.hasEmergencyFlag为true，报告开头必须包含危机干预提示
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

                   注意：这些措辞用于撰写分析时引用特定题目答案的方式，不同题目的引用可以有不同的确定度。
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

                === 输出JSON结构 ===
                {
                  "summary": "友好的评估摘要，200字以内",
                  "riskAssessment": {
                    "level": "从ruleResults引用风险等级",
                    "description": "对风险等级的通俗解释，100字以内",
                    "requiresUrgentAttention": true/false
                  },
                  "detailAnalysis": {
                    "overview": "整体评估段落的markdown",
                    "patterns": ["模式1", "模式2"],
                    "conclusion": "综合判断的markdown"
                  },
                  "recommendations": {
                    "immediate": [{"title": "标题", "description": "描述"}],
                    "shortTerm": [{"title": "标题", "description": "描述"}],
                    "professional": [{"title": "标题", "description": "描述", "resource": "电话或链接"}]
                  },
                  "followUp": {
                    "suggestedDate": "YYYY-MM-DD格式的建议重评日期",
                    "rationale": "建议重评的理由"
                  },
                  "disclaimer": "本报告由AI辅助生成，仅供参考，不构成医疗诊断..."
                }
                """;
    }

    private String buildInputBlock(AnalysisInput input) {
        try {
            return "【输入数据】\n```json\n" + objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(input) + "\n```";
        } catch (JsonProcessingException e) {
            return "【输入数据】\n" + input.toString();
        }
    }

    // ==================== 响应解析 ====================

    private AnalysisResult parseAnalysisResponse(String response) {
        try {
            String json = extractJson(response);
            var root = objectMapper.readTree(json);

            // 风险评估
            var riskNode = root.path("riskAssessment");
            var riskBlock = AnalysisResult.RiskAssessmentBlock.builder()
                    .level(riskNode.path("level").asText(""))
                    .description(riskNode.path("description").asText(""))
                    .requiresUrgentAttention(riskNode.path("requiresUrgentAttention").asBoolean(false))
                    .build();

            // 详细分析
            var detailNode = root.path("detailAnalysis");
            var patterns = new java.util.ArrayList<String>();
            var patternsNode = detailNode.path("patterns");
            if (patternsNode.isArray()) {
                for (var p : patternsNode) {
                    patterns.add(p.asText(""));
                }
            }
            var detailBlock = AnalysisResult.DetailAnalysisBlock.builder()
                    .overview(detailNode.path("overview").asText(""))
                    .patterns(patterns)
                    .conclusion(detailNode.path("conclusion").asText(""))
                    .build();

            // 建议
            var recNode = root.path("recommendations");
            var recBlock = AnalysisResult.RecommendationsBlock.builder()
                    .immediate(parseRecItems(recNode.path("immediate")))
                    .shortTerm(parseRecItems(recNode.path("shortTerm")))
                    .professional(parseProfessionalItems(recNode.path("professional")))
                    .build();

            // 随访
            var followUpNode = root.path("followUp");
            var followUpBlock = AnalysisResult.FollowUpBlock.builder()
                    .suggestedDate(followUpNode.path("suggestedDate").asText(""))
                    .rationale(followUpNode.path("rationale").asText(""))
                    .build();

            return AnalysisResult.builder()
                    .summary(root.path("summary").asText(""))
                    .riskAssessment(riskBlock)
                    .detailAnalysis(detailBlock)
                    .recommendations(recBlock)
                    .followUp(followUpBlock)
                    .disclaimer(root.path("disclaimer").asText(
                            "本报告由AI辅助生成，仅供参考，不构成医疗诊断、治疗建议或专业医疗意见。如有健康问题，请及时咨询专业医生。"))
                    .build();

        } catch (Exception e) {
            log.error("解析分析Agent响应失败: {}", e.getMessage());
            throw new RuntimeException("分析JSON解析失败", e);
        }
    }

    private java.util.List<AnalysisResult.RecItem> parseRecItems(com.fasterxml.jackson.databind.JsonNode node) {
        var items = new java.util.ArrayList<AnalysisResult.RecItem>();
        if (node.isArray()) {
            for (var item : node) {
                items.add(AnalysisResult.RecItem.builder()
                        .title(item.path("title").asText(""))
                        .description(item.path("description").asText(""))
                        .build());
            }
        }
        return items;
    }

    private java.util.List<AnalysisResult.ProfessionalRecItem> parseProfessionalItems(
            com.fasterxml.jackson.databind.JsonNode node) {
        var items = new java.util.ArrayList<AnalysisResult.ProfessionalRecItem>();
        if (node.isArray()) {
            for (var item : node) {
                items.add(AnalysisResult.ProfessionalRecItem.builder()
                        .title(item.path("title").asText(""))
                        .description(item.path("description").asText(""))
                        .resource(item.path("resource").asText(""))
                        .build());
            }
        }
        return items;
    }

    // ==================== 降级分析 ====================

    private AnalysisResult buildFallbackAnalysis(AnalysisInput input, long latencyMs) {
        var rule = input.getRuleResults();
        boolean hasEmergency = input.getSafetyStatus() != null
                && input.getSafetyStatus().isHasEmergencyFlag();

        return AnalysisResult.builder()
                .summary(String.format("您的%s评估已完成。总分为%d分（满分%d分），风险等级为「%s」。",
                        input.getMeta().getQuestionnaireTitle(),
                        rule.getTotalScore(), rule.getMaxScore(), rule.getRiskLevel()))
                .riskAssessment(AnalysisResult.RiskAssessmentBlock.builder()
                        .level(rule.getRiskLevel())
                        .description("评估已完成，请查看评分结果。如需详细分析，请稍后重试。")
                        .requiresUrgentAttention(hasEmergency)
                        .build())
                .detailAnalysis(AnalysisResult.DetailAnalysisBlock.builder()
                        .overview("评估已完成，但详细分析暂时无法生成。")
                        .patterns(java.util.List.of())
                        .conclusion("建议根据评分结果咨询专业医生。")
                        .build())
                .recommendations(AnalysisResult.RecommendationsBlock.builder()
                        .immediate(java.util.List.of(AnalysisResult.RecItem.builder()
                                .title("查看评分结果").description("请查看您的问卷评分和风险等级。").build()))
                        .shortTerm(java.util.List.of())
                        .professional(java.util.List.of(AnalysisResult.ProfessionalRecItem.builder()
                                .title("咨询专业医生")
                                .description("建议根据评估结果咨询专业医生进行进一步评估。")
                                .resource("全国心理援助热线：400-161-9995").build()))
                        .build())
                .followUp(AnalysisResult.FollowUpBlock.builder()
                        .suggestedDate("")
                        .rationale("建议在2-4周后重新评估以追踪变化。")
                        .build())
                .disclaimer("本报告由AI辅助生成，仅供参考，不构成医疗诊断。")
                .latencyMs(latencyMs)
                .build();
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}
