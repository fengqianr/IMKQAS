#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""快速验证三个新端点的连通性"""

import json
import urllib.request
import urllib.error
import sys
import io

if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = "http://localhost:8080/api/rag/synonym-test"

def get(url):
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read())

def post(url, data):
    body = json.dumps(data, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body,
        headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read())

def main():
    print("=" * 50)
    print("端点连通性测试")
    print("=" * 50)

    # Test 1: GET /lookup
    print("\n[Test 1] GET /lookup?term=哮喘")
    try:
        r = get(f"{BASE}/lookup?term=哮喘")
        print(f"  响应: {json.dumps(r, ensure_ascii=False)}")
        print("  ✅ 通过")
    except Exception as e:
        print(f"  ❌ 失败: {e}")

    # Test 2: GET /lookup (unmapped term)
    print("\n[Test 2] GET /lookup?term=test123")
    try:
        r = get(f"{BASE}/lookup?term=test123")
        print(f"  响应: {json.dumps(r, ensure_ascii=False)}")
        assert r.get("found") == False
        print("  ✅ 通过 (未映射词条正确处理)")
    except Exception as e:
        print(f"  ❌ 失败: {e}")

    # Test 3: POST /llm-infer
    print("\n[Test 3] POST /llm-infer (单条LLM推断)")
    try:
        r = post(f"{BASE}/llm-infer", {
            "term": "拉肚子",
            "contextQuery": "小孩子拉肚子怎么办"
        })
        print(f"  响应: {json.dumps(r, ensure_ascii=False)}")
        if r.get("confidence", 0) > 0:
            print("  ✅ 通过 (LLM返回了推断结果)")
        else:
            print("  ⚠️  LLM返回置信度为0 (可能API未配置或超时)")
    except Exception as e:
        print(f"  ❌ 失败: {e}")

    # Test 4: POST /batch-llm-infer
    print("\n[Test 4] POST /batch-llm-infer (批量LLM推断)")
    try:
        r = post(f"{BASE}/batch-llm-infer", [
            {"term": "哮喘", "contextQuery": "我家宝宝儿童哮喘怎么治"},
            {"term": "拉肚子", "contextQuery": "小孩子拉肚子吃什么药"},
        ])
        print(f"  响应: total={r.get('total')}, success={r.get('successCount')}, fail={r.get('failCount')}")
        for item in r.get("results", []):
            print(f"    {item.get('term')}: std={item.get('standardTerm')}, conf={item.get('confidence')}")
        if r.get("success"):
            print("  ✅ 通过")
        else:
            print("  ⚠️  部分失败")
    except Exception as e:
        print(f"  ❌ 失败: {e}")

    print("\n" + "=" * 50)
    print("测试完成")
    print("=" * 50)

if __name__ == "__main__":
    main()
