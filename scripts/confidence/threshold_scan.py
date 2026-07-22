# -*- coding: utf-8 -*-
"""
LLM置信度阈值扫描 —分析

在500条测试集上，将阈值从0.1到0.9以0.05为步长遍历，
记录每个阈值下的"准确率"和"覆盖率"。

用法:
    python scripts/confidence/threshold_scan.py
    python scripts/confidence/threshold_scan.py --chart
"""

import json
import os
import sys
import argparse
import numpy as np

# 路径配置
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CACHE_FILE = os.path.join(SCRIPT_DIR, "confidence_tuning_cache", "llm_confidence_cache.json")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_results")

THRESHOLD_MIN = 0.10
THRESHOLD_MAX = 0.90
THRESHOLD_STEP = 0.05
# 扩展高精度扫描: 0.90-1.00, 步长0.01 (真正有区分度的区间)
HIGH_PRECISION_MIN = 0.90
HIGH_PRECISION_MAX = 1.00
HIGH_PRECISION_STEP = 0.01
SAMPLE_SIZE = 500


def load_data(cache_path):
    """加载LLM置信度缓存数据"""
    if not os.path.exists(cache_path):
        print(f"[ERROR] 缓存文件不存在: {cache_path}")
        print("请先运行 collect_real_confidence.py 采集数据")
        sys.exit(1)

    with open(cache_path, "r", encoding="utf-8") as f:
        cache = json.load(f)

    entries = list(cache.get("entries", {}).values())
    print(f"缓存总条目: {len(entries)}")

    # 筛选有权威标注的条目（有ground truth可判断正确性）
    authoritative = [e for e in entries if e.get("hasAuthoritativeMapping")]
    print(f"权威标注条目: {len(authoritative)}")

    # 取SAMPLE_SIZE条
    if len(authoritative) > SAMPLE_SIZE:
        import random
        random.seed(42)
        authoritative = random.sample(authoritative, SAMPLE_SIZE)
        print(f"随机采样: {SAMPLE_SIZE}条")

    return authoritative


def scan_thresholds_range(entries, t_min, t_max, t_step):
    """通用阈值扫描函数"""
    thresholds = np.arange(t_min, t_max + t_step / 2, t_step)
    results = []
    for t in thresholds:
        accepted = 0
        correct = 0
        for entry in entries:
            confidence = entry.get("llmConfidence", 0.0)
            is_correct = entry.get("isLlmCorrect", False)
            if confidence >= t:
                accepted += 1
                if is_correct:
                    correct += 1
        total = len(entries)
        accuracy = correct / accepted if accepted > 0 else 0.0
        coverage = accepted / total if total > 0 else 0.0
        results.append({
            "threshold": round(t, 3),
            "total": total,
            "accepted": accepted,
            "correct": correct,
            "incorrect": accepted - correct,
            "accuracy": round(accuracy, 4),
            "coverage": round(coverage, 4),
        })
    return results


def scan_thresholds(entries):
    """遍历阈值，计算每个阈值下的准确率和覆盖率"""
    return scan_thresholds_range(entries, THRESHOLD_MIN, THRESHOLD_MAX, THRESHOLD_STEP)


def print_table(results):
    """打印结果表格"""
    sep = "+" + "-" * 12 + "+" + "-" * 12 + "+" + "-" * 12 + "+" + "-" * 14 + "+"
    header = f"| {'阈值':^10s} | {'接受数':^10s} | {'准确率':^10s} | {'覆盖率':^12s} |"

    print("\n" + "=" * 56)
    print("  LLM置信度阈值扫描结果 (500条测试集)")
    print("=" * 56)
    print(f"\n总条目: {results[0]['total']}")
    print(f"阈值范围: {THRESHOLD_MIN} ~ {THRESHOLD_MAX}, 步长: {THRESHOLD_STEP}")
    print()
    print(sep)
    print(header)
    print(sep.replace("-", "="))

    for r in results:
        print(f"| {r['threshold']:^10.2f} | {r['accepted']:^10d} | {r['accuracy']:^10.4f} | {r['coverage']:^12.4f} |")
        print(sep)

    print()


def print_high_precision_table(results):
    """打印高精度扫描结果"""
    sep = "+" + "-" * 12 + "+" + "-" * 12 + "+" + "-" * 12 + "+" + "-" * 14 + "+"
    header = f"| {'阈值':^10s} | {'接受数':^10s} | {'准确率':^10s} | {'覆盖率':^12s} |"

    print("\n" + "=" * 72)
    print("  高精度扫描: LLM置信度阈值 0.90 ~ 1.00 (步长 0.01)")
    print("=" * 72)
    print()
    print("  【说明】低精度扫描(0.1-0.9)中所有数据置信度均在0.92以上，")
    print("         无区分度。真正有区分度的区间在0.92-1.00。")
    print()
    print(sep)
    print(header)
    print(sep.replace("-", "="))

    for r in results:
        # 高亮变化行
        marker = ""
        acc_pct = r['accuracy'] * 100
        cov_pct = r['coverage'] * 100
        print(f"| {r['threshold']:^10.2f} | {r['accepted']:^10d} | {acc_pct:^9.2f}% | {cov_pct:^11.2f}% |")
        print(sep)

    print()


def print_best(results):
    """输出关键观察"""
    print("=" * 56)
    print("  关键观察")
    print("=" * 56)

    # 最高准确率
    best_acc = max(results, key=lambda r: r["accuracy"])
    print(f"\n  最高准确率: {best_acc['accuracy']:.4f} (阈值={best_acc['threshold']:.2f}, 覆盖率={best_acc['coverage']:.4f})")

    # 覆盖率 >= 90% 时的最高准确率
    high_cov = [r for r in results if r["coverage"] >= 0.90]
    if high_cov:
        best_high_cov = max(high_cov, key=lambda r: r["accuracy"])
        print(f"  覆盖率>=90%时的最高准确率: {best_high_cov['accuracy']:.4f} (阈值={best_high_cov['threshold']:.2f})")

    # 覆盖率 >= 80% 时的最高准确率
    mid_cov = [r for r in results if r["coverage"] >= 0.80]
    if mid_cov:
        best_mid_cov = max(mid_cov, key=lambda r: r["accuracy"])
        print(f"  覆盖率>=80%时的最高准确率: {best_mid_cov['accuracy']:.4f} (阈值={best_mid_cov['threshold']:.2f})")

    # 当前系统阈值对比
    current_thresholds = [0.35, 0.60, 0.85]
    print(f"\n  系统当前阈值对比:")
    for ct in current_thresholds:
        closest = min(results, key=lambda r: abs(r["threshold"] - ct))
        print(f"    阈值={ct:.2f}: 准确率={closest['accuracy']:.4f}, 覆盖率={closest['coverage']:.4f}")

    print()


def plot_results(results, output_path):
    """生成准确率-覆盖率关系图"""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        plt.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei", "DejaVu Sans"]
        plt.rcParams["axes.unicode_minus"] = False

        thresholds = [r["threshold"] for r in results]
        accuracies = [r["accuracy"] for r in results]
        coverages = [r["coverage"] for r in results]

        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5.5))

        # 子图1: 阈值 vs 准确率/覆盖率
        ax1.plot(thresholds, accuracies, "b-", linewidth=2, marker="o", markersize=6, label="准确率 (Precision)")
        ax1.plot(thresholds, coverages, "r-", linewidth=2, marker="s", markersize=6, label="覆盖率 (Coverage)")

        # 标注系统当前阈值
        for t, label, color in [(0.35, "minThreshold", "orange"), (0.60, "warningThreshold", "green"), (0.85, "llmMinConfidence", "purple")]:
            ax1.axvline(x=t, color=color, linestyle="--", alpha=0.6)
            ax1.annotate(f"{label}\n({t})", xy=(t, 1.02), xycoords=("data", "axes fraction"),
                         ha="center", fontsize=8, color=color)

        ax1.set_xlabel("置信度阈值")
        ax1.set_ylabel("比例")
        ax1.set_title("阈值 vs 准确率 & 覆盖率")
        ax1.legend(loc="center left", fontsize=9)
        ax1.grid(True, alpha=0.3)
        ax1.set_xlim(0.05, 0.95)
        ax1.set_ylim(0.0, 1.05)

        # 子图2: 覆盖率 vs 准确率 (P-R曲线风格)
        ax2.plot(coverages, accuracies, "g-", linewidth=2, marker="D", markersize=6)
        for i, r in enumerate(results):
            if i % 3 == 0:
                ax2.annotate(f"t={r['threshold']:.2f}", xy=(r["coverage"], r["accuracy"]),
                             textcoords="offset points", xytext=(8, -4), fontsize=7, alpha=0.7)

        ax2.set_xlabel("覆盖率 (Coverage)")
        ax2.set_ylabel("准确率 (Accuracy)")
        ax2.set_title("覆盖率 vs 准确率 (阈值递减从左到右)")
        ax2.grid(True, alpha=0.3)
        ax2.set_xlim(-0.02, 1.02)
        ax2.set_ylim(-0.02, 1.02)

        # 对角线参考
        ax2.plot([0, 1], [0, 1], "k--", alpha=0.15, linewidth=0.8)

        plt.tight_layout()
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        plt.savefig(output_path, dpi=150, bbox_inches="tight")
        plt.close()
        print(f"图表已保存: {output_path}")

    except ImportError:
        print("[WARN] matplotlib未安装，跳过图表生成。请安装: pip install matplotlib")
    except Exception as e:
        print(f"[WARN] 图表生成失败: {e}")


def plot_high_precision_chart(results, output_path):
    """生成高精度区间的准确率-覆盖率图表"""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        plt.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei", "DejaVu Sans"]
        plt.rcParams["axes.unicode_minus"] = False

        thresholds = [r["threshold"] for r in results]
        accuracies = [r["accuracy"] for r in results]
        coverages = [r["coverage"] for r in results]

        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5.5))

        # 子图1: 阈值 vs 准确率/覆盖率
        ax1.plot(thresholds, accuracies, "b-", linewidth=2, marker="o", markersize=6, label="准确率 (Precision)")
        ax1.plot(thresholds, coverages, "r-", linewidth=2, marker="s", markersize=6, label="覆盖率 (Coverage)")

        # 标注系统当前阈值
        for t, label, color in [(0.85, "当前llmMinConfidence", "purple"), (0.92, "上次推荐阈值", "green")]:
            ax1.axvline(x=t, color=color, linestyle="--", alpha=0.5)

        # 找准确率拐点：覆盖率首次下降时的阈值
        acc_labels = []
        for i, r in enumerate(results):
            if i > 0 and results[i - 1]["accuracy"] != r["accuracy"]:
                acc_labels.append(r)
        for r in acc_labels[:3]:
            ax1.annotate(f"{r['accuracy']:.3f}", xy=(r["threshold"], r["accuracy"]),
                         textcoords="offset points", xytext=(0, 12), fontsize=8, color="blue")

        ax1.set_xlabel("置信度阈值")
        ax1.set_ylabel("比例")
        ax1.set_title("阈值 vs 准确率 & 覆盖率 (高精度区间)")
        ax1.legend(loc="center left", fontsize=9)
        ax1.grid(True, alpha=0.3)
        ax1.set_xlim(0.89, 1.01)
        ax1.set_ylim(-0.02, 1.05)

        # 子图2: 覆盖率 vs 准确率
        ax2.plot(coverages, accuracies, "g-", linewidth=2, marker="D", markersize=6)
        for i, r in enumerate(results):
            if i % 2 == 0:
                ax2.annotate(f"t={r['threshold']:.2f}", xy=(r["coverage"], r["accuracy"]),
                             textcoords="offset points", xytext=(8, -4), fontsize=7, alpha=0.7)

        ax2.set_xlabel("覆盖率 (Coverage)")
        ax2.set_ylabel("准确率 (Accuracy)")
        ax2.set_title("覆盖率 vs 准确率 (阈值递减从左到右)")
        ax2.grid(True, alpha=0.3)
        ax2.set_xlim(-0.02, 1.02)
        ax2.set_ylim(-0.02, 1.02)
        ax2.plot([0, 1], [0, 1], "k--", alpha=0.15, linewidth=0.8)

        plt.tight_layout()
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        plt.savefig(output_path, dpi=150, bbox_inches="tight")
        plt.close()
        print(f"高精度图表已保存: {output_path}")

    except ImportError:
        print("[WARN] matplotlib未安装，跳过图表生成")
    except Exception as e:
        print(f"[WARN] 图表生成失败: {e}")


def save_json(results, output_path):
    """保存结果JSON"""
    output = {
        "description": "LLM置信度阈值扫描 — 准确率 vs 覆盖率",
        "sampleSize": SAMPLE_SIZE,
        "thresholdRange": [THRESHOLD_MIN, THRESHOLD_MAX, THRESHOLD_STEP],
        "results": results,
    }
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"结果已保存: {output_path}")


def main():
    parser = argparse.ArgumentParser(description="LLM置信度阈值扫描 — 准确率 vs 覆盖率")
    parser.add_argument("--chart", action="store_true", help="生成可视化图表")
    parser.add_argument("--json", action="store_true", help="保存结果JSON")
    parser.add_argument("--cache", default=None,
                       help="缓存文件路径（默认: confidence_tuning_cache/llm_confidence_cache.json）")
    args = parser.parse_args()

    # 确定缓存文件路径
    cache_path = args.cache if args.cache else CACHE_FILE

    # 加载数据
    entries = load_data(cache_path)
    if len(entries) == 0:
        print("[ERROR] 无有效数据")
        sys.exit(1)

    print(f"分析数据集: {len(entries)}条")

    # 置信度分布概览
    confidences = [e.get("llmConfidence", 0.0) for e in entries]
    print(f"置信度范围: [{min(confidences):.4f}, {max(confidences):.4f}]")
    print(f"置信度均值: {np.mean(confidences):.4f}")
    print(f"置信度中位数: {np.median(confidences):.4f}")

    # 扫描阈值
    results = scan_thresholds(entries)
    # 高精度扫描 (0.90-1.00)
    high_precision_results = scan_thresholds_range(entries, HIGH_PRECISION_MIN, HIGH_PRECISION_MAX, HIGH_PRECISION_STEP)

    # 输出结果
    print_table(results)
    print_high_precision_table(high_precision_results)
    print_best(high_precision_results)

    # 可选输出
    if args.json:
        json_path = os.path.join(OUTPUT_DIR, "threshold_scan_results.json")
        save_json(results, json_path)
        hp_json_path = os.path.join(OUTPUT_DIR, "threshold_scan_high_precision.json")
        save_json(high_precision_results, hp_json_path)

    if args.chart:
        chart_path = os.path.join(OUTPUT_DIR, "threshold_scan_chart.png")
        plot_results(results, chart_path)
        hp_chart_path = os.path.join(OUTPUT_DIR, "threshold_scan_chart_high_precision.png")
        plot_high_precision_chart(high_precision_results, hp_chart_path)


if __name__ == "__main__":
    main()
