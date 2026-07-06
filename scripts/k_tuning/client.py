#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HTTP客户端封装
处理重试、超时、RTT测量、请求/响应日志
"""

import time
import requests
import numpy as np
from typing import Optional

from . import config


class RetrievalTestClient:
    """检索测试端点的HTTP客户端"""

    def __init__(self, base_url: str = None, timeout: float = None):
        self.base_url = (base_url or config.BASE_URL).rstrip("/")
        self.timeout = timeout or config.REQUEST_TIMEOUT
        self.session = requests.Session()
        self.session.headers.update({
            "Content-Type": "application/json",
            "Accept": "application/json",
        })
        self.rtt_ms = 0.0
        self._connected = False

    # ================================================================
    # 连接检查
    # ================================================================

    def check_health(self) -> bool:
        """检查服务是否可达"""
        try:
            resp = self.session.get(
                f"{self.base_url}/api/rag/health",
                timeout=5
            )
            self._connected = resp.status_code == 200
            return self._connected
        except Exception:
            self._connected = False
            return False

    def measure_rtt(self, count: int = None) -> float:
        """测量基线往返时间(RTT)，通过多次ping /api/rag/health"""
        pings = []
        n = count or config.RTT_PING_COUNT
        for _ in range(n):
            try:
                t0 = time.perf_counter()
                resp = self.session.get(
                    f"{self.base_url}/api/rag/health",
                    timeout=5
                )
                if resp.status_code == 200:
                    pings.append((time.perf_counter() - t0) * 1000)
            except Exception:
                pass

        if pings:
            self.rtt_ms = float(np.median(pings))
        else:
            self.rtt_ms = 0.0
        return self.rtt_ms

    def get_collection_info(self) -> dict:
        """获取Milvus/Lucene统计信息"""
        resp = self.session.get(
            f"{self.base_url}/api/rag/retrieval-test/collection-info",
            timeout=10
        )
        resp.raise_for_status()
        return resp.json()

    # ================================================================
    # 核心检索调用
    # ================================================================

    def search_single(self, pathway: str, query: str, top_k: int,
                      hybrid_per_side_k: int = None,
                      vector_weight: float = None,
                      keyword_weight: float = None) -> dict:
        """
        单次检索调用。

        参数:
            pathway: VECTOR / KEYWORD / HYBRID
            query: 查询文本
            top_k: 返回K值
            hybrid_per_side_k: 混合检索每路K值（仅HYBRID）
            vector_weight: 向量权重（仅HYBRID）
            keyword_weight: 关键词权重（仅HYBRID）

        返回:
            API响应的JSON字典
        """
        body = {
            "pathway": pathway.upper(),
            "query": query,
            "topK": top_k,
        }
        if hybrid_per_side_k is not None:
            body["hybridPerSideK"] = hybrid_per_side_k
        if vector_weight is not None:
            body["vectorWeight"] = vector_weight
        if keyword_weight is not None:
            body["keywordWeight"] = keyword_weight

        resp = self.session.post(
            f"{self.base_url}/api/rag/retrieval-test/single",
            json=body,
            timeout=self.timeout
        )
        resp.raise_for_status()
        return resp.json()

    def search_all_pathways(self, query: str, top_k: int = 500,
                            hybrid_per_side_k: int = None) -> dict:
        """并行执行三条通路，用于GT构建"""
        body = {
            "query": query,
            "topK": top_k,
        }
        if hybrid_per_side_k is not None:
            body["hybridPerSideK"] = hybrid_per_side_k

        resp = self.session.post(
            f"{self.base_url}/api/rag/retrieval-test/all-pathways",
            json=body,
            timeout=self.timeout * 2  # 全通路调用需要更长的超时
        )
        resp.raise_for_status()
        return resp.json()

    # ================================================================
    # 带计时的检索（预热 + 多次采样）
    # ================================================================

    def search_with_timing(self, pathway: str, query: str, top_k: int,
                           repeats: int = None, warmup: int = None,
                           hybrid_per_side_k: int = None) -> list[dict]:
        """
        带预热和多次采样的检索。

        返回:
            list[dict]: 每次采样的结果，包含clientLatencyMs字段
        """
        n_repeats = repeats or config.MEASUREMENT_REPEATS
        n_warmup = warmup or config.WARMUP_REPEATS

        # 预热调用（结果丢弃）
        for _ in range(n_warmup):
            try:
                self.search_single(pathway, query, top_k, hybrid_per_side_k)
            except Exception:
                pass

        # 正式采样
        samples = []
        for _ in range(n_repeats):
            t0 = time.perf_counter()
            try:
                result = self.search_single(pathway, query, top_k, hybrid_per_side_k)
                client_latency = (time.perf_counter() - t0) * 1000
                result["clientLatencyMs"] = round(client_latency, 2)
                samples.append(result)
            except Exception as e:
                samples.append({
                    "success": False,
                    "error": str(e),
                    "clientLatencyMs": (time.perf_counter() - t0) * 1000,
                })

        return samples

    def close(self):
        """关闭HTTP会话"""
        self.session.close()
