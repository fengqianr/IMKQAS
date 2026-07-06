#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
指标计算模块
召回率、边际收益、拐点检测、最优K值确定
"""

import numpy as np

from . import config


def find_inflection_point(k_values: list[int], marginal_benefit: list[float],
                          threshold: float = None) -> dict:
    """
    检测边际收益拐点和饱和点。

    返回:
        {
            'inflection_k': int,     # 拐点K值（边际收益 < threshold）
            'inflection_idx': int,
            'saturation_k': int,     # 饱和点K值（边际收益 < saturation_threshold）
            'saturation_idx': int,
        }
    """
    thr = threshold or config.MARGINAL_THRESHOLD
    sat_thr = config.SATURATION_THRESHOLD

    inflection_k = k_values[-1]
    inflection_idx = len(k_values) - 1
    saturation_k = k_values[-1]
    saturation_idx = len(k_values) - 1

    for i, (k, mb) in enumerate(zip(k_values, marginal_benefit)):
        if mb < sat_thr and saturation_idx == len(k_values) - 1:
            saturation_k = k
            saturation_idx = i
        if mb < thr and inflection_idx == len(k_values) - 1:
            inflection_k = k
            inflection_idx = i
            break  # 拐点通常在饱和点之前

    return {
        "inflection_k": inflection_k,
        "inflection_idx": inflection_idx,
        "saturation_k": saturation_k,
        "saturation_idx": saturation_idx,
    }


def find_optimal_k(k_values: list[int],
                   recall: list[float],
                   latency_p99: list[float],
                   recall_min: float = None,
                   latency_max_ms: float = None) -> dict:
    """
    找到同时满足召回率和延迟约束的最小K值。

    返回:
        {'optimal_k': int, 'optimal_idx': int, 'meets_constraints': bool}
    """
    rec_min = recall_min or config.RECALL_SLA_MIN * 100  # 转为百分比
    lat_max = latency_max_ms or config.P99_LATENCY_SLA_MS

    optimal_idx = 0
    meets_constraints = False

    for i, (k, rec, lat) in enumerate(zip(k_values, recall, latency_p99)):
        if rec >= rec_min and lat <= lat_max:
            optimal_idx = i
            meets_constraints = True
            break

    if not meets_constraints:
        # 放宽约束：选召回率最高且延迟可接受的点
        best_score = -1
        for i, (k, rec, lat) in enumerate(zip(k_values, recall, latency_p99)):
            score = rec - 0.01 * lat  # 召回率与延迟的简单权衡
            if score > best_score:
                best_score = score
                optimal_idx = i

    return {
        "optimal_k": k_values[optimal_idx],
        "optimal_idx": optimal_idx,
        "meets_constraints": meets_constraints,
        "recall_at_optimal": recall[optimal_idx],
        "latency_p99_at_optimal": latency_p99[optimal_idx],
    }


def compute_efficiency_ratio(k_values: list[int], recall: list[float],
                             latency_p99: list[float]) -> list[float]:
    """计算效率比: recall / latency_p99，越高表示效率越好"""
    ratios = []
    for rec, lat in zip(recall, latency_p99):
        if lat > 0:
            ratios.append(rec / lat)
        else:
            ratios.append(0.0)
    return ratios


def summarize_pathway(results: dict) -> dict:
    """
    对一条通路的结果进行汇总分析。
    包含拐点检测、最优K值、效率分析。
    """
    k_vals = results["k_values"]
    recall = results["avg_recall"]
    latency_p99 = results["latency_p99"]
    marginal = results["marginal_benefit"]

    inflection = find_inflection_point(k_vals, marginal)
    optimal = find_optimal_k(k_vals, recall, latency_p99)
    efficiency = compute_efficiency_ratio(k_vals, recall, latency_p99)

    return {
        **results,
        "inflection": inflection,
        "optimal": optimal,
        "efficiency_ratio": efficiency,
    }
