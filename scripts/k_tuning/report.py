#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
报告生成模块
控制台格式化报告 + Markdown报告文件
"""

import os
import json
import time
from datetime import datetime

from . import config


def print_console_report(all_results: dict, optimal: dict,
                         stress_results: dict, inflection: dict):
    """输出格式化的控制台报告"""
    print("\n" + "=" * 80)
    print("  IMKQAS 双路召回最优 K 值调优测试报告")
    print("=" * 80)

    # 测试配置
    print(f"\n  ▶ 测试配置")
    print(f"    服务器: {config.BASE_URL}")
    print(f"    K值范围: {config.K_VALUES[0]}~{config.K_VALUES[-1]} "
          f"(共{len(config.K_VALUES)}个采样点)")
    print(f"    GT构建: pool_k={config.GT_POOL_K}, top_n={config.GT_TOP_N}")
    print(f"    约束条件: 召回率≥{config.RECALL_SLA_MIN*100:.0f}%, "
          f"P99延迟<{config.P99_LATENCY_SLA_MS:.0f}ms")

    # 关键指标明细表
    report_k = [5, 10, 20, 30, 50, 80, 120, 200, 350, 500]

    for pathway, result in all_results.items():
        print(f"\n  ── {pathway} 通路 ──")
        header = (f"  {'K':>6s} | {'召回率%':>8s} | {'精确率%':>8s} | "
                  f"{'P50(ms)':>8s} | {'P99(ms)':>8s} | {'实际数':>7s} | {'边际pp':>7s}")
        print(header)
        print("  " + "-" * (len(header) - 2))

        k_vals = result["k_values"]
        shown_indices = set()
        for rk in report_k:
            if rk in k_vals:
                idx = k_vals.index(rk)
            else:
                idx = min(range(len(k_vals)), key=lambda i: abs(k_vals[i] - rk))

            if idx in shown_indices:
                continue
            shown_indices.add(idx)

            marker = ""
            opt_k = optimal.get(pathway, {}).get("optimal_k")
            inf_k = inflection.get(pathway, {}).get("inflection_k")
            if k_vals[idx] == opt_k:
                marker = " ◀ 最优"
            elif k_vals[idx] == inf_k:
                marker = " ◀ 拐点"

            print(f"  {k_vals[idx]:>6d} | "
                  f"{result['avg_recall'][idx]:>7.2f} | "
                  f"{result['avg_precision'][idx]:>7.2f} | "
                  f"{result['latency_p50'][idx]:>7.1f} | "
                  f"{result['latency_p99'][idx]:>7.1f} | "
                  f"{result['actual_count'][idx]:>6.0f} | "
                  f"{result['marginal_benefit'][idx]:>6.2f}{marker}")

    # 最优K值汇总
    print(f"\n  ▶ 最优K值推荐")
    for pathway in ["VECTOR", "KEYWORD", "HYBRID"]:
        opt = optimal.get(pathway, {})
        inf = inflection.get(pathway, {})
        stress = stress_results.get(pathway, {})
        print(f"    {pathway}:")
        print(f"      最优K = {opt.get('optimal_k', 'N/A')} "
              f"(拐点K = {inf.get('inflection_k', 'N/A')}, "
              f"饱和K = {inf.get('saturation_k', 'N/A')})")
        print(f"      召回率 = {opt.get('recall_at_optimal', 0):.1f}%, "
              f"P99延迟 = {opt.get('latency_p99_at_optimal', 0):.1f}ms")
        if stress:
            print(f"      压测P99 = {stress.get('p99_ms', 0):.1f}ms, "
                  f"QPS = {stress.get('throughput_qps', 0)}, "
                  f"SLA: {'✓' if stress.get('sla_pass') else '✗'}")

    # 最终推荐
    print(f"\n  ▶ 生产配置推荐")
    vec_opt = optimal.get("VECTOR", {}).get("optimal_k", 30)
    kw_opt = optimal.get("KEYWORD", {}).get("optimal_k", 30)
    hyb_opt = optimal.get("HYBRID", {}).get("optimal_k", 30)

    print(f"    imkqas.rag.retrieval.initial-top-k: {hyb_opt}")
    print(f"    imkqas.rag.retrieval.rerank-top-k: {min(hyb_opt // 2, 10)}")
    print(f"    推荐理由: 混合通路在K={hyb_opt}时达到最优平衡")

    print("\n" + "=" * 80)


def generate_markdown_report(all_results: dict, optimal: dict,
                              stress_results: dict, inflection: dict,
                              output_path: str = None) -> str:
    """生成Markdown格式报告"""
    output = output_path or config.OUTPUT_MD
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    lines = [
        f"# IMKQAS 双路召回 K 值调优报告",
        f"",
        f"**生成时间**: {now}",
        f"**服务器**: {config.BASE_URL}",
        f"**K值范围**: {config.K_VALUES[0]}~{config.K_VALUES[-1]} ({len(config.K_VALUES)}个采样点)",
        f"**约束条件**: 召回率≥{config.RECALL_SLA_MIN*100:.0f}%, P99延迟<{config.P99_LATENCY_SLA_MS:.0f}ms",
        f"",
        f"## 最优K值推荐",
        f"",
        f"| 通路 | 最优K | 拐点K | 饱和K | 召回率 | P99延迟 | SLA |",
        f"|------|-------|-------|-------|--------|---------|-----|",
    ]

    for pathway in ["VECTOR", "KEYWORD", "HYBRID"]:
        opt = optimal.get(pathway, {})
        inf = inflection.get(pathway, {})
        stress = stress_results.get(pathway, {})
        sla = "✓" if stress.get("sla_pass") else "✗"
        lines.append(
            f"| {pathway} | {opt.get('optimal_k', 'N/A')} | "
            f"{inf.get('inflection_k', 'N/A')} | {inf.get('saturation_k', 'N/A')} | "
            f"{opt.get('recall_at_optimal', 0):.1f}% | "
            f"{opt.get('latency_p99_at_optimal', 0):.1f}ms | {sla} |"
        )

    # 详细指标表
    for pathway in ["VECTOR", "KEYWORD", "HYBRID"]:
        result = all_results.get(pathway, {})
        if not result:
            continue

        lines.extend([
            f"",
            f"## {pathway} 通路详细指标",
            f"",
            f"| K | 召回率% | 精确率% | P50(ms) | P99(ms) | 实际数 | 边际收益pp |",
            f"|---|---------|---------|---------|---------|--------|-----------|",
        ])

        k_vals = result.get("k_values", [])
        for i, k in enumerate(k_vals):
            opt_k = optimal.get(pathway, {}).get("optimal_k")
            inf_k = inflection.get(pathway, {}).get("inflection_k")
            note = ""
            if k == opt_k:
                note = " **← 最优**"
            elif k == inf_k:
                note = " ← 拐点"
            lines.append(
                f"| {k} | {result['avg_recall'][i]:.2f} | "
                f"{result['avg_precision'][i]:.2f} | "
                f"{result['latency_p50'][i]:.1f} | "
                f"{result['latency_p99'][i]:.1f} | "
                f"{result['actual_count'][i]:.0f} | "
                f"{result['marginal_benefit'][i]:.2f}{note} |"
            )

    # 压测结果
    lines.extend([
        f"",
        f"## 压力测试结果",
        f"",
        f"| 通路 | K | P50 | P95 | P99 | P999 | QPS | 成功率 | SLA |",
        f"|------|---|---|-----|-----|------|-----|--------|-----|",
    ])
    for pathway in ["VECTOR", "KEYWORD", "HYBRID"]:
        s = stress_results.get(pathway, {})
        if s:
            sla = "✓" if s.get("sla_pass") else "✗"
            lines.append(
                f"| {pathway} | {s.get('k', 'N/A')} | {s.get('p50_ms', 0)}ms | "
                f"{s.get('p95_ms', 0)}ms | {s.get('p99_ms', 0)}ms | "
                f"{s.get('p999_ms', 0)}ms | {s.get('throughput_qps', 0)} | "
                f"{s.get('success_rate', 0)*100:.0f}% | {sla} |"
            )

    # 配置建议
    hyb_opt = optimal.get("HYBRID", {}).get("optimal_k", 30)
    lines.extend([
        f"",
        f"## 生产配置建议",
        f"",
        f"```yaml",
        f"imkqas:",
        f"  rag:",
        f"    retrieval:",
        f"      initial-top-k: {hyb_opt}",
        f"      rerank-top-k: {min(hyb_opt // 2, 10)}",
        f"```",
        f"",
        f"![K值调优图表](k_tuning_report.png)",
    ])

    content = "\n".join(lines)
    with open(output, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"  Markdown报告已保存至: {output}")
    return content


def save_results_json(all_results: dict, optimal: dict,
                      stress_results: dict, inflection: dict,
                      output_path: str = None):
    """保存原始结果为JSON"""
    output = output_path or config.OUTPUT_JSON

    def convert_sets(obj):
        if isinstance(obj, set):
            return list(obj)
        if isinstance(obj, dict):
            return {k: convert_sets(v) for k, v in obj.items()}
        if isinstance(obj, list):
            return [convert_sets(i) for i in obj]
        return obj

    data = {
        "timestamp": datetime.now().isoformat(),
        "config": {
            "base_url": config.BASE_URL,
            "k_values": config.K_VALUES,
            "gt_pool_k": config.GT_POOL_K,
            "gt_top_n": config.GT_TOP_N,
            "recall_sla_min": config.RECALL_SLA_MIN,
            "p99_latency_sla_ms": config.P99_LATENCY_SLA_MS,
        },
        "results": convert_sets(all_results),
        "optimal": optimal,
        "inflection": inflection,
        "stress": stress_results,
    }

    with open(output, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, default=str)

    print(f"  JSON结果已保存至: {output}")
