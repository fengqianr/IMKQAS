# -*- coding: utf-8 -*-
"""
安全门控评估指标计算器 — 双阈值（minThreshold + warningThreshold）专用指标
"""

import itertools
from . import config


def compute_safety_metrics(entries, min_threshold, warning_threshold):
    """
    对给定的(minThreshold, warningThreshold)双阈值计算安全门控指标。

    门控逻辑：
    - rawConfidence < minThreshold → BLOCK（回答被阻断）
    - minThreshold ≤ rawConfidence < warningThreshold → WARNING（回答正常展示但加免责声明）
    - rawConfidence ≥ warningThreshold → PASS（正常通过）

    指标说明：
    - 安全项：不安全的回答被 BLOCK 或 WARNING
    - 风险项：不安全的回答直接 PASS
    - 过度项：安全的回答被 BLOCK
    - 打扰项：安全的回答被 WARNING

    返回: {
        minThreshold, warningThreshold,
        total, safeCount, unsafeCount,
        blocked, warned, passed,
        tpBlock, tpWarn, fnPass (不安全但通过),
        fpBlock, fpWarn, tnPass (安全且通过),
        safetyRecall, precision, f1,
        falseBlockRate, falsePassRate,
        userExperienceScore, safetyScore
    }
    """
    if warning_threshold < min_threshold:
        return None

    total = len(entries)
    if total == 0:
        return None

    # 计数器
    safe_count = 0
    unsafe_count = 0
    tp_block = 0     # 不安全的被BLOCK（正确阻断）
    tp_warn = 0      # 不安全的被WARNING（正确警告）
    fn_pass = 0      # 不安全的直接PASS（漏网之鱼/风险）
    fp_block = 0     # 安全的被BLOCK（过度阻断）
    fp_warn = 0      # 安全的被WARNING（打扰）
    tn_pass = 0      # 安全的直接PASS（完美通过）

    blocked_entries = []
    warned_entries = []
    passed_entries = []

    for entry in entries:
        conf = entry.get("rawConfidence", 0.0)
        is_safe = entry.get("isSafe", False)

        if is_safe:
            safe_count += 1
        else:
            unsafe_count += 1

        if conf < min_threshold:
            # BLOCK
            if not is_safe:
                tp_block += 1
            else:
                fp_block += 1
            blocked_entries.append(entry)
        elif conf < warning_threshold:
            # WARNING
            if not is_safe:
                tp_warn += 1
            else:
                fp_warn += 1
            warned_entries.append(entry)
        else:
            # PASS
            if not is_safe:
                fn_pass += 1
            else:
                tn_pass += 1
            passed_entries.append(entry)

    # 计算指标
    blocked = tp_block + fp_block
    warned = tp_warn + fp_warn
    passed = fn_pass + tn_pass

    # 安全召回率：不安全回答被拦截（BLOCK + WARNING）的比例
    safety_recall = (tp_block + tp_warn) / unsafe_count if unsafe_count > 0 else 1.0

    # 安全精确率：被拦截（BLOCK + WARNING）的回答中，真正不安全的比例
    total_intercepted = blocked + warned
    safety_precision = (tp_block + tp_warn) / total_intercepted if total_intercepted > 0 else 0.0

    # F1（从安全拦截角度）
    if safety_precision + safety_recall > 0:
        safety_f1 = 2 * safety_precision * safety_recall / (safety_precision + safety_recall)
    else:
        safety_f1 = 0.0

    # 漏网率（最危险指标）：不安全的回答直接PASS的比例
    false_pass_rate = fn_pass / unsafe_count if unsafe_count > 0 else 0.0

    # 过度阻断率：安全的回答被BLOCK的比例
    false_block_rate = fp_block / safe_count if safe_count > 0 else 0.0

    # 打扰率：安全的回答被WARNING的比例
    disturb_rate = fp_warn / safe_count if safe_count > 0 else 0.0

    # 用户体验分数：安全回答不被阻断/打扰的比例
    user_experience = tn_pass / safe_count if safe_count > 0 else 1.0

    # 综合安全分数：加权平均
    # 漏网率权重最高（安全第一），其次考虑用户体验
    safety_score = (
        0.5 * (1.0 - false_pass_rate)
        + 0.2 * safety_recall
        + 0.2 * user_experience
        + 0.1 * (1.0 - false_block_rate)
    )

    return {
        "minThreshold": round(min_threshold, 3),
        "warningThreshold": round(warning_threshold, 3),
        "total": total,
        "safeCount": safe_count,
        "unsafeCount": unsafe_count,
        "blocked": blocked,
        "warned": warned,
        "passed": passed,
        "blockRate": round(blocked / total, 4),
        "warnRate": round(warned / total, 4),
        "passRate": round(passed / total, 4),
        "tpBlock": tp_block,
        "tpWarn": tp_warn,
        "fnPass": fn_pass,
        "fpBlock": fp_block,
        "fpWarn": fp_warn,
        "tnPass": tn_pass,
        "safetyRecall": round(safety_recall, 4),
        "safetyPrecision": round(safety_precision, 4),
        "safetyF1": round(safety_f1, 4),
        "falsePassRate": round(false_pass_rate, 4),
        "falseBlockRate": round(false_block_rate, 4),
        "disturbRate": round(disturb_rate, 4),
        "userExperienceScore": round(user_experience, 4),
        "safetyScore": round(safety_score, 4)
    }


def scan_threshold_grid(entries, min_range=None, warning_range=None):
    """
    在双阈值网格上进行穷举扫描。

    min_range: (min_start, min_end, min_step)
    warning_range: (warn_start, warn_end, warn_step)

    返回: 扁平的metrics列表（每个元素是一个(minThreshold, warningThreshold)组合的指标）
    """
    import numpy as np

    if min_range is None:
        min_range = config.MIN_THRESHOLD_RANGE
    if warning_range is None:
        warning_range = config.WARNING_THRESHOLD_RANGE

    min_start, min_end, min_step = min_range
    warn_start, warn_end, warn_step = warning_range

    min_thresholds = np.arange(min_start, min_end + min_step / 2, min_step)
    warn_thresholds = np.arange(warn_start, warn_end + warn_step / 2, warn_step)

    all_metrics = []
    total_combos = len(min_thresholds) * len(warn_thresholds)
    count = 0

    for min_t in min_thresholds:
        for warn_t in warn_thresholds:
            if warn_t < min_t:
                continue  # warning必须 >= min
            result = compute_safety_metrics(entries, min_t, warn_t)
            if result:
                all_metrics.append(result)
            count += 1
            if count % 50 == 0:
                print(f"  阈值扫描进度: {count}/{total_combos}")

    print(f"  阈值网格扫描完成: {len(all_metrics)}个有效组合 (共{total_combos}个候选)")

    # 按minThreshold排序
    all_metrics.sort(key=lambda m: (m["minThreshold"], m["warningThreshold"]))
    return all_metrics


def find_optimal_thresholds(all_metrics, objective="balanced"):
    """
    在扫描结果中找最优双阈值。

    objective:
    - "safety_first": 优先安全（漏网率=0前提下最大化用户体验）
    - "balanced": 综合安全分数最高
    - "f1": F1最高
    - "user_experience": 用户体检最优（安全召回率>=0.8约束下）
    """
    if not all_metrics:
        return None

    if objective == "safety_first":
        # 漏网率为0的前提下，用户体验最高
        safe = [m for m in all_metrics if m["falsePassRate"] == 0]
        if not safe:
            # 找漏网率最低的
            min_fp = min(m["falsePassRate"] for m in all_metrics)
            safe = [m for m in all_metrics if m["falsePassRate"] == min_fp]
        return max(safe, key=lambda m: m["userExperienceScore"])

    elif objective == "f1":
        return max(all_metrics, key=lambda m: m["safetyF1"])

    elif objective == "user_experience":
        # 安全召回率 >= 0.8
        safe_enough = [m for m in all_metrics if m["safetyRecall"] >= 0.8]
        if not safe_enough:
            safe_enough = all_metrics
        return max(safe_enough, key=lambda m: m["userExperienceScore"])

    elif objective == "balanced":
        # 直接使用综合安全分数
        return max(all_metrics, key=lambda m: m["safetyScore"])

    return max(all_metrics, key=lambda m: m["safetyScore"])


def compute_current_metrics(all_metrics, current_min=0.50, current_warn=0.55):
    """获取当前默认阈值下的指标"""
    for m in all_metrics:
        if (abs(m["minThreshold"] - current_min) < 0.001
                and abs(m["warningThreshold"] - current_warn) < 0.001):
            return m
    return None


def generate_recommendation(optimal, current_metrics, all_metrics):
    """生成最终推荐文本"""
    lines = []
    lines.append("=" * 65)
    lines.append("  安全门控置信度双阈值调优 — 最终推荐")
    lines.append("=" * 65)

    if current_metrics:
        lines.append(f"当前最小阈值:     {current_metrics['minThreshold']}")
        lines.append(f"当前警告阈值:     {current_metrics['warningThreshold']}")
        lines.append(f"当前综合安全分:   {current_metrics['safetyScore']}")
        lines.append(f"当前漏网率:       {current_metrics['falsePassRate']}")
        lines.append(f"当前过度阻断率:   {current_metrics['falseBlockRate']}")
        lines.append("")

    if optimal:
        lines.append(f"推荐最小阈值:     {optimal['minThreshold']}")
        lines.append(f"推荐警告阈值:     {optimal['warningThreshold']}")
        lines.append(f"推荐综合安全分:   {optimal['safetyScore']}")
        lines.append(f"推荐漏网率:       {optimal['falsePassRate']}")
        lines.append(f"推荐过度阻断率:   {optimal['falseBlockRate']}")
        lines.append(f"推荐用户体验分:   {optimal['userExperienceScore']}")
        lines.append("")

        if current_metrics:
            score_diff = optimal["safetyScore"] - current_metrics["safetyScore"]
            fp_diff = current_metrics["falsePassRate"] - optimal["falsePassRate"]
            if score_diff > 0.02:
                lines.append(f"综合安全分提升: +{score_diff:.4f}")
            if fp_diff > 0:
                lines.append(f"漏网率降低: -{fp_diff:.4f} (安全提升)")

    lines.append("")
    lines.append("建议操作：")
    lines.append("  修改 application.yml 中的以下配置：")
    lines.append("    imkqas.rag.safety.confidence.min-threshold")
    lines.append("    imkqas.rag.safety.confidence.warning-threshold")
    lines.append("=" * 65)

    return "\n".join(lines)
