# -*- coding: utf-8 -*-
"""使用 DashScope API 自动批量标注 (query, doc) 相关性"""

import json
import os
import sys
import time
from datetime import datetime
import requests

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_results")

API_KEY = "sk-ws-H.RXMDPEH.ma8A.MEQCIHafDSrDc8rOiiD2r59TndVKwZBHkhMemGD_WsDjhcW9AiBHuYTNBTqEf9IpLk8a5Gs9V19_E0If5OSShE6HLiXX_w"
API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
MODEL = "qwen-plus"
BATCH_SIZE = 15  # 每批标注对数

SYSTEM_PROMPT = """你是一位RAG检索质量评估裁判。对每个(查询, 文档块)对打分，评估该文档块对回答查询的信息贡献度：

- 2分：文档块包含回答Query所需的关键信息（核心事实、诊断标准、治疗方案、鉴别要点等），能作为答案的主要信息源
- 1分：文档块与Query主题相关（同一病种/领域），但没有提供能直接回答Query的具体信息
- 0分：文档块与Query完全不相关（不同病种、不同主题、纯索引/导航文本）

关键区分：
- 2分 vs 1分：关键在于文档块是否包含"可用于回答Query的具体事实/数据/步骤"，而不在于是否已经组织成完整答案
- 1分 vs 0分：关键在于文档块是否与Query属于同一医学主题（同病种/同症状/同治疗领域）

只输出JSON对象数组，每个对象含query_id/doc_id/score三个字段。格式示例：
[{"query_id":"Q001","doc_id":"123","score":2},{"query_id":"Q001","doc_id":"456","score":0}]"""


def label_batch(pairs):
    """发送一批 pairs 给 LLM 标注，返回标签列表"""
    trimmed = []
    for p in pairs:
        content = p.get("doc_content", "")[:200]
        trimmed.append({
            "query_id": p["query_id"],
            "query": p["query"],
            "doc_id": p["doc_id"],
            "doc": content,
        })

    for attempt in range(5):
        try:
            resp = requests.post(
                API_URL,
                headers={
                    "Authorization": f"Bearer {API_KEY}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": MODEL,
                    "messages": [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": json.dumps(trimmed, ensure_ascii=False)},
                    ],
                    "temperature": 0.0,
                    "max_tokens": 8192,
                },
                timeout=120,
            )
            if resp.status_code == 200:
                data = resp.json()
                content = data["choices"][0]["message"]["content"].strip()
                start = content.find("[")
                end = content.rfind("]") + 1
                if start >= 0 and end > start:
                    raw = json.loads(content[start:end])
                    valid = []
                    if isinstance(raw, list) and len(raw) > 0:
                        if isinstance(raw[0], dict):
                            # 格式: [{"query_id":..., "doc_id":..., "score":...}, ...]
                            for l in raw:
                                if isinstance(l, dict) and "query_id" in l:
                                    s = l.get("score")
                                    if isinstance(s, str):
                                        s = int(s) if s.isdigit() else 1
                                    valid.append({
                                        "query_id": l["query_id"],
                                        "doc_id": str(l.get("doc_id", "")),
                                        "score": s if s in (0, 1, 2) else 1,
                                    })
                        elif isinstance(raw[0], int):
                            # 格式: [0, 1, 2, 0, ...] — 按顺序对应输入pairs
                            for idx, score in enumerate(raw):
                                if idx < len(trimmed) and score in (0, 1, 2):
                                    valid.append({
                                        "query_id": trimmed[idx]["query_id"],
                                        "doc_id": trimmed[idx]["doc_id"],
                                        "score": score,
                                    })
                    return valid
                print(f"  [WARN] JSON解析失败: {content[:100]}")
            elif resp.status_code == 429:
                wait = 5 * (attempt + 1)
                print(f"  [RATE] 限流, 等待{wait}s...")
                time.sleep(wait)
                continue
            else:
                print(f"  [ERR] HTTP {resp.status_code}: {resp.text[:100]}")
        except Exception as e:
            print(f"  [ERR] 第{attempt+1}次: {e}")
        time.sleep(2 * (attempt + 1))
    return []


def main():
    # 加载完整 pairs
    compact_files = sorted([
        f for f in os.listdir(OUTPUT_DIR) if f.startswith("llm_label_compact_")
    ])
    if not compact_files:
        print("[ERROR] 未找到标注输入，先运行 llm_labeling.py prepare")
        sys.exit(1)

    with open(os.path.join(OUTPUT_DIR, compact_files[-1]), "r", encoding="utf-8") as f:
        all_pairs = json.load(f)

    total_pairs = len(all_pairs)
    total_batches = (total_pairs + BATCH_SIZE - 1) // BATCH_SIZE
    print(f"总计 {total_pairs} 对, 每批 {BATCH_SIZE} 对, 共 {total_batches} 批")
    print(f"模型: {MODEL}")

    all_labels = []
    t0 = time.time()

    for bi in range(0, total_pairs, BATCH_SIZE):
        batch = all_pairs[bi:bi + BATCH_SIZE]
        batch_num = bi // BATCH_SIZE + 1
        elapsed = time.time() - t0
        eta = (elapsed / batch_num) * (total_batches - batch_num) if batch_num > 1 else 0
        print(f"\n批次 {batch_num}/{total_batches} ({len(batch)}对) "
              f"| 耗时 {elapsed:.0f}s | 预计剩余 {eta:.0f}s")

        labels = label_batch(batch)
        all_labels.extend(labels)
        print(f"  → 获得 {len(labels)} 条标注")

        if batch_num < total_batches:
            time.sleep(3)  # 批间延迟，避免限流

    elapsed = time.time() - t0
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_path = os.path.join(OUTPUT_DIR, f"llm_labels_{ts}.json")

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(all_labels, f, ensure_ascii=False, indent=2)

    # 统计
    dist = {0: 0, 1: 0, 2: 0}
    for l in all_labels:
        dist[l["score"]] = dist.get(l["score"], 0) + 1

    print(f"\n{'='*55}")
    print(f"  标注完成: {len(all_labels)}/{total_pairs} 条, 耗时 {elapsed:.0f}s")
    for s in [2, 1, 0]:
        pct = dist[s] / len(all_labels) * 100 if all_labels else 0
        print(f"  Label={s}: {dist[s]:5d} ({pct:5.1f}%)")

    if all_labels:
        p2 = dist[2] / len(all_labels) * 100
        if p2 > 50:
            print("  [警告] 2分>50%, 标注偏松")
        elif p2 < 15:
            print("  [警告] 2分<15%, 标注偏严或检索质量差")
        else:
            print("  [OK] 标签分布合理")

    print(f"  结果: {output_path}")

    factor_files = sorted([
        f for f in os.listdir(OUTPUT_DIR) if f.startswith("llm_label_factor_data_")
    ])
    if factor_files:
        fp = os.path.join(OUTPUT_DIR, factor_files[-1])
        print(f"\n下一步:")
        print(f"  py -3 scripts/confidence/llm_labeling.py apply")
        print(f"    --labels {output_path}")
        print(f"    --factors {fp}")


if __name__ == "__main__":
    main()
