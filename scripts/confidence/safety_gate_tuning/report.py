# -*- coding: utf-8 -*-
"""
报告生成器 — 控制台报告 + JSON报告 + Markdown报告
"""

import json
import os
from . import config


def generate_console_report(tuning_result):
    """生成控制台格式报告"""
    if not tuning_result:
        print("无可用的调优结果")
        return

    summary = tuning_result.get("summary", {})
    optimal = tuning_result.get("optimal", {})
    current = tuning_result.get("currentMetrics", {})

    print()
    print("=" * 65)
    print("  安全门控置信度双阈值调优 — 结果摘要")
    print("=" * 65)
    print(f"  调优条目: {summary.get('totalEntries', 0)}条 "
          f"(安全{summary.get('safeEntries', 0)}条, 不安全{summary.get('unsafeEntries', 0)}条)")
    print(f"  扫描组合: {summary.get('thresholdCombinations', 0)}个")
    print()

    # 置信度分布
    conf_dist = summary.get("confidenceDistribution", {})
    if conf_dist:
        print("  置信度分布:")
        for label in ["safe", "unsafe"]:
            d = conf_dist.get(label, {})
            if d["count"] > 0:
                print(f"    {label}: 均值={d['mean']:.3f}, 中位数={d['median']:.3f}, "
                      f"范围=[{d['min']:.3f}, {d['max']:.3f}], 数量={d['count']}")
        print()

    # 当前 vs 推荐
    print(f"  {'指标':<20} {'当前':>10} {'推荐':>10} {'变化':>10}")
    print(f"  {'-' * 50}")
    print(f"  {'最小阈值':<20} {current.get('minThreshold', config.DEFAULT_MIN_THRESHOLD):>10.2f} "
          f"{optimal.get('minThreshold', 'N/A'):>10} {'':>10}")
    print(f"  {'警告阈值':<20} {current.get('warningThreshold', config.DEFAULT_WARNING_THRESHOLD):>10.2f} "
          f"{optimal.get('warningThreshold', 'N/A'):>10} {'':>10}")
    print(f"  {'综合安全分':<20} {current.get('safetyScore', 0):>10.4f} "
          f"{optimal.get('safetyScore', 0):>10.4f} "
          f"{summary.get('safetyScoreImprovement', 0):>+10.4f}")
    print(f"  {'漏网率':<20} {current.get('falsePassRate', 0):>10.4f} "
          f"{optimal.get('falsePassRate', 0):>10.4f} {'':>10}")
    print(f"  {'过度阻断率':<20} {current.get('falseBlockRate', 0):>10.4f} "
          f"{optimal.get('falseBlockRate', 0):>10.4f} {'':>10}")
    print(f"  {'用户体验分':<20} {current.get('userExperienceScore', 0):>10.4f} "
          f"{optimal.get('userExperienceScore', 0):>10.4f} {'':>10}")
    print()


def generate_json_report(tuning_result, output_path=None):
    """生成JSON格式报告"""
    if output_path is None:
        output_path = config.OUTPUT_JSON

    report = {
        "title": "安全门控置信度双阈值调优报告",
        "summary": tuning_result.get("summary", {}),
        "optimal": tuning_result.get("optimal", {}),
        "currentMetrics": tuning_result.get("currentMetrics", {}),
        "topResults": _get_top_results(tuning_result.get("allMetrics", []), 15)
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"  JSON报告已保存: {output_path}")


def generate_markdown_report(tuning_result, output_path=None):
    """生成Markdown格式报告"""
    if output_path is None:
        output_path = config.OUTPUT_MD

    summary = tuning_result.get("summary", {})
    optimal = tuning_result.get("optimal", {})
    current = tuning_result.get("currentMetrics", {})
    all_metrics = tuning_result.get("allMetrics", [])

    lines = []
    lines.append("# 安全门控置信度双阈值调优报告")
    lines.append("")
    lines.append(f"**调优条目**: {summary.get('totalEntries', 0)}条 "
                 f"(安全{summary.get('safeEntries', 0)}条, 不安全{summary.get('unsafeEntries', 0)}条)")
    lines.append(f"**扫描组合**: {summary.get('thresholdCombinations', 0)}个")
    lines.append("")

    # 置信度分布
    lines.append("## 置信度分布")
    lines.append("")
    conf_dist = summary.get("confidenceDistribution", {})
    for label in ["safe", "unsafe"]:
        d = conf_dist.get(label, {})
        if d["count"] > 0:
            lines.append(f"- **{label}**: 均值={d['mean']:.3f}, "
                         f"中位数={d['median']:.3f}, "
                         f"范围=[{d['min']:.3f}, {d['max']:.3f}], "
                         f"数量={d['count']}")
    lines.append("")

    # 阈值对比
    lines.append("## 阈值对比")
    lines.append("")
    lines.append("| 指标 | 当前值 | 推荐值 | 变化 |")
    lines.append("|------|--------|--------|------|")
    cur_min = current.get('minThreshold', config.DEFAULT_MIN_THRESHOLD)
    cur_warn = current.get('warningThreshold', config.DEFAULT_WARNING_THRESHOLD)
    opt_min = optimal.get('minThreshold', 'N/A')
    opt_warn = optimal.get('warningThreshold', 'N/A')
    lines.append(f"| 最小阈值 | {cur_min:.2f} | {opt_min} | - |")
    lines.append(f"| 警告阈值 | {cur_warn:.2f} | {opt_warn} | - |")
    lines.append(f"| 综合安全分 | {current.get('safetyScore', 0):.4f} | "
                 f"{optimal.get('safetyScore', 0):.4f} | "
                 f"{summary.get('safetyScoreImprovement', 0):+.4f} |")
    lines.append(f"| 漏网率 | {current.get('falsePassRate', 0):.4f} | "
                 f"{optimal.get('falsePassRate', 0):.4f} | - |")
    lines.append(f"| 过度阻断率 | {current.get('falseBlockRate', 0):.4f} | "
                 f"{optimal.get('falseBlockRate', 0):.4f} | - |")
    lines.append(f"| 用户体验分 | {current.get('userExperienceScore', 0):.4f} | "
                 f"{optimal.get('userExperienceScore', 0):.4f} | - |")
    lines.append("")

    # Top N 结果
    lines.append("## Top 15 阈值组合")
    lines.append("")
    lines.append("| 最小阈值 | 警告阈值 | 安全分 | 漏网率 | 过度阻断 | 用户体验 |")
    lines.append("|----------|----------|--------|--------|----------|----------|")
    for m in _get_top_results(all_metrics, 15):
        lines.append(f"| {m['minThreshold']:.2f} | {m['warningThreshold']:.2f} | "
                     f"{m['safetyScore']:.4f} | {m['falsePassRate']:.4f} | "
                     f"{m['falseBlockRate']:.4f} | {m['userExperienceScore']:.4f} |")
    lines.append("")

    # 建议
    lines.append("## 配置建议")
    lines.append("")
    lines.append("修改 `application.yml` 中的以下配置：")
    lines.append("```yaml")
    lines.append("imkqas:")
    lines.append("  rag:")
    lines.append("    safety:")
    lines.append("      confidence:")
    lines.append(f"        min-threshold: {optimal.get('minThreshold', config.DEFAULT_MIN_THRESHOLD)}")
    lines.append(f"        warning-threshold: {optimal.get('warningThreshold', config.DEFAULT_WARNING_THRESHOLD)}")
    lines.append("```")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"  Markdown报告已保存: {output_path}")


def _get_top_results(all_metrics, n=15):
    """获取综合安全分最高的N个结果"""
    sorted_metrics = sorted(all_metrics, key=lambda m: m.get("safetyScore", 0), reverse=True)
    return sorted_metrics[:n]
