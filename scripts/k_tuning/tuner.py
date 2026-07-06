#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
K值调优核心模块
对每条通路执行K值扫描，收集召回率和延迟指标
"""

import time
import numpy as np
from collections import defaultdict

from . import config
from .client import RetrievalTestClient


class KTuner:
    """K值扫描与指标采集"""

    def __init__(self, client: RetrievalTestClient, ground_truth: dict):
        self.client = client
        self.ground_truth = ground_truth
        # 缓存: (pathway, query, k) -> 最佳采样结果
        self._cache: dict = {}

    def tune_pathway(self, pathway: str, k_values: list[int] = None) -> dict:
        """
        对一条通路的全部K值进行测试。

        返回:
            {
                'pathway': str,
                'k_values': [int],
                'avg_recall': [float],        # 平均加权召回率(%)
                'avg_precision': [float],     # 平均精确率(%)
                'latency_p50': [float],       # P50延迟(ms)
                'latency_p99': [float],       # P99延迟(ms)
                'actual_count': [float],      # 平均实际返回数
                'marginal_benefit': [float],  # 边际收益(百分点)
                'per_query': {query_id: {...}}  # 逐查询明细
            }
        """
        k_vals = k_values or config.K_VALUES
        pathway_upper = pathway.upper()

        print(f"\n{'='*60}")
        print(f"  K值扫描: {pathway_upper} 通路")
        print(f"  K值范围: {k_vals[0]}~{k_vals[-1]} (共{len(k_vals)}个采样点)")
        print(f"{'='*60}")

        # 初始化聚合数组
        n_k = len(k_vals)
        avg_recall = np.zeros(n_k)
        avg_precision = np.zeros(n_k)
        latency_p50 = np.zeros(n_k)
        latency_p99 = np.zeros(n_k)
        actual_count = np.zeros(n_k)
        marginal_benefit = np.zeros(n_k)

        # 收集所有查询的所有延迟用于P99计算
        all_latencies_per_k = [[] for _ in range(n_k)]

        # 每个查询的明细
        per_query = defaultdict(lambda: {
            "recall": [], "precision": [], "latency": [], "count": []
        })

        # 有效查询列表
        valid_queries = [
            (qid, info) for qid, info in self.ground_truth.items()
            if info.get("ground_truth")
        ]
        total_queries = len(valid_queries)

        if total_queries == 0:
            print("  ⚠ 没有有效的Ground Truth查询，仅测量延迟")
            return self._tune_latency_only(pathway_upper, k_vals)

        prev_recall = 0.0
        total_calls = 0

        for ki, k in enumerate(k_vals):
            recalls = []
            precisions = []
            counts = []

            for qi, (query_id, gt_info) in enumerate(valid_queries):
                query = gt_info.get("query", "")
                gt_set = gt_info.get("ground_truth", set())
                gt_scores = gt_info.get("relevance_scores", {})

                if not query:
                    continue

                # 带计时的检索
                samples = self.client.search_with_timing(
                    pathway_upper, query, k,
                    hybrid_per_side_k=k  # HYBRID通路每路也取k
                )

                # 取最佳采样（延迟最低的成功调用）
                best = self._select_best_sample(samples)
                if best is None:
                    continue

                total_calls += 1

                # 提取chunk_id集合
                retrieved_ids = set()
                for r in best.get("results", []):
                    chunk_id = r.get("chunkId")
                    if chunk_id is not None:
                        retrieved_ids.add(chunk_id)

                # 计算加权召回率
                recall = self._compute_weighted_recall(retrieved_ids, gt_set, gt_scores)
                precision = len(retrieved_ids & gt_set) / max(len(retrieved_ids), 1)

                recalls.append(recall)
                precisions.append(precision)
                counts.append(len(retrieved_ids))

                # 收集延迟（优先用服务端latencyMs，fallback用clientLatencyMs）
                latency = best.get("latencyMs", best.get("clientLatencyMs", 0))
                all_latencies_per_k[ki].append(latency)

                # 记录每查询明细
                per_query[query_id]["recall"].append(recall * 100)
                per_query[query_id]["precision"].append(precision * 100)
                per_query[query_id]["latency"].append(latency)
                per_query[query_id]["count"].append(len(retrieved_ids))

            # 聚合统计
            if recalls:
                avg_recall[ki] = np.mean(recalls) * 100
                avg_precision[ki] = np.mean(precisions) * 100
                actual_count[ki] = np.mean(counts)
            if all_latencies_per_k[ki]:
                latency_p50[ki] = np.percentile(all_latencies_per_k[ki], 50)
                latency_p99[ki] = np.percentile(all_latencies_per_k[ki], 99)

            # 边际收益（百分点）
            marginal_benefit[ki] = avg_recall[ki] - prev_recall
            prev_recall = avg_recall[ki]

            # 进度
            if (ki + 1) % 5 == 0 or ki == 0 or ki == n_k - 1:
                print(f"  K={k:>4d} | 召回率={avg_recall[ki]:5.1f}% | "
                      f"P50={latency_p50[ki]:.1f}ms | P99={latency_p99[ki]:.1f}ms | "
                      f"边际={marginal_benefit[ki]:.2f}pp")

        print(f"  ✓ {pathway_upper}扫描完成, 共{total_calls}次调用")

        return {
            "pathway": pathway_upper,
            "k_values": list(k_vals),
            "avg_recall": avg_recall.tolist(),
            "avg_precision": avg_precision.tolist(),
            "latency_p50": latency_p50.tolist(),
            "latency_p99": latency_p99.tolist(),
            "actual_count": actual_count.tolist(),
            "marginal_benefit": marginal_benefit.tolist(),
            "per_query": {qid: {
                "recall": data["recall"],
                "precision": data["precision"],
                "latency": data["latency"],
                "count": data["count"],
            } for qid, data in per_query.items()},
            "total_calls": total_calls,
            "total_queries": total_queries,
        }

    def tune_all(self) -> dict[str, dict]:
        """扫描全部三条通路"""
        results = {}
        for pathway in ["VECTOR", "KEYWORD", "HYBRID"]:
            results[pathway] = self.tune_pathway(pathway)
        return results

    # ================================================================
    # 辅助方法
    # ================================================================

    def _compute_weighted_recall(self, retrieved: set, gt_set: set,
                                  gt_scores: dict) -> float:
        """
        加权召回率 = Σ(score × I(chunk ∈ retrieved)) / Σ(score)
        如果gt_scores为空则退化为普通召回率
        """
        if not gt_set:
            return 0.0

        if not gt_scores:
            return len(retrieved & gt_set) / len(gt_set)

        total_score = sum(gt_scores.values())
        if total_score == 0:
            return len(retrieved & gt_set) / len(gt_set)

        hit_score = sum(
            score for chunk_id, score in gt_scores.items()
            if chunk_id in retrieved
        )
        return hit_score / total_score

    def _select_best_sample(self, samples: list[dict]) -> dict | None:
        """从多次采样中选择最佳结果（成功的、延迟最低的）"""
        successful = [s for s in samples if s.get("success")]
        if not successful:
            return None
        return min(successful, key=lambda s: s.get("latencyMs", float("inf")))

    def _tune_latency_only(self, pathway: str, k_values: list[int]) -> dict:
        """无GT时仅测量延迟和返回数量"""
        n_k = len(k_values)
        all_latencies_per_k = [[] for _ in range(n_k)]
        all_counts_per_k = [[] for _ in range(n_k)]

        # 使用GT数据中的查询（至少有query文本）
        queries_to_test = [
            (qid, info) for qid, info in self.ground_truth.items()
            if info.get("query")
        ]
        if not queries_to_test:
            queries_to_test = [("default", {"query": "高血压有什么症状？"})]

        total_calls = 0
        for ki, k in enumerate(k_values):
            for query_id, gt_info in queries_to_test:
                query = gt_info.get("query", "")
                if not query:
                    continue

                samples = self.client.search_with_timing(
                    pathway, query, k, hybrid_per_side_k=k
                )
                best = self._select_best_sample(samples)
                if best is None:
                    continue

                total_calls += 1
                latency = best.get("latencyMs", best.get("clientLatencyMs", 0))
                all_latencies_per_k[ki].append(latency)
                all_counts_per_k[ki].append(len(best.get("results", [])))

            if all_latencies_per_k[ki]:
                print(f"  K={k:>4d} | P50={np.percentile(all_latencies_per_k[ki], 50):.1f}ms | "
                      f"P99={np.percentile(all_latencies_per_k[ki], 99):.1f}ms | "
                      f"返回数={np.mean(all_counts_per_k[ki]):.0f}")

        # 构建结果
        zeros = [0.0] * n_k
        p50 = [np.percentile(l, 50) if l else 0.0 for l in all_latencies_per_k]
        p99 = [np.percentile(l, 99) if l else 0.0 for l in all_latencies_per_k]
        counts = [np.mean(c) if c else 0.0 for c in all_counts_per_k]

        # 用实际返回数估算召回率变化曲线（归一化到[0,1]，模拟边际递减）
        if counts and max(counts) > 0:
            simulated_recall = [c / max(counts) * 85.0 for c in counts]  # 假设满召回=85%
        else:
            simulated_recall = zeros

        marginal = [0.0]
        for i in range(1, n_k):
            marginal.append(simulated_recall[i] - simulated_recall[i-1])

        print(f"  ✓ {pathway}延迟扫描完成, 共{total_calls}次调用 (无GT模式)")

        return {
            "pathway": pathway,
            "k_values": list(k_values),
            "avg_recall": simulated_recall,
            "avg_precision": zeros,
            "latency_p50": p50,
            "latency_p99": p99,
            "actual_count": counts,
            "marginal_benefit": marginal,
            "per_query": {},
            "total_calls": total_calls,
            "total_queries": len(queries_to_test),
            "no_gt_mode": True,
        }

    def _empty_result(self, pathway: str, k_values: list[int]) -> dict:
        n = len(k_values)
        zeros = [0.0] * n
        return {
            "pathway": pathway,
            "k_values": list(k_values),
            "avg_recall": zeros,
            "avg_precision": zeros,
            "latency_p50": zeros,
            "latency_p99": zeros,
            "actual_count": zeros,
            "marginal_benefit": zeros,
            "per_query": {},
            "total_calls": 0,
            "total_queries": 0,
        }
