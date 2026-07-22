# -*- coding: utf-8 -*-
"""
安全门控调优器 — 双阈值网格扫描 + 最优阈值搜索
"""

from . import config
from . import metrics as metrics_module


def run_tuning(label_entries_list, objective="balanced"):
    """
    执行完整的安全门控双阈值调优分析。

    label_entries_list: 已标注的条目列表
        每个条目包含: {query, rawConfidence, answer, isSafe, qualityScore, safetyIssues}
    objective: "safety_first" | "balanced" | "f1" | "user_experience"

    返回: {
        "allMetrics": [...],
        "optimal": {...},
        "currentMetrics": {...},
        "summary": {...}
    }
    """
    if not label_entries_list:
        print("  [ERROR] 没有已标注的条目，无法进行调优")
        return None

    total = len(label_entries_list)
    safe_count = sum(1 for e in label_entries_list if e.get("isSafe"))
    unsafe_count = total - safe_count

    print(f"  参与调优的条目: {total}条 (安全{safe_count}条, 不安全{unsafe_count}条)")

    if unsafe_count == 0:
        print("  [WARN] 无不安全条目，调优结果可靠性有限")

    # 网格扫描
    all_metrics = metrics_module.scan_threshold_grid(label_entries_list)

    # 找最优
    optimal = metrics_module.find_optimal_thresholds(all_metrics, objective)

    # 当前默认阈值指标
    current_metrics = metrics_module.compute_current_metrics(
        all_metrics,
        config.DEFAULT_MIN_THRESHOLD,
        config.DEFAULT_WARNING_THRESHOLD
    )

    # 计算各zone的置信度分布
    confidence_dist = _compute_confidence_distribution(label_entries_list)

    summary = {
        "totalEntries": total,
        "safeEntries": safe_count,
        "unsafeEntries": unsafe_count,
        "thresholdCombinations": len(all_metrics),
        "currentMinThreshold": config.DEFAULT_MIN_THRESHOLD,
        "currentWarningThreshold": config.DEFAULT_WARNING_THRESHOLD,
        "confidenceDistribution": confidence_dist
    }

    if current_metrics:
        summary.update({
            "currentSafetyScore": current_metrics["safetyScore"],
            "currentFalsePassRate": current_metrics["falsePassRate"],
            "currentFalseBlockRate": current_metrics["falseBlockRate"],
            "currentUserExperience": current_metrics["userExperienceScore"]
        })

    if optimal:
        summary.update({
            "optimalMinThreshold": optimal["minThreshold"],
            "optimalWarningThreshold": optimal["warningThreshold"],
            "optimalSafetyScore": optimal["safetyScore"],
            "optimalFalsePassRate": optimal["falsePassRate"],
            "optimalFalseBlockRate": optimal["falseBlockRate"],
            "optimalUserExperience": optimal["userExperienceScore"]
        })

        if current_metrics:
            summary["safetyScoreImprovement"] = round(
                optimal["safetyScore"] - current_metrics["safetyScore"], 4)

    return {
        "allMetrics": all_metrics,
        "optimal": optimal,
        "currentMetrics": current_metrics,
        "summary": summary
    }


def _compute_confidence_distribution(entries):
    """计算安全/不安全条目的置信度分布"""
    safe_confs = [e["rawConfidence"] for e in entries if e.get("isSafe")]
    unsafe_confs = [e["rawConfidence"] for e in entries if not e.get("isSafe")]

    def stats(confs):
        if not confs:
            return {"count": 0, "mean": 0, "min": 0, "max": 0, "median": 0}
        sorted_c = sorted(confs)
        n = len(sorted_c)
        return {
            "count": n,
            "mean": round(sum(confs) / n, 4),
            "min": round(min(confs), 4),
            "max": round(max(confs), 4),
            "median": round(sorted_c[n // 2], 4)
        }

    return {
        "safe": stats(safe_confs),
        "unsafe": stats(unsafe_confs)
    }
