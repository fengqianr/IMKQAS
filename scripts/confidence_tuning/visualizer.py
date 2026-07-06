#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
可视化 — 生成阈值调优相关图表
"""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np
import os

# 中文字体设置
plt.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei", "WenQuanYi Micro Hei", "Noto Sans CJK SC", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False


def plot_tuning_results(tuning_result, output_path):
    """
    生成4合1图表：
    1. 阈值 vs F1/Precision/Recall
    2. 阈值 vs 采纳率/成本效率
    3. 置信度校准曲线
    4. 混淆矩阵（最优阈值处）
    """
    all_metrics = tuning_result.get("allMetrics", [])
    optimal = tuning_result.get("optimal", {})
    calibration = tuning_result.get("calibration", [])
    summary = tuning_result.get("summary", {})

    if not all_metrics:
        print("  [WARN] 无指标数据，跳过图表生成")
        return

    fig, axes = plt.subplots(2, 2, figsize=(14, 11))

    thresholds = [m["threshold"] for m in all_metrics]
    f1s = [m["f1"] for m in all_metrics]
    precisions = [m["precision"] for m in all_metrics]
    recalls = [m["recall"] for m in all_metrics]
    accept_rates = [m["acceptanceRate"] for m in all_metrics]
    cost_effs = [m["costEfficiency"] for m in all_metrics]

    # ---- 子图1: F1/Precision/Recall vs Threshold ----
    ax1 = axes[0, 0]
    ax1.plot(thresholds, f1s, "b-", linewidth=2, label="F1", marker="o", markersize=4)
    ax1.plot(thresholds, precisions, "g--", linewidth=1.5, label="Precision")
    ax1.plot(thresholds, recalls, "r--", linewidth=1.5, label="Recall")

    if optimal:
        opt_t = optimal.get("threshold")
        opt_f1 = optimal.get("f1")
        ax1.axvline(x=opt_t, color="blue", linestyle=":", alpha=0.6)
        ax1.plot(opt_t, opt_f1, "b*", markersize=12, label=f"最优 t={opt_t:.2f}")

    current_t = summary.get("currentThreshold", 0.85)
    ax1.axvline(x=current_t, color="gray", linestyle="--", alpha=0.5, label=f"当前 t={current_t}")

    ax1.set_xlabel("置信度阈值")
    ax1.set_ylabel("分数")
    ax1.set_title("阈值 vs F1/Precision/Recall")
    ax1.legend(loc="lower left", fontsize=8)
    ax1.grid(True, alpha=0.3)
    ax1.set_xlim(0.48, 1.0)
    ax1.set_ylim(0.0, 1.05)

    # ---- 子图2: 采纳率 & 成本效率 ----
    ax2 = axes[0, 1]
    ax2.plot(thresholds, accept_rates, "b-", linewidth=2, label="采纳率", marker="s", markersize=4)
    ax2_twin = ax2.twinx()
    ax2_twin.plot(thresholds, cost_effs, "orange", linewidth=1.5, label="成本效率", marker="^", markersize=4)

    if optimal:
        opt_t = optimal.get("threshold")
        ax2.axvline(x=opt_t, color="blue", linestyle=":", alpha=0.6)

    ax2.set_xlabel("置信度阈值")
    ax2.set_ylabel("采纳率", color="b")
    ax2_twin.set_ylabel("成本效率 (F1/采纳率)", color="orange")
    ax2.set_title("阈值 vs 采纳率 & 成本效率")
    ax2.grid(True, alpha=0.3)
    ax2.set_xlim(0.48, 1.0)
    ax2.set_ylim(0.0, 1.05)
    ax2.tick_params(axis="y", labelcolor="b")
    ax2_twin.tick_params(axis="y", labelcolor="orange")

    # 合并图例
    lines1, labels1 = ax2.get_legend_handles_labels()
    lines2, labels2 = ax2_twin.get_legend_handles_labels()
    ax2.legend(lines1 + lines2, labels1 + labels2, loc="upper right", fontsize=8)

    # ---- 子图3: 置信度校准曲线 ----
    ax3 = axes[1, 0]
    if calibration:
        bin_mids = [c["binMid"] for c in calibration]
        accuracies = [c["accuracy"] for c in calibration]
        counts = [c["count"] for c in calibration]
        bin_width = 0.08

        # 气泡图（面积代表样本数）
        sizes = [max(30, c * 3) for c in counts]
        ax3.scatter(bin_mids, accuracies, s=sizes, alpha=0.6, color="steelblue", edgecolors="navy")

        # 理想校准线
        ax3.plot([0, 1], [0, 1], "k--", alpha=0.4, linewidth=1, label="理想校准")

        # 回归线
        if len(bin_mids) > 1:
            coeffs = np.polyfit(bin_mids, accuracies, 1)
            fit_line = np.poly1d(coeffs)
            x_fit = np.linspace(0, 1, 100)
            ax3.plot(x_fit, fit_line(x_fit), "r-", alpha=0.5, linewidth=1, label="拟合趋势")

        ax3.set_xlabel("LLM置信度")
        ax3.set_ylabel("实际准确率")
        ax3.set_title("置信度校准 (气泡大小=样本数)")
        ax3.legend(loc="upper left", fontsize=8)
    else:
        ax3.text(0.5, 0.5, "无校准数据", ha="center", va="center", transform=ax3.transAxes)
        ax3.set_title("置信度校准")

    ax3.grid(True, alpha=0.3)
    ax3.set_xlim(-0.02, 1.02)
    ax3.set_ylim(-0.02, 1.02)

    # ---- 子图4: 最优阈值的混淆矩阵 ----
    ax4 = axes[1, 1]
    if optimal:
        cm = np.array([
            [optimal.get("tp", 0), optimal.get("fp", 0)],
            [optimal.get("fn", 0), optimal.get("tn", 0)]
        ])
        im = ax4.imshow(cm, cmap="Blues", alpha=0.8)

        labels = [["TP\n(采纳且正确)", "FP\n(采纳但错误)"],
                  ["FN\n(拒绝但正确)", "TN\n(拒绝且错误)"]]
        for i in range(2):
            for j in range(2):
                ax4.text(j, i, f"{labels[i][j]}\n{cm[i, j]}",
                         ha="center", va="center", fontsize=10)

        ax4.set_xticks([0, 1])
        ax4.set_xticklabels(["LLM正确", "LLM错误"])
        ax4.set_yticks([0, 1])
        ax4.set_yticklabels(["采纳(>=阈值)", "拒绝(<阈值)"])
        ax4.set_title(f"混淆矩阵 (阈值={optimal.get('threshold', 0):.2f})")
    else:
        ax4.text(0.5, 0.5, "无最优阈值数据", ha="center", va="center", transform=ax4.transAxes)
        ax4.set_title("混淆矩阵")

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  图表已保存: {output_path}")
