#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分层抽样器 — 按词频分层抽取代表性样本
"""

import random
from .term_extractor import compute_term_frequencies, classify_frequency
from . import config


def stratified_sample(entries, sample_size=150, random_seed=42):
    """
    按词频分层抽样。

    返回: 抽样后的entries列表 + 采样统计信息
    """
    random.seed(random_seed)
    freq = compute_term_frequencies(entries)

    # 分层
    high_entries = [e for e in entries if classify_frequency(
        e["term"], freq, config.FREQ_HIGH_THRESHOLD, config.FREQ_MEDIUM_THRESHOLD) == "high"]
    medium_entries = [e for e in entries if classify_frequency(
        e["term"], freq, config.FREQ_HIGH_THRESHOLD, config.FREQ_MEDIUM_THRESHOLD) == "medium"]
    low_entries = [e for e in entries if classify_frequency(
        e["term"], freq, config.FREQ_HIGH_THRESHOLD, config.FREQ_MEDIUM_THRESHOLD) == "low"]

    # 按比例分配名额，优先选有权威GT的
    high_quota = min(len(high_entries), int(sample_size * 0.35))
    medium_quota = min(len(medium_entries), int(sample_size * 0.40))
    low_quota = min(len(low_entries), int(sample_size * 0.25))

    # 补足未分配的配额
    remaining = sample_size - high_quota - medium_quota - low_quota
    while remaining > 0:
        if len(high_entries) > high_quota:
            high_quota += 1
        elif len(medium_entries) > medium_quota:
            medium_quota += 1
        elif len(low_entries) > low_quota:
            low_quota += 1
        else:
            break
        remaining -= 1

    # 每层内优先选权威GT
    def sample_with_gt_priority(pool, quota):
        if len(pool) <= quota:
            return list(pool)
        with_gt = [e for e in pool if e.get("hasAuthoritativeMapping")]
        without_gt = [e for e in pool if not e.get("hasAuthoritativeMapping")]
        selected = random.sample(with_gt, min(quota, len(with_gt)))
        if len(selected) < quota:
            selected += random.sample(without_gt, min(quota - len(selected), len(without_gt)))
        return selected

    high_sample = sample_with_gt_priority(high_entries, high_quota)
    medium_sample = sample_with_gt_priority(medium_entries, medium_quota)
    low_sample = sample_with_gt_priority(low_entries, low_quota)

    sampled = high_sample + medium_sample + low_sample
    random.shuffle(sampled)

    # 统计
    total_unique_terms = len(set(e["term"] for e in entries))
    stats = {
        "totalEntries": len(entries),
        "totalUniqueTerms": total_unique_terms,
        "highFreq": {"uniqueTerms": len(set(e["term"] for e in high_entries)),
                      "entries": len(high_entries), "sampled": len(high_sample)},
        "mediumFreq": {"uniqueTerms": len(set(e["term"] for e in medium_entries)),
                        "entries": len(medium_entries), "sampled": len(medium_sample)},
        "lowFreq": {"uniqueTerms": len(set(e["term"] for e in low_entries)),
                     "entries": len(low_entries), "sampled": len(low_sample)},
        "sampleSize": len(sampled)
    }

    print(f"  分层抽样完成: 总计{len(sampled)}条 "
          f"(高{len(high_sample)}/中{len(medium_sample)}/低{len(low_sample)})")
    return sampled, stats
