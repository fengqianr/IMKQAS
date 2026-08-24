package com.imkqas.service.agent;

import com.imkqas.config.AgentProperties;
import com.imkqas.service.his.InterviewEngine;
import com.imkqas.service.his.InterviewSuggestion;
import com.imkqas.service.rag.ContraindicationDetectionService;
import com.imkqas.service.rag.MultiRetrievalService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 医疗 Agent 工具封装
 * 将现有 RAG/问卷/禁忌检测能力暴露为 LLM 可自主调用的工具
 *
 * @author 系统
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MedicalTools {

    private final MultiRetrievalService multiRetrievalService;
    private final InterviewEngine interviewEngine;
    private final ContraindicationDetectionService contraindicationDetectionService;
    private final AgentProperties agentProperties;

    /** 已触发的工具名记录（供 AgentExecutor 采集诊断信息；MVP 阶段并发下可能串扰，仅作诊断用） */
    private final List<String> toolCallLog = Collections.synchronizedList(new ArrayList<>());

    /** 检索来源记录（供 Agent 回答附带引用；与 toolCallLog 相同的并发约定） */
    private final List<MultiRetrievalService.RetrievalResult> retrievedSources =
            Collections.synchronizedList(new ArrayList<>());

    /** 工具调用步骤记录（供前端可视化 Agent 思考/工具调用过程；并发约定同上） */
    private final List<AgentToolStep> toolSteps = Collections.synchronizedList(new ArrayList<>());

    /**
     * 知识库检索工具
     */
    @Tool("在知识库中检索与 query 相关的医学知识片段，返回最相关的若干条内容；无结果返回 [无结果]")
    public String searchKnowledge(@P("检索关键词，如\"布洛芬 孕妇\"") String query) {
        long start = System.currentTimeMillis();
        List<MultiRetrievalService.RetrievalResult> results =
                multiRetrievalService.hybridRetrieval(query, agentProperties.getTopK());
        if (results == null || results.isEmpty()) {
            recordStep("searchKnowledge", "query=" + query, "[无结果]", start);
            return "[无结果]";
        }
        retrievedSources.addAll(results);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            MultiRetrievalService.RetrievalResult r = results.get(i);
            String content = r.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }
            sb.append(i + 1).append(". ").append(content).append("\n");
        }
        String result = sb.length() == 0 ? "[无结果]" : sb.toString();
        recordStep("searchKnowledge", "query=" + query, "命中" + results.size() + "条知识片段", start);
        return result;
    }

    /**
     * 问卷推荐工具
     */
    @Tool("根据症状描述推荐合适的心理/健康评估问卷，返回问卷名与匹配理由；无匹配返回 [无匹配问卷]")
    public String suggestQuestionnaire(@P("用户症状或情绪描述，如\"我最近总是睡不着\"") String symptoms) {
        long start = System.currentTimeMillis();
        InterviewSuggestion suggestion = interviewEngine.suggestQuestionnaire(symptoms);
        if (suggestion == null || !suggestion.isMatched() || suggestion.getQuestionnaire() == null) {
            recordStep("suggestQuestionnaire", "symptoms=" + symptoms, "[无匹配问卷]", start);
            return "[无匹配问卷]";
        }
        String title = suggestion.getQuestionnaire().getTitle();
        String text = suggestion.getSuggestionText();
        StringBuilder sb = new StringBuilder("推荐问卷：").append(title);
        if (text != null && !text.isBlank()) {
            sb.append("；建议：").append(text);
        }
        sb.append("；置信度：").append(String.format("%.2f", suggestion.getConfidence()));
        String result = sb.toString();
        recordStep("suggestQuestionnaire", "symptoms=" + symptoms, result, start);
        return result;
    }

    /**
     * 禁忌检测工具
     */
    @Tool("查询某药物在特定人群（如孕妇/儿童/老人）中的禁忌信息；无规则返回 [无匹配禁忌规则]")
    public String checkContraindication(@P("药物名，如\"布洛芬\"") String drug,
                                        @P("人群，如\"孕妇\"") String population) {
        long start = System.currentTimeMillis();
        ContraindicationDetectionService.ContraindicationMatch match =
                contraindicationDetectionService.checkContraindication(drug, population);
        if (match == null) {
            recordStep("checkContraindication", "drug=" + drug + ", population=" + population,
                    "[无匹配禁忌规则]", start);
            return "[无匹配禁忌规则]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("药物：").append(match.drug())
                .append("；人群：").append(match.population())
                .append("；禁忌类型：").append(match.type() == null ? "未知" : match.type())
                .append("；证据等级：").append(match.evidence() == null ? "未知" : match.evidence());
        if (match.description() != null && !match.description().isBlank()) {
            sb.append("；说明：").append(match.description());
        }
        String result = sb.toString();
        recordStep("checkContraindication", "drug=" + drug + ", population=" + population, result, start);
        return result;
    }

    /** 记录工具调用步骤（含参数、结果摘要与耗时） */
    private void recordStep(String toolName, String params, String result, long startMs) {
        String summary = result == null ? "" : (result.length() > 200 ? result.substring(0, 200) + "..." : result);
        toolSteps.add(new AgentToolStep(toolName, params, summary, System.currentTimeMillis() - startMs));
        toolCallLog.add(toolName);
        log.info("[Agent工具] 触发工具: {}, 参数: {}", toolName, params);
    }

    /** 返回并清空已记录的工具调用列表 */
    public List<String> drainToolCalls() {
        synchronized (toolCallLog) {
            List<String> calls = new ArrayList<>(toolCallLog);
            toolCallLog.clear();
            return calls;
        }
    }

    /** 返回并清空已记录的检索来源（供 Agent 回答附带引用） */
    public List<MultiRetrievalService.RetrievalResult> drainRetrievedSources() {
        synchronized (retrievedSources) {
            List<MultiRetrievalService.RetrievalResult> sources = new ArrayList<>(retrievedSources);
            retrievedSources.clear();
            return sources;
        }
    }

    /** 返回并清空已记录的工具调用步骤（供前端可视化 Agent 思考过程） */
    public List<AgentToolStep> drainToolSteps() {
        synchronized (toolSteps) {
            List<AgentToolStep> steps = new ArrayList<>(toolSteps);
            toolSteps.clear();
            return steps;
        }
    }
}
