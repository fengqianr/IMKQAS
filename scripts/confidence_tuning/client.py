#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HTTP客户端 — 封装 synonym-test 三个端点
"""

import requests
import time
from . import config


class SynonymTestClient:
    """同义词扩展测试端点的HTTP客户端"""

    def __init__(self, base_url=None):
        self.base_url = base_url or config.BASE_URL
        self.endpoint = f"{self.base_url}/api/rag/synonym-test"
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    def check_health(self):
        """检查服务器连通性"""
        try:
            r = self.session.get(f"{self.base_url}/api/rag/health", timeout=5)
            return r.status_code == 200
        except Exception:
            return False

    def lookup_term(self, term):
        """
        查询单个术语的标准名称映射
        返回: {term, standardTerm, source, confidence, found} 或 None
        """
        try:
            r = self.session.get(
                f"{self.endpoint}/lookup",
                params={"term": term},
                timeout=config.REQUEST_TIMEOUT
            )
            if r.status_code == 200:
                return r.json()
            return None
        except Exception as e:
            print(f"  [WARN] lookup请求失败: term={term}, error={e}")
            return None

    def force_llm_infer(self, term, context_query):
        """
        强制LLM推断（绕过缓存）
        返回: {term, standardTerm, confidence, source}
        """
        try:
            r = self.session.post(
                f"{self.endpoint}/llm-infer",
                json={"term": term, "contextQuery": context_query},
                timeout=config.REQUEST_TIMEOUT
            )
            if r.status_code == 200:
                return r.json()
            return {"term": term, "standardTerm": None, "confidence": 0.0, "error": f"HTTP {r.status_code}"}
        except Exception as e:
            print(f"  [WARN] LLM推断请求失败: term={term}, error={e}")
            return {"term": term, "standardTerm": None, "confidence": 0.0, "error": str(e)}

    def batch_force_llm_infer(self, pairs):
        """
        批量强制LLM推断
        pairs: [{"term": str, "contextQuery": str}, ...]
        返回: {success, total, successCount, failCount, results: [...]}
        """
        try:
            r = self.session.post(
                f"{self.endpoint}/batch-llm-infer",
                json=pairs,
                timeout=config.REQUEST_TIMEOUT * 2
            )
            if r.status_code == 200:
                return r.json()
            return {"success": False, "error": f"HTTP {r.status_code}"}
        except Exception as e:
            print(f"  [WARN] 批量LLM推断失败: error={e}")
            return {"success": False, "error": str(e)}

    def measure_rtt(self, count=5):
        """测量平均RTT"""
        times = []
        for _ in range(count):
            t0 = time.time()
            try:
                self.session.get(f"{self.base_url}/api/rag/health", timeout=5)
                times.append((time.time() - t0) * 1000)
            except Exception:
                pass
        return sum(times) / len(times) if times else 0
