#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量检查术语映射状态，对未映射的调LLM推断标准名
"""
import json
import time
import requests

BASE = "http://localhost:8080"
LOOKUP_URL = f"{BASE}/api/rag/synonym-test/lookup"
BATCH_URL = f"{BASE}/api/rag/synonym-test/batch-llm-infer"

# 读取唯一术语
terms = []
with open("D:/Java/代码/IMKQAS/scripts/confidence_tuning_cache/_unique_terms.txt", "r", encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if line:
            parts = line.split("\t")
            if parts:
                terms.append(parts[0])

print(f"共有 {len(terms)} 个唯一术语")

# 1. 逐个检查映射状态
mapped = {}
unmapped = []
for term in terms:
    try:
        r = requests.get(LOOKUP_URL, params={"term": term}, timeout=30)
        if r.status_code == 200:
            data = r.json()
            if data.get("found"):
                mapped[term] = {
                    "standardTerm": data.get("standardTerm"),
                    "source": data.get("source"),
                    "confidence": data.get("confidence")
                }
            else:
                unmapped.append(term)
        else:
            unmapped.append(term)
    except Exception as e:
        print(f"  [ERROR] {term}: {e}")
        unmapped.append(term)
    time.sleep(0.1)

print(f"\n已映射: {len(mapped)}")
for t, m in mapped.items():
    print(f"  {t} -> {m['standardTerm']} ({m['source']})")

print(f"\n未映射: {len(unmapped)}")
print(f"待LLM推断: {', '.join(unmapped)}")

# 2. 批量LLM推断未映射术语
if unmapped:
    # 用术语自身作为上下文（因为没有具体查询上下文）
    pairs = [{"term": t, "contextQuery": f"请解释{t}的含义"} for t in unmapped]

    all_results = []
    batch_size = 10
    for i in range(0, len(pairs), batch_size):
        batch = pairs[i:i + batch_size]
        try:
            r = requests.post(BATCH_URL, json=batch, timeout=120)
            if r.status_code == 200:
                data = r.json()
                if data.get("success"):
                    for res in data.get("results", []):
                        all_results.append(res)
                    print(f"  batch {i // batch_size + 1}/{(len(pairs) - 1) // batch_size + 1}: "
                          f"{len(batch)} 条, 成功 {data.get('successCount', 0)}")
                else:
                    print(f"  batch {i // batch_size + 1} 失败: {data}")
            else:
                print(f"  batch {i // batch_size + 1} HTTP {r.status_code}")
        except Exception as e:
            print(f"  batch {i // batch_size + 1} 异常: {e}")
        time.sleep(0.5)

    # 3. 整理结果
    results = []
    for r in all_results:
        results.append({
            "term": r.get("term"),
            "standardTerm": r.get("standardTerm"),
            "confidence": r.get("confidence", 0),
            "contextQuery": r.get("contextQuery", "")
        })

    # 4. 写入结果文件供用户审核
    with open("D:/Java/代码/IMKQAS/scripts/confidence_tuning_cache/_llm_inferred_terms.json", "w", encoding="utf-8") as f:
        json.dump({
            "mapped": mapped,
            "llm_inferred": results,
            "total_unique": len(terms),
            "previously_mapped": len(mapped),
            "llm_inferred_count": len(results)
        }, f, ensure_ascii=False, indent=2)

    print(f"\n结果已保存, 共 {len(results)} 条LLM推断结果")
    print("待用户确认后可写入数据库")
else:
    print("\n所有术语都已映射, 无需LLM推断")
