package com.student.service.evaluation.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.student.entity.evaluation.EvalRun;
import com.student.mapper.evaluation.EvalRunMapper;
import com.student.service.evaluation.ReportService;
import com.student.utils.evaluation.ReportTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 评估报告生成服务实现
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final EvalRunMapper runMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String generateConsoleReport(Long runId) {
        EvalRun run = runMapper.selectById(runId);
        if (run == null) {
            return "评估运行不存在: " + runId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════╗\n");
        sb.append("║          IMKQAS RAG 评估报告 - 控制台摘要             ║\n");
        sb.append("╠══════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  运行名称: %-42s ║\n", truncate(run.getRunName(), 42)));
        sb.append(String.format("║  运行状态: %-42s ║\n", run.getStatus()));
        sb.append(String.format("║  评估查询: %-8d                耗时: %-8s ║\n",
                run.getEvaluatedQueries(), formatDuration(run)));
        sb.append("╠══════════════════════════════════════════════════════╣\n");
        sb.append("║  检索质量                                            ║\n");
        sb.append(String.format("║    Recall@5:   %.4f    Recall@10:  %.4f     ║\n",
                nvl(run.getRecallAt5()), nvl(run.getRecallAt10())));
        sb.append(String.format("║    Precision@5:%.4f    MRR:        %.4f     ║\n",
                nvl(run.getPrecisionAt5()), nvl(run.getMrr())));
        sb.append(String.format("║    NDCG@10:    %.4f    HitRate@5:  %.4f     ║\n",
                nvl(run.getNdcgAt10()), nvl(run.getHitRateAt5())));
        sb.append("║  融合质量                                            ║\n");
        sb.append(String.format("║    MRR(vector):%.4f  MRR(keyword):%.4f      ║\n",
                nvl(run.getMrrVectorOnly()), nvl(run.getMrrKeywordOnly())));
        sb.append(String.format("║    MRR(fused): %.4f  互补度:     %.4f       ║\n",
                nvl(run.getMrrFused()), nvl(run.getComplementarityScore())));
        sb.append("║  安全质量                                            ║\n");
        sb.append(String.format("║    紧急检测准确率: %.4f  安全阻断率:   %.4f     ║\n",
                nvl(run.getEmergencyDetectionAccuracy()), nvl(run.getSafetyBlockRate())));
        sb.append("║  生成质量                                            ║\n");
        sb.append(String.format("║    忠实度:      %.4f  答案相关性:   %.4f     ║\n",
                nvl(run.getAvgFaithfulness()), nvl(run.getAvgAnswerRelevance())));
        sb.append(String.format("║    上下文相关性: %.4f                              ║\n",
                nvl(run.getAvgContextRelevance())));
        sb.append("║  管线耗时                                            ║\n");
        sb.append(String.format("║    平均总耗时: %.0fms    缓存命中率: %.2f%%        ║\n",
                nvl(run.getAvgTotalTimeMs()), nvl(run.getCacheHitRate()) * 100));
        sb.append("╚══════════════════════════════════════════════════════╝\n");

        log.info("生成控制台评估报告: runId={}", runId);
        return sb.toString();
    }

    @Override
    public String generateJsonReport(Long runId) {
        EvalRun run = runMapper.selectById(runId);
        if (run == null) {
            return "{}";
        }
        try {
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            return objectMapper.writeValueAsString(run);
        } catch (Exception e) {
            log.error("生成JSON报告失败: {}", e.getMessage(), e);
            return "{}";
        }
    }

    @Override
    public String generateHtmlReport(Long runId) {
        EvalRun run = runMapper.selectById(runId);
        if (run == null) {
            return "<html><body><h1>评估运行不存在: " + runId + "</h1></body></html>";
        }
        return ReportTemplates.buildHtmlReport(run);
    }

    @Override
    public String generateComparisonReport(java.util.List<Long> runIds) {
        List<EvalRun> runs = new ArrayList<>();
        for (Long id : runIds) {
            EvalRun run = runMapper.selectById(id);
            if (run != null) {
                runs.add(run);
            }
        }
        return ReportTemplates.buildComparisonReport(runs);
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "-";
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }

    private String formatDuration(EvalRun run) {
        if (run.getStartedAt() == null || run.getCompletedAt() == null) {
            return "-";
        }
        long seconds = java.time.Duration.between(run.getStartedAt(), run.getCompletedAt()).getSeconds();
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m" + (seconds % 60) + "s";
        return (seconds / 3600) + "h" + ((seconds % 3600) / 60) + "m";
    }

    private double nvl(Double val) {
        return val != null ? val : 0.0;
    }
}
