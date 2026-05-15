package com.student.service.evaluation;

/**
 * 评估报告生成服务
 * 支持控制台摘要、JSON详细报告、HTML可视化报告
 *
 * @author 系统
 * @version 1.0
 */
public interface ReportService {

    /**
     * 生成控制台摘要报告
     */
    String generateConsoleReport(Long runId);

    /**
     * 生成JSON格式完整报告
     */
    String generateJsonReport(Long runId);

    /**
     * 生成HTML格式可视化报告
     */
    String generateHtmlReport(Long runId);

    /**
     * 对比多次评估运行
     */
    String generateComparisonReport(java.util.List<Long> runIds);
}
