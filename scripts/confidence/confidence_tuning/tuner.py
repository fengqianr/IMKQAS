#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
阈值扫描器 — 在置信度区间内扫描，找出最优阈值
"""

import numpy as np
from . import config
from . import metrics as metrics_module


def scan_thresholds(cached_entries, min_th=0.50, max_th=0.98, step=0.02):
    """
    扫描所有阈值，计算每个阈值下的完整指标。

    返回: metrics列表（按阈值升序排列）
    """
    thresholds = np.arange(min_th, max_th + step / 2, step)
    all_metrics = []

    for t in thresholds:
        m = metrics_module.compute_threshold_metrics(cached_entries, t)
        all_metrics.append(m)

    # 计算成本效率
    all_metrics = metrics_module.compute_cost_efficiency(all_metrics)

    return all_metrics


def run_tuning(cached_entries, objective="f1", constraint=None):
    """
    执行完整的阈值调优分析。

    返回: {
        "allMetrics": [...],
        "optimal": {...},
        "saturationPoint": {...},
        "calibration": [...],
        "summary": {...}
    }
    """
    # 仅用权威标注条目
    authoritative = [e for e in cached_entries if e.get("hasAuthoritativeMapping")]
    if not authoritative:
        print("  [ERROR] 没有找到权威标注条目，无法进行调优")
        return None

    print(f"  参与F1计算的权威标注条目: {len(authoritative)}")

    # 扫描所有阈值
    all_metrics = scan_thresholds(cached_entries)
    print(f"  阈值扫描完成: {len(all_metrics)}个阈值点")

    # 找最优阈值
    optimal = metrics_module.find_optimal_threshold(all_metrics, objective, constraint)
    saturation = metrics_module.find_saturation_point(all_metrics, config.MARGINAL_THRESHOLD)

    # 置信度校准
    calibration = metrics_module.compute_confidence_calibration(cached_entries)

    # 摘要
    summary = build_summary(all_metrics, optimal, saturation, cached_entries)

    return {
        "allMetrics": all_metrics,
        "optimal": optimal,
        "saturationPoint": saturation,
        "calibration": calibration,
        "summary": summary
    }


def build_summary(all_metrics, optimal, saturation, cached_entries):
    """构建调优摘要"""
    authoritative = [e for e in cached_entries if e.get("hasAuthoritativeMapping")]
    non_authoritative = [e for e in cached_entries if not e.get("hasAuthoritativeMapping")]

    current_threshold_metrics = None
    for m in all_metrics:
        if abs(m["threshold"] - 0.85) < 0.001:
            current_threshold_metrics = m
            break

    summary = {
        "totalEntries": len(cached_entries),
        "authoritativeEntries": len(authoritative),
        "nonAuthoritativeEntries": len(non_authoritative),
        "thresholdsScanned": len(all_metrics),
        "currentThreshold": 0.85
    }

    if current_threshold_metrics:
        summary["currentF1"] = current_threshold_metrics["f1"]
        summary["currentAcceptanceRate"] = current_threshold_metrics["acceptanceRate"]

    if optimal:
        summary["optimalThreshold"] = optimal["threshold"]
        summary["optimalF1"] = optimal["f1"]
        summary["optimalPrecision"] = optimal["precision"]
        summary["optimalRecall"] = optimal["recall"]
        summary["optimalAcceptanceRate"] = optimal["acceptanceRate"]

        if current_threshold_metrics:
            f1_diff = optimal["f1"] - current_threshold_metrics["f1"]
            summary["f1Improvement"] = round(f1_diff, 4)

    if saturation:
        summary["saturationThreshold"] = saturation["threshold"]

    return summary


def generate_recommendation(tuning_result):
    """
    生成最终阈值推荐。
    """
    if not tuning_result:
        return "无法生成推荐：调优数据不足"

    optimal = tuning_result.get("optimal", {})
    summary = tuning_result.get("summary", {})

    lines = []
    lines.append("=" * 60)
    lines.append("LLM置信度阈值调优 — 最终推荐")
    lines.append("=" * 60)
    lines.append(f"当前阈值: {summary.get('currentThreshold', 0.85)}")
    lines.append(f"当前F1:   {summary.get('currentF1', 'N/A')}")
    lines.append("")

    if optimal:
        lines.append(f"推荐阈值: {optimal.get('threshold', 'N/A')}")
        lines.append(f"推荐F1:   {optimal.get('f1', 'N/A')}")
        lines.append(f"精度:     {optimal.get('precision', 'N/A')}")
        lines.append(f"召回率:   {optimal.get('recall', 'N/A')}")
        lines.append(f"采纳率:   {optimal.get('acceptanceRate', 'N/A')}")
        lines.append("")

        f1_imp = summary.get('f1Improvement', 0)
        if f1_imp and f1_imp > 0.01:
            lines.append(f"相比当前阈值F1提升: +{f1_imp:.4f}")
        elif f1_imp and f1_imp < -0.01:
            lines.append(f"注意：推荐阈值F1低于当前: {f1_imp:.4f}")

        saturation_t = summary.get("saturationThreshold", None)
        if saturation_t:
            lines.append(f"边际收益拐点: {saturation_t}")

    lines.append("")
    lines.append("建议操作：")
    lines.append(f"  修改 application.yml 中的 imkqas.query-rewrite.synonym-expansion.llm-min-confidence")
    lines.append("=" * 60)

    return "\n".join(lines)
