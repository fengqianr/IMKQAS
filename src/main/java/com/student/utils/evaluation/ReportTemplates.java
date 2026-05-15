package com.student.utils.evaluation;

import com.student.entity.evaluation.EvalRun;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HTML评估报告模板工具类
 *
 * @author 系统
 * @version 1.0
 */
public final class ReportTemplates {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReportTemplates() {
    }

    /**
     * 构建单次运行的HTML报告
     */
    public static String buildHtmlReport(EvalRun run) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <title>IMKQAS RAG 评估报告</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; background: #f5f5f5; color: #333; }
                        .container { max-width: 960px; margin: 40px auto; }
                        .header { background: linear-gradient(135deg, #00478d, #0066cc); color: white; padding: 32px; border-radius: 8px 8px 0 0; }
                        .header h1 { font-size: 24px; margin-bottom: 8px; }
                        .header .meta { opacity: 0.85; font-size: 14px; }
                        .section { background: white; padding: 24px; margin-bottom: 2px; }
                        .section h2 { font-size: 18px; color: #00478d; margin-bottom: 16px; border-bottom: 2px solid #00478d; padding-bottom: 8px; }
                        .metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
                        .metric { background: #f8f9fa; padding: 16px; border-radius: 6px; text-align: center; }
                        .metric .value { font-size: 28px; font-weight: bold; color: #00478d; }
                        .metric .label { font-size: 13px; color: #666; margin-top: 4px; }
                        .good { color: #2e7d32 !important; }
                        .warn { color: #e65100 !important; }
                        .bad { color: #c62828 !important; }
                        .footer { text-align: center; padding: 16px; color: #999; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>IMKQAS RAG 管线评估报告</h1>
                            <div class="meta">
                                运行名称: %s | 完成时间: %s | 评估查询数: %d
                            </div>
                        </div>
                        %s
                        %s
                        %s
                        %s
                        <div class="footer">
                            IMKQAS 医疗知识问答系统 | 评估报告自动生成 | %s
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                esc(run.getRunName()),
                run.getCompletedAt() != null ? run.getCompletedAt().format(DATE_FMT) : "-",
                run.getEvaluatedQueries() != null ? run.getEvaluatedQueries() : 0,
                buildRetrievalSection(run),
                buildFusionSection(run),
                buildSafetySection(run),
                buildGenerationSection(run),
                java.time.LocalDateTime.now().format(DATE_FMT)
        );
    }

    private static String buildRetrievalSection(EvalRun run) {
        return """
                <div class="section">
                    <h2>检索质量</h2>
                    <div class="metrics">
                        %s
                        %s
                        %s
                        %s
                    </div>
                </div>
                """.formatted(
                metricCard("Recall@5", run.getRecallAt5(), 0.7),
                metricCard("Recall@10", run.getRecallAt10(), 0.8),
                metricCard("MRR", run.getMrr(), 0.5),
                metricCard("NDCG@10", run.getNdcgAt10(), 0.6)
        );
    }

    private static String buildFusionSection(EvalRun run) {
        return """
                <div class="section">
                    <h2>融合质量</h2>
                    <div class="metrics">
                        %s
                        %s
                        %s
                        %s
                    </div>
                </div>
                """.formatted(
                metricCard("MRR(向量)", run.getMrrVectorOnly(), 0.4),
                metricCard("MRR(关键词)", run.getMrrKeywordOnly(), 0.4),
                metricCard("MRR(融合)", run.getMrrFused(), 0.5),
                metricCard("互补度", run.getComplementarityScore(), 1.3)
        );
    }

    private static String buildSafetySection(EvalRun run) {
        return """
                <div class="section">
                    <h2>安全质量</h2>
                    <div class="metrics">
                        %s
                        %s
                    </div>
                </div>
                """.formatted(
                metricCard("紧急检测准确率", run.getEmergencyDetectionAccuracy(), 0.8),
                metricCard("缓存命中率", run.getCacheHitRate(), 0.3)
        );
    }

    private static String buildGenerationSection(EvalRun run) {
        return """
                <div class="section">
                    <h2>生成质量 & 管线耗时</h2>
                    <div class="metrics">
                        %s
                        %s
                        %s
                        %s
                    </div>
                </div>
                """.formatted(
                metricCard("忠实度", run.getAvgFaithfulness(), 0.7),
                metricCard("答案相关性", run.getAvgAnswerRelevance(), 0.7),
                metricCard("上下文相关性", run.getAvgContextRelevance(), 0.7),
                metricCard("平均耗时(ms)", run.getAvgTotalTimeMs(), 3000)
        );
    }

    private static String metricCard(String label, Double value, double goodThreshold) {
        if (value == null) value = 0.0;
        String cssClass = value >= goodThreshold ? "good" : (value >= goodThreshold * 0.6 ? "warn" : "bad");
        return """
                <div class="metric">
                    <div class="value %s">%.4f</div>
                    <div class="label">%s</div>
                </div>
                """.formatted(cssClass, value, label);
    }

    /**
     * 构建多次运行的对比报告
     */
    public static String buildComparisonReport(List<EvalRun> runs) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"><title>评估运行对比</title>
                <style>
                    body { font-family: sans-serif; padding: 20px; }
                    table { border-collapse: collapse; width: 100%%; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }
                    th { background: #00478d; color: white; }
                    .improve { color: #2e7d32; }
                    .decline { color: #c62828; }
                </style></head><body>
                <h1>评估运行对比报告</h1>
                <table>
                <tr><th>指标</th>""");

        for (EvalRun run : runs) {
            sb.append("<th>").append(esc(run.getRunName())).append("</th>");
        }
        sb.append("</tr>");

        // 主要指标行
        addComparisonRow(sb, "Recall@5", runs, EvalRun::getRecallAt5);
        addComparisonRow(sb, "Recall@10", runs, EvalRun::getRecallAt10);
        addComparisonRow(sb, "MRR", runs, EvalRun::getMrr);
        addComparisonRow(sb, "NDCG@10", runs, EvalRun::getNdcgAt10);
        addComparisonRow(sb, "互补度", runs, EvalRun::getComplementarityScore);
        addComparisonRow(sb, "平均耗时(ms)", runs, EvalRun::getAvgTotalTimeMs);
        addComparisonRow(sb, "缓存命中率", runs, EvalRun::getCacheHitRate);

        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static <T> void addComparisonRow(StringBuilder sb, String metric, List<T> items, java.util.function.Function<T, Double> getter) {
        sb.append("<tr><td>").append(metric).append("</td>");
        for (T item : items) {
            Double val = getter.apply(item);
            sb.append("<td>").append(val != null ? String.format("%.4f", val) : "-").append("</td>");
        }
        sb.append("</tr>\n");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
