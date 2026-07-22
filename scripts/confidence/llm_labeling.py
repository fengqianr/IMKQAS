
# -*- coding: utf-8 -*-
"""
LLM 相关性标注流水线

步骤:
  1. prepare: 采集检索结果，生成标注输入文件（供 LLM 批量打分）
  2. apply:  将 LLM 标注结果注入测试数据，重新运行权重对比

用法:
  py -3 scripts/confidence/llm_labeling.py prepare --queries 30
  py -3 scripts/confidence/llm_labeling.py apply --labels labels.json --factors factor_data.json
"""

import json
import os
import sys
import argparse
import time
import random
from datetime import datetime

import requests

BASE_URL = os.environ.get("IMKQAS_BASE_URL", "http://localhost:8080")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_results")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 标注 Prompt 模板（来自测试手册 1.3 节）
LABELING_PROMPT = """你是一位严格的医学信息检索裁判。你需要对每个(查询, 文档)对进行相关性打分。

## 评分标准

| 分数 | 含义 | 判定规则 |
|------|------|----------|
| 2 | 直接完整回答 | 文档内容能**直接、完整**地回答Query提出的问题。必须包含核心答案句。 |
| 1 | 提及主题但未给答案 | 文档涉及Query的主题领域，但**没有给出具体答案**，或只提供了背景/定义。 |
| 0 | 答非所问 | 文档内容与Query无关，或相关度极低。 |

## 严格判定规则（必须遵守）

1. **意图匹配优先**：若Query问"怎么做/步骤/如何处理"，而文档只讲"定义/分类/原理"，直接判为 1 分，不得给 2 分。
2. **核心答案句检测**：给 2 分前，你必须在文档中找到至少一句"核心答案句"——这句子能直接回答Query的问题。找不到则不給 2 分。
3. **否定信规则**：如果文档只提到Query中的关键词，但没有展开讲该关键词与Query意图的关系，最多给 1 分。
4. **同义词判例**：Query和文档使用不同但等价的医学术语（如"发热"vs"发烧"）不影响评分。

## 待标注数据

{pairs_json}

## 输出格式

返回一个 JSON 数组，每个元素仅含 query_id、doc_id、score 三个字段：

```json
[
  {{"query_id": "Q001", "doc_id": "123", "score": 2}},
  {{"query_id": "Q001", "doc_id": "456", "score": 1}}
]
```

仅输出 JSON 数组，不要解释理由，不要加任何其他文字。"""


# ============================================================
# prepare: 采集检索结果 → 生成标注输入文件
# ============================================================
def load_queries(max_queries=30, seed=42):
    """从 test_scenarios.json 加载测试查询"""
    path = os.path.join(SCRIPT_DIR, "..", "test_scenarios.json")
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    scenarios = data if isinstance(data, list) else data.get("scenarios", [])
    queries = []
    for s in scenarios:
        q = s.get("userQuery") or s.get("query") or s.get("question")
        if q:
            queries.append({"id": s.get("id", ""), "query": q})
    if max_queries and len(queries) > max_queries:
        rng = random.Random(seed)
        queries = rng.sample(queries, max_queries)
    return queries


def collect_pairs(queries, top_k=15):
    """调用 factor-breakdown API，提取 (query, doc) 对供 LLM 标注"""
    client = requests.Session()
    client.headers.update({"Content-Type": "application/json"})

    pairs = []
    factor_data = []  # 保留完整因子分数，供后续合并
    total = len(queries)

    print(f"\n采集 {total} 条查询的检索结果...")
    t0 = time.time()

    for i, q in enumerate(queries):
        try:
            r = client.post(
                f"{BASE_URL}/api/rag/retrieval-test/factor-breakdown",
                json={"query": q["query"], "topK": top_k},
                timeout=60,
            )
            if r.status_code == 200:
                data = r.json()
                if data.get("success") and data.get("docs"):
                    q_docs = []
                    for doc in data["docs"]:
                        content = (doc.get("content", "") or "")[:400]
                        pairs.append({
                            "query_id": q["id"],
                            "query": q["query"],
                            "doc_id": str(doc.get("chunkId", "")),
                            "doc_content": content,
                        })
                        q_docs.append({
                            "doc_id": str(doc.get("chunkId", "")),
                            "factors": {
                                "semantic": round(float(doc.get("semantic", 0.5)), 4),
                                "authority": round(float(doc.get("authority", 0.5)), 4),
                                "intent": round(float(doc.get("intent", 0.5)), 4),
                                "timeliness": round(float(doc.get("timeliness", 0.7)), 4),
                                "hierarchy": round(float(doc.get("hierarchy", 0.5)), 4),
                            },
                        })
                    factor_data.append({
                        "query_id": q["id"],
                        "query": q["query"],
                        "docs": q_docs,
                    })
            if (i + 1) % 10 == 0:
                elapsed = time.time() - t0
                eta = (elapsed / (i + 1)) * (total - i - 1)
                print(f"  进度: {i + 1}/{total} | 耗时: {elapsed:.0f}s | 预计剩余: {eta:.0f}s")
        except Exception as e:
            print(f"  [WARN] 请求失败 id={q['id']}: {e}")

    elapsed = time.time() - t0
    print(f"采集完成: {len(factor_data)}条查询, {len(pairs)}个标注对, 耗时: {elapsed:.1f}s")
    return pairs, factor_data


def run_prepare(args):
    """主流程: 采集数据 → 生成标注文件"""
    queries = load_queries(max_queries=args.queries, seed=args.seed)
    if not queries:
        print("[ERROR] 未找到测试查询")
        sys.exit(1)

    print(f"加载 {len(queries)} 条查询, 目标服务: {BASE_URL}")
    pairs, factor_data = collect_pairs(queries, top_k=args.top_k)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")

    # 保存因子数据（标注完成后需要用）
    factor_path = os.path.join(OUTPUT_DIR, f"llm_label_factor_data_{ts}.json")
    with open(factor_path, "w", encoding="utf-8") as f:
        json.dump(factor_data, f, ensure_ascii=False, indent=2)
    print(f"因子数据已保存: {factor_path}")

    # 生成标注输入文件（仅含 query/doc，不含因子分数）
    labeling_input = {
        "description": "LLM相关性标注输入 —— 请将完整Prompt复制给LLM，获取标注结果",
        "total_pairs": len(pairs),
        "pairs": pairs,
    }
    pairs_path = os.path.join(OUTPUT_DIR, f"llm_label_input_{ts}.json")
    with open(pairs_path, "w", encoding="utf-8") as f:
        json.dump(labeling_input, f, ensure_ascii=False, indent=2)
    print(f"标注输入已保存: {pairs_path}")

    # 生成可直接复制给LLM的完整Prompt文本
    pairs_json = json.dumps(pairs, ensure_ascii=False, indent=2)
    full_prompt = LABELING_PROMPT.replace("{pairs_json}", pairs_json)
    prompt_path = os.path.join(OUTPUT_DIR, f"llm_label_prompt_{ts}.txt")
    with open(prompt_path, "w", encoding="utf-8") as f:
        f.write(full_prompt)
    print(f"完整Prompt已保存: {prompt_path}")
    print(f"  → 将此文件内容复制给 LLM（需开启新会话，不要与Query生成共用上下文）")

    # 输出简洁的标注输入JSON（仅query/doc对，适合直接粘贴给支持JSON输入的LLM）
    compact_path = os.path.join(OUTPUT_DIR, f"llm_label_compact_{ts}.json")
    with open(compact_path, "w", encoding="utf-8") as f:
        json.dump(pairs, f, ensure_ascii=False, indent=2)
    print(f"精简标注数据已保存: {compact_path}")
    print(f"  → 将此JSON + 上面的Prompt一起发给LLM")


# ============================================================
# apply: 注入LLM标注结果 → 重新运行权重测试
# ============================================================
def run_apply(args):
    """将LLM标注结果合并到因子数据，生成标准测试输入并运行权重测试"""
    # 加载标签
    with open(args.labels, "r", encoding="utf-8") as f:
        labels_data = json.load(f)

    # 兼容多种格式：纯数组 或 {labels: [...]} 或 {results: [...]}
    if isinstance(labels_data, list):
        labels = labels_data
    elif isinstance(labels_data, dict):
        labels = labels_data.get("labels") or labels_data.get("results") or []

    # 构建 (query_id, doc_id) → score 索引
    label_map = {}
    for item in labels:
        key = (item["query_id"], str(item["doc_id"]))
        label_map[key] = item["score"]

    print(f"加载 {len(labels)} 条标注, 覆盖 {len(label_map)} 个唯一(query,doc)对")

    # 加载因子数据
    with open(args.factors, "r", encoding="utf-8") as f:
        factor_data = json.load(f)

    # 合并标签
    docs_by_query = []
    matched = 0
    for q_data in factor_data:
        docs = []
        for doc in q_data["docs"]:
            key = (q_data["query_id"], str(doc["doc_id"]))
            label = label_map.get(key, 1)  # 未标注的默认1分
            if key in label_map:
                matched += 1
            docs.append({
                "doc_id": doc["doc_id"],
                "factors": doc["factors"],
                "label": label,
            })
        docs_by_query.append({
            "query_id": q_data["query_id"],
            "query": q_data["query"],
            "docs": docs,
        })

    print(f"合并完成: {matched}/{sum(len(q['docs']) for q in factor_data)} 个文档已标注")

    # 保存合并后的测试数据
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    merged_path = os.path.join(OUTPUT_DIR, f"llm_labeled_data_{ts}.json")
    with open(merged_path, "w", encoding="utf-8") as f:
        json.dump(docs_by_query, f, ensure_ascii=False, indent=2)
    print(f"合并后测试数据已保存: {merged_path}")

    # 运行权重测试
    from weight_test import run_round1, check_label_distribution

    dist = check_label_distribution(docs_by_query)
    print("\n" + "=" * 64)
    print("  LLM标注后 —— 标签分布质检")
    print("=" * 64)
    total = sum(dist["counts"].values())
    print(f"  总标注数: {total}")
    for s in [2, 1, 0]:
        print(f"  Label={s}: {dist['counts'][s]:5d}  ({dist['percentages'][s]:5.1f}%)")
    if not dist["healthy"]:
        print("\n  [警告] 标签分布不健康:")
        for w in dist["warnings"]:
            print(f"    - {w}")
        if args.force or input("\n仍要继续运行权重测试? (y/n): ").strip().lower() == "y":
            pass
        else:
            print("已取消。建议重新标注后再试。")
            return
    else:
        print("  [OK] 标签分布健康")

    run_round1(docs_by_query, output_prefix=f"llm_labeled_{ts}")


# ============================================================
# CLI
# ============================================================
def main():
    parser = argparse.ArgumentParser(description="LLM相关性标注流水线")
    sub = parser.add_subparsers(dest="command", help="子命令")

    p1 = sub.add_parser("prepare", help="采集检索结果，生成标注输入文件")
    p1.add_argument("--queries", type=int, default=30, help="查询数量（默认30）")
    p1.add_argument("--top-k", type=int, default=15, help="每查询检索文档数（默认15）")
    p1.add_argument("--seed", type=int, default=42)

    p2 = sub.add_parser("apply", help="注入LLM标注结果，运行权重测试")
    p2.add_argument("--labels", "-l", required=True, help="LLM标注结果JSON文件")
    p2.add_argument("--factors", "-f", required=True, help="prepare步骤生成的因子数据文件")
    p2.add_argument("--force", action="store_true", help="即使标签分布不健康也继续")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(1)

    if args.command == "prepare":
        run_prepare(args)
    elif args.command == "apply":
        run_apply(args)


if __name__ == "__main__":
    main()
