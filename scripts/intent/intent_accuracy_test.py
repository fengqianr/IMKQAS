# -*- coding: utf-8 -*-
"""
意图识别准确率测试脚本

测试6层意图路由（本地缓存→规则→正则→Redis→LLM→兜底）的分类准确率。
测试集: 150条Query，三类意图各50条（KNOWLEDGE_QUERY / DATA_COLLECTION / MIXED）。

用法:
  py intent_accuracy_test.py --dry-run    # 检查测试集与API连通性
  py intent_accuracy_test.py              # 执行150条测试并生成报告
  py intent_accuracy_test.py --report     # 用已保存结果离线重新生成报告
"""

import argparse
import json
import os
import sys
import time
from collections import defaultdict
from datetime import datetime

# 强制 UTF-8 输出，避免 Windows GBK 编码问题
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import requests

# ═══════════════════════════════════════════════
# 配置
# ═══════════════════════════════════════════════

API_BASE = os.environ.get("IMKQAS_API", "http://localhost:8080")
INTENT_ENDPOINT = f"{API_BASE}/api/qa/intent"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "intent_results")
TESTSET_FILE = os.path.join(OUTPUT_DIR, "testset_150.json")
RESULTS_FILE = os.path.join(OUTPUT_DIR, "results.json")

INTENT_TYPES = ["KNOWLEDGE_QUERY", "DATA_COLLECTION", "MIXED"]

# LLM层判定阈值：分类耗时超过此值(ms)视为走了LLM分类
LLM_COST_THRESHOLD_MS = 500

REQUEST_TIMEOUT = 30
RETRY_COUNT = 2


def load_testset(testset_file):
    """加载测试集并校验分布"""
    with open(testset_file, "r", encoding="utf-8") as f:
        testset = json.load(f)
    cases = testset["cases"]
    dist = defaultdict(int)
    for c in cases:
        dist[c["expected"]] += 1
    print(f"测试集加载完成: 共{len(cases)}条 ({os.path.basename(testset_file)})")
    for intent in INTENT_TYPES:
        print(f"  {intent:<16}: {dist[intent]}条")
    return cases


def classify(query):
    """调用意图分类端点，返回 (intent, costMs)"""
    last_err = None
    for attempt in range(RETRY_COUNT + 1):
        try:
            resp = requests.get(INTENT_ENDPOINT, params={"query": query}, timeout=REQUEST_TIMEOUT)
            resp.raise_for_status()
            body = resp.json()
            data = body.get("data") or {}
            return data.get("intent"), data.get("costMs", -1)
        except Exception as e:
            last_err = e
            if attempt < RETRY_COUNT:
                time.sleep(1)
    raise RuntimeError(f"分类请求失败: {last_err}")


def guess_error_reason(case, predicted, cost_ms):
    """根据分类耗时和Query特征推测误分类根因"""
    query = case["query"]
    expected = case["expected"]
    via_llm = cost_ms >= LLM_COST_THRESHOLD_MS
    layer = "LLM层" if via_llm else "规则/正则层"

    if via_llm:
        return f"{layer}误判（LLM将边界case判为{predicted}）"
    if expected == "MIXED" and predicted == "KNOWLEDGE_QUERY":
        return f"{layer}：命中知识关键词但个人描述特征未被规则识别"
    if expected == "MIXED" and predicted == "DATA_COLLECTION":
        return f"{layer}：命中个人描述/问卷关键词但知识提问特征未被规则识别"
    if expected == "DATA_COLLECTION" and predicted == "KNOWLEDGE_QUERY":
        return f"{layer}：个人症状描述关键词未覆盖，被判为知识查询（可能兜底）"
    if expected == "KNOWLEDGE_QUERY" and predicted == "DATA_COLLECTION":
        return f"{layer}：知识提问误命中数据采集关键词/正则"
    return f"{layer}：规则覆盖不足"


def run_test(cases, results_file, delay=0.0):
    """逐条调用分类端点，记录结果"""
    results = []
    total = len(cases)
    print(f"\n开始执行意图分类测试（{total}条，请求间隔{delay}s）...\n")
    start_time = time.time()

    for i, case in enumerate(cases, 1):
        predicted, cost_ms = classify(case["query"])
        correct = predicted == case["expected"]
        results.append({
            "id": case["id"],
            "query": case["query"],
            "expected": case["expected"],
            "predicted": predicted,
            "style": case["style"],
            "costMs": cost_ms,
            "correct": correct,
        })
        mark = "OK " if correct else "ERR"
        print(f"[{i:>3}/{total}] {mark} {case['expected']:<16} -> {predicted:<16} ({cost_ms:>5}ms) {case['query']}")
        if delay > 0 and i < total:
            time.sleep(delay)

    elapsed = time.time() - start_time
    print(f"\n测试完成，总耗时 {elapsed:.1f}s")

    with open(results_file, "w", encoding="utf-8") as f:
        json.dump({"testTime": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                   "elapsedSeconds": round(elapsed, 1),
                   "results": results}, f, ensure_ascii=False, indent=2)
    print(f"原始结果已保存: {results_file}")
    return results


def generate_report(results, tag="150"):
    """生成准确率报告"""
    total = len(results)
    correct_total = sum(1 for r in results if r["correct"])

    # 各类意图准确率
    by_intent = defaultdict(lambda: {"total": 0, "correct": 0})
    # 各问法准确率
    by_style = defaultdict(lambda: {"total": 0, "correct": 0})
    # 混淆矩阵 expected -> predicted -> count
    confusion = defaultdict(lambda: defaultdict(int))
    # 分层统计（按耗时推测）
    layer_stats = {"规则/正则/缓存层": 0, "LLM层": 0}
    errors = []

    for r in results:
        by_intent[r["expected"]]["total"] += 1
        by_style[r["style"]]["total"] += 1
        confusion[r["expected"]][r["predicted"]] += 1
        if r["costMs"] >= LLM_COST_THRESHOLD_MS:
            layer_stats["LLM层"] += 1
        else:
            layer_stats["规则/正则/缓存层"] += 1
        if r["correct"]:
            by_intent[r["expected"]]["correct"] += 1
            by_style[r["style"]]["correct"] += 1
        else:
            errors.append(r)

    lines = []
    lines.append("=== 意图识别准确率测试报告 ===")
    lines.append("")
    dist_str = " / ".join(f"{i} {by_intent[i]['total']}条" for i in INTENT_TYPES if by_intent[i]["total"])
    lines.append(f"测试集: {total}条（{dist_str}）")
    lines.append(f"测试时间: {datetime.now().strftime('%Y-%m-%d')}")
    lines.append("")
    lines.append("【总体准确率】")
    lines.append(f"  正确分类: {correct_total} / {total}")
    lines.append(f"  准确率: {correct_total / total * 100:.1f}%")
    lines.append("")
    lines.append("【各类意图准确率】")
    for intent in INTENT_TYPES:
        s = by_intent[intent]
        if s["total"]:
            lines.append(f"  {intent:<16}: {s['correct']}/{s['total']} = {s['correct'] / s['total'] * 100:.1f}%")
    lines.append("")
    lines.append("【各问法准确率】")
    style_names = {"plain": "直白", "colloquial": "口语", "mixed": "混合",
                   "with_wo": "带我", "no_wo": "无我", "assess": "评估请求",
                   "generic": "泛化知识", "family": "家人主语"}
    for style, s in sorted(by_style.items()):
        lines.append(f"  {style_names.get(style, style):<4}({style:<10}): {s['correct']}/{s['total']} = {s['correct'] / s['total'] * 100:.1f}%")
    lines.append("")
    lines.append("【混淆矩阵】（行=预期, 列=实际）")
    header = f"  {'':<18}" + "".join(f"{i:<18}" for i in INTENT_TYPES)
    lines.append(header)
    for exp in INTENT_TYPES:
        row = f"  {exp:<18}" + "".join(f"{confusion[exp][pred]:<18}" for pred in INTENT_TYPES)
        lines.append(row)
    lines.append("")
    lines.append(f"【分类路径分布】（按耗时>={LLM_COST_THRESHOLD_MS}ms推测走LLM）")
    for layer, count in layer_stats.items():
        lines.append(f"  {layer}: {count}条 ({count / total * 100:.1f}%)")
    lines.append("")
    lines.append(f"【错误分类明细】（共{len(errors)}条）")
    if not errors:
        lines.append("  无")
    for e in errors:
        lines.append(f"  Query: \"{e['query']}\"")
        lines.append(f"  预期: {e['expected']} -> 实际: {e['predicted']} ({e['costMs']}ms)")
        lines.append(f"  原因: {guess_error_reason(e, e['predicted'], e['costMs'])}")
        lines.append("")

    # 结论
    acc = correct_total / total * 100
    lines.append("【结论】")
    if acc >= 95:
        lines.append(f"  意图识别准确率 {acc:.1f}%，达到 95% 的预期目标。")
    else:
        lines.append(f"  意图识别准确率 {acc:.1f}%，未达到 95% 的预期目标。")
    # 找出错误最集中的混淆对
    pair_count = defaultdict(int)
    for e in errors:
        pair_count[(e["expected"], e["predicted"])] += 1
    if pair_count:
        (exp, pred), cnt = max(pair_count.items(), key=lambda kv: kv[1])
        lines.append(f"  主要错误集中在 {exp} 被误判为 {pred}（{cnt}条）的边界模糊场景。")
        if exp == "MIXED":
            lines.append("  优化方向: 补充 MIXED 意图的个人描述关键词规则（如\"我\"+疑问句式组合判定）。")
        elif exp == "DATA_COLLECTION":
            lines.append("  优化方向: 扩充数据采集关键词/问卷触发词覆盖。")
        else:
            lines.append("  优化方向: 收紧数据采集正则，避免知识提问误命中。")

    report = "\n".join(lines)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    report_file = os.path.join(OUTPUT_DIR, f"report_{tag}_{ts}.txt")
    with open(report_file, "w", encoding="utf-8") as f:
        f.write(report)
    print("\n" + report)
    print(f"\n报告已保存: {report_file}")


def dry_run(cases):
    """连通性检查：取每类第一条试跑"""
    print("\n[dry-run] API连通性检查...")
    health = requests.get(f"{API_BASE}/api/qa/health", timeout=5)
    print(f"  健康检查: {health.status_code} {health.text}")
    seen = set()
    for case in cases:
        if case["expected"] in seen:
            continue
        seen.add(case["expected"])
        intent, cost = classify(case["query"])
        print(f"  [{case['expected']}] \"{case['query']}\" -> {intent} ({cost}ms)")
        if len(seen) == 3:
            break
    print("[dry-run] 通过")


def main():
    parser = argparse.ArgumentParser(description="意图识别准确率测试")
    parser.add_argument("--dry-run", action="store_true", help="检查测试集与API连通性")
    parser.add_argument("--report", action="store_true", help="用已保存结果离线重新生成报告")
    parser.add_argument("--testset", default=TESTSET_FILE, help="测试集JSON文件路径（默认testset_150.json）")
    parser.add_argument("--delay", type=float, default=0.0, help="每条请求之间的间隔秒数，避免burst触发LLM限流/断路器")
    args = parser.parse_args()

    # 从测试集文件名派生结果/报告标识，如 testset_blind_60 -> blind_60
    stem = os.path.splitext(os.path.basename(args.testset))[0]
    tag = stem.replace("testset_", "")
    results_file = os.path.join(OUTPUT_DIR, f"results_{tag}.json") if tag != "150" else RESULTS_FILE

    if args.report:
        with open(results_file, "r", encoding="utf-8") as f:
            saved = json.load(f)
        generate_report(saved["results"], tag)
        return

    cases = load_testset(args.testset)
    if args.dry_run:
        dry_run(cases)
        return

    results = run_test(cases, results_file, args.delay)
    generate_report(results, tag)


if __name__ == "__main__":
    main()
