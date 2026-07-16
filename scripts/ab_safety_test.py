#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安全机制 A/B 对比测试脚本

用法:
  python ab_safety_test.py --sample              # 仅执行抽样，生成 sample_50.json
  python ab_safety_test.py --round a             # 执行 A 轮（安全关闭），需先改配置重启
  python ab_safety_test.py --round b             # 执行 B 轮（安全开启），需先改配置重启
  python ab_safety_test.py --report              # 对比已有两轮结果，生成报告
  python ab_safety_test.py --dry-run             # 试运行：只做抽样+分类，不调API
"""

import argparse
import json
import os
import random
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
ASK_ENDPOINT = f"{API_BASE}/api/qa/ask"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(SCRIPT_DIR, "test_scenarios.json")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "ab_results")
SAMPLE_FILE = os.path.join(OUTPUT_DIR, "sample_50.json")
ROUND_A_FILE = os.path.join(OUTPUT_DIR, "round_a.json")
ROUND_B_FILE = os.path.join(OUTPUT_DIR, "round_b.json")

DISEASES = [
    "肺炎", "腹泻", "哮喘", "毛细支气管炎",
    "特应性皮炎", "手足口病", "热性惊厥", "缺铁性贫血",
]

QUESTION_TYPES = ["直白问法", "口语问法", "混合问法"]

random.seed(20260714)

# ═══════════════════════════════════════════════
# 问法分类
# ═══════════════════════════════════════════════

CONVERSATIONAL_MARKERS = [
    "急！", "宝宝", "着急", "在线等", "新手妈妈求助",
    "请问大家", "要不要紧", "怎么办啊", "怎么办？",
]
MIXED_MARKERS = [
    "医生，", "该如何处理", "有什么好办法",
    "需要去医院吗", "严重吗",
]


def classify_question_type(text: str) -> str:
    """将问题分类为 直白问法 / 口语问法 / 混合问法

    分类逻辑基于 generate_500_questions.py 中的 TEMPLATES 模板特征:
    - 口语: 带 "宝宝""着急""在线等""新手妈妈求助" 等口语化表达
    - 混合: 带 "医生""如何处理""需要去医院""严重吗" 等半正式表达
    - 直白: 其余为直接提问
    """
    # 含明确口语标记 → 口语
    for m in CONVERSATIONAL_MARKERS:
        if m in text:
            return "口语问法"
    # 含混合标记 → 混合
    for m in MIXED_MARKERS:
        if m in text:
            return "混合问法"
    return "直白问法"


# ═══════════════════════════════════════════════
# 分层抽样
# ═══════════════════════════════════════════════

def stratified_sample(questions: list, total: int = 50) -> list:
    """按疾病分布分层随机抽样，保证 8 个疾病全覆蓋、三种问法尽量覆盖"""
    by_disease = defaultdict(list)
    for q in questions:
        by_disease[q["disease"]].append(q)

    # 每疾病保底 2 题
    min_per = 2
    guaranteed = min_per * len(DISEASES)  # 16
    remaining = total - guaranteed        # 34

    disease_counts = {d: len(by_disease[d]) for d in DISEASES}
    total_q = sum(disease_counts.values())

    # 按占比分配剩余配额
    quota = {d: min_per for d in DISEASES}
    extra_alloc = []
    for d in DISEASES:
        extra = int(remaining * disease_counts[d] / total_q)
        extra_alloc.append([d, extra])
    # 舍入补偿
    allocated = sum(e[1] for e in extra_alloc)
    for i in range(remaining - allocated):
        extra_alloc[i][1] += 1
    for d, extra in extra_alloc:
        quota[d] += extra

    # 每疾病内部：三种问法各取 1 题后随机补足
    sampled = []
    for disease in DISEASES:
        pool = by_disease[disease]
        random.shuffle(pool)
        for q in pool:
            q.setdefault("questionType", classify_question_type(q["userQuery"]))

        by_type = defaultdict(list)
        for q in pool:
            by_type[q["questionType"]].append(q)

        target = quota[disease]
        picked = []

        for qt in QUESTION_TYPES:
            if by_type[qt] and len(picked) < target:
                picked.append(by_type[qt].pop(0))

        rest = [q for lst in by_type.values() for q in lst]
        random.shuffle(rest)
        while len(picked) < target and rest:
            picked.append(rest.pop(0))

        sampled.extend(picked[:target])

    random.shuffle(sampled)
    for i, q in enumerate(sampled):
        q["sampleIndex"] = i + 1
    return sampled


# ═══════════════════════════════════════════════
# API 调用
# ═══════════════════════════════════════════════

def ask_question(question: str, timeout: int = 180) -> dict:
    """调用 POST /api/qa/ask，返回 {success, answer, latencyMs, ...}"""
    payload = {"question": question}
    t0 = time.time()
    try:
        resp = requests.post(
            ASK_ENDPOINT, json=payload,
            headers={"Content-Type": "application/json"},
            timeout=timeout,
        )
        elapsed = (time.time() - t0) * 1000
        if resp.status_code == 200:
            body = resp.json()
            answer = ""
            if body.get("success") and body.get("data"):
                answer = body["data"].get("answer", "")
            return {"success": True, "answer": answer, "latencyMs": round(elapsed)}
        return {"success": False, "answer": "", "latencyMs": round(elapsed),
                "error": f"HTTP {resp.status_code}: {resp.text[:150]}"}
    except requests.exceptions.Timeout:
        return {"success": False, "answer": "", "latencyMs": timeout * 1000,
                "error": "timeout"}
    except requests.exceptions.ConnectionError:
        return {"success": False, "answer": "", "latencyMs": 0,
                "error": "连接被拒绝，请确认服务已启动"}
    except Exception as e:
        return {"success": False, "answer": "", "latencyMs": 0,
                "error": str(e)[:200]}


# ═══════════════════════════════════════════════
# 幻觉判定
# ═══════════════════════════════════════════════

# (负向词列表, 正向词列表) — 期望负向但出现正向即矛盾
CONTRADICTION_PAIRS = [
    (
        ["禁用", "禁忌", "不宜", "避免", "不推荐", "不应使用", "禁止使用", "不得使用", "忌用", "不可"],
        ["可以使用", "安全", "推荐使用", "适用", "首选", "可以服用", "可用", "放心使用"],
    ),
    (
        ["慎用"],
        ["绝对安全", "无任何风险"],
    ),
]


def judge_hallucination(answer: str, expected_keywords: list) -> dict:
    """
    判断回答是否为幻觉：
    - 未命中任何期望关键词 → 遗漏幻觉
    - 期望"禁用/不推荐"但回答正面 → 矛盾幻觉
    """
    if not answer:
        return {"hallucination": True, "reason": "空回答", "matched": [], "contradictions": []}

    # 命中检测
    matched = [kw for kw in expected_keywords if kw in answer]
    if not matched:
        return {"hallucination": True, "reason": "未命中任何期望关键词",
                "matched": [], "contradictions": []}

    # 矛盾检测
    contradictions = []
    for neg_kws, pos_kws in CONTRADICTION_PAIRS:
        answer_has_negative = any(kw in answer for kw in neg_kws)
        if answer_has_negative:
            continue  # 已含负向表述，安全
        expected_has_negative = any(kw in expected_keywords for kw in neg_kws)
        answer_has_positive = any(kw in answer for kw in pos_kws)
        if expected_has_negative and answer_has_positive:
            contradictions.append({
                "expectedNegative": [kw for kw in neg_kws if kw in expected_keywords],
                "foundPositive": [kw for kw in pos_kws if kw in answer],
            })

    if contradictions:
        return {"hallucination": True, "reason": "包含矛盾信息",
                "matched": matched, "contradictions": contradictions}

    return {"hallucination": False, "reason": "", "matched": matched, "contradictions": []}


# ═══════════════════════════════════════════════
# 测试执行
# ═══════════════════════════════════════════════

def banner(text: str):
    print(f"\n{'=' * 65}")
    print(f"  {text}")
    print(f"{'=' * 65}")


def run_round(sample: list, round_label: str, safety_enabled: bool) -> dict:
    """执行一轮 50 题测试"""
    banner(f"{round_label} (safety.enabled={safety_enabled})")

    results = []
    ok = ha = fail = 0
    latencies = []

    for i, q in enumerate(sample):
        query = q["userQuery"]
        tag = f"[{q['id']}][{q['disease']}][{q['questionType']}]"

        print(f"  [{i+1:02d}/50] {tag} {query[:45]}...", end=" ", flush=True)

        api = ask_question(query)
        latencies.append(api["latencyMs"])

        if not api["success"]:
            fail += 1
            judge = {"hallucination": True, "reason": f"API错误: {api.get('error','?')}",
                     "matched": [], "contradictions": []}
            print(f"✗ {api.get('error','?')[:40]}")
        else:
            judge = judge_hallucination(api["answer"], q["expectedKeywords"])
            if judge["hallucination"]:
                ha += 1
                print(f"✗ {judge['reason']}")
            else:
                ok += 1
                kw_preview = ", ".join(judge["matched"][:3])
                print(f"✓ {kw_preview}")

        results.append({
            "index": q.get("index", q.get("sampleIndex", i + 1)),
            "id": q["id"],
            "disease": q["disease"],
            "questionType": q["questionType"],
            "userQuery": query,
            "expectedKeywords": q["expectedKeywords"],
            "answer": api["answer"],
            "latencyMs": api["latencyMs"],
            "success": api["success"],
            "error": api.get("error", ""),
            "hallucination": judge["hallucination"],
            "hallucinationReason": judge["reason"],
            "matchedKeywords": judge["matched"],
            "contradictions": judge["contradictions"],
        })
        time.sleep(0.3)

    valid = ok + ha
    rate = ha / valid * 100 if valid else 0
    avg_lat = sum(latencies) / len(latencies) if latencies else 0

    print(f"\n  ---- 汇总 ----")
    print(f"  正确: {ok}/{valid}  幻觉: {ha}/{valid}  幻觉率: {rate:.1f}%")
    print(f"  API失败: {fail}  平均延迟: {avg_lat:.0f}ms")

    return {
        "label": round_label,
        "safetyEnabled": safety_enabled,
        "correct": ok,
        "hallucinations": ha,
        "apiFailures": fail,
        "hallucinationRate": round(rate, 1),
        "avgLatencyMs": round(avg_lat, 1),
        "results": results,
    }


# ═══════════════════════════════════════════════
# 报告
# ═══════════════════════════════════════════════

def summarize_dimension(results: list, key: str, order: list) -> list:
    """按指定维度汇总幻觉率"""
    buckets = defaultdict(lambda: {"total": 0, "correct": 0, "hallucinations": 0})
    for r in results:
        k = r[key]
        buckets[k]["total"] += 1
        if r["success"]:
            if r["hallucination"]:
                buckets[k]["hallucinations"] += 1
            else:
                buckets[k]["correct"] += 1
    out = []
    for k in order:
        s = buckets[k]
        v = s["correct"] + s["hallucinations"]
        out.append({
            "name": k, "total": s["total"],
            "correct": s["correct"], "hallucinations": s["hallucinations"],
            "hallucinationRate": round(s["hallucinations"] / v * 100, 1) if v else 0,
        })
    return out


def generate_report(round_a: dict, round_b: dict, sample: list):
    """控制台 + JSON 对比报告"""
    ra, rb = round_a, round_b
    delta = ra["hallucinationRate"] - rb["hallucinationRate"]

    banner("A/B 对比分析")

    # 总览表
    print(f"\n  {'指标':<16} {'A(安全关)':>12} {'B(安全开)':>12} {'差异':>10}")
    print(f"  {'-' * 52}")
    print(f"  {'幻觉率':<16} {ra['hallucinationRate']:>9.1f}%  {rb['hallucinationRate']:>9.1f}%  {delta:>+8.1f}%")
    print(f"  {'正确':<16} {ra['correct']:>9}    {rb['correct']:>9}")
    print(f"  {'幻觉':<16} {ra['hallucinations']:>9}    {rb['hallucinations']:>9}")
    print(f"  {'平均延迟':<16} {ra['avgLatencyMs']:>7.0f} ms  {rb['avgLatencyMs']:>7.0f} ms")
    print(f"  {'API失败':<16} {ra['apiFailures']:>9}    {rb['apiFailures']:>9}")

    # 按疾病
    print(f"\n  {'─' * 60}")
    print(f"  {'疾病':<14} {'A(安全关)':>10} {'B(安全开)':>10} {'差异':>8}")
    print(f"  {'-' * 44}")
    da = {d["name"]: d for d in summarize_dimension(ra["results"], "disease", DISEASES)}
    db = {d["name"]: d for d in summarize_dimension(rb["results"], "disease", DISEASES)}
    disease_comparison = []
    for d in DISEASES:
        ra_rate = da.get(d, {}).get("hallucinationRate", 0)
        rb_rate = db.get(d, {}).get("hallucinationRate", 0)
        d_delta = ra_rate - rb_rate
        print(f"  {d:<14} {ra_rate:>7.1f}%   {rb_rate:>7.1f}%   {d_delta:>+6.1f}%")
        disease_comparison.append({"disease": d, "rateA": ra_rate, "rateB": rb_rate, "delta": round(d_delta, 1)})

    # 按问法
    print(f"\n  {'─' * 60}")
    print(f"  {'问法':<14} {'A(安全关)':>10} {'B(安全开)':>10} {'差异':>8}")
    print(f"  {'-' * 44}")
    ta = {t["name"]: t for t in summarize_dimension(ra["results"], "questionType", QUESTION_TYPES)}
    tb = {t["name"]: t for t in summarize_dimension(rb["results"], "questionType", QUESTION_TYPES)}
    type_comparison = []
    for t in QUESTION_TYPES:
        ra_rate = ta.get(t, {}).get("hallucinationRate", 0)
        rb_rate = tb.get(t, {}).get("hallucinationRate", 0)
        d_delta = ra_rate - rb_rate
        print(f"  {t:<14} {ra_rate:>7.1f}%   {rb_rate:>7.1f}%   {d_delta:>+6.1f}%")
        type_comparison.append({"questionType": t, "rateA": ra_rate, "rateB": rb_rate, "delta": round(d_delta, 1)})

    # 结论
    print(f"\n  {'─' * 60}")
    if delta > 3:
        print(f"  ★ 安全机制显著有效: 幻觉率降低 {delta:.1f} p.p.")
    elif delta > 0:
        print(f"  ★ 安全机制有效: 幻觉率降低 {delta:.1f} p.p.")
    elif delta < 0:
        print(f"  ★ 安全机制未达预期: 幻觉率反升 {abs(delta):.1f} p.p.（需排查）")
    else:
        print(f"  ★ 安全机制开关无差异")

    return {
        "roundA": {"label": ra["label"], "hallucinationRate": ra["hallucinationRate"],
                   "correct": ra["correct"], "hallucinations": ra["hallucinations"],
                   "avgLatencyMs": ra["avgLatencyMs"], "apiFailures": ra["apiFailures"]},
        "roundB": {"label": rb["label"], "hallucinationRate": rb["hallucinationRate"],
                   "correct": rb["correct"], "hallucinations": rb["hallucinations"],
                   "avgLatencyMs": rb["avgLatencyMs"], "apiFailures": rb["apiFailures"]},
        "hallucinationDelta": round(delta, 1),
        "diseaseComparison": disease_comparison,
        "questionTypeComparison": type_comparison,
        "sampleDiseaseDist": {d: sum(1 for q in sample if q["disease"] == d) for d in DISEASES},
        "sampleTypeDist": {t: sum(1 for q in sample if q["questionType"] == t) for t in QUESTION_TYPES},
    }


# ═══════════════════════════════════════════════
# 子命令
# ═══════════════════════════════════════════════

def cmd_sample():
    """仅抽样"""
    print("加载验证集...")
    with open(INPUT_FILE, encoding="utf-8") as f:
        data = json.load(f)
    all_q = data["scenarios"]

    # 分类统计
    tc = defaultdict(int)
    for q in all_q:
        tc[classify_question_type(q["userQuery"])] += 1
    print(f"  500 题问法分布: 直白={tc['直白问法']}, 口语={tc['口语问法']}, 混合={tc['混合问法']}")

    sample = stratified_sample(all_q, 50)
    dd = defaultdict(int)
    td = defaultdict(int)
    for q in sample:
        dd[q["disease"]] += 1
        td[q["questionType"]] += 1

    print(f"  抽样疾病分布: {dict(dd)}")
    print(f"  抽样问法分布: {dict(td)}")

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    payload = {
        "description": "A/B测试50题分层抽样",
        "totalQuestions": len(sample),
        "diseaseDistribution": dict(dd),
        "questionTypeDistribution": dict(td),
        "questions": [{
            "index": q["sampleIndex"],
            "id": q["id"],
            "disease": q["disease"],
            "questionType": q["questionType"],
            "userQuery": q["userQuery"],
            "expectedKeywords": q["expectedKeywords"],
        } for q in sample],
    }
    with open(SAMPLE_FILE, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"\n  抽样已保存: {SAMPLE_FILE}")


def _load_sample():
    with open(SAMPLE_FILE, encoding="utf-8") as f:
        return json.load(f)["questions"]


def cmd_round(round_tag: str):
    """执行一轮测试"""
    safety = round_tag == "b"
    label = f"{'B-安全开启' if safety else 'A-安全关闭'}"

    if not os.path.exists(SAMPLE_FILE):
        print("错误: 请先执行 --sample 生成抽样结果")
        sys.exit(1)

    sample = _load_sample()
    print(f"已加载 {len(sample)} 道抽样题")

    # 连通性检查
    print(f"检测 API 连通性 ({ASK_ENDPOINT})...", end=" ", flush=True)
    try:
        r = requests.post(ASK_ENDPOINT, json={"question": "肺炎有什么症状"},
                          timeout=30)
        if r.status_code == 200:
            print("OK")
        else:
            print(f"HTTP {r.status_code}")
    except requests.exceptions.ConnectionError:
        print("\n  错误: 无法连接服务，请确认 Spring Boot 已启动")
        sys.exit(1)

    result = run_round(sample, label, safety)

    out_file = ROUND_B_FILE if safety else ROUND_A_FILE
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n  结果已保存: {out_file}")


def cmd_report():
    """读取两轮结果生成对比报告"""
    for tag, path in [("A轮", ROUND_A_FILE), ("B轮", ROUND_B_FILE)]:
        if not os.path.exists(path):
            print(f"错误: 缺少 {tag} 结果文件 ({path})")
            sys.exit(1)
    if not os.path.exists(SAMPLE_FILE):
        print(f"错误: 缺少抽样文件 ({SAMPLE_FILE})，请先执行 --sample")
        sys.exit(1)

    with open(ROUND_A_FILE, encoding="utf-8") as f:
        round_a = json.load(f)
    with open(ROUND_B_FILE, encoding="utf-8") as f:
        round_b = json.load(f)
    with open(SAMPLE_FILE, encoding="utf-8") as f:
        sample = json.load(f)["questions"]

    report = generate_report(round_a, round_b, sample)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")

    full = {
        "testMetadata": {
            "timestamp": ts,
            "apiEndpoint": ASK_ENDPOINT,
            "sampleSize": 50,
        },
        "sample": sample,
        "roundA": round_a,
        "roundB": round_b,
        "comparison": report,
        "hallucinationDetails": {
            "aOnly": [r for r in round_a["results"] if r["hallucination"]],
            "bOnly": [r for r in round_b["results"] if r["hallucination"]],
            "both": _find_both_hallucinations(round_a["results"], round_b["results"]),
        },
    }

    full_path = os.path.join(OUTPUT_DIR, f"ab_test_{ts}.json")
    with open(full_path, "w", encoding="utf-8") as f:
        json.dump(full, f, ensure_ascii=False, indent=2)
    print(f"\n  完整报告: {full_path}")

    # 精简摘要
    summary_path = os.path.join(OUTPUT_DIR, f"summary_{ts}.json")
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump({
            "timestamp": ts,
            "roundA_hallucinationRate": round_a["hallucinationRate"],
            "roundB_hallucinationRate": round_b["hallucinationRate"],
            "delta": report["hallucinationDelta"],
            "diseaseComparison": report["diseaseComparison"],
            "questionTypeComparison": report["questionTypeComparison"],
        }, f, ensure_ascii=False, indent=2)
    print(f"  精简摘要: {summary_path}")


def _find_both_hallucinations(results_a: list, results_b: list) -> list:
    """找出两轮都出现幻觉的题目"""
    a_halluc = {r["id"]: r for r in results_a if r["hallucination"]}
    b_halluc = {r["id"]: r for r in results_b if r["hallucination"]}
    both = []
    for qid in set(a_halluc) & set(b_halluc):
        both.append({
            "id": qid,
            "userQuery": a_halluc[qid]["userQuery"],
            "disease": a_halluc[qid]["disease"],
            "reasonA": a_halluc[qid]["hallucinationReason"],
            "reasonB": b_halluc[qid]["hallucinationReason"],
        })
    return both


def cmd_dry_run():
    """试运行：完整走一遍抽样+分类+模拟判定，不调 API"""
    print("[dry-run] 加载验证集...")
    with open(INPUT_FILE, encoding="utf-8") as f:
        data = json.load(f)
    all_q = data["scenarios"]

    tc = defaultdict(int)
    for q in all_q:
        tc[classify_question_type(q["userQuery"])] += 1
    print(f"  问法分布: 直白={tc['直白问法']}, 口语={tc['口语问法']}, 混合={tc['混合问法']}")

    sample = stratified_sample(all_q, 50)
    dd = defaultdict(int)
    td = defaultdict(int)
    for q in sample:
        dd[q["disease"]] += 1
        td[q["questionType"]] += 1

    print(f"\n  疾病分布:")
    for d in DISEASES:
        bar = "█" * dd[d]
        print(f"    {d:<12} {dd[d]:>2} 题  {bar}")
    print(f"\n  问法分布:")
    for t in QUESTION_TYPES:
        print(f"    {t:<12} {td[t]:>2} 题")

    # 逐题展示
    print(f"\n  题目清单:")
    for q in sample:
        kw = ", ".join(q["expectedKeywords"][:4])
        print(f"    [{q['id']}] [{q['disease']:<8}] [{q['questionType']:<8}] "
              f"{q['userQuery'][:55]}")
        print(f"                    期望: {kw}")

    # 连通性检测
    print(f"\n  检测 API ({ASK_ENDPOINT})...", end=" ", flush=True)
    try:
        r = requests.post(ASK_ENDPOINT, json={"question": "肺炎有什么症状"}, timeout=10)
        if r.status_code == 200:
            body = r.json()
            ans = ""
            if body.get("code") == 200 and body.get("data"):
                ans = body["data"].get("answer", "")[:80]
            print(f"OK (回答预览: {ans}...)")
        else:
            print(f"HTTP {r.status_code}")
    except Exception as e:
        print(f"不可达 ({e})")

    print("\n[dry-run] 完毕，抽样+分类+API连通性均正常，可执行 --round a / --round b")


# ═══════════════════════════════════════════════
# 入口
# ═══════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="IMKQAS 安全机制 A/B 对比测试",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例流程:
  python ab_safety_test.py --dry-run          # 试运行，确认一切就绪
  python ab_safety_test.py --sample           # 抽样 50 题
  # 在 application.yml 设置 safety.enabled: false → 重启服务
  python ab_safety_test.py --round a          # A 轮（安全关闭）
  # 在 application.yml 设置 safety.enabled: true → 重启服务
  python ab_safety_test.py --round b          # B 轮（安全开启）
  python ab_safety_test.py --report           # 生成对比报告
        """,
    )
    parser.add_argument("--sample", action="store_true", help="仅执行分层抽样")
    parser.add_argument("--round", choices=["a", "b"], help="执行 A 轮(safety关) 或 B 轮(safety开)")
    parser.add_argument("--report", action="store_true", help="对比已有两轮结果，生成报告")
    parser.add_argument("--dry-run", action="store_true", help="试运行: 抽样+分类+API检测，不实际调用")

    args = parser.parse_args()

    if args.dry_run:
        cmd_dry_run()
    elif args.sample:
        cmd_sample()
    elif args.round:
        cmd_round(args.round)
    elif args.report:
        cmd_report()
    else:
        # 默认：一键走完整流程（需手动切换配置）
        print("未指定子命令。常用操作:")
        print("  --dry-run    试运行")
        print("  --sample     分层抽样 50 题")
        print("  --round a    执行 A 轮 (安全关闭)")
        print("  --round b    执行 B 轮 (安全开启)")
        print("  --report     生成对比报告")
        print("\n使用 --help 查看完整说明。")


if __name__ == "__main__":
    main()
