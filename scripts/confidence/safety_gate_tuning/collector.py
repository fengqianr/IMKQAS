# -*- coding: utf-8 -*-
"""
QA置信度采集器 — 批量调用QA接口获取LLM置信度+答案，支持缓存管理
"""

import json
import os
import hashlib
import time
from datetime import datetime
from . import config


def make_cache_key(query):
    """生成缓存键：MD5(query)"""
    return hashlib.md5(query.encode("utf-8")).hexdigest()


def load_cache(path=None):
    """加载已有缓存"""
    if path is None:
        path = config.QA_CACHE_FILE
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_cache(cache, path=None):
    """保存缓存"""
    if path is None:
        path = config.QA_CACHE_FILE
    cache["updated_at"] = datetime.now().isoformat()
    cache["total_entries"] = len(cache.get("entries", {}))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=2)


def collect_qa_responses(queries, client, use_cache=True, label_data=None):
    """
    批量采集QA响应（含LLM置信度+答案）。

    queries: 查询字符串列表
    client: SafetyGateTestClient实例
    use_cache: 是否使用已有缓存
    label_data: 已有的标注数据字典 {query_key: {isSafe, ...}}

    返回: cache字典
    """
    cache = load_cache() if use_cache else None
    if cache is None:
        cache = {
            "version": "1.0",
            "created_at": datetime.now().isoformat(),
            "updated_at": "",
            "total_entries": 0,
            "entries": {}
        }

    entries_dict = cache["entries"]
    total = len(queries)
    new_count = 0
    cache_hit_count = 0

    for i, query in enumerate(queries):
        key = make_cache_key(query)
        if key in entries_dict:
            cache_hit_count += 1
            # 补充标注信息
            if label_data and query in label_data:
                entries_dict[key]["label"] = label_data[query]
            continue

        # 调QA接口
        resp = client.ask(query)
        if resp is None:
            print(f"  [WARN] QA请求无响应: query='{query[:50]}...'")
            continue

        entry = {
            "query": query,
            "answer": resp.get("answer", ""),
            "rawConfidence": resp.get("rawConfidence", 0.0),
            "confidence": resp.get("confidence", 0.0),
            "intentType": resp.get("intentType", "UNKNOWN"),
            "modelUsed": resp.get("modelUsed", "unknown"),
            "processingTime": resp.get("processingTime", 0),
            "collectedAt": datetime.now().isoformat(),
            "success": resp.get("success", False)
        }

        if not resp.get("success"):
            entry["error"] = resp.get("error", "未知错误")

        # 合并已有标注
        if label_data and query in label_data:
            entry["label"] = label_data[query]

        entries_dict[key] = entry
        new_count += 1

        # 定期保存缓存
        if new_count > 0 and new_count % 10 == 0:
            save_cache(cache)

        if (i + 1) % 10 == 0:
            print(f"  QA采集进度: {i + 1}/{total}")

    # 最终保存
    save_cache(cache)

    total_collected = cache_hit_count + new_count
    print(f"  QA采集完成: 新增{new_count}条, 缓存命中{cache_hit_count}条, 共{total_collected}条")
    return cache


def extract_queries_from_scenarios(scenarios, limit=None):
    """
    从测试场景中提取查询列表。

    scenarios: 测试场景列表
    limit: 限制查询数量

    返回: [(scenario_id, user_query), ...]
    """
    queries = []
    for s in scenarios:
        if "userQuery" in s and s["userQuery"]:
            queries.append((s.get("id", ""), s["userQuery"]))
    if limit and limit < len(queries):
        queries = queries[:limit]
    return queries


def get_cache_stats(cache):
    """获取缓存统计信息"""
    entries = cache.get("entries", {})
    total = len(entries)
    if total == 0:
        return {"total": 0, "withLabel": 0, "labeledSafe": 0, "labeledUnsafe": 0,
                "meanConfidence": 0.0, "medianConfidence": 0.0}

    labeled = sum(1 for e in entries.values() if "label" in e)
    labeled_safe = sum(1 for e in entries.values()
                       if e.get("label", {}).get("isSafe") is True)
    labeled_unsafe = sum(1 for e in entries.values()
                         if e.get("label", {}).get("isSafe") is False)

    valid_conf = [e["rawConfidence"] for e in entries.values()
                  if e.get("success") and e.get("rawConfidence", 0) > 0]
    mean_conf = sum(valid_conf) / len(valid_conf) if valid_conf else 0.0
    sorted_conf = sorted(valid_conf)
    n = len(sorted_conf)
    median_conf = sorted_conf[n // 2] if n > 0 else 0.0

    return {
        "total": total,
        "withLabel": labeled,
        "labeledSafe": labeled_safe,
        "labeledUnsafe": labeled_unsafe,
        "meanConfidence": round(mean_conf, 4),
        "medianConfidence": round(median_conf, 4)
    }


def get_labeled_entries(cache):
    """
    筛选出有标注的条目，返回用于阈值调优的数据。
    """
    entries = cache.get("entries", {})
    labeled = []
    for key, entry in entries.items():
        if "label" not in entry:
            continue
        label = entry["label"]
        labeled.append({
            "query": entry["query"],
            "rawConfidence": entry.get("rawConfidence", 0.0),
            "confidence": entry.get("confidence", 0.0),
            "answer": entry.get("answer", ""),
            "isSafe": label.get("isSafe", False),
            "qualityScore": label.get("qualityScore", 0.0),
            "safetyIssues": label.get("safetyIssues", []),
            "intentType": entry.get("intentType", "UNKNOWN")
        })
    return labeled
