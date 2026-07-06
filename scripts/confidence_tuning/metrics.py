#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
评估指标计算器 — 同义词扩展专用指标
"""

import math


def compute_threshold_metrics(entries, threshold):
    """
    对给定阈值计算二分类指标。

    仅使用 hasAuthoritativeMapping=True 的条目。

    返回: {precision, recall, f1, accuracy, tp, fp, fn, tn, acceptanceRate}
    """
    tp = fp = fn = tn = 0

    for entry in entries:
        if not entry.get("hasAuthoritativeMapping"):
            continue

        confidence = entry.get("llmConfidence", 0.0)
        is_correct = entry.get("isLlmCorrect", False)
        accepted = confidence >= threshold

        if accepted and is_correct:
            tp += 1
        elif accepted and not is_correct:
            fp += 1
        elif not accepted and is_correct:
            fn += 1
        else:
            tn += 1

    total = tp + fp + fn + tn
    accepted_total = tp + fp

    precision = tp / accepted_total if accepted_total > 0 else 1.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
    accuracy = (tp + tn) / total if total > 0 else 0.0
    acceptance_rate = accepted_total / total if total > 0 else 0.0

    # 拒绝正确率：拒绝的条目中有多少确实是错误的
    rejected_total = fn + tn
    rejection_correctness = tn / rejected_total if rejected_total > 0 else 0.0

    return {
        "threshold": round(threshold, 3),
        "total": total,
        "tp": tp, "fp": fp, "fn": fn, "tn": tn,
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "accuracy": round(accuracy, 4),
        "acceptanceRate": round(acceptance_rate, 4),
        "rejectionCorrectness": round(rejection_correctness, 4)
    }


def compute_cost_efficiency(metrics_list):
    """
    成本效率 = F1 / acceptance_rate
    越高表示用更少的LLM调用获得更好的效果
    """
    for m in metrics_list:
        ar = m["acceptanceRate"]
        m["costEfficiency"] = round(m["f1"] / ar, 4) if ar > 0 else 0.0
    return metrics_list


def find_optimal_threshold(metrics_list, objective="f1", constraint=None):
    """
    找到最优阈值。

    objective: "f1" | "precision_min_recall" | "balanced"
    constraint: {"minPrecision": 0.9} 等约束条件
    """
    if not metrics_list:
        return None

    if constraint:
        min_precision = constraint.get("minPrecision", 0.0)
        min_recall = constraint.get("minRecall", 0.0)
        valid = [m for m in metrics_list
                 if m["precision"] >= min_precision and m["recall"] >= min_recall]
    else:
        valid = metrics_list

    if not valid:
        valid = metrics_list

    if objective == "f1":
        return max(valid, key=lambda m: m["f1"])
    elif objective == "precision_min_recall":
        return max(valid, key=lambda m: (m["precision"] + m["recall"]) / 2)
    elif objective == "balanced":
        return max(valid, key=lambda m: m["costEfficiency"])
    else:
        return max(valid, key=lambda m: m["f1"])


def find_saturation_point(metrics_list, marginal_threshold=0.5):
    """
    找到边际F1增益低于阈值的拐点。
    从低阈值往高阈值方向扫描，找到F1增益首次低于marginal_threshold的点。
    """
    sorted_metrics = sorted(metrics_list, key=lambda m: m["threshold"])
    best_f1 = 0.0
    for m in sorted_metrics:
        gain = m["f1"] - best_f1
        if gain < (marginal_threshold / 100.0) and m["f1"] > 0.5:
            return m
        if m["f1"] > best_f1:
            best_f1 = m["f1"]
    return sorted_metrics[-1] if sorted_metrics else None


def compute_confidence_calibration(entries, num_bins=10):
    """
    置信度校准分析：将置信度分为N个区间，比较每个区间的平均置信度和实际准确率。
    返回每个bin的 {binMid, avgConfidence, accuracy, count}
    """
    authoritative = [e for e in entries if e.get("hasAuthoritativeMapping")]
    if not authoritative:
        return []

    bin_width = 1.0 / num_bins
    bins = [[] for _ in range(num_bins)]

    for entry in authoritative:
        conf = entry.get("llmConfidence", 0.0)
        bin_idx = min(int(conf / bin_width), num_bins - 1)
        bins[bin_idx].append(entry)

    calibration = []
    for i, bin_entries in enumerate(bins):
        if not bin_entries:
            continue
        avg_conf = sum(e.get("llmConfidence", 0) for e in bin_entries) / len(bin_entries)
        accuracy = sum(1 for e in bin_entries if e.get("isLlmCorrect")) / len(bin_entries)
        calibration.append({
            "binMin": round(i * bin_width, 2),
            "binMax": round((i + 1) * bin_width, 2),
            "binMid": round((i + 0.5) * bin_width, 2),
            "avgConfidence": round(avg_conf, 4),
            "accuracy": round(accuracy, 4),
            "count": len(bin_entries)
        })

    return calibration
