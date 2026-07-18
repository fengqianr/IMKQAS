package com.imkqas.dto.rag;

import java.util.List;
import java.util.Map;

/**
 * 管理员审核统计数据VO
 *
 * @author 系统
 * @version 1.0
 */
public class AdminStatsVO {

    /** 待审核词条数 */
    private long pendingCount;

    /** 已审核数（今日） */
    private long approvedTodayCount;

    /** 当前未映射率（百分比） */
    private double unmappedRate;

    /** 告警阈值 */
    private double alertThreshold;

    /** 是否触发告警 */
    private boolean alertTriggered;

    /** 累计告警次数 */
    private long totalAlertCount;

    /** 近24小时未映射率趋势（小时 → 比率） */
    private List<Map<String, Object>> hourlyTrend;

    /** 触发告警最多的词条Top5 */
    private List<String> topUnmappedTerms;

    public AdminStatsVO() {}

    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }

    public long getApprovedTodayCount() { return approvedTodayCount; }
    public void setApprovedTodayCount(long approvedTodayCount) { this.approvedTodayCount = approvedTodayCount; }

    public double getUnmappedRate() { return unmappedRate; }
    public void setUnmappedRate(double unmappedRate) { this.unmappedRate = unmappedRate; }

    public double getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(double alertThreshold) { this.alertThreshold = alertThreshold; }

    public boolean isAlertTriggered() { return alertTriggered; }
    public void setAlertTriggered(boolean alertTriggered) { this.alertTriggered = alertTriggered; }

    public long getTotalAlertCount() { return totalAlertCount; }
    public void setTotalAlertCount(long totalAlertCount) { this.totalAlertCount = totalAlertCount; }

    public List<Map<String, Object>> getHourlyTrend() { return hourlyTrend; }
    public void setHourlyTrend(List<Map<String, Object>> hourlyTrend) { this.hourlyTrend = hourlyTrend; }

    public List<String> getTopUnmappedTerms() { return topUnmappedTerms; }
    public void setTopUnmappedTerms(List<String> topUnmappedTerms) { this.topUnmappedTerms = topUnmappedTerms; }
}
