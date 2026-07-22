# -*- coding: utf-8 -*-
"""
HTTP客户端 — 封装QA问答端点
"""

import requests
import time
from . import config


class SafetyGateTestClient:
    """安全门控测试端点的HTTP客户端"""

    def __init__(self, base_url=None):
        self.base_url = base_url or config.BASE_URL
        self.qa_endpoint = config.QA_ENDPOINT
        self.health_endpoint = config.HEALTH_ENDPOINT
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    def check_health(self):
        """检查服务器连通性"""
        try:
            r = self.session.get(self.health_endpoint, timeout=5)
            return r.status_code == 200
        except Exception:
            return False

    def ask(self, query, user_id=None, conversation_id=None):
        """
        调用QA问答接口，获取包含置信度的完整响应。

        返回: {
            success, query, answer, rawConfidence, confidence,
            intentType, modelUsed, processingTime, retrievedContext
        } 或 None
        """
        body = {"question": query}
        if user_id:
            body["userId"] = user_id
        if conversation_id:
            body["conversationId"] = conversation_id

        try:
            r = self.session.post(
                self.qa_endpoint,
                json=body,
                timeout=config.REQUEST_TIMEOUT
            )
            if r.status_code == 200:
                data = r.json()
                resp_data = data.get("data", data)
                return {
                    "success": True,
                    "query": resp_data.get("query", query),
                    "answer": resp_data.get("answer", ""),
                    "rawConfidence": resp_data.get("rawConfidence", 0.0),
                    "confidence": resp_data.get("confidence", 0.0),
                    "intentType": resp_data.get("intentType", "UNKNOWN"),
                    "modelUsed": resp_data.get("modelUsed", "unknown"),
                    "processingTime": resp_data.get("processingTime", 0),
                    "retrievedContext": resp_data.get("retrievedContext", []),
                    "citations": resp_data.get("citations", [])
                }
            return {
                "success": False,
                "query": query,
                "answer": "",
                "rawConfidence": 0.0,
                "confidence": 0.0,
                "error": f"HTTP {r.status_code}: {r.text[:200]}"
            }
        except Exception as e:
            return {
                "success": False,
                "query": query,
                "answer": "",
                "rawConfidence": 0.0,
                "confidence": 0.0,
                "error": str(e)
            }

    def ask_batch(self, queries, delay=None):
        """
        批量调用QA接口（顺序执行，内置延迟控制）。

        queries: 查询字符串列表
        delay: 每次调用间隔秒数，默认使用config.LLM_CALL_DELAY_SEC

        返回: [(query, response), ...]
        """
        if delay is None:
            delay = config.LLM_CALL_DELAY_SEC

        results = []
        total = len(queries)
        for i, q in enumerate(queries):
            resp = self.ask(q)
            results.append((q, resp))
            if resp and resp.get("success"):
                print(f"  QA进度: {i + 1}/{total} query='{q[:40]}...' "
                      f"rawConf={resp['rawConfidence']:.2f} conf={resp['confidence']:.2f}")
            else:
                error = resp.get("error", "未知错误") if resp else "无响应"
                print(f"  QA进度: {i + 1}/{total} query='{q[:40]}...' ERROR: {error}")
            if i < total - 1:
                time.sleep(delay)
        return results

    def measure_rtt(self, count=5):
        """测量平均RTT"""
        times = []
        for _ in range(count):
            t0 = time.time()
            try:
                self.session.get(self.health_endpoint, timeout=5)
                times.append((time.time() - t0) * 1000)
            except Exception:
                pass
        return sum(times) / len(times) if times else 0
