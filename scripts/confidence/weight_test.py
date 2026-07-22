# -*- coding: utf-8 -*-
"""
多因子权重对比测试 —— 3 轮定稿流程

第1轮（粗筛）：5组预设权重对比 MRR@All + Recall@10，淘汰2组
第2轮（消融）：对晋级组分别砍 semantic/hierarchy，观察 MRR 跌幅
第3轮（盲测）：随机抽取20个Query生成盲测卡片

用法:
    py -3 scripts/confidence/weight_test.py round1 --mock
    py -3 scripts/confidence/weight_test.py round1 --real
    py -3 scripts/confidence/weight_test.py round2 --input round1_results.json
    py -3 scripts/confidence/weight_test.py round3 --input round2_results.json
    py -3 scripts/confidence/weight_test.py all --mock
"""

import json
import os
import sys
import argparse
import math
import time
import random
from datetime import datetime
from copy import deepcopy

import numpy as np

# ============================================================
# 配置
# ============================================================
BASE_URL = os.environ.get("IMKQAS_BASE_URL", "http://localhost:8080")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_results")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 5组预设权重
WEIGHT_GROUPS = {
    "A": {"name": "语义主导", "authority": 0.15, "timeliness": 0.10,
          "semantic": 0.40, "intent": 0.20, "hierarchy": 0.15},
    "B": {"name": "结构主导", "authority": 0.15, "timeliness": 0.10,
          "semantic": 0.25, "intent": 0.15, "hierarchy": 0.35},
    "C": {"name": "意图优先", "authority": 0.20, "timeliness": 0.10,
          "semantic": 0.25, "intent": 0.30, "hierarchy": 0.15},
    "D": {"name": "权威+时效", "authority": 0.30, "timeliness": 0.25,
          "semantic": 0.20, "intent": 0.15, "hierarchy": 0.10},
    "E": {"name": "当前配置(基线)", "authority": 0.20, "timeliness": 0.15,
          "semantic": 0.40, "intent": 0.15, "hierarchy": 0.10},
}

# 底线标准
MRR_BASELINE = 0.75
RECALL_BASELINE = 0.85


# ============================================================
# 指标计算
# ============================================================
def compute_score(factors, weights):
    """五因子加权得分"""
    return (weights["semantic"] * factors["semantic"] +
            weights["authority"] * factors["authority"] +
            weights["intent"] * factors["intent"] +
            weights["timeliness"] * factors["timeliness"] +
            weights["hierarchy"] * factors["hierarchy"])


def compute_mrr_at_all(query_results):
    """
    MRR@All: 第一个 Label=2 文档的倒数排名平均值。
    query_results: [{"score": ..., "label": 0|1|2}, ...] 已按得分降序排列
    """
    mrr_sum = 0.0
    for docs in query_results:
        for rank, doc in enumerate(docs, start=1):
            if doc["label"] == 2:
                mrr_sum += 1.0 / rank
                break
    return mrr_sum / len(query_results) if query_results else 0.0


def compute_recall_at_k(query_results, k=3):
    """
    Recall@K: 前K条结果中至少包含一个 Label=2 的 Query 占比。
    """
    hit = 0
    for docs in query_results:
        if any(d["label"] == 2 for d in docs[:k]):
            hit += 1
    return hit / len(query_results) if query_results else 0.0


def rank_docs(docs, weights):
    """按指定权重排序文档，返回带排名和标签的列表"""
    scored = []
    for doc in docs:
        s = compute_score(doc["factors"], weights)
        scored.append({"score": round(s, 6), "label": doc["label"], "doc_id": doc.get("doc_id", "")})
    scored.sort(key=lambda x: x["score"], reverse=True)
    return scored


def evaluate_group(docs_by_query, weights):
    """评估一组权重，返回 (MRR@All, Recall@10, per_query_rankings)"""
    rankings = []
    for q_data in docs_by_query:
        ranked = rank_docs(q_data["docs"], weights)
        rankings.append(ranked)
    mrr = compute_mrr_at_all(rankings)
    recall10 = compute_recall_at_k(rankings, k=10)
    return mrr, recall10, rankings


# ============================================================
# 标签分布检查（健康标准: 2:1:0 ≈ 3:4:3）
# ============================================================
def check_label_distribution(docs_by_query):
    """检查标签分布是否健康，返回统计信息和警告"""
    all_labels = []
    for q_data in docs_by_query:
        for doc in q_data["docs"]:
            all_labels.append(doc["label"])

    total = len(all_labels)
    counts = {0: 0, 1: 0, 2: 0}
    for l in all_labels:
        counts[l] = counts.get(l, 0) + 1

    pct = {k: v / total * 100 for k, v in counts.items()}

    warnings = []
    if pct[2] > 50:
        warnings.append("2分占比 > 50%: 标注过松，建议在标注Prompt中强调严格批判")
    if pct[0] > 50:
        warnings.append("0分占比 > 50%: Query-文档匹配度过低，检查类型C Query是否过于偏离")
    if pct[1] > 60:
        warnings.append("1分占比 > 60%: 标注过于保守，建议大胆给2分")

    healthy = len(warnings) == 0
    return {"counts": counts, "percentages": pct, "healthy": healthy, "warnings": warnings}


# ============================================================
# 模拟数据生成
# ============================================================
MEDICAL_QUERIES = [
    "高血压有什么症状", "糖尿病怎么治疗", "阿司匹林的副作用",
    "胸痛应该看什么科", "新冠肺炎的预防措施", "胃癌的早期诊断",
    "抗生素的使用原则", "心电图的检查意义", "腰椎间盘突出的康复",
    "儿童发烧的处理方法", "抑郁症的临床表现", "甲状腺结节的评估",
    "慢性肾病的饮食控制", "骨质疏松的药物治疗", "肝炎疫苗的接种方案",
    "小儿肺炎怎么护理", "哮喘急性发作怎么办", "手足口病会传染吗",
    "过敏性鼻炎怎么治", "缺铁性贫血吃什么好", "湿疹反复发作怎么办",
    "热性惊厥危险吗", "扁桃体发炎要不要手术", "肺炎疫苗有必要打吗",
    "毛细支气管炎症状", "新生儿黄疸正常值", "婴儿腹泻怎么补水",
    "咳嗽变异性哮喘诊断", "支原体肺炎用什么药", "过敏性紫癜会复发吗",
]


def generate_mock_data(n_queries=50, docs_per_query=20, seed=42):
    """
    生成模拟测试数据：每个 Query 有一组文档，每个文档有5因子分数 + 0/1/2 标签。
    标签分布目标: 2:1:0 ≈ 3:4:3
    """
    rng = np.random.default_rng(seed)

    # 文档标题模板
    doc_templates = [
        "诊疗指南", "临床路径", "药物说明书", "专家共识", "病例分析",
        "护理要点", "流行病学调查", "基础研究", "综述", "科普文章",
    ]

    docs_by_query = []
    for qi in range(n_queries):
        query_text = MEDICAL_QUERIES[qi % len(MEDICAL_QUERIES)]
        docs = []
        for di in range(docs_per_query):
            # 五因子分数（模拟真实分布）
            semantic = float(rng.beta(4, 3))       # 偏右 ~0.55
            authority_vals = [0.10, 0.30, 0.50, 0.60, 0.70, 0.90, 0.95, 1.00]
            authority_probs = [0.02, 0.05, 0.12, 0.20, 0.25, 0.22, 0.10, 0.04]
            authority = float(rng.choice(authority_vals, p=authority_probs))
            intent = float(rng.beta(4, 4))          # 居中 ~0.50
            timeliness = float(rng.beta(5, 3))      # 偏右 ~0.62
            hierarchy = float(rng.beta(3, 6))       # 偏左 ~0.33

            # 标签生成: semantic+authority 决定信号，增大噪声实现 3:4:3 分布
            signal = 0.45 * semantic + 0.35 * authority + 0.20 * intent
            noise = float(rng.normal(0, 0.18))
            relevance = np.clip(signal + noise, 0, 1)

            if relevance >= 0.72:
                label = 2
            elif relevance >= 0.30:
                label = 1
            else:
                label = 0

            doc_type = doc_templates[di % len(doc_templates)]
            docs.append({
                "doc_id": f"doc_{qi}_{di}",
                "title": f"{MEDICAL_QUERIES[qi % len(MEDICAL_QUERIES)][:6]}{doc_type}",
                "factors": {
                    "semantic": round(semantic, 4),
                    "authority": round(authority, 4),
                    "intent": round(intent, 4),
                    "timeliness": round(timeliness, 4),
                    "hierarchy": round(hierarchy, 4),
                },
                "label": label,
            })

        docs_by_query.append({
            "query_id": f"q_{qi:03d}",
            "query": query_text,
            "docs": docs,
        })

    return docs_by_query


# ============================================================
# 真实 API 数据采集
# ============================================================
def load_test_queries(max_queries=None, seed=42):
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
        if max_queries and len(queries) > max_queries:
            rng = random.Random(seed)
            queries = rng.sample(queries, max_queries)
        return queries
    except Exception as e:
        print(f"[WARN] 无法加载 test_scenarios.json: {e}")
    return []


def keyword_label(content, expected_keywords):
    """基于关键词匹配计算相关性标签 (0/1/2)"""
    if not content or not expected_keywords:
        return 0
    content_lower = content.lower()
    hits = sum(1 for kw in expected_keywords if kw.lower() in content_lower)
    rate = hits / len(expected_keywords) if expected_keywords else 0
    if rate >= 0.5:
        return 2
    elif rate >= 0.2:
        return 1
    return 0


def collect_real_factors(queries, top_k=20):
    """调用 /api/rag/retrieval-test/factor-breakdown 采集五因子分数"""
    import requests

    client = requests.Session()
    client.headers.update({"Content-Type": "application/json"})
    docs_by_query = []
    total = len(queries)

    print(f"\n开始采集 {total} 条查询的五因子分解数据...")
    print(f"目标服务: {BASE_URL}")
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
                            "factors": {
                                "semantic": round(float(doc.get("semantic", 0.5)), 4),
                                "authority": round(float(doc.get("authority", 0.5)), 4),
                                "intent": round(float(doc.get("intent", 0.5)), 4),
                                "timeliness": round(float(doc.get("timeliness", 0.7)), 4),
                                "hierarchy": round(float(doc.get("hierarchy", 0.5)), 4),
                            },
                            "label": keyword_label(
                                doc.get("content", ""), q.get("expected_keywords", [])
                            ),
                        })
                    docs_by_query.append({
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
    print(f"采集完成: {len(docs_by_query)}条有效查询, 总耗时: {elapsed:.1f}s")
    return docs_by_query


# ============================================================
# 第1轮：粗筛（5组权重 → 淘汰2组）
# ============================================================
def run_round1(docs_by_query, output_prefix=None):
    """对5组权重计算 MRR@All 和 Recall@10，淘汰最差的2组"""
    if output_prefix is None:
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_prefix = f"round1_{ts}"

    # 标签分布检查
    dist = check_label_distribution(docs_by_query)
    print("\n" + "=" * 64)
    print("  标签分布质检")
    print("=" * 64)
    print(f"  总标注数: {sum(dist['counts'].values())}")
    print(f"  Label=2 (直接回答): {dist['counts'][2]:5d}  ({dist['percentages'][2]:5.1f}%)")
    print(f"  Label=1 (提及主题): {dist['counts'][1]:5d}  ({dist['percentages'][1]:5.1f}%)")
    print(f"  Label=0 (答非所问): {dist['counts'][0]:5d}  ({dist['percentages'][0]:5.1f}%)")
    if not dist["healthy"]:
        print("\n  [警告] 标签分布不健康:")
        for w in dist["warnings"]:
            print(f"    - {w}")
    else:
        print("  [OK] 标签分布健康 (2:1:0 ≈ 3:4:3)")

    # 评估5组权重
    print("\n" + "=" * 64)
    print("  第1轮：粗筛 —— 5组权重 MRR@All + Recall@10")
    print("=" * 64)

    results = {}
    for group_id, weights in WEIGHT_GROUPS.items():
        w = {k: v for k, v in weights.items() if k != "name"}
        mrr, recall10, _ = evaluate_group(docs_by_query, w)
        results[group_id] = {
            "name": weights["name"],
            "weights": w,
            "mrr": round(mrr, 6),
            "recall10": round(recall10, 6),
        }

    # 按 MRR 降序排名
    ranked = sorted(results.items(), key=lambda x: x[1]["mrr"], reverse=True)

    print(f"\n  {'组':>3s}  {'名称':<16s} │ {'authority':>9s} {'timeliness':>10s} "
          f"{'semantic':>8s} {'intent':>6s} {'hierarchy':>9s} │ {'MRR@All':>10s} {'Recall@10':>10s} │ 排名")
    print("  " + "-" * 108)
    for rank, (gid, info) in enumerate(ranked, 1):
        w = info["weights"]
        flag = ""
        if info["mrr"] < MRR_BASELINE:
            flag += " [MRR不达标]"
        if info["recall10"] < RECALL_BASELINE:
            flag += " [Recall不达标]"
        print(f"  {gid:>3s}  {info['name']:<16s} │ {w['authority']:9.2f} {w['timeliness']:10.2f} "
              f"{w['semantic']:8.2f} {w['intent']:6.2f} {w['hierarchy']:9.2f} │ "
              f"{info['mrr']:10.6f} {info['recall10']:10.4f} │ #{rank}{flag}")

    # 淘汰规则
    eliminated = [gid for gid, _ in ranked[-2:]]
    surviving = [gid for gid, _ in ranked[:3]]

    # 检查第3名和第4名差距
    if len(ranked) >= 4:
        gap = ranked[2][1]["mrr"] - ranked[3][1]["mrr"]
        if gap < 0.03:
            print(f"\n  [注意] 第3名与第4名 MRR 差距仅 {gap:.4f} (< 0.03)，建议保留4组进入第2轮")
            eliminated = [ranked[-1][0]]
            surviving = [gid for gid, _ in ranked[:4]]

    # 检查 Recall 不稳定标记
    for gid, info in ranked:
        if info["recall10"] < RECALL_BASELINE and gid in surviving:
            print(f"  [警告] {gid}组 Recall@10={info['recall10']:.4f} < {RECALL_BASELINE}，召回不稳定")

    print(f"\n  淘汰组: {', '.join(f'{gid}({results[gid][chr(34)+chr(34)]})' for gid in eliminated)}" if False else "")
    print(f"  淘汰组: {', '.join(f'{gid}({results[gid]["name"]})' for gid in eliminated)}")
    print(f"  晋级组: {', '.join(f'{gid}({results[gid]["name"]})' for gid in surviving)}")

    # 保存结果
    output = {
        "round": 1,
        "description": "粗筛：5组预设权重 MRR@All + Recall@10 对比",
        "timestamp": datetime.now().isoformat(),
        "label_distribution": dist,
        "results": {gid: results[gid] for gid, _ in ranked},
        "ranking": [gid for gid, _ in ranked],
        "eliminated": eliminated,
        "surviving": surviving,
    }

    json_path = os.path.join(OUTPUT_DIR, f"{output_prefix}.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存: {json_path}")

    return output


# ============================================================
# 消融权重工具
# ============================================================
def zero_out_dimension(weights, dim_to_zero):
    """将指定维度置0，其他维度等比例放大补足1.0"""
    new_weights = dict(weights)
    new_weights[dim_to_zero] = 0.0
    remaining_sum = sum(v for k, v in new_weights.items())
    if remaining_sum > 0:
        scale = 1.0 / remaining_sum
        for k in new_weights:
            new_weights[k] = round(new_weights[k] * scale, 6)
    return new_weights


# ============================================================
# 第2轮：消融测试
# ============================================================
def run_round2(docs_by_query, round1_output, output_prefix=None):
    """对晋级组分别砍 semantic 和 hierarchy，观察 MRR 跌幅"""
    if output_prefix is None:
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_prefix = f"round2_{ts}"

    surviving = round1_output["surviving"]
    original_results = round1_output["results"]

    print("\n" + "=" * 64)
    print("  第2轮：消融测试 —— 砍 semantic / hierarchy，观察 MRR 跌幅")
    print("=" * 64)

    ablation_results = {}
    for group_id in surviving:
        orig_weights = original_results[group_id]["weights"]
        orig_mrr = original_results[group_id]["mrr"]

        # 砍 semantic
        w_no_sem = zero_out_dimension(orig_weights, "semantic")
        mrr_no_sem, recall_no_sem, _ = evaluate_group(docs_by_query, w_no_sem)

        # 砍 hierarchy
        w_no_hier = zero_out_dimension(orig_weights, "hierarchy")
        mrr_no_hier, recall_no_hier, _ = evaluate_group(docs_by_query, w_no_hier)

        drop_sem = (orig_mrr - mrr_no_sem) / orig_mrr * 100 if orig_mrr > 0 else 0
        drop_hier = (orig_mrr - mrr_no_hier) / orig_mrr * 100 if orig_mrr > 0 else 0

        ablation_results[group_id] = {
            "name": original_results[group_id]["name"],
            "original_mrr": orig_mrr,
            "no_semantic_mrr": round(mrr_no_sem, 6),
            "no_semantic_drop_pct": round(drop_sem, 2),
            "no_hierarchy_mrr": round(mrr_no_hier, 6),
            "no_hierarchy_drop_pct": round(drop_hier, 2),
            "no_semantic_weights": w_no_sem,
            "no_hierarchy_weights": w_no_hier,
        }

        # 维度重要性判断
        sem_verdict = _dimension_verdict("semantic", drop_sem)
        hier_verdict = _dimension_verdict("hierarchy", drop_hier)

        print(f"\n  {group_id}组 ({original_results[group_id]['name']})")
        print(f"    原 MRR: {orig_mrr:.6f}")
        print(f"    砍 semantic: MRR={mrr_no_sem:.6f}, 跌幅={drop_sem:+.2f}% → {sem_verdict}")
        print(f"    砍 hierarchy: MRR={mrr_no_hier:.6f}, 跌幅={drop_hier:+.2f}% → {hier_verdict}")

    # 保存结果
    output = {
        "round": 2,
        "description": "消融测试：对晋级组分别砍 semantic/hierarchy",
        "timestamp": datetime.now().isoformat(),
        "surviving_groups": surviving,
        "ablation_results": ablation_results,
    }

    json_path = os.path.join(OUTPUT_DIR, f"{output_prefix}.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存: {json_path}")

    return output


def _dimension_verdict(dim_name, drop_pct):
    """消融跌幅判定"""
    if drop_pct < 3:
        return f"[可砍] {dim_name} 几乎无用，可从配置中移除"
    elif drop_pct < 10:
        return f"[弱贡献] {dim_name} 有弱贡献，保留但可降低权重"
    else:
        return f"[关键因子] {dim_name} 必须保留"


# ============================================================
# 第3轮：盲测卡片生成
# ============================================================
def run_round3(docs_by_query, round1_output, round2_output=None, n_samples=20, seed=123, output_prefix=None):
    """随机抽取 Query，对晋级组生成盲测卡片（Markdown格式）"""
    if output_prefix is None:
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_prefix = f"round3_{ts}"

    surviving = round1_output["surviving"]
    original_results = round1_output["results"]
    rng = random.Random(seed)

    # 按 label 分布分层抽样
    type_a_queries = []  # 至少1个 label=2
    type_b_queries = []  # 有 label=1 无 label=2
    type_c_queries = []  # 全部 label=0

    for q_data in docs_by_query:
        labels = {d["label"] for d in q_data["docs"]}
        if 2 in labels:
            type_a_queries.append(q_data)
        elif 1 in labels:
            type_b_queries.append(q_data)
        else:
            type_c_queries.append(q_data)

    # 按 1/3 比例各取
    per_type = max(n_samples // 3, 1)
    sampled_a = rng.sample(type_a_queries, min(per_type, len(type_a_queries)))
    sampled_b = rng.sample(type_b_queries, min(per_type, len(type_b_queries)))
    sampled_c = rng.sample(type_c_queries, min(per_type, len(type_c_queries)))
    sampled = sampled_a + sampled_b + sampled_c
    rng.shuffle(sampled)

    # 确保总数不超过 n_samples
    if len(sampled) > n_samples:
        sampled = sampled[:n_samples]

    print("\n" + "=" * 64)
    print("  第3轮：盲测卡片生成")
    print("=" * 64)
    print(f"  样本分布: A型(有答案)={len(sampled_a)}, B型(弱相关)={len(sampled_b)}, "
          f"C型(无关)={len(sampled_c)}, 合计={len(sampled)}")

    # 为每个 Query 生成排序结果（方案 X/Y/Z → 随机映射到晋级组）
    blind_cards = []
    voting_sheet = []

    for qi, q_data in enumerate(sampled, 1):
        # 随机排列 X/Y/Z → 晋级组映射
        shuffled_groups = list(surviving)
        rng.shuffle(shuffled_groups)
        mapping = {"X": shuffled_groups[0], "Y": shuffled_groups[1], "Z": shuffled_groups[2]}

        card = {
            "query_id": q_data["query_id"],
            "query": q_data["query"],
            "mapping": mapping,
            "schemes": {},
        }

        for scheme_label in ["X", "Y", "Z"]:
            group_id = mapping[scheme_label]
            weights = original_results[group_id]["weights"]
            ranked = rank_docs(q_data["docs"], weights)
            top5_ids = [r["doc_id"] for r in ranked[:5]]
            card["schemes"][scheme_label] = {
                "group_id": group_id,
                "group_name": original_results[group_id]["name"],
                "top5_doc_ids": top5_ids,
            }

        blind_cards.append(card)

        # 投票记录行
        voting_sheet.append({
            "query_id": q_data["query_id"],
            "query": q_data["query"],
            "voter1": "[   ]",
            "voter2": "[   ]",
            "voter3": "[   ]",
            "winner": "[待填写]",
        })

    # 生成 Markdown 盲测卡片
    md_path = os.path.join(OUTPUT_DIR, f"{output_prefix}_cards.md")
    _write_blind_cards_markdown(blind_cards, md_path)

    # 生成揭盲表（评审者不可见）
    key_path = os.path.join(OUTPUT_DIR, f"{output_prefix}_key.json")
    with open(key_path, "w", encoding="utf-8") as f:
        json.dump(blind_cards, f, ensure_ascii=False, indent=2)

    # 生成投票记录表
    vote_path = os.path.join(OUTPUT_DIR, f"{output_prefix}_voting.md")
    _write_voting_sheet(voting_sheet, surviving, original_results, vote_path)

    print(f"\n  盲测卡片: {md_path}")
    print(f"  揭盲密钥: {key_path} (评审结束后查看)")
    print(f"  投票记录表: {vote_path}")

    return {"blind_cards": blind_cards, "voting_sheet": voting_sheet}


def _write_blind_cards_markdown(blind_cards, output_path):
    """生成 Markdown 格式的盲测卡片"""
    lines = [
        "# 权重对比盲测卡片",
        "",
        "> **说明**: 以下每个 Query 给出3种排序方案（X/Y/Z），请选出排序最合理的方案。",
        "> 方案 X/Y/Z 对应的实际权重配置是**随机打乱的**，每个 Query 的映射关系不同。",
        "> **评审后请将投票结果填入投票记录表。**",
        "",
        "---",
    ]

    for qi, card in enumerate(blind_cards, 1):
        lines.append(f"## Query #{qi}: {card['query']}")
        lines.append("")

        for scheme_label in ["X", "Y", "Z"]:
            scheme = card["schemes"][scheme_label]
            lines.append(f"### 方案 {scheme_label}")
            lines.append("| 排名 | 文档ID |")
            lines.append("|------|--------|")
            for rank, doc_id in enumerate(scheme["top5_doc_ids"], 1):
                lines.append(f"| {rank} | {doc_id} |")
            lines.append("")

        lines.append("**我的选择**: [  ] X   [  ] Y   [  ] Z")
        lines.append("")
        lines.append("---")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def _write_voting_sheet(voting_sheet, surviving, original_results, output_path):
    """生成投票记录表 Markdown"""
    group_names = ", ".join(f"{gid}={original_results[gid]['name']}" for gid in surviving)
    lines = [
        "# 盲测投票记录表",
        "",
        f"晋级组: {group_names}",
        "",
        "| Query ID | Query | 评审者1 | 评审者2 | 评审者3 | 获胜方案 |",
        "|----------|-------|---------|---------|---------|----------|",
    ]
    for v in voting_sheet:
        lines.append(f"| {v['query_id']} | {v['query'][:30]} | [ ] | [ ] | [ ] | [待填写] |")

    lines.extend([
        "",
        "## 得票统计",
        "",
        "| 方案 | 实际权重组 | 得票数 |",
        "|------|-----------|--------|",
    ])
    for gid in surviving:
        lines.append(f"| [待揭盲] | {gid} ({original_results[gid]['name']}) | [待统计] |")

    lines.extend([
        "",
        "## 最终结论",
        "",
        "- **获胜组**: [待填写]",
        "- **定稿权重**: authority=[ ], timeliness=[ ], semantic=[ ], intent=[ ], hierarchy=[ ]",
        "- **调整理由**: [待填写]",
    ])

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


# ============================================================
# 最终决策逻辑
# ============================================================
def print_final_recommendation(winning_group, ablation_results=None):
    """根据获胜组输出权重调整建议"""
    print("\n" + "=" * 64)
    print("  最终决策建议")
    print("=" * 64)

    recommendations = {
        "A": "维持当前语义权重 0.40，或小幅降至 0.35。语义信号在小样本下已有足够区分度。",
        "B": "将 semantic 降至 0.25，hierarchy 升至 0.30~0.35。小样本下标题/层级匹配是最稳健的信号。",
        "C": "将 intent 提升至 0.30，semantic 降至 0.25。意图消歧对医学查询排序贡献显著。",
        "D": "将 authority 升至 0.30，timeliness 升至 0.25，semantic 压低至 0.20。来源可信度比语义相似度更重要。",
        "E": "保持当前配置不变。现有权重已经过充分调优，无需调整。",
    }

    print(f"\n  获胜组: {winning_group} ({WEIGHT_GROUPS[winning_group]['name']})")
    print(f"  建议: {recommendations.get(winning_group, '请人工决策')}")

    # 维度冗余处理建议
    if ablation_results:
        for gid, ar in ablation_results.items():
            for dim in ["semantic", "hierarchy"]:
                key = f"no_{dim}_drop_pct"
                if ar.get(key, 0) < 3:
                    print(f"\n  [维度冗余] {gid}组 {dim} 跌幅仅 {ar[key]:.2f}%，可砍。")
                    print(f"  建议: 将省出的权重补给 hierarchy（小样本下标题信号最稳）。")


# ============================================================
# 数据加载器（统一入口）
# ============================================================
def load_data(args):
    """根据命令行参数加载测试数据"""
    if args.mock:
        print(f"生成模拟测试数据: {args.queries}条查询 × {args.docs_per_query}篇文档...")
        return generate_mock_data(
            n_queries=args.queries,
            docs_per_query=args.docs_per_query,
            seed=args.seed,
        )
    else:
        queries = load_test_queries(max_queries=args.queries, seed=args.seed)
        if not queries:
            print("[ERROR] 未找到测试查询。请确保 test_scenarios.json 存在。")
            sys.exit(1)
        print(f"加载测试查询: {len(queries)}条, 目标服务: {BASE_URL}")
        return collect_real_factors(queries, top_k=args.top_k)


# ============================================================
# CLI 入口
# ============================================================
def main():
    parser = argparse.ArgumentParser(
        description="多因子权重对比测试 —— 3轮定稿流程",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  py -3 scripts/confidence/weight_test.py round1 --mock
  py -3 scripts/confidence/weight_test.py round1 --real --queries 100
  py -3 scripts/confidence/weight_test.py round2 --input round1_xxx.json
  py -3 scripts/confidence/weight_test.py round3 --input round1_xxx.json
  py -3 scripts/confidence/weight_test.py all --mock
        """,
    )

    sub = parser.add_subparsers(dest="command", help="测试轮次")

    # 公共参数
    def add_common_args(p):
        p.add_argument("--mock", action="store_true", help="使用模拟数据（不调用API）")
        p.add_argument("--queries", type=int, default=50, help="测试Query数量（默认50）")
        p.add_argument("--docs-per-query", type=int, default=20, help="模拟模式下每Query文档数")
        p.add_argument("--seed", type=int, default=42, help="随机种子")
        p.add_argument("--top-k", type=int, default=20, help="API模式下每Query检索文档数")

    def add_input_arg(p):
        p.add_argument("--input", "-i", required=True, help="上一轮的JSON结果文件路径")
        p.add_argument("--output", "-o", default=None, help="输出文件前缀")

    # round1
    p1 = sub.add_parser("round1", help="第1轮：粗筛（5组权重对比，淘汰2组）")
    add_common_args(p1)
    p1.add_argument("--output", "-o", default=None, help="输出文件前缀")

    # round2
    p2 = sub.add_parser("round2", help="第2轮：消融测试（砍semantic/hierarchy）")
    add_common_args(p2)
    add_input_arg(p2)

    # round3
    p3 = sub.add_parser("round3", help="第3轮：盲测卡片生成")
    add_common_args(p3)
    add_input_arg(p3)
    p3.add_argument("--n-samples", type=int, default=20, help="盲测Query抽样数（默认20）")
    p3.add_argument("--blind-seed", type=int, default=123, help="盲测随机种子")

    # all
    p_all = sub.add_parser("all", help="执行全部3轮测试")
    add_common_args(p_all)
    p_all.add_argument("--n-samples", type=int, default=20, help="盲测Query抽样数（默认20）")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")

    if args.command == "round1":
        docs = load_data(args)
        run_round1(docs, output_prefix=args.output)

    elif args.command == "round2":
        with open(args.input, "r", encoding="utf-8") as f:
            r1 = json.load(f)
        docs = load_data(args)
        run_round2(docs, r1, output_prefix=args.output)

    elif args.command == "round3":
        with open(args.input, "r", encoding="utf-8") as f:
            r1 = json.load(f)
        docs = load_data(args)
        run_round3(docs, r1, n_samples=args.n_samples, seed=args.blind_seed, output_prefix=args.output)

    elif args.command == "all":
        docs = load_data(args)
        prefix_base = f"weight_test_{ts}"
        r1 = run_round1(docs, output_prefix=f"{prefix_base}_round1")
        r2 = run_round2(docs, r1, output_prefix=f"{prefix_base}_round2")
        run_round3(docs, r1, r2, n_samples=args.n_samples, output_prefix=f"{prefix_base}_round3")
        # 最终建议（默认E组为基线，实际使用时应以盲测获胜组为准）
        print_final_recommendation("E", r2.get("ablation_results"))


if __name__ == "__main__":
    main()
