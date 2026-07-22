# -*- coding: utf-8 -*-
"""
采集LLM原始置信度数据 — 调用 /api/qa/ask 接口

读取 raw_confidence（LLM门控前的原始置信度），按新评分标准判定正确性，
生成 threshold_scan.py 所需的缓存文件。

评分标准:
  - 主要标准: expectedKeywords 命中 >= 2 个 → 正确
  - 正确拒答: 回答为"知识不足/建议就医"且知识库无相关内容 → 计为正确

用法:
    py scripts/confidence/collect_real_confidence.py
    py scripts/confidence/collect_real_confidence.py --queries 500
    py scripts/confidence/collect_real_confidence.py --queries 500 --delay 1.5
"""

import json
import os
import sys
import time
import argparse
import random
from datetime import datetime

import requests
import numpy as np

# 路径配置
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_URL = os.environ.get("IMKQAS_BASE_URL", "http://localhost:8080")
CACHE_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_cache")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_results")
CACHE_FILE = os.path.join(CACHE_DIR, "llm_raw_confidence_cache.json")

os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 低置信度拒答模板的关键词（用于判断"正确拒答"）
# 对齐 LlmServiceImpl.inferConfidenceFromAnswer() 和 SafetyConfig 的拒答判断逻辑
LOW_CONFIDENCE_PATTERNS = [
    "当前知识不足",                  # LLM prompt明确拒答词（LlmServiceImpl:614,652）
    "无法生成可靠的回答",            # SafetyConfig.lowConfidenceResponse
    "无法从现有医学资料库中找到",    # SafetyConfig.noRetrievalResponse
    "当前无法处理您的查询",          # QaServiceImpl降级响应
    "无法回答",                      # LlmServiceImpl:907 + QaServiceImpl:760
    "没有相关信息",                  # LlmServiceImpl:908
]


def load_queries(max_queries=None, seed=42):
    """从 test_scenarios.json 加载测试查询（全量500条）"""
    path = os.path.join(SCRIPT_DIR, "..", "test_scenarios.json")
    if not os.path.exists(path):
        print(f"[ERROR] 测试场景文件不存在: {path}")
        sys.exit(1)

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    scenarios = data if isinstance(data, list) else data.get("scenarios", [])
    queries = []
    for s in scenarios:
        q = s.get("userQuery") or s.get("query") or s.get("question")
        if q:
            queries.append({
                "id": s.get("id", ""),
                "query": q,
                "disease": s.get("disease", ""),
                "expected_keywords": s.get("expectedKeywords", []),
            })

    if max_queries and len(queries) > max_queries:
        rng = random.Random(seed)
        queries = rng.sample(queries, max_queries)

    return queries


def is_low_confidence_reject(answer):
    """判断回答是否为低置信度拒答模板"""
    if not answer:
        return False
    for pattern in LOW_CONFIDENCE_PATTERNS:
        if pattern in answer:
            return True
    return False


def evaluate_correctness(answer, expected_keywords):
    """
    判定回答正确性。

    规则:
      1. 如果回答是低置信度拒答模板 → "正确拒答"（计为正确）
      2. 关键词命中 >= 2 → 正确
      3. 否则 → 错误
    """
    if not expected_keywords:
        return False, "no_keywords"

    # 规则1: 正确拒答
    if is_low_confidence_reject(answer):
        return True, "correct_reject"

    # 规则2: 关键词命中 >= 2
    if not answer:
        return False, "empty_answer"
    answer_lower = answer.lower()
    hits = sum(1 for kw in expected_keywords if kw.lower() in answer_lower)
    if hits >= 2:
        return True, f"keyword_{hits}"
    else:
        return False, f"keyword_{hits}"


def load_checkpoint():
    """加载上次的检查点"""
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                cache = json.load(f)
            if "metadata" not in cache:
                cache["metadata"] = {
                    "collectedQueries": len(cache.get("entries", {})),
                    "totalQueries": len(cache.get("entries", {})),
                }
            return cache
        except Exception:
            pass
    return {"entries": {}, "metadata": {"collectedQueries": 0}}


def save_checkpoint(cache):
    """保存检查点"""
    with open(CACHE_FILE, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=2)


def call_qa_api(query_text, timeout=120):
    """
    调用 /api/qa/ask 接口。
    返回 (raw_confidence, gated_confidence, answer, processing_time, intent_type, error)
    """
    try:
        resp = requests.post(
            f"{BASE_URL}/api/qa/ask",
            json={"question": query_text},
            headers={"Content-Type": "application/json"},
            timeout=timeout,
        )
        if resp.status_code == 200:
            body = resp.json()
            data = body.get("data", {})
            raw_conf = float(data.get("rawConfidence", data.get("confidence", 0.0)))
            gated_conf = float(data.get("confidence", 0.0))
            answer = data.get("answer", "") or ""
            proc_time = data.get("processingTime", 0)
            intent = data.get("intentType", "")
            return raw_conf, gated_conf, answer, proc_time, intent, None
        else:
            return 0.0, 0.0, "", 0, "", f"HTTP {resp.status_code}"
    except requests.Timeout:
        return 0.0, 0.0, "", 0, "", "timeout"
    except Exception as e:
        return 0.0, 0.0, "", 0, "", str(e)


def collect(queries, delay=1.0, resume=True):
    """采集所有查询的原始置信度数据"""
    cache = load_checkpoint() if resume else {"entries": {}, "metadata": {"collectedQueries": 0}}
    existing_ids = set(cache["entries"].keys())
    collected = cache["metadata"].get("collectedQueries", 0)

    pending = [q for q in queries if q["id"] not in existing_ids]
    total = len(queries)
    done = total - len(pending)

    if done > 0:
        print(f"从检查点恢复: 已完成 {done}/{total}, 剩余 {len(pending)}")

    if not pending:
        print("所有查询已采集完毕。")
        return cache

    print(f"\n开始采集 {len(pending)} 条查询的LLM原始置信度...")
    print(f"目标服务: {BASE_URL}")
    print(f"请求间隔: {delay}s\n")

    t0 = time.time()
    error_count = 0

    for i, q in enumerate(pending):
        query_id = q["id"]
        query_text = q["query"]
        expected_kw = q.get("expected_keywords", [])
        disease = q.get("disease", "")

        raw_conf, gated_conf, answer, proc_time, intent, error = call_qa_api(query_text)

        if error:
            error_count += 1
            print(f"  [{i+1}/{len(pending)}] {query_id}: [ERROR] {error}")
            is_correct, reason = False, f"api_error:{error}"
        else:
            is_correct, reason = evaluate_correctness(answer, expected_kw)

        # 关键词命中详情
        answer_lower = answer.lower() if answer else ""
        kw_hits = sum(1 for kw in expected_kw if kw.lower() in answer_lower) if expected_kw else 0
        is_reject = is_low_confidence_reject(answer)

        entry = {
            "queryId": query_id,
            "query": query_text,
            "disease": disease,
            "rawConfidence": round(raw_conf, 4),
            "gatedConfidence": round(gated_conf, 4),
            "llmConfidence": round(raw_conf, 4),  # 兼容 threshold_scan.py 的字段名
            "isLlmCorrect": is_correct,
            "correctReason": reason,
            "isCorrectReject": is_reject and is_correct,
            "hasAuthoritativeMapping": len(expected_kw) > 0,
            "answer": answer[:500] if answer else "",
            "keywordHits": f"{kw_hits}/{len(expected_kw)}" if expected_kw else "N/A",
            "processingTime": proc_time,
            "intentType": intent,
            "collectedAt": datetime.now().isoformat(),
        }

        cache["entries"][query_id] = entry
        collected += 1

        # 进度输出
        elapsed = time.time() - t0
        rate = (i + 1) / elapsed if elapsed > 0 else 0
        eta = (len(pending) - i - 1) / rate if rate > 0 else 0

        if is_reject and is_correct:
            mark = "[CORRECT-REJECT]"
        elif is_correct:
            mark = "[OK]"
        else:
            mark = "[WRONG]"

        print(f"  [{i+1}/{len(pending)}] {query_id}: raw={raw_conf:.3f} gated={gated_conf:.3f} "
              f"{mark} ({reason}) | {elapsed:.0f}s, ETA {eta:.0f}s")

        # 每50条保存检查点
        if (i + 1) % 50 == 0:
            cache["metadata"] = {
                "collectedQueries": collected,
                "totalQueries": total,
                "errors": error_count,
                "lastUpdated": datetime.now().isoformat(),
            }
            save_checkpoint(cache)
            print(f"  --- 检查点已保存 ({collected}/{total}) ---")

        if i < len(pending) - 1:
            time.sleep(delay)

    elapsed = time.time() - t0
    cache["metadata"] = {
        "collectedQueries": collected,
        "totalQueries": total,
        "errors": error_count,
        "elapsedSeconds": round(elapsed, 1),
        "completedAt": datetime.now().isoformat(),
    }
    save_checkpoint(cache)

    print(f"\n采集完成: {collected}条, 错误{error_count}条, 耗时 {elapsed:.1f}s")
    print(f"缓存文件: {CACHE_FILE}")

    return cache


def print_summary(cache):
    """打印采集数据摘要"""
    entries = list(cache["entries"].values())
    if not entries:
        print("无数据")
        return

    raw_confs = [e["llmConfidence"] for e in entries]
    correct_count = sum(1 for e in entries if e.get("isLlmCorrect"))
    correct_reject_count = sum(1 for e in entries if e.get("isCorrectReject"))
    keyword_correct_count = correct_count - correct_reject_count
    authoritative = [e for e in entries if e.get("hasAuthoritativeMapping")]

    print("\n" + "=" * 60)
    print("  采集数据摘要 (LLM原始置信度)")
    print("=" * 60)
    print(f"  总条目: {len(entries)}")
    print(f"  权威标注条目: {len(authoritative)}")
    print(f"  正确总数: {correct_count}/{len(entries)} ({correct_count/max(len(entries),1)*100:.1f}%)")
    print(f"    其中关键词正确: {keyword_correct_count}")
    print(f"    其中正确拒答: {correct_reject_count}")
    print(f"  原始置信度范围: [{min(raw_confs):.4f}, {max(raw_confs):.4f}]")
    print(f"  原始置信度均值: {np.mean(raw_confs):.4f}")
    print(f"  原始置信度中位数: {np.median(raw_confs):.4f}")

    # 原始置信度分布
    bins = [(0, 0.3), (0.3, 0.5), (0.5, 0.7), (0.7, 0.85), (0.85, 0.95), (0.95, 1.0)]
    print(f"\n  原始置信度分布:")
    for lo, hi in bins:
        count = sum(1 for c in raw_confs if lo <= c < hi)
        bar = "#" * (count * 40 // max(len(entries), 1))
        print(f"    [{lo:.2f}-{hi:.2f}): {count:5d} {bar}")


def main():
    parser = argparse.ArgumentParser(description="采集LLM原始置信度数据")
    parser.add_argument("--queries", "-n", type=int, default=500,
                       help="采集查询数量（默认500，即全量）")
    parser.add_argument("--delay", "-d", type=float, default=1.5,
                       help="请求间隔秒数（默认1.5）")
    parser.add_argument("--seed", type=int, default=42, help="随机种子")
    parser.add_argument("--no-resume", action="store_true", help="不恢复检查点，从头开始")
    parser.add_argument("--summary-only", action="store_true", help="仅打印已有缓存的摘要")
    args = parser.parse_args()

    if args.summary_only:
        cache = load_checkpoint()
        print_summary(cache)
        return

    queries = load_queries(max_queries=args.queries, seed=args.seed)
    print(f"加载 {len(queries)} 条测试查询")

    cache = collect(queries, delay=args.delay, resume=not args.no_resume)

    print_summary(cache)

    print(f"\n下一步: py scripts/confidence/threshold_scan.py "
          f"--cache {CACHE_FILE} --chart --json")


if __name__ == "__main__":
    main()
