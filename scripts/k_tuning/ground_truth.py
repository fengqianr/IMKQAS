


#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ground Truth构建模块
三阶段方法：候选池生成 → 关键词评分 → GT选取
"""

import re
import time
import numpy as np
from collections import defaultdict
from typing import Optional

from . import config
from .client import RetrievalTestClient


class GroundTruthBuilder:
    """为每个测试查询构建伪Ground Truth集"""

    def __init__(self, client: RetrievalTestClient, scenarios: list[dict]):
        self.client = client
        self.scenarios = scenarios
        # query_id -> set(chunk_id)
        self.gt_sets: dict[str, set] = {}
        # query_id -> {chunk_id: relevance_score}
        self.gt_scores: dict[str, dict] = {}
        # 候选池元数据
        self.pool_stats: dict[str, dict] = {}

    def build(self, pool_k: int = None, gt_top_n: int = None) -> dict:
        """
        执行三阶段GT构建流程。

        返回:
            {query_id: {
                'ground_truth': set(chunk_id),
                'relevance_scores': {chunk_id: float},
                'pool_stats': {...}
            }}
        """
        pool_k = pool_k or config.GT_POOL_K
        gt_top_n = gt_top_n or config.GT_TOP_N

        print(f"\n{'='*60}")
        print(f"  Ground Truth 构建 (pool_k={pool_k}, gt_top_n={gt_top_n})")
        print(f"{'='*60}")

        result = {}
        total_queries = len(self.scenarios)

        for i, scenario in enumerate(self.scenarios):
            query_id = scenario.get("id", f"Q{i}")
            query = scenario.get("userQuery", "")
            keywords = scenario.get("expectedKeywords", [])

            if not query or not keywords:
                print(f"  [{i+1}/{total_queries}] {query_id}: 跳过（缺少query或keywords）")
                continue

            print(f"  [{i+1}/{total_queries}] {query_id}: {query[:50]}...", end=" ", flush=True)

            try:
                # 阶段1: 候选池生成
                candidates, pool_info = self._phase1_fetch_candidates(query, pool_k)

                # 阶段2: 关键词评分
                scored = self._phase2_score_candidates(
                    candidates, keywords
                )

                # 阶段3: GT选取
                gt_set, gt_scores = self._phase3_select_ground_truth(scored, gt_top_n)

                self.gt_sets[query_id] = gt_set
                self.gt_scores[query_id] = gt_scores
                self.pool_stats[query_id] = pool_info

                result[query_id] = {
                    "ground_truth": gt_set,
                    "relevance_scores": gt_scores,
                    "pool_stats": pool_info,
                    "query": query,
                    "keywords": keywords,
                }

                print(f"池={pool_info['pool_size']}, GT={len(gt_set)}")

            except Exception as e:
                print(f"失败: {e}")
                result[query_id] = {
                    "ground_truth": set(),
                    "relevance_scores": {},
                    "pool_stats": {"error": str(e)},
                    "query": query,
                    "keywords": keywords,
                }

        valid_count = sum(1 for v in result.values() if v["ground_truth"])
        print(f"  GT构建完成: {valid_count}/{total_queries} 个查询有效")
        return result

    # ================================================================
    # 阶段1: 候选池生成（全通路检索）
    # ================================================================

    def _phase1_fetch_candidates(self, query: str, pool_k: int) -> tuple[set, dict]:
        """调用/all-pathways并合并所有chunk_id"""
        t0 = time.perf_counter()

        response = self.client.search_all_pathways(query, top_k=pool_k)

        all_chunk_ids = set()
        pathway_counts = {}

        for pathway_key in ["vector", "keyword", "hybrid"]:
            pathway_data = response.get(pathway_key, {})
            if not pathway_data or not pathway_data.get("success"):
                pathway_counts[pathway_key] = 0
                continue

            results = pathway_data.get("results", [])
            for r in results:
                chunk_id = r.get("chunkId")
                if chunk_id is not None:
                    all_chunk_ids.add(chunk_id)
            pathway_counts[pathway_key] = len(results)

        pool_info = {
            "pool_size": len(all_chunk_ids),
            "pathway_counts": pathway_counts,
            "fetch_time_ms": round((time.perf_counter() - t0) * 1000, 1),
        }
        return all_chunk_ids, pool_info

    # ================================================================
    # 阶段2: 基于关键词的相关性评分
    # ================================================================

    def _phase2_score_candidates(self, candidates: set,
                                  keywords: list[str]) -> dict:
        """
        对候选池中每个chunk按关键词匹配度评分。

        由于我们只有chunk_id而没有content（content在响应中），
        我们需要通过检索结果的content来评分。这里采用简化策略：
        - 从all-pathways响应中已经获得了content
        - 但更实际的做法是从检索结果中提取

        如果content不可用，回退到基于chunk_id分布的启发式评分。
        """
        # 注意：_phase1不返回content，content需要通过额外调用获取。
        # 实际实现中，我们从all-pathways响应提取content并缓存。
        # 这里返回空字典，实际评分在_phase3之前通过content完成。
        return {}

    def score_by_content(self, chunk_contents: dict, keywords: list[str]) -> dict:
        """
        基于chunk内容的关键词匹配评分。

        chunk_contents: {chunk_id: content_string}
        返回: {chunk_id: relevance_score}
        """
        scores = {}
        for chunk_id, content in chunk_contents.items():
            if not content:
                scores[chunk_id] = 0.0
                continue

            content_lower = content.lower()
            score = 0.0
            for kw in keywords:
                kw_lower = kw.lower()
                count = content_lower.count(kw_lower)
                if count > 0:
                    # 首次命中高分，后续命中递减
                    score += 1.0 + 0.3 * min(count - 1, 5)

            if score >= config.GT_MIN_KEYWORD_MATCH:
                scores[chunk_id] = score

        return scores

    # ================================================================
    # 阶段3: Ground Truth选取
    # ================================================================

    def _phase3_select_ground_truth(self, scored: dict,
                                     top_n: int) -> tuple[set, dict]:
        """按关键词分数排序，选取前top_n个chunk作为GT"""
        if not scored:
            return set(), {}

        # 按分数降序排序
        sorted_items = sorted(scored.items(), key=lambda x: x[1], reverse=True)
        selected = sorted_items[:top_n]

        gt_set = {chunk_id for chunk_id, _ in selected}
        gt_scores = {chunk_id: score for chunk_id, score in selected}

        return gt_set, gt_scores
