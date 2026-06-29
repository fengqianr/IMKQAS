package com.student.service.his.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.service.LlmService;
import com.student.service.his.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 采集子Agent实现
 * 状态机做"交警"——指挥流程；LLM做"翻译官"——理解人话和说人话。
 * 本类只负责LLM交互，不参与状态机决策、不直接操作SSE。
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
public class CollectionSubAgentImpl implements CollectionSubAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    private static final int TOKEN_BUDGET_LIGHT = 2000;
    private static final int TOKEN_BUDGET_DEEP = 3000;
    private static final int TOKEN_BUDGET_EMERGENCY = 4000;
    private static final int SLIDING_WINDOW_SIZE = 3;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    public CollectionSubAgentImpl(LlmService llmService) {
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CollectionToolOutputs.AgentResult processUserInput(
            InterviewSession session,
            String userInput,
            QuestionnaireTemplate questionnaire) {

        long startTime = System.currentTimeMillis();

        // 断路器检查
        if (session.getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES) {
            log.warn("断路器熔断,降级为规则解析器: sessionId={}, failures={}",
                    session.getSessionId(), session.getConsecutiveFailures());
            return ruleParserFallback(session, userInput, questionnaire);
        }

        try {
            // T2: 预调用token估算与压缩
            CompressionLevel level = decideCompressionLevel(session, userInput);
            log.info("采集子Agent Token估算: sessionId={}, estimatedTokens≈{}, compression={}",
                    session.getSessionId(), estimateTokens(session, userInput), level);
            String prompt = buildPrompt(session, userInput, questionnaire, level);

            log.info("采集子Agent LLM调用: sessionId={}, questionnaire={}, progress={}/{}, compression={}, promptLen={}",
                    session.getSessionId(), questionnaire.getId(),
                    session.getCurrentQuestionIndex() + 1, session.getTotalQuestions(),
                    level, prompt.length());

            String response = llmService.generateAnswerDirect(prompt);
            long latency = System.currentTimeMillis() - startTime;

            CollectionToolOutputs.AgentResult result = parseResponse(response, questionnaire);
            result.setLatencyMs(latency);

            // 成功则重置断路器，并恢复到LLM层级
            session.setConsecutiveFailures(0);
            session.setDegradationLevel("llm");

            log.info("LLM采集完成: sessionId={}, type={}, latency={}ms",
                    session.getSessionId(), result.getType(), latency);
            return result;

        } catch (Exception e) {
            log.error("LLM调用失败: sessionId={}, questionnaire={}, error={}",
                    session.getSessionId(), questionnaire.getId(), e.getMessage());

            session.setConsecutiveFailures(session.getConsecutiveFailures() + 1);
            return ruleParserFallback(session, userInput, questionnaire);
        }
    }

    @Override
    public boolean isAvailable() {
        return llmService != null && llmService.isAvailable();
    }

    // ==================== Prompt构建 ====================

    private String buildPrompt(InterviewSession session, String userInput,
                                QuestionnaireTemplate questionnaire, CompressionLevel level) {
        StringBuilder sb = new StringBuilder();

        // 系统提示词
        sb.append(buildSystemPrompt(questionnaire));
        sb.append("\n\n");

        // 当前状态
        sb.append(buildStateContext(session, questionnaire, level));
        sb.append("\n\n");

        // 用户消息
        sb.append("【用户消息】\n");
        sb.append(userInput);
        sb.append("\n\n");

        // 指令
        sb.append(buildInstruction(session, questionnaire));

        return sb.toString();
    }

    private String buildSystemPrompt(QuestionnaireTemplate questionnaire) {
        return String.format("""
                你是一位温暖、专业、共情的医疗健康助手，正在进行「%s」的评估采集。

                === 你的角色 ===
                你通过自然对话方式引导用户完成问卷，让用户感觉是在和关心TA的人聊天，而不是在填表。

                === 采集规则 ===
                1. 每次只问一个问题，不要一次性列出所有问题
                2. 当用户回答后，你需要理解TA的真实含义，映射到最接近的选项
                3. 如果用户回答模糊，可以温和地追问一次
                4. 不要评判用户的回答，保持接纳和理解
                5. 使用通俗易懂的语言，不要用医学术语
                6. 如果用户表达了消极情绪，先共情再继续

                === 安全规则（优先级最高）===
                如果用户表达了自杀、自伤、或伤害他人的意图，你必须立即使用 emergency_interrupt 工具。
                不要试图劝说、不要继续提问、不要做任何其他操作。

                === 以下工具你可以在每轮回复中调用 ===
                根据用户当前的消息，选择合适的工具调用。你的回复必须是一个JSON对象，格式如下：

                1. 需要展示问题给用户时：
                {"tool": "ask_question", "linkId": "/q1", "text": "润色后的问题文本"}

                2. 用户已回答，你能确定对应选项时：
                {"tool": "record_answer", "linkId": "当前题linkId", "code": "选项code", "display": "选项显示文本", "value": 分数值, "confidence": 0.0-1.0, "context_summary": "一句话总结用户本轮的表述"}

                3. 用户回答模糊，需要追问时：
                {"tool": "clarify", "linkId": "当前题linkId", "text": "温和的追问文本"}

                4. 全部问题已完成时：
                {"tool": "complete", "message": "完成寄语"}

                5. 检测到危险信号时：
                {"tool": "emergency_interrupt", "reason": "触发原因"}

                重要：只输出JSON，不要输出其他文本。
                """, questionnaire.getTitle());
    }

    private String buildStateContext(InterviewSession session,
                                      QuestionnaireTemplate questionnaire,
                                      CompressionLevel level) {
        int ctxLen = session.getContextSummary() != null
                ? session.getContextSummary().length() : 0;
        log.info("T2 构建状态上下文: sessionId={}, compression={}, summaryLen={}, progress={}/{}",
                session.getSessionId(), level, ctxLen,
                session.getCurrentQuestionIndex() + 1, session.getTotalQuestions());

        StringBuilder sb = new StringBuilder();
        sb.append("【当前进度】\n");
        sb.append(String.format("问卷：%s，共%d题，当前第%d题\n",
                questionnaire.getTitle(),
                session.getTotalQuestions(),
                session.getCurrentQuestionIndex() + 1));

        // 当前题目
        if (session.getCurrentQuestionIndex() < session.getTotalQuestions()) {
            var currentItem = questionnaire.getItems().get(session.getCurrentQuestionIndex());
            sb.append(String.format("当前问题：%s（linkId: %s）\n", currentItem.getText(), currentItem.getLinkId()));
            sb.append("可选答案：\n");
            for (var opt : currentItem.getOptions()) {
                sb.append(String.format("  code=%s, display=%s, score=%d\n",
                        opt.getCode(), opt.getDisplay(), opt.getScore()));
            }
        }

        // 历史上下文（根据压缩等级应用规则裁剪）
        if (level == CompressionLevel.NONE || level == CompressionLevel.LIGHT) {
            // NONE/LIGHT: 完整上下文摘要
            if (session.getContextSummary() != null && !session.getContextSummary().isEmpty()) {
                sb.append("\n【已完成的题目摘要】\n");
                sb.append(session.getContextSummary());
            }
        } else if (level == CompressionLevel.DEEP) {
            // DEEP: 规则裁剪 —— 仅保留最近5条摘要记录
            if (session.getContextSummary() != null && !session.getContextSummary().isEmpty()) {
                String trimmed = trimContextSummary(session.getContextSummary(), 5);
                sb.append("\n【已完成题目摘要（精简）】\n");
                sb.append(trimmed);
            }
        }
        // EMERGENCY: 不提供历史上下文，仅当前题目

        return sb.toString();
    }

    private String buildInstruction(InterviewSession session, QuestionnaireTemplate questionnaire) {
        if (session.getCurrentQuestionIndex() < session.getTotalQuestions()) {
            var currentItem = questionnaire.getItems().get(session.getCurrentQuestionIndex());
            if (session.getAnswers().isEmpty()) {
                // 用户正在回答首题（首题已由SSE直接发送，无需LLM再次提问）
                return String.format(
                        "用户正在回答第一个问题：「%s」\n" +
                        "请理解用户的回答，将其映射到最接近的选项。\n" +
                        "如果用户回答清晰，使用 record_answer 工具记录。\n" +
                        "如果用户回答模糊，使用 clarify 工具温和追问。",
                        currentItem.getText());
            }
            return String.format(
                    "请理解用户刚才的回答，将其映射到当前问题（%s）最接近的选项。\n" +
                    "如果用户回答清晰，使用 record_answer 工具记录。\n" +
                    "如果用户回答模糊，使用 clarify 工具追问一次。\n" +
                    "如果确认了答案，同时用自然口语提出下一题（除非这是最后一题）。",
                    currentItem.getText());
        }

        return "所有题目已完成，请使用 complete 工具。";
    }

    // ==================== 响应解析 ====================

    private CollectionToolOutputs.AgentResult parseResponse(String response,
                                                             QuestionnaireTemplate questionnaire) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            String tool = root.path("tool").asText("");

            log.info("采集子Agent解析工具调用: tool={}", tool);

            return switch (tool) {
                case "ask_question" -> {
                    var r = parseAskQuestion(root, questionnaire);
                    log.info("LLM输出→提问: linkId={}", r.getAskQuestion().getLinkId());
                    yield r;
                }
                case "record_answer" -> {
                    var r = parseRecordAnswer(root, questionnaire);
                    log.info("LLM输出→记录答案: linkId={}, code={}, value={}",
                            r.getRecordAnswer().getLinkId(), r.getRecordAnswer().getCode(),
                            r.getRecordAnswer().getValue());
                    yield r;
                }
                case "clarify" -> {
                    var r = parseClarify(root, questionnaire);
                    log.info("LLM输出→追问: linkId={}", r.getClarify().getLinkId());
                    yield r;
                }
                case "complete" -> {
                    log.info("LLM输出→完成: message={}",
                            root.path("message").asText("评估完成"));
                    yield CollectionToolOutputs.AgentResult.complete(
                            CollectionToolOutputs.CompleteOutput.builder()
                                    .message(root.path("message").asText("评估完成"))
                                    .build());
                }
                case "emergency_interrupt" -> {
                    log.warn("LLM输出→紧急中断: reason={}",
                            root.path("reason").asText("检测到危险信号"));
                    yield CollectionToolOutputs.AgentResult.emergency(
                            CollectionToolOutputs.EmergencyInterruptOutput.builder()
                                    .reason(root.path("reason").asText("检测到危险信号"))
                                    .userMessage(root.path("message").asText(
                                            "我听到了你刚才说的话，这让我很担心。请记住你并不孤单，" +
                                            "专业帮助随时可用。全国心理援助热线：400-161-9995（24小时）。"))
                                    .build());
                }
                default -> {
                    log.warn("未知工具类型: {}, 原始响应: {}", tool, response);
                    yield ruleParserFallback(null, null, questionnaire);
                }
            };

        } catch (Exception e) {
            log.error("解析LLM响应失败: {}", e.getMessage());
            return ruleParserFallback(null, null, questionnaire);
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private CollectionToolOutputs.AgentResult parseAskQuestion(JsonNode root,
                                                                QuestionnaireTemplate questionnaire) {
        String linkId = root.path("linkId").asText("");
        String text = root.path("text").asText("");

        // 查找对应题目获取完整信息
        for (int i = 0; i < questionnaire.getItems().size(); i++) {
            var item = questionnaire.getItems().get(i);
            if (item.getLinkId().equals(linkId)) {
                return CollectionToolOutputs.AgentResult.askQuestion(
                        CollectionToolOutputs.AskQuestionOutput.builder()
                                .linkId(linkId)
                                .text(text.isEmpty() ? item.getText() : text)
                                .currentIndex(i)
                                .totalQuestions(questionnaire.getItems().size())
                                .options(item.getOptions())
                                .build());
            }
        }

        log.warn("LLM返回了未知linkId: {}", linkId);
        return CollectionToolOutputs.AgentResult.askQuestion(
                CollectionToolOutputs.AskQuestionOutput.builder()
                        .linkId(linkId).text(text).currentIndex(0)
                        .totalQuestions(questionnaire.getItems().size()).build());
    }

    private CollectionToolOutputs.AgentResult parseRecordAnswer(JsonNode root,
                                                                  QuestionnaireTemplate questionnaire) {
        // 先找当前题目的 linkId 对应的题目
        String linkId = root.path("linkId").asText("");
        var item = questionnaire.getItems().stream()
                .filter(i -> i.getLinkId().equals(linkId)).findAny();

        return CollectionToolOutputs.AgentResult.recordAnswer(
                CollectionToolOutputs.RecordAnswerOutput.builder()
                        .linkId(linkId)
                        .code(root.path("code").asText(""))
                        .display(root.path("display").asText(""))
                        .value(root.path("value").asInt(0))
                        .rawInput(root.path("raw_input").asText(""))
                        .confidence(root.path("confidence").asDouble(0.8))
                        .contextSummary(root.path("context_summary").asText(""))
                        .source("llm")
                        .build());
    }

    private CollectionToolOutputs.AgentResult parseClarify(JsonNode root,
                                                            QuestionnaireTemplate questionnaire) {
        return CollectionToolOutputs.AgentResult.clarify(
                CollectionToolOutputs.ClarifyOutput.builder()
                        .linkId(root.path("linkId").asText(""))
                        .clarifyingText(root.path("text").asText("能再说详细一点吗？"))
                        .build());
    }

    // ==================== T2: Token估算与压缩 ====================

    private CompressionLevel decideCompressionLevel(InterviewSession session, String userInput) {
        int estimated = estimateTokens(session, userInput);
        int budgetLight = TOKEN_BUDGET_LIGHT;
        int budgetDeep = TOKEN_BUDGET_DEEP;
        int budgetEmergency = TOKEN_BUDGET_EMERGENCY;
        CompressionLevel level;
        if (estimated < budgetLight) {
            level = CompressionLevel.NONE;
        } else if (estimated < budgetDeep) {
            level = CompressionLevel.LIGHT;
        } else if (estimated < budgetEmergency) {
            level = CompressionLevel.DEEP;
        } else {
            level = CompressionLevel.EMERGENCY;
        }
        log.info("T2 压缩等级决策: sessionId={}, estimatedTokens={}, "
                + "thresholds=[NONE<{} LIGHT<{} DEEP<{} EMERGENCY], level={}",
                session.getSessionId(), estimated,
                budgetLight, budgetDeep, budgetEmergency, level);
        return level;
    }

    private int estimateTokens(InterviewSession session, String userInput) {
        int systemPromptTokens = 250;
        int currentQuestionTokens = 120;
        int summaryLen = session.getContextSummary() != null
                ? session.getContextSummary().length() : 0;
        int summaryTokens = (int)(summaryLen * 1.5);
        int userInputLen = userInput != null ? userInput.length() : 0;
        int userInputTokens = (int)(userInputLen * 1.5);
        int instructionTokens = 80;
        int questionScaleTokens = Math.min(session.getTotalQuestions(), 20) * 10;
        int total = systemPromptTokens + currentQuestionTokens + summaryTokens
                + userInputTokens + instructionTokens + questionScaleTokens;
        log.debug("T2 Token估算: sessionId={}, system={}, question={}, "
                + "summary(len={}, tkn={}), userInput(len={}, tkn={}), "
                + "instruction={}, qScale={}, total={}",
                session.getSessionId(), systemPromptTokens, currentQuestionTokens,
                summaryLen, summaryTokens, userInputLen, userInputTokens,
                instructionTokens, questionScaleTokens, total);
        return total;
    }

    // ==================== T2: 规则裁剪 ====================

    /**
     * 规则裁剪上下文摘要，保留最近 N 条记录
     * 以中文分号"；"为分隔符切分条目，保留尾部 N 条，前置压缩标记
     */
    private String trimContextSummary(String summary, int keepLast) {
        if (summary == null || summary.isEmpty()) {
            return "";
        }
        String[] parts = summary.split("；");
        if (parts.length <= keepLast) {
            log.debug("T2 规则裁剪无需裁剪: entries={}, keepLast={}", parts.length, keepLast);
            return summary;
        }
        StringBuilder trimmed = new StringBuilder("...(已压缩) ");
        int kept = 0;
        for (int i = parts.length - keepLast; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.isEmpty()) {
                trimmed.append(part).append("；");
                kept++;
            }
        }
        String result = trimmed.toString();
        log.info("T2 规则裁剪完成: entriesBefore={}, entriesKept={}, "
                + "lenBefore={}, lenAfter={}, prefix=\"...(已压缩)\"",
                parts.length, kept, summary.length(), result.length());
        return result;
    }

    // ==================== 降级：规则解析器 ====================

    private CollectionToolOutputs.AgentResult ruleParserFallback(
            InterviewSession session, String userInput, QuestionnaireTemplate questionnaire) {

        if (session == null || userInput == null || questionnaire == null) {
            return CollectionToolOutputs.AgentResult.clarify(
                    CollectionToolOutputs.ClarifyOutput.builder()
                            .clarifyingText("抱歉，我没太理解您的回答，能换种方式描述一下吗？")
                            .build());
        }

        // 标记进入规则解析器层级
        session.setDegradationLevel("rule_parser");

        log.info("规则解析器降级: sessionId={}, questionIndex={}, linkId={}, degradationLevel={}",
                session.getSessionId(), session.getCurrentQuestionIndex(),
                questionnaire.getItems().get(session.getCurrentQuestionIndex()).getLinkId(),
                session.getDegradationLevel());

        var currentItem = questionnaire.getItems().get(session.getCurrentQuestionIndex());
        String lower = userInput.toLowerCase().trim();

        // 步骤1: 数值提取（用户可能直接输入了选项编号）
        try {
            int num = Integer.parseInt(lower.replaceAll("[^0-9]", ""));
            // 先按选项序号匹配（1-based，对应 options 列表索引）
            if (num >= 1 && num <= currentItem.getOptions().size()) {
                var opt = currentItem.getOptions().get(num - 1);
                log.info("规则解析→选项序号匹配: num={}, code={}", num, opt.getCode());
                return buildRecordResult(opt, currentItem.getLinkId(), userInput, 0.95);
            }
            // 再按 score 或 code 数值匹配
            var opt = currentItem.getOptions().stream()
                    .filter(o -> o.getScore() == num || o.getCode().equals(String.valueOf(num)))
                    .findAny();
            if (opt.isPresent()) {
                log.info("规则解析→数值匹配: num={}, code={}", num, opt.get().getCode());
                return buildRecordResult(opt.get(), currentItem.getLinkId(), userInput, 0.9);
            }
        } catch (NumberFormatException ignored) {}

        // 步骤2: 频率词映射（正则模糊匹配）
        if (lower.contains("完全") || lower.contains("从不") || lower.contains("没有") || lower.contains("根本不")) {
            var opt = currentItem.getOptions().stream().filter(o -> o.getScore() == 0).findAny();
            if (opt.isPresent()) {
                log.info("规则解析→频率词匹配(无/从不): code={}", opt.get().getCode());
                return buildRecordResult(opt.get(), currentItem.getLinkId(), userInput, 0.75);
            }
        }
        if (lower.contains("几乎每天") || lower.contains("总是") || lower.contains("天天") || lower.contains("每天")) {
            var opt = currentItem.getOptions().stream()
                    .filter(o -> o.getScore() == currentItem.getOptions().get(currentItem.getOptions().size() - 1).getScore())
                    .findAny();
            if (opt.isPresent()) {
                log.info("规则解析→频率词匹配(总是/每天): code={}", opt.get().getCode());
                return buildRecordResult(opt.get(), currentItem.getLinkId(), userInput, 0.8);
            }
        }
        if (lower.contains("经常") || lower.contains("一半") || lower.contains("多数") || lower.contains("很多")) {
            var opt = currentItem.getOptions().stream().filter(o -> o.getScore() == 2).findAny();
            if (opt.isPresent()) {
                log.info("规则解析→频率词匹配(经常/多数): code={}", opt.get().getCode());
                return buildRecordResult(opt.get(), currentItem.getLinkId(), userInput, 0.7);
            }
        }
        if (lower.contains("偶尔") || lower.contains("有时") || lower.contains("几天") || lower.contains("还行")) {
            var opt = currentItem.getOptions().stream().filter(o -> o.getScore() == 1).findAny();
            if (opt.isPresent()) {
                log.info("规则解析→频率词匹配(偶尔/有时): code={}", opt.get().getCode());
                return buildRecordResult(opt.get(), currentItem.getLinkId(), userInput, 0.7);
            }
        }

        // 步骤3: 模糊匹配（选项文本包含关系）
        for (var opt : currentItem.getOptions()) {
            String optDisplay = opt.getDisplay().toLowerCase();
            if (optDisplay.length() >= 2 && lower.contains(optDisplay.substring(0, 2))) {
                log.info("规则解析→模糊匹配: code={}", opt.getCode());
                return buildRecordResult(opt, currentItem.getLinkId(), userInput, 0.65);
            }
        }

        // 步骤4: 多候选 → 发澄清追问（仍在规则解析器层级）
        if (lower.length() >= 2) {
            var candidates = currentItem.getOptions().stream()
                    .filter(o -> o.getDisplay().toLowerCase().contains(lower)
                            || lower.contains(o.getDisplay().toLowerCase().substring(0,
                                    Math.min(2, o.getDisplay().length()))))
                    .toList();
            if (candidates.size() >= 2) {
                String candidateText = candidates.stream()
                        .map(o -> o.getDisplay())
                        .collect(Collectors.joining(" / "));
                log.info("规则解析→多候选澄清: candidates={}", candidates.size());
                return CollectionToolOutputs.AgentResult.clarify(
                        CollectionToolOutputs.ClarifyOutput.builder()
                                .linkId(currentItem.getLinkId())
                                .clarifyingText("您是指 " + candidateText + "？")
                                .options(currentItem.getOptions())
                                .build());
            }
        }

        // 最终兜底：规则解析也无法确定 → 降级到纯表单模式
        session.setDegradationLevel("manual_form");
        log.info("规则解析→兜底纯表单模式: linkId={}, degradationLevel={}",
                currentItem.getLinkId(), session.getDegradationLevel());

        StringBuilder formText = new StringBuilder("请直接输入数字选择最符合您情况的选项：");
        for (int i = 0; i < currentItem.getOptions().size(); i++) {
            var opt = currentItem.getOptions().get(i);
            formText.append(String.format("  %d-%s", i + 1, opt.getDisplay()));
        }

        CollectionToolOutputs.AgentResult result = CollectionToolOutputs.AgentResult.clarify(
                CollectionToolOutputs.ClarifyOutput.builder()
                        .linkId(currentItem.getLinkId())
                        .clarifyingText(formText.toString())
                        .options(currentItem.getOptions())
                        .build());
        result.setDegradationLevel("manual_form");
        return result;
    }

    private CollectionToolOutputs.AgentResult buildRecordResult(
            QuestionnaireTemplate.AnswerOption opt, String linkId,
            String rawInput, double confidence) {
        return CollectionToolOutputs.AgentResult.recordAnswer(
                CollectionToolOutputs.RecordAnswerOutput.builder()
                        .linkId(linkId)
                        .code(opt.getCode())
                        .display(opt.getDisplay())
                        .value(opt.getScore())
                        .rawInput(rawInput)
                        .confidence(confidence)
                        .contextSummary(String.format("Q%s: %s", linkId, opt.getDisplay()))
                        .source("rule_parser")
                        .build());
    }

    // ==================== 内部枚举 ====================

    private enum CompressionLevel {
        NONE, LIGHT, DEEP, EMERGENCY
    }
}
