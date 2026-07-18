#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LLM置信度采集器 — 批量调用LLM获取置信度，支持缓存管理
"""

import json
import os
import hashlib
import time
from datetime import datetime
from . import config
from .ground_truth import check_llm_correctness


def make_cache_key(term, context_query):
    """生成缓存键：MD5(term + || + contextQuery)"""
    raw = f"{term}||{context_query}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def load_cache(path=None):
    """加载已有缓存"""
    if path is None:
        path = config.CONFIDENCE_CACHE_FILE
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_cache(cache, path=None):
    """保存缓存"""
    if path is None:
        path = config.CONFIDENCE_CACHE_FILE
    cache["updated_at"] = datetime.now().isoformat()
    cache["total_entries"] = len(cache.get("entries", {}))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=2)


def collect_confidence(entries, gt_data, client, use_cache=True):
    """
    批量采集LLM置信度分数。

    entries: 要采集的词条列表
    gt_data: ground_truth字典
    client: SynonymTestClient实例
    use_cache: 是否使用已有缓存（跳过已缓存的词条）

    返回: cache字典
    """
    # 加载或创建缓存
    cache = load_cache() if use_cache else None
    if cache is None:
        cache = {
            "version": "1.0",
            "llm_model": "qwen-plus",
            "created_at": datetime.now().isoformat(),
            "updated_at": "",
            "total_entries": 0,
            "entries": {}
        }

    entries_dict = cache["entries"]
    total = len(entries)
    new_count = 0
    cache_hit_count = 0

    # 分批处理
    batches = [entries[i:i + config.BATCH_SIZE] for i in range(0, total, config.BATCH_SIZE)]

    for bi, batch in enumerate(batches):
        # 构建批量请求，跳过已缓存的
        uncached_pairs = []
        uncached_indices = []

        for idx, entry in enumerate(batch):
            key = make_cache_key(entry["term"], entry["contextQuery"])
            if key in entries_dict:
                cache_hit_count += 1
                continue
            uncached_pairs.append({
                "term": entry["term"],
                "contextQuery": entry["contextQuery"]
            })
            uncached_indices.append(idx)

        if not uncached_pairs:
            continue

        # 调批量接口
        result = client.batch_force_llm_infer(uncached_pairs)

        if result and result.get("success"):
            for i, r in enumerate(result.get("results", [])):
                global_idx = (bi * config.BATCH_SIZE) + uncached_indices[i]
                if global_idx >= len(entries):
                    continue
                entry = entries[global_idx]

                key = make_cache_key(entry["term"], entry["contextQuery"])
                scenario_key = f"{entry['scenarioId']}_{entry['term']}"
                gt_entry = gt_data.get(scenario_key, {})

                has_auth = gt_entry.get("hasAuthoritativeMapping", False)
                is_correct = None
                if has_auth:
                    is_correct = check_llm_correctness(
                        r.get("standardTerm"),
                        gt_entry.get("groundTruthTerm")
                    )

                entries_dict[key] = {
                    "term": entry["term"],
                    "contextQuery": entry["contextQuery"],
                    "scenarioId": entry["scenarioId"],
                    "llmStandardTerm": r.get("standardTerm"),
                    "llmConfidence": r.get("confidence", 0.0),
                    "groundTruthTerm": gt_entry.get("groundTruthTerm"),
                    "groundTruthSource": gt_entry.get("groundTruthSource", "NONE"),
                    "hasAuthoritativeMapping": has_auth,
                    "isLlmCorrect": is_correct,
                    "collectedAt": datetime.now().isoformat()
                }
                new_count += 1
        else:
            error_msg = result.get("error", "未知错误") if result else "无响应"
            print(f"  批次{bi + 1}/{len(batches)}失败: {error_msg}")

        # 每个批次后保存缓存
        if new_count > 0 and (bi + 1) % 5 == 0:
            save_cache(cache)

        # 速率控制
        if bi < len(batches) - 1:
            time.sleep(config.LLM_CALL_DELAY_SEC)

        if (bi + 1) % 10 == 0:
            print(f"  LLM采集进度: {min((bi + 1) * config.BATCH_SIZE, total)}/{total}")

    # 最终保存
    save_cache(cache)

    total_collected = cache_hit_count + new_count
    print(f"  LLM采集完成: 新增{new_count}条, 缓存命中{cache_hit_count}条, 共{total_collected}条")
    return cache


def get_entries_with_confidence(cache, sampled_entries):
    """
    从缓存中提取采样条目的置信度数据。
    """
    entries_dict = cache.get("entries", {})
    results = []
    for entry in sampled_entries:
        key = make_cache_key(entry["term"], entry["contextQuery"])
        if key in entries_dict:
            results.append(entries_dict[key])
    return results


def get_authoritative_entries(cached_entries):
    """
    筛选出有权威标注的条目（可用于F1计算）。
    """
    return [e for e in cached_entries if e.get("hasAuthoritativeMapping")]


def get_cache_stats(cache):
    """获取缓存统计信息"""
    entries = cache.get("entries", {})
    total = len(entries)
    if total == 0:
        return {"total": 0, "authoritative": 0, "correct": 0, "incorrect": 0}

    authoritative = sum(1 for e in entries.values() if e.get("hasAuthoritativeMapping"))
    correct = sum(1 for e in entries.values() if e.get("isLlmCorrect") is True)
    incorrect = sum(1 for e in entries.values() if e.get("isLlmCorrect") is False)

    return {
        "total": total,
        "authoritative": authoritative,
        "authoritativeRate": authoritative / total if total > 0 else 0,
        "correct": correct,
        "incorrect": incorrect,
        "accuracyOnAuthoritative": correct / (correct + incorrect) if (correct + incorrect) > 0 else 0
    }
