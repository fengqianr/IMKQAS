# -*- coding: utf-8 -*-
"""
多因子权重网格搜索 —— 基于医疗领域先验，用 NDCG@5 微调权重

领域先验（固定基线）:
    语义=0.35, 权威=0.25, 意图=0.15, 时效=0.15, 层级=0.10

网格搜索策略:
    固定: 意图=0.15, 时效=0.15, 层级=0.10 (合计 0.40)
    扫描: 语义 ∈ [0.30, 0.40], 步长 0.02
    推导: 权威 = 1.0 - 语义 - 0.40
    指标: NDCG@5

用法:
    py -3 scripts/confidence/grid_search_weights.py --mock
    py -3 scripts/confidence/grid_search_weights.py --real validation_data.json
"""

import json
import os
import sys
import argparse
import math
import time
from datetime import datetime

import numpy as np

# ============================================================
# 配置
# ============================================================
FIXED_INTENT = 0.15
FIXED_TIMELINESS = 0.15
FIXED_HIERARCHY = 0.10
FIXED_SUM = FIXED_INTENT + FIXED_TIMELINESS + FIXED_HIERARCHY  # 0.40

SEMANTIC_MIN, SEMANTIC_MAX, SEMANTIC_STEP = 0.30, 0.40, 0.02
BASE_URL = os.environ.get("IMKQAS_BASE_URL", "http://localhost:8080")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_results")
os.makedirs(OUTPUT_DIR, exist_ok=True)


# ============================================================
# 模拟验证集生成（医疗场景建模）
# ============================================================
def generate_mock_validation(n_queries=50, docs_per_query=20, seed=42):
    """
    生成模拟医疗验证集。
    每条数据包含查询级别的一组文档，每个文档有五因子分数 + 真实相关性标签。
    建模假设：语义和权威是排序的主要信号，意图/时效/层级是辅助信号。
    """
    rng = np.random.default_rng(seed)

    # 医学查询模板
    query_templates = [
        "高血压有什么症状", "糖尿病怎么治疗", "阿司匹林的副作用",
        "胸痛应该看什么科", "新冠肺炎的预防措施", "胃癌的早期诊断",
        "抗生素的使用原则", "心电图的检查意义", "腰椎间盘突出的康复",
        "儿童发烧的处理方法", "抑郁症的临床表现", "甲状腺结节的评估",
        "慢性肾病的饮食控制", "骨质疏松的药物治疗", "肝炎疫苗的接种方案",
    ]

    queries = []
    for qi in range(n_queries):
        q = query_templates[qi % len(query_templates)]
        # 每个查询生成一组文档
        docs = []
        for di in range(docs_per_query):
            # 五因子：语义和权威是强信号，意图/时效/层级是弱信号
            semantic = float(rng.beta(4, 3))  # 偏右 ~0.55
            authority = float(rng.choice(
                [0.10, 0.30, 0.50, 0.60, 0.70, 0.85, 0.95, 1.00],
                p=[0.02, 0.05, 0.12, 0.20, 0.24, 0.22, 0.10, 0.05]
            ))
            intent = float(rng.beta(4, 4))  # 居中 ~0.50
            timeliness = float(rng.beta(5, 3))  # 偏右 ~0.62
            hierarchy = float(rng.beta(3, 6))  # 偏左 ~0.33

            # 真实相关性：主要由语义+权威决定，加噪声
            true_signal = 0.55 * semantic + 0.35 * authority + 0.10 * (intent + timeliness) / 2
            noise = float(rng.normal(0, 0.08))
            relevance = np.clip(true_signal + noise, 0, 1)

            # 映射到 0-4 分级
            if relevance >= 0.85:
                label = 4
            elif relevance >= 0.70:
                label = 3
            elif relevance >= 0.50:
                label = 2
            elif relevance >= 0.25:
                label = 1
            else:
                label = 0

            docs.append({
                "doc_id": f"doc_{qi}_{di}",
                "semantic": round(semantic, 4),
                "authority": round(authority, 4),
                "intent": round(intent, 4),
                "timeliness": round(timeliness, 4),
                "hierarchy": round(hierarchy, 4),
                "relevance": label,
            })
        queries.append({"query_id": f"q_{qi}", "query": q, "docs": docs})

    return queries


# ============================================================
# 真实 API 数据采集
# ============================================================
def load_test_queries():
    """从 test_scenarios.json 加载测试查询"""
    path = os.path.join(SCRIPT_DIR, "..", "test_scenarios.json")
    try:
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        scenarios = json.loads(content)
        if isinstance(scenarios, dict):
            scenarios = scenarios.get("scenarios", [])
        queries = []
        for s in scenarios:
            q = s.get("userQuery") or s.get("query") or s.get("question")
            if q:
                queries.append({
                    "id": s.get("id", ""),
                    "query": q,
                    "expected_keywords": s.get("expectedKeywords", []),
                })
        return queries
    except Exception as e:
        print(f"[WARN] 无法加载测试场景: {e}")
    return []


def keyword_relevance(content, expected_keywords):
    """基于关键词匹配计算相关性标签（0-4分级）"""
    if not content or not expected_keywords:
        return 0
    content_lower = content.lower()
    hits = sum(1 for kw in expected_keywords if kw.lower() in content_lower)
    rate = hits / len(expected_keywords) if expected_keywords else 0
    if rate >= 0.8:
        return 4
    elif rate >= 0.5:
        return 3
    elif rate >= 0.25:
        return 2
    elif rate > 0:
        return 1
    return 0


def collect_real_factors(queries, top_k=20):
    """调用 /api/rag/retrieval-test/factor-breakdown 采集各文档的五因子分数"""
    import requests

    client = requests.Session()
    client.headers.update({"Content-Type": "application/json"})
    validation_data = []
    total = len(queries)

    print(f"\n开始采集 {total} 条查询的五因子分解数据...")
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
                    docs = []
                    for doc in data["docs"]:
                        docs.append({
                            "doc_id": str(doc.get("chunkId", "")),
                            "semantic": round(float(doc.get("semantic", 0.5)), 4),
                            "authority": round(float(doc.get("authority", 0.5)), 4),
                            "intent": round(float(doc.get("intent", 0.5)), 4),
                            "timeliness": round(float(doc.get("timeliness", 0.7)), 4),
                            "hierarchy": round(float(doc.get("hierarchy", 0.5)), 4),
                            "relevance": keyword_relevance(
                                doc.get("content", ""), q.get("expected_keywords", [])
                            ),
                        })
                    validation_data.append({
                        "query_id": q["id"],
                        "query": q["query"],
                        "docs": docs,
                    })
                else:
                    print(f"  [WARN] 查询无结果 id={q['id']}: {data.get('error', 'no docs')}")
            else:
                print(f"  [WARN] HTTP {r.status_code} id={q['id']}")

            if (i + 1) % 10 == 0:
                elapsed = time.time() - t0
                eta = (elapsed / (i + 1)) * (total - i - 1)
                print(f"  进度: {i + 1}/{total} | 耗时: {elapsed:.0f}s | 预计剩余: {eta:.0f}s")

        except Exception as e:
            print(f"  [WARN] 请求失败 id={q['id']}: {e}")

    elapsed = time.time() - t0
    print(f"采集完成: {len(validation_data)}条有效查询, 总耗时: {elapsed:.1f}s")
    return validation_data


# ============================================================
# NDCG@K 计算
# ============================================================
def dcg_at_k(relevances, k):
    """DCG@k = sum(rel_i / log2(i + 1)) for i = 0..k-1 (1-indexed: i+1)"""
    dcg = 0.0
    for i, rel in enumerate(relevances[:k]):
        dcg += rel / math.log2(i + 2)  # i=0 → log2(2)=1
    return dcg


def ndcg_at_k(pred_relevances, true_relevances, k=5):
    """NDCG@k = DCG(pred_ranking) / DCG(ideal_ranking)"""
    idcg = dcg_at_k(sorted(true_relevances, reverse=True), k)
    if idcg == 0:
        return 1.0 if all(r == 0 for r in true_relevances[:k]) else 0.0
    return dcg_at_k(pred_relevances, k) / idcg


def compute_score(semantic, authority, intent, timeliness, hierarchy, weights):
    """五因子加权得分"""
    return (weights["semantic"] * semantic + weights["authority"] * authority +
            weights["intent"] * intent + weights["timeliness"] * timeliness +
            weights["hierarchy"] * hierarchy)


# ============================================================
# 网格搜索
# ============================================================
def grid_search(validation_data):
    """
    固定意图、时效、层级，扫描语义∈[0.30, 0.40]，权威=1-语义-0.40。
    额外评估基线权重（语义=0.35, 权威=0.25）。
    每个权重组合计算所有查询的平均 NDCG@5。
    """
    results = []
    semantic_values = np.arange(SEMANTIC_MIN, SEMANTIC_MAX + SEMANTIC_STEP / 2, SEMANTIC_STEP)

    # 先评估基线
    baseline_weights = {
        "semantic": 0.35, "authority": 0.25,
        "intent": FIXED_INTENT, "timeliness": FIXED_TIMELINESS,
        "hierarchy": FIXED_HIERARCHY,
    }
    baseline_ndcg = _evaluate_weights(baseline_weights, validation_data)
    results.append({
        "w_semantic": 0.35, "w_authority": 0.25,
        "w_intent": FIXED_INTENT, "w_timeliness": FIXED_TIMELINESS,
        "w_hierarchy": FIXED_HIERARCHY,
        "ndcg5_mean": round(baseline_ndcg[0], 6),
        "ndcg5_std": round(baseline_ndcg[1], 6),
        "weight_sum": 1.0,
        "is_baseline": True,
    })

    for w_sem in semantic_values:
        w_sem = round(float(w_sem), 2)
        w_auth = round(1.0 - w_sem - FIXED_SUM, 2)

        if w_auth < 0.15 or w_auth > 0.30:
            continue

        # 避免重复基线
        if w_sem == 0.35 and w_auth == 0.25:
            continue

        weights = {
            "semantic": w_sem, "authority": w_auth,
            "intent": FIXED_INTENT, "timeliness": FIXED_TIMELINESS,
            "hierarchy": FIXED_HIERARCHY,
        }
        avg_ndcg, std_ndcg = _evaluate_weights(weights, validation_data)

        results.append({
            "w_semantic": w_sem, "w_authority": w_auth,
            "w_intent": FIXED_INTENT, "w_timeliness": FIXED_TIMELINESS,
            "w_hierarchy": FIXED_HIERARCHY,
            "ndcg5_mean": round(avg_ndcg, 6),
            "ndcg5_std": round(std_ndcg, 6),
            "weight_sum": round(w_sem + w_auth + FIXED_SUM, 2),
            "is_baseline": False,
        })

    return results


def _evaluate_weights(weights, validation_data):
    """评估一组权重，返回 (mean_ndcg, std_ndcg)"""
    ndcg_scores = []
    for q_data in validation_data:
        docs = q_data["docs"]
        scored = []
        for doc in docs:
            score = compute_score(
                doc["semantic"], doc["authority"],
                doc["intent"], doc["timeliness"], doc["hierarchy"],
                weights,
            )
            scored.append((score, doc["relevance"]))
        scored.sort(key=lambda x: x[0], reverse=True)

        pred_rels = [rel for _, rel in scored]
        true_rels = [doc["relevance"] for doc in docs]
        ndcg = ndcg_at_k(pred_rels, true_rels, k=5)
        ndcg_scores.append(ndcg)

    return float(np.mean(ndcg_scores)), float(np.std(ndcg_scores))


# ============================================================
# 输出
# ============================================================
def print_report(results, baseline_weights):
    """打印网格搜索结果"""
    print("\n" + "=" * 72)
    print("  多因子权重网格搜索报告（NDCG@5 指标）")
    print("=" * 72)

    print(f"\n领域先验基线:")
    print(f"  语义={baseline_weights['semantic']}, 权威={baseline_weights['authority']}, "
          f"意图={baseline_weights['intent']}, 时效={baseline_weights['timeliness']}, "
          f"层级={baseline_weights['hierarchy']}")

    print(f"\n固定因子: 意图={FIXED_INTENT}, 时效={FIXED_TIMELINESS}, 层级={FIXED_HIERARCHY}")

    # 计算基线 NDCG
    baseline_ndcg = None
    for r in results:
        if (r["w_semantic"] == baseline_weights["semantic"] and
                r["w_authority"] == baseline_weights["authority"]):
            baseline_ndcg = r["ndcg5_mean"]
            break

    print(f"\n网格扫描结果 (语义∈[{SEMANTIC_MIN}, {SEMANTIC_MAX}], 步长{SEMANTIC_STEP}):\n")
    header = (f"  {'语义':>6s}  {'权威':>6s}  {'意图':>6s}  {'时效':>6s}  {'层级':>6s}  │ "
              f"{'NDCG@5':>10s}  {'Std':>8s}  │ 对比基线")
    print(header)
    print("  " + "-" * 80)

    # 按 NDCG 降序排列
    sorted_results = sorted(results, key=lambda r: r["ndcg5_mean"], reverse=True)

    # 找到基线
    baseline = next((r for r in sorted_results if r.get("is_baseline")), None)
    if baseline:
        baseline_ndcg = baseline["ndcg5_mean"]

    for r in sorted_results:
        diff = ""
        if r.get("is_baseline"):
            diff = "<-- 基线 (领域先验)"
        elif baseline_ndcg is not None:
            delta = r["ndcg5_mean"] - baseline_ndcg
            sign = "+" if delta > 0 else ""
            diff = f"{sign}{delta:.6f}"

        marker = " <-- BEST" if r is sorted_results[0] else ""
        print(f"  {r['w_semantic']:6.2f}  {r['w_authority']:6.2f}  "
              f"{r['w_intent']:6.2f}  {r['w_timeliness']:6.2f}  "
              f"{r['w_hierarchy']:6.2f}  | {r['ndcg5_mean']:10.6f}  {r['ndcg5_std']:8.6f}  | "
              f"{diff}{marker}")

    # 最佳组合
    best = sorted_results[0]
    print(f"\n{'=' * 72}")
    print(f"  最佳权重组合")
    print(f"{'=' * 72}")
    print(f"  语义={best['w_semantic']}, 权威={best['w_authority']}, "
          f"意图={best['w_intent']}, 时效={best['w_timeliness']}, "
          f"层级={best['w_hierarchy']}")
    print(f"  NDCG@5 = {best['ndcg5_mean']:.6f}")

    if baseline_ndcg:
        delta = best["ndcg5_mean"] - baseline_ndcg
        print(f"  相比基线提升: {delta:+.6f} ({(delta / baseline_ndcg) * 100:+.2f}%)")

    # 所有有效组合的简要排名
    print(f"\n权重组合排名 (Top-5):")
    for i, r in enumerate(sorted_results[:5]):
        print(f"  {i + 1}. 语义={r['w_semantic']:.2f} 权威={r['w_authority']:.2f} "
              f"NDCG@5={r['ndcg5_mean']:.6f}")

    return sorted_results


def save_results(results, output_path):
    """保存完整结果到 JSON"""
    output = {
        "description": "多因子权重网格搜索 — NDCG@5 指标",
        "method": "控制变量法：固定意图/时效/层级，扫描语义/权威",
        "fixed_factors": {
            "intent": FIXED_INTENT, "timeliness": FIXED_TIMELINESS,
            "hierarchy": FIXED_HIERARCHY,
        },
        "scan_range": {
            "semantic": [SEMANTIC_MIN, SEMANTIC_MAX, SEMANTIC_STEP],
            "authority_derived": "1 - semantic - 0.40",
        },
        "metric": "NDCG@5",
        "generated_at": datetime.now().isoformat(),
        "results": results,
    }
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存: {output_path}")


# ============================================================
# 入口
# ============================================================
def main():
    parser = argparse.ArgumentParser(description="多因子权重网格搜索（NDCG@5）")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--mock", action="store_true", help="使用模拟医疗验证集")
    group.add_argument("--real", nargs="?", const="__api__", default=None,
                       help="真实验证: 不带参数调用API采集，或指定JSON文件路径")
    parser.add_argument("--queries", type=int, default=50, help="模拟查询数/API采集数（默认50）")
    parser.add_argument("--docs-per-query", type=int, default=20, help="模拟模式下每个查询的文档数")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--json-only", action="store_true", help="仅保存JSON，不打印报告")
    parser.add_argument("--top-k", type=int, default=20, help="API模式下每查询检索文档数")
    args = parser.parse_args()

    # 基线权重（领域先验）
    baseline_weights = {
        "semantic": 0.35, "authority": 0.25,
        "intent": 0.15, "timeliness": 0.15, "hierarchy": 0.10,
    }

    # 加载验证集
    if args.mock:
        print(f"生成模拟医疗验证集: {args.queries}条查询 x {args.docs_per_query}篇文档...")
        validation_data = generate_mock_validation(
            n_queries=args.queries, docs_per_query=args.docs_per_query, seed=args.seed
        )
        mode = "mock"
    elif args.real == "__api__":
        queries = load_test_queries()
        if not queries:
            print("[ERROR] 未找到测试查询。请确保 test_scenarios.json 存在。")
            sys.exit(1)
        if args.queries and len(queries) > args.queries:
            import random
            random.seed(args.seed)
            queries = random.sample(queries, args.queries)
        print(f"加载测试查询: {len(queries)}条, 目标服务: {BASE_URL}")
        validation_data = collect_real_factors(queries, top_k=args.top_k)
        mode = "real_api"
    else:
        with open(args.real, "r", encoding="utf-8") as f:
            validation_data = json.load(f)
        if isinstance(validation_data, dict):
            validation_data = validation_data.get("queries", validation_data.get("data", []))
        mode = "real_file"
        print(f"加载真实验证集: {args.real} ({len(validation_data)}条查询)")

    if not validation_data:
        print("[ERROR] 验证集为空")
        sys.exit(1)

    # 网格搜索
    print(f"开始网格搜索: 语义∈[{SEMANTIC_MIN}, {SEMANTIC_MAX}], 步长{SEMANTIC_STEP}...")
    results = grid_search(validation_data)
    print(f"完成: {len(results)} 个有效权重组合")

    # 输出
    if not args.json_only:
        sorted_results = print_report(results, baseline_weights)

    # 保存
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    json_path = os.path.join(OUTPUT_DIR, f"grid_search_weights_{mode}_{ts}.json")
    save_results(results, json_path)


if __name__ == "__main__":
    main()
