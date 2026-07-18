#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
报告生成器 — 控制台 + Markdown + JSON
"""

import json
import os
from datetime import datetime
from . import config


def generate_console_report(tuning_result):
    """生成控制台格式报告"""
    if not tuning_result:
        print("无调优数据，无法生成报告")
        return

    summary = tuning_result.get("summary", {})
    optimal = tuning_result.get("optimal", {})
    all_metrics = tuning_result.get("allMetrics", [])

    print()
    print("=" * 60)
    print("  LLM置信度阈值调优报告")
    print("=" * 60)
    print(f"  总条目数:       {summary.get('totalEntries', 0)}")
    print(f"  权威标注条目:    {summary.get('authoritativeEntries', 0)} "
          f"({summary.get('authoritativeEntries', 0) / max(summary.get('totalEntries', 1), 1) * 100:.1f}%)")
    print(f"  扫描阈值点数:    {summary.get('thresholdsScanned', 0)}")
    print("-" * 60)
    print(f"  当前阈值:       {summary.get('currentThreshold', 0.85)}")
    print(f"  当前F1:         {summary.get('currentF1', 'N/A')}")
    print(f"  当前采纳率:      {summary.get('currentAcceptanceRate', 'N/A')}")
    print("-" * 60)

    if optimal:
        print(f"  ★ 推荐阈值:     {optimal.get('threshold', 'N/A')}")
        print(f"  ★ 推荐F1:       {optimal.get('f1', 'N/A')}")
        print(f"     精度:        {optimal.get('precision', 'N/A')}")
        print(f"     召回率:       {optimal.get('recall', 'N/A')}")
        print(f"     采纳率:       {optimal.get('acceptanceRate', 'N/A')}")

        f1_imp = summary.get("f1Improvement", 0)
        if f1_imp:
            direction = "↑" if f1_imp > 0 else "↓"
            print(f"     F1变化:       {direction}{abs(f1_imp):.4f}")

    saturation_t = summary.get("saturationThreshold", None)
    if saturation_t:
        print(f"  边际收益拐点:    {saturation_t}")

    print("-" * 60)

    # Top-5 阈值
    if all_metrics:
        sorted_by_f1 = sorted(all_metrics, key=lambda m: m["f1"], reverse=True)
        print("  Top-5 阈值 (按F1):")
        for i, m in enumerate(sorted_by_f1[:5]):
            print(f"    {i + 1}. t={m['threshold']:.2f}  F1={m['f1']:.4f}  "
                  f"P={m['precision']:.4f}  R={m['recall']:.4f}  AR={m['acceptanceRate']:.4f}")

    print("=" * 60)

    # 置信度校准摘要
    calibration = tuning_result.get("calibration", [])
    if calibration:
        ece = compute_ece(calibration)
        print(f"  期望校准误差 (ECE): {ece:.4f}")
        print("=" * 60)

    print()


def generate_markdown_report(tuning_result, output_path=None):
    """生成Markdown格式报告"""
    if output_path is None:
        output_path = config.OUTPUT_MD

    summary = tuning_result.get("summary", {})
    optimal = tuning_result.get("optimal", {})
    all_metrics = tuning_result.get("allMetrics", [])
    calibration = tuning_result.get("calibration", [])

    lines = []
    lines.append("# LLM置信度阈值调优报告")
    lines.append(f"\n生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")

    lines.append("## 数据概况\n")
    lines.append(f"| 指标 | 值 |")
    lines.append(f"|------|-----|")
    lines.append(f"| 总条目数 | {summary.get('totalEntries', 0)} |")
    lines.append(f"| 权威标注条目 | {summary.get('authoritativeEntries', 0)} |")
    lines.append(f"| 扫描阈值点数 | {summary.get('thresholdsScanned', 0)} |")
    lines.append("")

    lines.append("## 阈值对比\n")
    lines.append(f"| 指标 | 当前 (t={summary.get('currentThreshold', 0.85)}) | 推荐 (t={optimal.get('threshold', 'N/A')}) |")
    lines.append(f"|------|-----|-----|")
    lines.append(f"| F1 | {summary.get('currentF1', 'N/A')} | {optimal.get('f1', 'N/A')} |")
    lines.append(f"| Precision | - | {optimal.get('precision', 'N/A')} |")
    lines.append(f"| Recall | - | {optimal.get('recall', 'N/A')} |")
    lines.append(f"| 采纳率 | {summary.get('currentAcceptanceRate', 'N/A')} | {optimal.get('acceptanceRate', 'N/A')} |")
    f1_imp = summary.get("f1Improvement", 0)
    if f1_imp:
        lines.append(f"| F1变化 | - | {'+' if f1_imp > 0 else ''}{f1_imp:.4f} |")
    lines.append("")

    if all_metrics:
        lines.append("## Top-10 阈值 (按F1降序)\n")
        lines.append("| 排名 | 阈值 | F1 | Precision | Recall | 采纳率 |")
        lines.append("|------|------|-----|-----------|--------|--------|")
        sorted_by_f1 = sorted(all_metrics, key=lambda m: m["f1"], reverse=True)
        for i, m in enumerate(sorted_by_f1[:10]):
            lines.append(f"| {i + 1} | {m['threshold']:.2f} | {m['f1']:.4f} | "
                         f"{m['precision']:.4f} | {m['recall']:.4f} | {m['acceptanceRate']:.4f} |")
        lines.append("")

    if calibration:
        ece = compute_ece(calibration)
        lines.append(f"## 置信度校准\n")
        lines.append(f"期望校准误差 (ECE): **{ece:.4f}**\n")
        lines.append("| 置信度区间 | 平均置信度 | 实际准确率 | 样本数 |")
        lines.append("|-----------|-----------|-----------|--------|")
        for c in calibration:
            lines.append(f"| [{c['binMin']:.2f}, {c['binMax']:.2f}) | "
                         f"{c['avgConfidence']:.4f} | {c['accuracy']:.4f} | {c['count']} |")
        lines.append("")

    lines.append("## 建议操作\n")
    if optimal:
        lines.append(f"1. 修改 `application.yml` 中的配置项:")
        lines.append(f"   ```yaml")
        lines.append(f"   imkqas.query-rewrite.synonym-expansion.llm-min-confidence: {optimal.get('threshold', 0.85)}")
        lines.append(f"   ```")
    lines.append(f"2. 重启服务使新阈值生效")
    lines.append(f"3. 观察线上未映射率（`unmapped-alert-threshold`）变化")
    lines.append("")

    content = "\n".join(lines)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"  Markdown报告已保存: {output_path}")


def generate_json_report(tuning_result, output_path=None):
    """保存完整JSON结果"""
    if output_path is None:
        output_path = config.OUTPUT_JSON

    report = {
        "reportGeneratedAt": datetime.now().isoformat(),
        **tuning_result
    }

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"  JSON结果已保存: {output_path}")


def compute_ece(calibration):
    """
    期望校准误差 (Expected Calibration Error)
    ECE = Σ (|bin| / N) * |accuracy(bin) - confidence(bin)|
    """
    total = sum(c["count"] for c in calibration)
    if total == 0:
        return 0.0
    ece = 0.0
    for c in calibration:
        error = abs(c["accuracy"] - c["avgConfidence"])
        weight = c["count"] / total
        ece += weight * error
    return ece
