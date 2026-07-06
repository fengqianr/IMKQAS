#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
压力测试模块
对推荐K值进行并发压力测试
"""

import time
import random
import numpy as np
from concurrent.futures import ThreadPoolExecutor, as_completed

from . import config
from .client import RetrievalTestClient


class StressTester:
    """并发压力测试"""

    def __init__(self, client: RetrievalTestClient):
        self.client = client

    def run(self, pathway: str, k: int, queries: list[str],
            iterations: int = None, parallel: int = None) -> dict:
        """
        对指定通路和K值进行压力测试。

        参数:
            pathway: 通路名
            k: K值
            queries: 查询列表（随机选取）
            iterations: 总请求数
            parallel: 并发数

        返回:
            {p50_ms, p95_ms, p99_ms, p999_ms, mean_ms, std_ms,
             throughput_qps, success_rate, sla_pass}
        """
        n_iter = iterations or config.STRESS_ITERATIONS
        n_parallel = parallel or config.STRESS_PARALLEL

        print(f"\n  压测 {pathway} K={k}: {n_iter}次请求, {n_parallel}并发...")

        latencies = []
        successes = 0
        failures = 0
        t_start = time.perf_counter()

        with ThreadPoolExecutor(max_workers=n_parallel) as executor:
            futures = []
            for _ in range(n_iter):
                q = random.choice(queries) if queries else "高血压有什么症状？"
                future = executor.submit(
                    self._single_request, pathway, q, k
                )
                futures.append(future)

            for future in as_completed(futures):
                try:
                    latency_ms, success = future.result()
                    if success:
                        latencies.append(latency_ms)
                        successes += 1
                    else:
                        failures += 1
                except Exception:
                    failures += 1

        total_time = time.perf_counter() - t_start

        if not latencies:
            return self._empty_stats(k)

        lat_arr = np.array(latencies)
        stats = {
            "pathway": pathway,
            "k": k,
            "p50_ms": round(float(np.percentile(lat_arr, 50)), 2),
            "p95_ms": round(float(np.percentile(lat_arr, 95)), 2),
            "p99_ms": round(float(np.percentile(lat_arr, 99)), 2),
            "p999_ms": round(float(np.percentile(lat_arr, 99.9)), 2),
            "mean_ms": round(float(np.mean(lat_arr)), 2),
            "std_ms": round(float(np.std(lat_arr)), 2),
            "min_ms": round(float(np.min(lat_arr)), 2),
            "max_ms": round(float(np.max(lat_arr)), 2),
            "throughput_qps": round(successes / total_time, 2) if total_time > 0 else 0,
            "success_rate": round(successes / n_iter, 4),
            "total_time_s": round(total_time, 1),
            "sla_pass": float(np.percentile(lat_arr, 99)) < config.P99_LATENCY_SLA_MS,
        }

        verdict = "✓ 通过" if stats["sla_pass"] else "✗ 不通过"
        print(f"    P50={stats['p50_ms']}ms P99={stats['p99_ms']}ms "
              f"QPS={stats['throughput_qps']} 成功率={stats['success_rate']*100:.0f}% "
              f"SLA: {verdict}")

        return stats

    def _single_request(self, pathway: str, query: str, k: int) -> tuple[float, bool]:
        """单次请求，返回 (延迟ms, 是否成功)"""
        t0 = time.perf_counter()
        try:
            result = self.client.search_single(pathway, query, k,
                                                hybrid_per_side_k=k)
            latency = (time.perf_counter() - t0) * 1000
            return latency, result.get("success", False)
        except Exception:
            latency = (time.perf_counter() - t0) * 1000
            return latency, False

    def _empty_stats(self, k: int) -> dict:
        return {
            "pathway": "", "k": k,
            "p50_ms": 0, "p95_ms": 0, "p99_ms": 0, "p999_ms": 0,
            "mean_ms": 0, "std_ms": 0, "min_ms": 0, "max_ms": 0,
            "throughput_qps": 0, "success_rate": 0, "total_time_s": 0,
            "sla_pass": False,
        }
