#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
可视化模块
生成4张子图：召回率、延迟、边际收益、效率比
"""

import os
import numpy as np
import matplotlib
try:
    matplotlib.use("TkAgg")
except Exception:
    matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

from . import config


# 中文字体设置
plt.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False


def plot_results(all_results: dict, optimal_k_values: dict,
                 output_path: str = None):
    """
    生成4张子图的综合分析图表。

    参数:
        all_results: {pathway: summarized_results}
        optimal_k_values: {pathway: optimal_k}
        output_path: 输出PNG路径
    """
    output = output_path or config.OUTPUT_PNG

    fig, axes = plt.subplots(2, 2, figsize=(16, 11))
    fig.suptitle("IMKQAS 双路召回最优 K 值分析", fontsize=16, fontweight="bold", y=1.01)

    colors = {"VECTOR": "#2196F3", "KEYWORD": "#FF9800", "HYBRID": "#4CAF50"}
    markers = {"VECTOR": "o", "KEYWORD": "s", "HYBRID": "^"}
    linestyles = {"VECTOR": "-", "KEYWORD": "--", "HYBRID": "-."}

    # ---- 子图1: K vs 召回率 ----
    ax1 = axes[0, 0]
    for pathway, result in all_results.items():
        k_vals = result["k_values"]
        recall = result["avg_recall"]
        c = colors.get(pathway, "#333")
        m = markers.get(pathway, "o")
        ls = linestyles.get(pathway, "-")
        opt_k = optimal_k_values.get(pathway, {}).get("optimal_k")
        label = f"{pathway}"
        if opt_k:
            label += f" (最优K={opt_k})"
        ax1.plot(k_vals, recall, color=c, marker=m, linestyle=ls,
                markersize=5, linewidth=1.8, label=label, alpha=0.85)
        if opt_k:
            idx = result["k_values"].index(opt_k) if opt_k in result["k_values"] else -1
            if idx >= 0:
                ax1.scatter([opt_k], [recall[idx]], color=c, s=100, zorder=5,
                           edgecolors="white", linewidth=1.5)

    ax1.axhline(y=config.RECALL_SLA_MIN * 100, color="red", linestyle=":",
                linewidth=1.2, alpha=0.7, label=f"召回率下限 ({config.RECALL_SLA_MIN*100:.0f}%)")
    ax1.set_xlabel("K")
    ax1.set_ylabel("加权召回率 (%)")
    ax1.set_title("K vs 加权召回率")
    ax1.legend(fontsize=8, loc="lower right")
    ax1.grid(True, alpha=0.3)
    ax1.set_xlim(left=0)

    # ---- 子图2: K vs P99延迟 ----
    ax2 = axes[0, 1]
    for pathway, result in all_results.items():
        k_vals = result["k_values"]
        lat_p99 = result["latency_p99"]
        lat_p50 = result["latency_p50"]
        c = colors.get(pathway, "#333")
        m = markers.get(pathway, "o")
        ax2.plot(k_vals, lat_p99, color=c, marker=m, linestyle="-",
                markersize=5, linewidth=1.8, label=f"{pathway} P99", alpha=0.85)
        ax2.plot(k_vals, lat_p50, color=c, marker=m, linestyle=":",
                markersize=3, linewidth=1.0, label=f"{pathway} P50", alpha=0.5)

    ax2.axhline(y=config.P99_LATENCY_SLA_MS, color="red", linestyle="--",
                linewidth=1.5, alpha=0.8, label=f"P99 SLA ({config.P99_LATENCY_SLA_MS:.0f}ms)")
    ax2.set_xlabel("K")
    ax2.set_ylabel("延迟 (ms)")
    ax2.set_title("K vs 延迟 (P50 / P99)")
    ax2.legend(fontsize=7, loc="upper left")
    ax2.grid(True, alpha=0.3)
    ax2.set_xlim(left=0)

    # ---- 子图3: K vs 边际收益 ----
    ax3 = axes[1, 0]
    bar_width = 8
    for pathway, result in all_results.items():
        k_vals = np.array(result["k_values"])
        mb = np.array(result["marginal_benefit"])
        c = colors.get(pathway, "#333")
        # 用不同透明度的柱状图区分
        color_list = [c if v >= config.MARGINAL_THRESHOLD else "#e74c3c" for v in mb]
        offset = {"VECTOR": -bar_width, "KEYWORD": 0, "HYBRID": bar_width}
        ax3.bar(k_vals + offset.get(pathway, 0), mb, width=bar_width,
               color=color_list, alpha=0.7, edgecolor="white", linewidth=0.3,
               label=pathway)

    ax3.axhline(y=config.MARGINAL_THRESHOLD, color="orange", linestyle="--",
                linewidth=1.5, label=f"拐点阈值 ({config.MARGINAL_THRESHOLD}pp)")
    ax3.axhline(y=config.SATURATION_THRESHOLD, color="red", linestyle=":",
                linewidth=1.2, label=f"饱和阈值 ({config.SATURATION_THRESHOLD}pp)")
    ax3.set_xlabel("K")
    ax3.set_ylabel("边际收益 (百分点)")
    ax3.set_title("K vs 边际收益")
    ax3.legend(fontsize=8, loc="upper right")
    ax3.grid(True, alpha=0.3, axis="y")
    ax3.set_xlim(left=0)

    # ---- 子图4: K vs 效率比 ----
    ax4 = axes[1, 1]
    for pathway, result in all_results.items():
        k_vals = result["k_values"]
        eff = result.get("efficiency_ratio", [0] * len(k_vals))
        c = colors.get(pathway, "#333")
        m = markers.get(pathway, "o")
        ax4.plot(k_vals, eff, color=c, marker=m, linestyle="-",
                markersize=5, linewidth=1.8, label=pathway, alpha=0.85)

        # 标注最高效率点
        if eff:
            best_idx = np.argmax(eff)
            ax4.scatter([k_vals[best_idx]], [eff[best_idx]], color=c, s=80,
                       zorder=5, edgecolors="white", linewidth=1.5)

    ax4.set_xlabel("K")
    ax4.set_ylabel("效率比 (召回率/延迟)")
    ax4.set_title("K vs 效率比 (召回率/P99延迟)")
    ax4.legend(fontsize=8, loc="upper right")
    ax4.grid(True, alpha=0.3)
    ax4.set_xlim(left=0)

    plt.tight_layout()
    plt.savefig(output, dpi=150, bbox_inches="tight")
    print(f"\n  图表已保存至: {output}")
    return fig
