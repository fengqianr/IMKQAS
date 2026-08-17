# -*- coding: utf-8 -*-
"""
语义召回率评估脚本

用 LLM 标注（0/1/2 相关性打分）评估三通路（VECTOR/KEYWORD/HYBRID）的语义检索召回能力。
与 k_tuning 的关键词计数 GT 不同，本脚本用 DashScope qwen-plus 对 (query, doc) 对做语义相关性打分，
再计算 Recall@K 与 nDCG@K。

流程:
  1. collect: 对每题×每通路调 /api/rag/retrieval-test/single 拿 top-K 纯检索结果，跨通路去重
  2. label:   对去重后的 (query, doc) 对用 qwen-plus 批量打分 0/1/2
  3. eval:    计算三通路 Recall@K + nDCG@K，生成对比报告

用法:
  py scripts/confidence/semantic_recall_eval.py --phase collect --top-k 10
  py scripts/confidence/semantic_recall_eval.py --phase label --collect-file <采集json>
  py scripts/confidence/semantic_recall_eval.py --phase eval --collect-file <采集json> --labels-file <标注json>
"""

import sys
import os
import io
import json
import math
import time
import argparse
from datetime import datetime

# 强制 UTF-8 stdout，解决 Windows GBK 终端编码问题
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import requests  # noqa: E402

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_URL = os.environ.get("IMKQAS_BASE_URL", "http://localhost:8080")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_results")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 复用 auto_label 的 DashScope 标注能力
sys.path.insert(0, SCRIPT_DIR)
from auto_label import label_batch, BATCH_SIZE  # noqa: E402

PATHWAYS = ["VECTOR", "KEYWORD", "HYBRID"]
SINGLE_ENDPOINT = f"{BASE_URL}/api/rag/retrieval-test/single"

# nDCG 相关性增益映射（2^label - 1，让 label=2 权重显著高于 label=1）
GAIN = {0: 0.0, 1: 1.0, 2: 3.0}


def load_queries(path):
    """加载测试场景，返回 [{"id", "query"}]"""
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    scenarios = data if isinstance(data, list) else data.get("scenarios", [])
    queries = []
    for s in scenarios:
        q = s.get("userQuery") or s.get("query") or s.get("question")
        if q:
            queries.append({"id": s.get("id", ""), "query": q})
    return queries


def fetch_pathway(query, pathway, top_k):
    """调 /single 端点，返回有序 chunk 列表 [{chunk_id, content, score}]"""
    try:
        r = requests.post(
            SINGLE_ENDPOINT,
            json={"pathway": pathway, "query": query, "topK": top_k},
            timeout=60,
        )
        if r.status_code != 200:
            return []
        data = r.json()
        if not data.get("success"):
            return []
        results = []
        for it in data.get("results", []):
            cid = it.get("chunkId")
            if cid is None:
                continue
            results.append({
                "chunk_id": str(cid),
                "content": it.get("content", "") or "",
                "score": it.get("score"),
            })
        return results
    except Exception as e:
        print(f"    [WARN] {pathway} 检索失败: {e}")
        return []


def phase_collect(args):
    """阶段1: 三通路采集 + 去重"""
    queries = load_queries(args.scenarios)
    print(f"加载 {len(queries)} 条查询, 目标服务: {BASE_URL}")
    print(f"通路: {PATHWAYS}, top-K: {args.top_k}")

    queries_out = []
    docs = {}  # key: "query_id|chunk_id" -> {"content", "score", "sources"}

    t0 = time.time()
    total = len(queries)
    for i, q in enumerate(queries):
        qid = q["id"]
        pathway_lists = {}
        for pw in PATHWAYS:
            res = fetch_pathway(q["query"], pw, args.top_k)
            ids = []
            for r in res:
                key = f"{qid}|{r['chunk_id']}"
                if key not in docs:
                    docs[key] = {"content": r["content"], "score": r["score"], "sources": []}
                if pw not in docs[key]["sources"]:
                    docs[key]["sources"].append(pw)
                ids.append(r["chunk_id"])
            pathway_lists[pw] = ids
        queries_out.append({
            "query_id": qid,
            "query": q["query"],
            "pathways": pathway_lists,
        })

        if (i + 1) % 10 == 0:
            elapsed = time.time() - t0
            eta = elapsed / (i + 1) * (total - i - 1)
            print(f"  进度: {i + 1}/{total} | 耗时 {elapsed:.0f}s | 预计剩余 {eta:.0f}s")

    elapsed = time.time() - t0
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = os.path.join(OUTPUT_DIR, f"semantic_recall_collect_{ts}.json")
    payload = {
        "meta": {
            "base_url": BASE_URL,
            "top_k": args.top_k,
            "pathways": PATHWAYS,
            "total_queries": len(queries_out),
            "total_unique_docs": len(docs),
            "created": ts,
        },
        "queries": queries_out,
        "docs": docs,
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    raw_pairs = sum(len(q["pathways"][pw]) for q in queries_out for pw in PATHWAYS)
    print(f"\n采集完成: {len(queries_out)}题, 原始 {raw_pairs} 对 → 去重后 {len(docs)} 唯一对")
    print(f"耗时 {elapsed:.0f}s")
    print(f"采集结果: {out_path}")
    return out_path


def phase_label(args):
    """阶段2: LLM 批量标注（复用 auto_label.label_batch）"""
    with open(args.collect_file, "r", encoding="utf-8") as f:
        collect = json.load(f)
    queries = collect["queries"]
    docs = collect["docs"]
    query_text = {q["query_id"]: q["query"] for q in queries}

    pairs = []
    for key, meta in docs.items():
        qid, cid = key.split("|", 1)
        pairs.append({
            "query_id": qid,
            "query": query_text.get(qid, ""),
            "doc_id": cid,
            "doc_content": meta["content"],
        })

    total = len(pairs)
    total_batches = (total + BATCH_SIZE - 1) // BATCH_SIZE
    print(f"标注对总数: {total}, 每批 {BATCH_SIZE}, 共 {total_batches} 批")

    all_labels = []
    t0 = time.time()
    for bi in range(0, total, BATCH_SIZE):
        batch = pairs[bi:bi + BATCH_SIZE]
        batch_num = bi // BATCH_SIZE + 1
        elapsed = time.time() - t0
        eta = elapsed / batch_num * (total_batches - batch_num) if batch_num > 1 else 0
        print(f"批次 {batch_num}/{total_batches} ({len(batch)}对) "
              f"| 耗时 {elapsed:.0f}s | 剩余 {eta:.0f}s", flush=True)
        labels = label_batch(batch)
        all_labels.extend(labels)
        print(f"  → 获得 {len(labels)} 条标注", flush=True)
        if batch_num < total_batches:
            time.sleep(3)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = os.path.join(OUTPUT_DIR, f"semantic_recall_labels_{ts}.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(all_labels, f, ensure_ascii=False, indent=2)

    dist = {0: 0, 1: 0, 2: 0}
    for l in all_labels:
        dist[l["score"]] = dist.get(l["score"], 0) + 1
    print(f"\n标注完成: {len(all_labels)}/{total} 条")
    for s in [2, 1, 0]:
        pct = dist[s] / len(all_labels) * 100 if all_labels else 0
        print(f"  Label={s}: {dist[s]:5d} ({pct:5.1f}%)")
    print(f"标注结果: {out_path}")
    return out_path


def dcg(labels, k):
    d = 0.0
    for i, lab in enumerate(labels[:k], start=1):
        d += GAIN[lab] / math.log2(i + 1)
    return d


def ndcg(labels, k):
    ideal = sorted(labels, key=lambda x: x, reverse=True)
    idcg = dcg(ideal, k)
    return dcg(labels, k) / idcg if idcg > 0 else 0.0


def recall_at_k(labels, k):
    """Recall@K: top-K 中至少命中一个 label=2 的 query 占比（hit rate）"""
    return 1.0 if any(lab == 2 for lab in labels[:k]) else 0.0


def phase_eval(args):
    """阶段3: 计算 Recall@K + nDCG@K 并生成报告"""
    with open(args.collect_file, "r", encoding="utf-8") as f:
        collect = json.load(f)
    with open(args.labels_file, "r", encoding="utf-8") as f:
        labels_raw = json.load(f)

    if isinstance(labels_raw, list):
        labels = labels_raw
    else:
        labels = labels_raw.get("labels") or labels_raw.get("results") or []

    label_map = {}
    for item in labels:
        label_map[(item["query_id"], str(item["doc_id"]))] = item["score"]

    queries = collect["queries"]
    pathways = collect["meta"]["pathways"]

    results = {}
    for pw in pathways:
        rec5, rec10, nd5, nd10 = [], [], [], []
        missing = 0
        total_judged = 0
        for q in queries:
            ids = q["pathways"].get(pw, [])
            labels_seq = []
            for cid in ids:
                key = (q["query_id"], cid)
                total_judged += 1
                if key in label_map:
                    labels_seq.append(label_map[key])
                else:
                    missing += 1
                    labels_seq.append(1)  # 未标注默认 1 分（与 llm_labeling.apply 一致）
            rec5.append(recall_at_k(labels_seq, 5))
            rec10.append(recall_at_k(labels_seq, 10))
            nd5.append(ndcg(labels_seq, 5))
            nd10.append(ndcg(labels_seq, 10))
        n = len(queries)
        results[pw] = {
            "recall5": sum(rec5) / n if n else 0.0,
            "recall10": sum(rec10) / n if n else 0.0,
            "ndcg5": sum(nd5) / n if n else 0.0,
            "ndcg10": sum(nd10) / n if n else 0.0,
            "missing": missing,
            "total_judged": total_judged,
        }

    # 控制台表格
    print("\n" + "=" * 78)
    print("  三通路语义召回率评估（LLM 0/1/2 标注）")
    print("=" * 78)
    print(f"  {'通路':<8} {'Recall@5':>10} {'Recall@10':>10} {'nDCG@5':>8} {'nDCG@10':>8} {'未标注':>8}")
    print("-" * 78)
    for pw in pathways:
        r = results[pw]
        print(f"  {pw:<8} {r['recall5']:>10.4f} {r['recall10']:>10.4f} "
              f"{r['ndcg5']:>8.4f} {r['ndcg10']:>8.4f} {r['missing']:>8d}")

    # 保存 JSON 结果
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    json_path = os.path.join(OUTPUT_DIR, f"semantic_recall_results_{ts}.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump({
            "meta": collect["meta"],
            "label_stats": _label_distribution(labels),
            "pathways": results,
        }, f, ensure_ascii=False, indent=2)

    # 生成 Markdown 报告
    md_path = os.path.join(OUTPUT_DIR, f"semantic_recall_report_{ts}.md")
    _write_report(md_path, collect, labels, results)
    print(f"\n结果 JSON: {json_path}")
    print(f"报告 MD:   {md_path}")
    return json_path


def _label_distribution(labels):
    dist = {0: 0, 1: 0, 2: 0}
    for l in labels:
        dist[l["score"]] = dist.get(l["score"], 0) + 1
    total = len(labels)
    return {
        "counts": dist,
        "percentages": {k: (v / total * 100 if total else 0) for k, v in dist.items()},
        "total": total,
    }


def _write_report(path, collect, labels, results):
    meta = collect["meta"]
    dist = _label_distribution(labels)
    lines = []
    lines.append("# 三通路语义召回率评估报告")
    lines.append("")
    lines.append(f"> **生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"> **标注方式**: LLM 0/1/2 相关性打分 (qwen-plus, DashScope)")
    lines.append(f"> **样本**: {meta['total_queries']} 题 × 三通路 top-{meta['top_k']}，去重后 {meta['total_unique_docs']} 个标注对")
    lines.append("")
    lines.append("## 1. 核心结果")
    lines.append("")
    lines.append("| 通路 | Recall@5 | Recall@10 | nDCG@5 | nDCG@10 | 未标注数 |")
    lines.append("|------|----------|-----------|--------|---------|----------|")
    for pw in meta["pathways"]:
        r = results[pw]
        lines.append(f"| {pw} | {r['recall5']:.4f} | {r['recall10']:.4f} | "
                     f"{r['ndcg5']:.4f} | {r['ndcg10']:.4f} | {r['missing']} |")
    lines.append("")
    lines.append("> Recall@K = top-K 中至少命中一个 label=2 文档的 query 占比（hit rate）；"
                 "nDCG@K 增益映射 2^label-1（label=2→3, 1→1, 0→0）。")
    lines.append("")
    lines.append("## 2. 标签分布")
    lines.append("")
    lines.append("| 标签 | 数量 | 占比 |")
    lines.append("|------|------|------|")
    for s in [2, 1, 0]:
        lines.append(f"| {s} | {dist['counts'][s]} | {dist['percentages'][s]:.1f}% |")
    lines.append("")
    lines.append("## 3. 指标定义")
    lines.append("")
    lines.append("- **Recall@K**: 命中率（hit rate），前 K 条结果中至少包含一个 label=2（可直接回答）文档的查询占比。")
    lines.append("- **nDCG@K**: 归一化折损累计增益，考虑排序位置，label 越高越靠前得分越高。")
    lines.append("")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    global BASE_URL, SINGLE_ENDPOINT
    parser = argparse.ArgumentParser(description="三通路语义召回率评估（LLM 标注）")
    parser.add_argument("--phase", choices=["collect", "label", "eval"],
                        default="collect", help="执行阶段")
    parser.add_argument("--scenarios", default=os.path.join(SCRIPT_DIR, "..", "test_scenarios_100.json"),
                        help="测试场景文件")
    parser.add_argument("--top-k", type=int, default=10, help="每通路检索文档数（默认10）")
    parser.add_argument("--base-url", default=BASE_URL, help="后端服务地址")
    parser.add_argument("--collect-file", help="复用已有采集结果JSON，跳过collect阶段")
    parser.add_argument("--labels-file", help="复用已有标注结果JSON，跳过label阶段")
    args = parser.parse_args()

    if args.base_url != BASE_URL:
        BASE_URL = args.base_url
        SINGLE_ENDPOINT = f"{BASE_URL}/api/rag/retrieval-test/single"

    if args.phase == "collect":
        phase_collect(args)
    elif args.phase == "label":
        if not args.collect_file:
            print("[ERROR] --phase label 需要 --collect-file")
            sys.exit(1)
        phase_label(args)
    elif args.phase == "eval":
        if not args.collect_file or not args.labels_file:
            print("[ERROR] --phase eval 需要 --collect-file 和 --labels-file")
            sys.exit(1)
        phase_eval(args)


if __name__ == "__main__":
    main()
