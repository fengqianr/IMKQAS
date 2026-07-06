#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IMKQAS 双路召回最优 K 值调优 - 主入口

功能:
  1. 连接后端服务，获取集合统计信息
  2. 加载测试场景，构建 Ground Truth
  3. 对三条通路（VECTOR/KEYWORD/HYBRID）进行 K 值扫描
  4. 检测拐点和最优 K 值
  5. 对推荐 K 值进行压力测试
  6. 生成可视化图表和报告

依赖: pip install requests numpy matplotlib

用法:
    python scripts/k_tuning/main.py
    IMKQAS_BASE_URL=http://localhost:8080 python scripts/k_tuning/main.py
"""

import sys
import os
import json
import time
import argparse

# 确保项目根目录在 Python 路径中
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from k_tuning import config
from k_tuning.client import RetrievalTestClient
from k_tuning.ground_truth import GroundTruthBuilder
from k_tuning.tuner import KTuner
from k_tuning.metrics import find_inflection_point, find_optimal_k, summarize_pathway
from k_tuning.stress_test import StressTester
from k_tuning.visualizer import plot_results
from k_tuning.report import (
    print_console_report, generate_markdown_report, save_results_json
)


def load_scenarios(path: str = None) -> list[dict]:
    """加载测试场景JSON文件"""
    filepath = path or config.TEST_SCENARIOS_PATH
    if not os.path.exists(filepath):
        print(f"⚠ 测试场景文件不存在: {filepath}")
        print("  使用内置默认查询...")
        return _default_scenarios()

    with open(filepath, "r", encoding="utf-8") as f:
        data = json.load(f)

    scenarios = data.get("scenarios", [])
    print(f"已加载 {len(scenarios)} 个测试场景")
    return scenarios


def _default_scenarios() -> list[dict]:
    """内置默认测试查询"""
    return [
        {"id": "Q01", "userQuery": "高血压有什么症状？",
         "expectedKeywords": ["高血压", "症状", "头痛", "眩晕", "心悸"]},
        {"id": "Q02", "userQuery": "糖尿病怎么治疗？",
         "expectedKeywords": ["糖尿病", "治疗", "胰岛素", "降糖药", "饮食控制"]},
        {"id": "Q03", "userQuery": "阿司匹林是治什么的？",
         "expectedKeywords": ["阿司匹林", "适应症", "解热", "镇痛", "抗血小板"]},
        {"id": "Q04", "userQuery": "怎么预防冠心病？",
         "expectedKeywords": ["冠心病", "预防", "健康饮食", "戒烟", "运动"]},
        {"id": "Q05", "userQuery": "小孩发烧怎么办？",
         "expectedKeywords": ["儿童", "发烧", "体温", "退烧", "儿科"]},
    ]


def main():
    parser = argparse.ArgumentParser(description="IMKQAS 双路召回 K 值调优")
    parser.add_argument("--base-url", default=config.BASE_URL,
                        help=f"后端服务地址 (默认: {config.BASE_URL})")
    parser.add_argument("--scenarios", default=config.TEST_SCENARIOS_PATH,
                        help="测试场景JSON文件路径")
    parser.add_argument("--skip-gt", action="store_true",
                        help="跳过GT构建（使用缓存的结果）")
    parser.add_argument("--pathway", choices=["VECTOR", "KEYWORD", "HYBRID", "ALL"],
                        default="ALL", help="仅测试指定通路")
    parser.add_argument("--k-values", type=int, nargs="+",
                        help="自定义K值列表，如: --k-values 5 10 20 50 100")
    parser.add_argument("--quick", action="store_true",
                        help="快速模式：减少采样和重复次数")
    args = parser.parse_args()

    # 应用参数
    if args.base_url != config.BASE_URL:
        config.BASE_URL = args.base_url
        config.RETRIEVAL_ENDPOINT = f"{args.base_url}/api/rag/retrieval-test"
        config.HEALTH_ENDPOINT = f"{args.base_url}/api/rag/health"

    if args.k_values:
        config.K_VALUES = args.k_values

    if args.quick:
        config.K_VALUES = [5, 10, 20, 50, 100, 200, 500]
        config.MEASUREMENT_REPEATS = 1
        config.WARMUP_REPEATS = 0
        config.GT_POOL_K = 200
        config.STRESS_ITERATIONS = 50
        print("⚡ 快速模式启用")

    print("=" * 60)
    print("  IMKQAS 双路召回最优 K 值调优工具 v1.0")
    print(f"  服务器: {config.BASE_URL}")
    print(f"  K值范围: {config.K_VALUES[0]}~{config.K_VALUES[-1]} "
          f"(共{len(config.K_VALUES)}个采样点)")
    print("=" * 60)

    # ================================================================
    # Step 0: 初始化客户端，检查连接
    # ================================================================
    print("\n[0/6] 连接检查...")
    client = RetrievalTestClient()

    if not client.check_health():
        print("  ✗ 无法连接到后端服务！")
        print(f"    请确认服务已启动: {config.BASE_URL}")
        print("    或使用 --base-url 指定其他地址")
        return 1

    rtt = client.measure_rtt()
    print(f"  ✓ 服务可达, RTT={rtt:.1f}ms")

    # 获取集合信息
    try:
        info = client.get_collection_info()
        milvus_count = info.get("milvusDocumentCount", "N/A")
        rag_cfg = info.get("ragConfig", {})
        print(f"  ✓ Milvus文档数: {milvus_count}")
        print(f"  ✓ 当前配置: initialTopK={rag_cfg.get('initialTopK')}, "
              f"rerankTopK={rag_cfg.get('rerankTopK')}, mode={rag_cfg.get('mode')}")
    except Exception as e:
        print(f"  ⚠ 获取集合信息失败: {e}")

    # ================================================================
    # Step 1: 加载测试场景
    # ================================================================
    print("\n[1/6] 加载测试场景...")
    scenarios = load_scenarios(args.scenarios)
    if not scenarios:
        print("  ✗ 无可用测试场景")
        return 1

    # ================================================================
    # Step 2: 构建 Ground Truth
    # ================================================================
    gt_data = {}
    if not args.skip_gt:
        print("\n[2/6] 构建 Ground Truth...")
        gt_builder = GroundTruthBuilder(client, scenarios)

        # 阶段1: 获取候选池
        gt_raw = gt_builder.build()

        # 阶段2: 对每个查询执行content关键词评分构建GT
        for query_id, info in gt_raw.items():
            query_text = info.get("query", "")
            keywords = info.get("keywords", [])
            pool_stats = info.get("pool_stats", {})

            if not query_text or not keywords:
                continue

            try:
                response = client.search_all_pathways(query_text, top_k=config.GT_POOL_K)
                chunk_contents = {}
                for pathway_key in ["vector", "keyword", "hybrid"]:
                    pathway_data = response.get(pathway_key, {})
                    for r in pathway_data.get("results", []):
                        chunk_id = r.get("chunkId")
                        content = r.get("content", "")
                        if chunk_id is not None and content:
                            chunk_contents[chunk_id] = content

                scored = gt_builder.score_by_content(chunk_contents, keywords)
                gt_set, gt_scores = gt_builder._phase3_select_ground_truth(
                    scored, config.GT_TOP_N
                )

                gt_data[query_id] = {
                    "ground_truth": gt_set,
                    "relevance_scores": gt_scores,
                    "pool_stats": pool_stats,
                    "query": query_text,
                    "keywords": keywords,
                }
            except Exception as e:
                print(f"  ⚠ {query_id} GT构建失败: {e}")
                # 至少保留查询文本，设为空GT
                gt_data[query_id] = {
                    "ground_truth": set(),
                    "relevance_scores": {},
                    "pool_stats": pool_stats,
                    "query": query_text,
                    "keywords": keywords,
                }

        valid_count = sum(1 for v in gt_data.values() if v.get("ground_truth"))
        print(f"  ✓ GT构建完成: {valid_count}个有效查询")
    else:
        print("\n[2/6] 跳过GT构建 (--skip-gt)")
        # 从缓存加载
        json_path = config.OUTPUT_JSON
        if os.path.exists(json_path):
            with open(json_path, "r", encoding="utf-8") as f:
                cached = json.load(f)
            # 从缓存恢复GT（简化处理）
            print(f"  从缓存加载: {json_path}")

    if not gt_data:
        print("  ⚠ 无有效GT数据，将使用无GT模式（仅测试延迟）")
        # 创建空GT结构以便继续
        for s in scenarios:
            qid = s.get("id", f"Q{scenarios.index(s)}")
            gt_data[qid] = {
                "ground_truth": set(),
                "relevance_scores": {},
                "query": s.get("userQuery", ""),
                "keywords": s.get("expectedKeywords", []),
            }

    # ================================================================
    # Step 3: K值扫描
    # ================================================================
    print("\n[3/6] K值扫描...")
    tuner = KTuner(client, gt_data)

    pathways_to_test = ["VECTOR", "KEYWORD", "HYBRID"] if args.pathway == "ALL" else [args.pathway]
    all_results = {}
    for pathway in pathways_to_test:
        result = tuner.tune_pathway(pathway)
        all_results[pathway] = result

    # ================================================================
    # Step 4: 拐点检测和最优K值分析
    # ================================================================
    print("\n[4/6] 拐点检测与最优K值分析...")
    inflection = {}
    optimal = {}

    for pathway, result in all_results.items():
        k_vals = result["k_values"]
        mb = result["marginal_benefit"]
        recall = result["avg_recall"]
        lat_p99 = result["latency_p99"]

        inf = find_inflection_point(k_vals, mb)
        opt = find_optimal_k(k_vals, recall, lat_p99)
        inflection[pathway] = inf
        optimal[pathway] = opt

        # 汇总
        all_results[pathway] = summarize_pathway(result)

        print(f"  {pathway}: 拐点K={inf['inflection_k']}, "
              f"饱和K={inf['saturation_k']}, "
              f"最优K={opt['optimal_k']} "
              f"(召回率={opt['recall_at_optimal']:.1f}%, "
              f"P99={opt['latency_p99_at_optimal']:.1f}ms, "
              f"约束满足={'✓' if opt['meets_constraints'] else '✗'})")

    # ================================================================
    # Step 5: 压力测试
    # ================================================================
    print("\n[5/6] 压力测试...")
    stress_tester = StressTester(client)

    # 收集查询列表
    all_queries = [info.get("query", "") for info in gt_data.values() if info.get("query")]
    if not all_queries:
        all_queries = ["高血压有什么症状？"]

    stress_results = {}
    for pathway in pathways_to_test:
        opt_k = optimal[pathway]["optimal_k"]
        stress_results[pathway] = stress_tester.run(
            pathway, opt_k, all_queries
        )

    # ================================================================
    # Step 6: 报告和可视化
    # ================================================================
    print("\n[6/6] 生成报告...")

    # 控制台报告
    print_console_report(all_results, optimal, stress_results, inflection)

    # 可视化
    try:
        plot_results(all_results, optimal)
    except Exception as e:
        print(f"  ⚠ 图表生成失败: {e}")

    # Markdown报告
    try:
        generate_markdown_report(all_results, optimal, stress_results, inflection)
    except Exception as e:
        print(f"  ⚠ Markdown报告生成失败: {e}")

    # JSON结果
    try:
        save_results_json(all_results, optimal, stress_results, inflection)
    except Exception as e:
        print(f"  ⚠ JSON结果保存失败: {e}")

    # 清理
    client.close()

    print("\n调优完成！")
    return 0


if __name__ == "__main__":
    sys.exit(main())
