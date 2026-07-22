# -*- coding: utf-8 -*-
"""
可视化工具 — 安全门控双阈值调优图表生成
"""

import os
from . import config


def plot_tuning_results(tuning_result, output_path=None):
    """
    生成调优结果可视化图表。

    包含三个子图：
    1. 安全分数热力图（minThreshold x warningThreshold）
    2. 漏网率热力图
    3. 用户体验分数热力图
    """
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        import matplotlib.font_manager as fm
        import numpy as np

        # 查找支持中文的字体（Windows / Linux 兼容）
        _cjk_candidates = ["Microsoft YaHei", "SimHei", "SimSun", "Noto Sans CJK SC", "WenQuanYi Micro Hei"]
        _available = {f.name for f in fm.fontManager.ttflist}
        _cjk_font = next((c for c in _cjk_candidates if c in _available), None)
        if _cjk_font:
            plt.rcParams["font.family"] = _cjk_font
        plt.rcParams["axes.unicode_minus"] = False
    except ImportError:
        print("  [WARN] matplotlib未安装，跳过图表生成")
        return

    if output_path is None:
        output_path = config.OUTPUT_PNG

    all_metrics = tuning_result.get("allMetrics", [])
    if not all_metrics:
        print("  [WARN] 无指标数据，跳过图表生成")
        return

    optimal = tuning_result.get("optimal", {})
    current = tuning_result.get("currentMetrics", {})

    # 提取数据
    min_vals = sorted(set(m["minThreshold"] for m in all_metrics))
    warn_vals = sorted(set(m["warningThreshold"] for m in all_metrics))

    # 构建网格
    safety_grid = np.full((len(min_vals), len(warn_vals)), np.nan)
    false_pass_grid = np.full((len(min_vals), len(warn_vals)), np.nan)
    ux_grid = np.full((len(min_vals), len(warn_vals)), np.nan)

    min_to_idx = {v: i for i, v in enumerate(min_vals)}
    warn_to_idx = {v: i for i, v in enumerate(warn_vals)}

    for m in all_metrics:
        mi = min_to_idx[m["minThreshold"]]
        wi = warn_to_idx[m["warningThreshold"]]
        safety_grid[mi, wi] = m["safetyScore"]
        false_pass_grid[mi, wi] = m["falsePassRate"]
        ux_grid[mi, wi] = m["userExperienceScore"]

    # 创建图形
    fig, axes = plt.subplots(1, 3, figsize=(18, 5.5))
    fig.suptitle("安全门控双阈值调优 — 热力图分析", fontsize=14, fontweight="bold")

    # 子图1: 综合安全分数
    _plot_heatmap(axes[0], safety_grid, min_vals, warn_vals,
                  "综合安全分数", "YlGn", optimal, current, higher_better=True)

    # 子图2: 漏网率
    _plot_heatmap(axes[1], false_pass_grid, min_vals, warn_vals,
                  "漏网率 (不安全但通过)", "YlOrRd_r", optimal, current, higher_better=False)

    # 子图3: 用户体验分
    _plot_heatmap(axes[2], ux_grid, min_vals, warn_vals,
                  "用户体验分", "Blues", optimal, current, higher_better=True)

    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  图表已保存: {output_path}")


def _plot_heatmap(ax, grid, x_vals, y_vals, title, cmap, optimal, current, higher_better=True):
    """绘制单张热力图"""
    import matplotlib.pyplot as plt
    import numpy as np

    masked = np.ma.masked_invalid(grid)

    im = ax.imshow(masked, aspect="auto", origin="lower", cmap=cmap,
                   extent=[min(y_vals), max(y_vals), min(x_vals), max(x_vals)])

    # 标记最优
    if optimal:
        opt_min = optimal.get("minThreshold")
        opt_warn = optimal.get("warningThreshold")
        if opt_min is not None and opt_warn is not None:
            ax.plot(opt_warn, opt_min, "r*", markersize=15, markeredgecolor="white",
                    markeredgewidth=1.5, label="推荐值")
            ax.annotate(f"  ({opt_min:.2f}, {opt_warn:.2f})",
                        (opt_warn, opt_min),
                        xytext=(5, 5), textcoords="offset points",
                        fontsize=8, color="red", fontweight="bold")

    # 标记当前
    if current:
        cur_min = current.get("minThreshold")
        cur_warn = current.get("warningThreshold")
        if cur_min is not None and cur_warn is not None:
            ax.plot(cur_warn, cur_min, "ko", markersize=10, markerfacecolor="none",
                    markeredgewidth=2, label="当前值")

    ax.set_xlabel("警告阈值 (warningThreshold)")
    ax.set_ylabel("阻断阈值 (minThreshold)")
    ax.set_title(title)

    if optimal or current:
        ax.legend(fontsize=7, loc="lower right")

    plt.colorbar(im, ax=ax, shrink=0.85)


def plot_confidence_distribution(entries, output_dir=None):
    """
    绘制安全/不安全条目的置信度分布直方图。
    """
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        return

    if output_dir is None:
        output_dir = config.OUTPUT_DIR

    safe_confs = [e["rawConfidence"] for e in entries if e.get("isSafe")]
    unsafe_confs = [e["rawConfidence"] for e in entries if not e.get("isSafe")]

    fig, ax = plt.subplots(figsize=(10, 5))

    bins = [i / 20 for i in range(21)]  # 0.0, 0.05, ..., 1.0

    if safe_confs:
        ax.hist(safe_confs, bins=bins, alpha=0.6, label=f"安全回答 (n={len(safe_confs)})",
                color="green", edgecolor="white")
    if unsafe_confs:
        ax.hist(unsafe_confs, bins=bins, alpha=0.6, label=f"不安全回答 (n={len(unsafe_confs)})",
                color="red", edgecolor="white")

    ax.axvline(x=config.DEFAULT_MIN_THRESHOLD, color="orange", linestyle="--",
               label=f"当前minThreshold={config.DEFAULT_MIN_THRESHOLD}")
    ax.axvline(x=config.DEFAULT_WARNING_THRESHOLD, color="blue", linestyle="--",
               label=f"当前warningThreshold={config.DEFAULT_WARNING_THRESHOLD}")

    ax.set_xlabel("LLM原始置信度 (rawConfidence)")
    ax.set_ylabel("频次")
    ax.set_title("安全 vs 不安全回答的置信度分布")
    ax.legend(fontsize=8)

    output_path = os.path.join(output_dir, "confidence_distribution.png")
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  分布图已保存: {output_path}")
