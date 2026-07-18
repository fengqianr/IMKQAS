# -*- coding: utf-8 -*-
"""
P99 响应时间测试脚本 — 冷启动 vs 热启动对比

用法:
  py p99_latency_test.py --clear-cache      # 清空Redis缓存（冷启动准备）
  py p99_latency_test.py --cold              # 冷启动测试（500条）
  py p99_latency_test.py --warm              # 热启动测试（500条，缓存已预热）
  py p99_latency_test.py --all               # 一键：清缓存 → 冷启动 → 热启动 → 报告
  py p99_latency_test.py --report            # 对比已有两轮结果，生成报告
  py p99_latency_test.py --dry-run           # 试运行（连通性检查 + 抽样5条）

环境变量:
  IMKQAS_API           API地址，默认 http://localhost:8080
  IMKQAS_REDIS_HOST    Redis主机，默认 localhost
  IMKQAS_REDIS_PORT    Redis端口，默认 6379
  IMKQAS_REDIS_PWD     Redis密码，默认空
"""

import argparse
import json
import math
import os
import sys
import time
import subprocess
from collections import defaultdict
from datetime import datetime

if sys.platform == "win32":
    try:
        if sys.stdout.isatty():
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import requests

# ═══════════════════════════════════════════════
# 配置
# ═══════════════════════════════════════════════

API_BASE = os.environ.get("IMKQAS_API", "http://localhost:8080")
ASK_ENDPOINT = f"{API_BASE}/api/qa/ask"
INTENT_ENDPOINT = f"{API_BASE}/api/qa/intent"
HEALTH_ENDPOINT = f"{API_BASE}/api/qa/health"

REDIS_HOST = os.environ.get("IMKQAS_REDIS_HOST", "localhost")
REDIS_PORT = int(os.environ.get("IMKQAS_REDIS_PORT", "6379"))
REDIS_PWD = os.environ.get("IMKQAS_REDIS_PWD", "")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(SCRIPT_DIR, "test_scenarios.json")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "p99_results")
COLD_FILE = os.path.join(OUTPUT_DIR, "cold_start_results.json")
WARM_FILE = os.path.join(OUTPUT_DIR, "warm_start_results.json")

REQUEST_TIMEOUT = 180
WARMUP_COUNT = 50
TEST_COUNT = 500

# Redis缓存键前缀（与后端代码一致）
REDIS_CACHE_PREFIXES = [
    "intent:",                   # 意图分类缓存 (IntentRouterImpl)
    "sem:cache:",                # 语义缓存 (SemanticCacheServiceImpl)
    "sem:lock:",                 # 分布式锁（残留）
    "qa:result:",                # 查询结果缓存 (RedisService)
    "embedding:text-embedding-v3:",  # Embedding向量缓存
    "llm:qwen-plus:",            # LLM结果缓存
    "synonym:llm:",              # 同义词扩展LLM缓存
]

# ═══════════════════════════════════════════════
# Redis 缓存清除
# ═══════════════════════════════════════════════

def _redis_cli(*args: str) -> tuple[int, str, str]:
    """执行 redis-cli 命令，返回 (returncode, stdout, stderr)"""
    cmd = ["redis-cli", "-h", REDIS_HOST, "-p", str(REDIS_PORT)]
    if REDIS_PWD:
        cmd.extend(["-a", REDIS_PWD])
    cmd.extend(args)
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=15,
                                creationflags=(subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0))
        return result.returncode, result.stdout.strip(), result.stderr.strip()
    except FileNotFoundError:
        return -1, "", "redis-cli 未找到"
    except subprocess.TimeoutExpired:
        return -1, "", "redis-cli 执行超时"
    except Exception as e:
        return -1, "", str(e)


def _redis_py_clear() -> tuple[bool, str, int]:
    """使用 redis-py 直连清除缓存，返回 (成功, 信息, 删除数)"""
    try:
        import redis
        r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT,
                        password=REDIS_PWD or None,
                        socket_connect_timeout=5, socket_timeout=5,
                        decode_responses=True)
        r.ping()
    except Exception as e:
        return False, f"redis-py 连接失败: {e}", 0

    total_deleted = 0
    try:
        for prefix in REDIS_CACHE_PREFIXES:
            keys = list(r.scan_iter(match=f"{prefix}*", count=500))
            if keys:
                deleted = r.delete(*keys)
                total_deleted += deleted
                print(f"  {prefix}* → 删除 {deleted} 个键")
            else:
                print(f"  {prefix}* → 0 个键")

        # 显示版本号（保留不删）
        ver = r.get("sem:version:knowledge")
        print(f"  知识库版本号: {ver or '(未设置)'} (已保留)")

        # 检查残留
        remaining = r.dbsize()
        print(f"  Redis 剩余键总数: {remaining}")
    except Exception as e:
        return False, f"操作异常: {e}", total_deleted
    finally:
        r.close()

    return True, "", total_deleted


def clear_redis_cache() -> bool:
    """清除 Redis 缓存键（优先使用 redis-py，fallback 到 redis-cli）"""
    banner("清空 Redis 缓存")

    # 优先使用 redis-py
    ok, err, deleted = _redis_py_clear()
    if ok:
        print(f"\n  总计删除 {deleted} 个缓存键 (via redis-py)")
        return True

    print(f"  redis-py 不可用: {err}")
    print(f"  尝试 redis-cli...")

    # Fallback: redis-cli
    code, out, err = _redis_cli("PING")
    if code != 0 or "PONG" not in out:
        print(f"  Redis CLI 连接失败: {err or out}")
        print(f"  请手动执行: redis-cli -h {REDIS_HOST} KEYS 'intent:*' | ...")
        return False

    total_deleted = 0
    for prefix in REDIS_CACHE_PREFIXES:
        _, keys_out, _ = _redis_cli("KEYS", f"{prefix}*")
        if not keys_out:
            print(f"  {prefix}* → 0 个键")
            continue
        keys = keys_out.split("\n")
        if keys:
            code, del_out, _ = _redis_cli("DEL", *keys)
            if code == 0:
                deleted = int(del_out) if del_out.isdigit() else len(keys)
                total_deleted += deleted
                print(f"  {prefix}* → 删除 {deleted} 个键")
            else:
                print(f"  {prefix}* → 删除失败: {del_out}")

    _, ver_out, _ = _redis_cli("GET", "sem:version:knowledge")
    print(f"  知识库版本号: {ver_out or '(未设置)'} (已保留)")
    print(f"\n  总计删除 {total_deleted} 个缓存键 (via redis-cli)")
    return True


# ═══════════════════════════════════════════════
# 工具函数
# ═══════════════════════════════════════════════

def banner(text: str):
    print(f"\n{'=' * 65}")
    print(f"  {text}")
    print(f"{'=' * 65}")


def load_questions(limit: int = TEST_COUNT) -> list:
    """加载测试问题，返回 [{id, query, disease, expectedKeywords}, ...]"""
    with open(INPUT_FILE, encoding="utf-8") as f:
        data = json.load(f)
    questions = data["scenarios"][:limit]
    print(f"加载测试集: {len(questions)} 条 (共{len(data['scenarios'])}条)")
    return questions


def check_connectivity() -> bool:
    """检查API连通性"""
    print(f"检测 API 连通性 ({ASK_ENDPOINT})...", end=" ", flush=True)
    try:
        r = requests.post(ASK_ENDPOINT, json={"question": "肺炎有什么症状"}, timeout=30)
        if r.status_code == 200:
            body = r.json()
            code = body.get("code")
            if body.get("success") or code == 200 or str(code) == "00000":
                print("OK")
                return True
        print(f"HTTP {r.status_code}: {r.text[:80]}")
        return False
    except requests.exceptions.ConnectionError:
        print("连接被拒绝，请确认 Spring Boot 已启动")
        return False
    except Exception as e:
        print(f"异常: {e}")
        return False


# 缓存命中判定阈值(ms)：processingTime 低于此值视为缓存命中
CACHE_HIT_THRESHOLD_MS = 200


def ask_question(question: str, timeout: int = REQUEST_TIMEOUT) -> dict:
    """调用 POST /api/qa/ask，返回 {success, answer, latencyMs, processingMs, cacheHit, ...}"""
    payload = {"question": question}
    t0 = time.time()
    try:
        resp = requests.post(ASK_ENDPOINT, json=payload,
                             headers={"Content-Type": "application/json"}, timeout=timeout)
        elapsed = round((time.time() - t0) * 1000)
        if resp.status_code == 200:
            body = resp.json()
            answer = ""
            processing_ms = -1
            code = body.get("code")
            is_ok = body.get("success") or code == 200 or str(code) == "00000"
            if is_ok and body.get("data"):
                data = body["data"]
                answer = data.get("answer", "")
                processing_ms = data.get("processingTime", -1)
            # 估算缓存命中：服务端处理时间 < 阈值
            cache_hit = 0 < processing_ms < CACHE_HIT_THRESHOLD_MS
            return {"success": True, "answer": answer, "latencyMs": elapsed,
                    "processingMs": processing_ms, "cacheHit": cache_hit}
        return {"success": False, "answer": "", "latencyMs": elapsed,
                "processingMs": -1, "cacheHit": False,
                "error": f"HTTP {resp.status_code}"}
    except requests.exceptions.Timeout:
        return {"success": False, "answer": "", "latencyMs": timeout * 1000,
                "processingMs": -1, "cacheHit": False, "error": "timeout"}
    except requests.exceptions.ConnectionError:
        return {"success": False, "answer": "", "latencyMs": 0,
                "processingMs": -1, "cacheHit": False, "error": "连接被拒绝"}
    except Exception as e:
        return {"success": False, "answer": "", "latencyMs": 0,
                "processingMs": -1, "cacheHit": False, "error": str(e)[:200]}


def warmup(questions: list):
    """预热缓存：发送 WARMUP_COUNT 条请求（不计入统计）"""
    banner(f"缓存预热 ({WARMUP_COUNT} 条)")
    warmup_qs = questions[:WARMUP_COUNT]
    ok = fail = 0
    for i, q in enumerate(warmup_qs):
        tag = f"[{q['id']}][{q['disease']}]"
        print(f"  预热 [{i+1:02d}/{WARMUP_COUNT}] {tag} {q['userQuery'][:40]}...",
              end=" ", flush=True)
        api = ask_question(q["userQuery"])
        if api["success"]:
            ok += 1
            print(f"OK {api['latencyMs']}ms")
        else:
            fail += 1
            print(f"ERR {api.get('error','?')}")
        time.sleep(0.5)
    print(f"\n  预热完成: 成功 {ok}, 失败 {fail}")


def run_round(questions: list, label: str, skip: int = 0) -> dict:
    """执行一轮测试，返回 {label, results, stats, ...}"""
    banner(f"{label} ({len(questions)} 条)")
    results = []
    ok = fail = 0
    hit = 0
    latencies = []
    t_start = time.time()

    for i, q in enumerate(questions):
        tag = f"[{q['id']}][{q['disease']}]"
        api = ask_question(q["userQuery"])
        latencies.append(api["latencyMs"])

        if api["success"]:
            ok += 1
            if api["cacheHit"]:
                hit += 1
        else:
            fail += 1

        results.append({
            "index": i + 1 + skip,
            "id": q["id"],
            "disease": q["disease"],
            "userQuery": q["userQuery"],
            "latencyMs": api["latencyMs"],
            "processingMs": api["processingMs"],
            "success": api["success"],
            "cacheHit": api["cacheHit"],
            "error": api.get("error", ""),
        })

        mark = "OK" if api["success"] else "ER"
        ch = "H" if api["cacheHit"] else "M"
        if (i + 1) % 50 == 0 or i == 0:
            print(f"  [{i+1:>3}/{len(questions)}] {mark} {api['latencyMs']:>5}ms [{ch}] "
                  f"{tag} {q['userQuery'][:40]}...")
        time.sleep(0.15)

    elapsed = time.time() - t_start
    stats = calc_stats(latencies)
    stats["totalRequests"] = len(questions)
    stats["successCount"] = ok
    stats["failureCount"] = fail
    stats["cacheHitCount"] = hit
    stats["cacheHitRate"] = round(hit / ok * 100, 1) if ok else 0
    stats["elapsedSeconds"] = round(elapsed, 1)
    stats["throughputQps"] = round(len(questions) / elapsed, 1) if elapsed > 0 else 0

    print(f"\n  完成: 成功 {ok}, 失败 {fail}, 耗时 {elapsed:.0f}s")
    print(f"  缓存命中: {hit}/{ok} ({stats['cacheHitRate']}%)")
    print(f"  P50={stats['p50Ms']}ms  P95={stats['p95Ms']}ms  P99={stats['p99Ms']}ms  "
          f"Avg={stats['avgMs']}ms")

    return {"label": label, "timestamp": datetime.now().isoformat(),
            "stats": stats, "results": results}


# ═══════════════════════════════════════════════
# 统计计算
# ═══════════════════════════════════════════════

def calc_stats(latencies: list) -> dict:
    """计算 P50/P90/P95/P99/P999、平均值、min/max、标准差"""
    if not latencies:
        return {}
    sorted_lat = sorted(latencies)
    n = len(sorted_lat)

    def percentile(pct: float) -> float:
        k = (n - 1) * pct / 100.0
        f = math.floor(k)
        c = math.ceil(k)
        if f == c:
            return sorted_lat[int(k)]
        return sorted_lat[f] * (c - k) + sorted_lat[c] * (k - f)

    avg = sum(sorted_lat) / n
    variance = sum((x - avg) ** 2 for x in sorted_lat) / n

    return {
        "minMs": sorted_lat[0],
        "maxMs": sorted_lat[-1],
        "avgMs": round(avg, 1),
        "stdMs": round(math.sqrt(variance), 1),
        "p50Ms": round(percentile(50), 1),
        "p90Ms": round(percentile(90), 1),
        "p95Ms": round(percentile(95), 1),
        "p99Ms": round(percentile(99), 1),
        "p999Ms": round(percentile(99.9), 1),
    }


def latency_distribution(latencies: list) -> dict:
    """延迟分布统计：按区间计数"""
    buckets = {"<100ms": 0, "100-200ms": 0, "200-300ms": 0, "300-500ms": 0,
               "500-1000ms": 0, "1-3s": 0, "3-5s": 0, ">5s": 0}
    for lat in latencies:
        if lat < 100:
            buckets["<100ms"] += 1
        elif lat < 200:
            buckets["100-200ms"] += 1
        elif lat < 300:
            buckets["200-300ms"] += 1
        elif lat < 500:
            buckets["300-500ms"] += 1
        elif lat < 1000:
            buckets["500-1000ms"] += 1
        elif lat < 3000:
            buckets["1-3s"] += 1
        elif lat < 5000:
            buckets["3-5s"] += 1
        else:
            buckets[">5s"] += 1
    total = len(latencies)
    return {k: {"count": v, "pct": round(v / total * 100, 1)} for k, v in buckets.items()}


# ═══════════════════════════════════════════════
# 报告生成
# ═══════════════════════════════════════════════

def format_ms(v: float) -> str:
    return f"{v:.0f}ms"


def gen_report(cold: dict, warm: dict):
    """生成控制台 + JSON 对比报告"""
    cs = cold["stats"]
    ws = warm["stats"]

    banner("P99 响应时间测试报告")
    print(f"\n  测试集: {cs['totalRequests']} 条")
    print(f"  测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M')}")
    print(f"  API: {ASK_ENDPOINT}")

    # 冷启动汇总
    print(f"\n  {'-' * 55}")
    print(f"  【冷启动（无缓存）】")
    print(f"    P50: {format_ms(cs['p50Ms']):>6}   P90: {format_ms(cs['p90Ms']):>6}"
          f"   P95: {format_ms(cs['p95Ms']):>6}")
    print(f"    P99: {format_ms(cs['p99Ms']):>6}   Avg: {format_ms(cs['avgMs']):>6}"
          f"   Max: {format_ms(cs['maxMs']):>6}")
    print(f"    缓存命中率: {cs['cacheHitRate']}%")
    print(f"    成功/失败: {cs['successCount']}/{cs['failureCount']}"
          f"    QPS: {cs['throughputQps']}")

    # 热启动汇总
    print(f"\n  【热启动（有缓存）】")
    print(f"    P50: {format_ms(ws['p50Ms']):>6}   P90: {format_ms(ws['p90Ms']):>6}"
          f"   P95: {format_ms(ws['p95Ms']):>6}")
    print(f"    P99: {format_ms(ws['p99Ms']):>6}   Avg: {format_ms(ws['avgMs']):>6}"
          f"   Max: {format_ms(ws['maxMs']):>6}")
    print(f"    缓存命中率: {ws['cacheHitRate']}%")
    print(f"    成功/失败: {ws['successCount']}/{ws['failureCount']}"
          f"    QPS: {ws['throughputQps']}")

    # 对比分析表
    metrics = [
        ("P50", "p50Ms"), ("P90", "p90Ms"), ("P95", "p95Ms"),
        ("P99", "p99Ms"), ("P999", "p999Ms"), ("Avg", "avgMs"),
        ("Max", "maxMs"),
    ]
    print(f"\n  【对比分析】")
    print(f"  {'指标':<10} {'冷启动':>10} {'热启动':>10} {'优化幅度':>10}")
    print(f"  {'-' * 42}")
    comparison = {}
    for name, key in metrics:
        cold_v = cs[key]
        warm_v = ws[key]
        delta = (cold_v - warm_v) / cold_v * 100 if cold_v > 0 else 0
        print(f"  {name:<10} {format_ms(cold_v):>10} {format_ms(warm_v):>10} {delta:>+9.1f}%")
        comparison[key] = {"cold": cold_v, "warm": warm_v, "improvementPct": round(delta, 1)}

    # 缓存对比
    print(f"\n  {'指标':<16} {'冷启动':>10} {'热启动':>10}")
    print(f"  {'-' * 38}")
    print(f"  {'缓存命中率':<16} {cs['cacheHitRate']:>9.1f}%  {ws['cacheHitRate']:>9.1f}%")
    print(f"\n  * 缓存命中率根据服务端 processingTime < {CACHE_HIT_THRESHOLD_MS}ms 估算")

    # 延迟分布
    cold_dist = latency_distribution([r["latencyMs"] for r in cold["results"]])
    warm_dist = latency_distribution([r["latencyMs"] for r in warm["results"]])
    print(f"\n  【延迟分布】")
    print(f"  {'区间':<14} {'冷启动':>10} {'热启动':>10}")
    print(f"  {'-' * 36}")
    for bucket in cold_dist:
        cold_pct = cold_dist[bucket]["pct"]
        warm_pct = warm_dist[bucket]["pct"]
        print(f"  {bucket:<14} {cold_pct:>8.1f}%  {warm_pct:>8.1f}%")

    # 结论
    p99_cold = cs["p99Ms"]
    p99_warm = ws["p99Ms"]
    p99_improve = (p99_cold - p99_warm) / p99_cold * 100 if p99_cold > 0 else 0
    print(f"\n  【结论】")
    print(f"  P99 冷启动: {format_ms(p99_cold)} -> 热启动: {format_ms(p99_warm)}"
          f" (优化 {p99_improve:.1f}%)")
    print(f"  P99 {format_ms(p99_warm)}")
    print(f"  热启动缓存命中率: {ws['cacheHitRate']}%")

    return {
        "cold": {"label": cold["label"], "stats": cs,
                 "distribution": cold_dist},
        "warm": {"label": warm["label"], "stats": ws,
                 "distribution": warm_dist},
        "comparison": comparison,
        "cacheHitRateDelta": round(ws["cacheHitRate"] - cs["cacheHitRate"], 1),
    }


# ═══════════════════════════════════════════════
# 子命令
# ═══════════════════════════════════════════════

def cmd_clear_cache():
    """清空Redis缓存"""
    ok = clear_redis_cache()
    if ok:
        print("\n提示: 还需重启 Spring Boot 以清空 Caffeine 本地缓存")


def _save_json(data: dict, path: str):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"\n  结果已保存: {path}")


def cmd_cold():
    """冷启动测试"""
    if not check_connectivity():
        sys.exit(1)
    questions = load_questions(TEST_COUNT)
    result = run_round(questions, "冷启动测试（无缓存）")
    _save_json(result, COLD_FILE)


def cmd_warm():
    """热启动测试（缓存应已被预热）"""
    if not check_connectivity():
        sys.exit(1)
    questions = load_questions(TEST_COUNT)
    result = run_round(questions, "热启动测试（有缓存）")
    _save_json(result, WARM_FILE)


def cmd_all():
    """一键完整测试"""
    # 1. 清缓存
    print("\n[>] 步骤 1/5: 清空 Redis 缓存...")
    clear_redis_cache()
    print("\n[!]  请重启 Spring Boot 以清空 Caffeine 本地缓存，然后按 Enter 继续...")
    input()

    # 2. 连通性检查
    if not check_connectivity():
        print("请先启动 Spring Boot 服务")
        sys.exit(1)

    questions = load_questions(TEST_COUNT)

    # 3. 预热
    warmup(questions)

    # 4. 冷启动
    print("\n[~] 等待 60 秒确保缓存稳定...")
    time.sleep(60)
    # 冷启动需要重新清缓存+重启，所以这里提示用户
    print("\n[!]  冷启动测试需要重新清空缓存并重启应用。")
    print("   如果已执行步骤1的清缓存+重启，请按 Enter 继续...")
    input()
    cold_result = run_round(questions, "冷启动测试（无缓存）")
    _save_json(cold_result, COLD_FILE)

    # 5. 热启动
    print("\n[~] 等待 30 秒让缓存充分预热...")
    time.sleep(30)
    warm_result = run_round(questions, "热启动测试（有缓存）")
    _save_json(warm_result, WARM_FILE)

    # 6. 报告
    report = gen_report(cold_result, warm_result)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    _save_json({
        "testMetadata": {"timestamp": ts, "apiEndpoint": ASK_ENDPOINT,
                         "testCount": TEST_COUNT, "warmupCount": WARMUP_COUNT},
        "cold": cold_result, "warm": warm_result, "report": report,
    }, os.path.join(OUTPUT_DIR, f"p99_report_{ts}.json"))


def cmd_report():
    """从已有结果文件生成对比报告"""
    for tag, path in [("冷启动", COLD_FILE), ("热启动", WARM_FILE)]:
        if not os.path.exists(path):
            print(f"错误: 缺少{tag}结果文件 ({path})")
            print(f"请先运行 --cold 和 --warm")
            sys.exit(1)
    with open(COLD_FILE, encoding="utf-8") as f:
        cold = json.load(f)
    with open(WARM_FILE, encoding="utf-8") as f:
        warm = json.load(f)
    report = gen_report(cold, warm)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    _save_json({
        "testMetadata": {"timestamp": ts, "apiEndpoint": ASK_ENDPOINT,
                         "testCount": TEST_COUNT, "warmupCount": WARMUP_COUNT},
        "cold": cold, "warm": warm, "report": report,
    }, os.path.join(OUTPUT_DIR, f"p99_report_{ts}.json"))


def cmd_dry_run():
    """试运行：连通性检查 + 抽样5条"""
    print("[dry-run] P99 延迟测试试运行")
    if not check_connectivity():
        sys.exit(1)
    questions = load_questions(5)
    for i, q in enumerate(questions):
        api = ask_question(q["userQuery"])
        ch = "HIT" if api["cacheHit"] else "MISS"
        print(f"  [{i+1}/5] [{ch}] {api['latencyMs']}ms "
              f"[{q['disease']}] {q['userQuery'][:50]}...")
    # Redis检测
    code, out, _ = _redis_cli("PING")
    redis_ok = code == 0 and "PONG" in out
    print(f"\n  Redis 连通性: {'OK' if redis_ok else '不可达 (缓存清除功能受限)'}")
    print(f"  缓存前缀: {REDIS_CACHE_PREFIXES}")
    _, cnt_out, _ = _redis_cli("DBSIZE") if redis_ok else (-1, "N/A", "")
    print(f"  Redis 键总数: {cnt_out}")
    print("\n[dry-run] 完成，确认以上输出正常后可执行 --cold / --warm / --all")


# ═══════════════════════════════════════════════
# 入口
# ═══════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="IMKQAS P99 响应时间测试 — 冷启动 vs 热启动",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
    示例流程:
      py p99_latency_test.py --dry-run       # 试运行
      py p99_latency_test.py --clear-cache   # 清空Redis缓存
      # 重启 Spring Boot 清空 Caffeine 缓存
      py p99_latency_test.py --cold          # 冷启动测试
      py p99_latency_test.py --warm          # 热启动测试
      py p99_latency_test.py --report        # 生成对比报告
      py p99_latency_test.py --all           # 一键完整流程
        """,
    )
    parser.add_argument("--clear-cache", action="store_true", help="清空Redis缓存")
    parser.add_argument("--cold", action="store_true", help="冷启动测试（500条）")
    parser.add_argument("--warm", action="store_true", help="热启动测试（500条）")
    parser.add_argument("--all", action="store_true", help="一键完整流程")
    parser.add_argument("--report", action="store_true", help="对比已有两轮结果")
    parser.add_argument("--dry-run", action="store_true", help="试运行")

    args = parser.parse_args()

    if args.dry_run:
        cmd_dry_run()
    elif args.clear_cache:
        cmd_clear_cache()
    elif args.cold:
        cmd_cold()
    elif args.warm:
        cmd_warm()
    elif args.all:
        cmd_all()
    elif args.report:
        cmd_report()
    else:
        print("未指定子命令，常用操作:")
        print("  --dry-run      试运行")
        print("  --clear-cache  清空Redis缓存")
        print("  --cold         冷启动测试")
        print("  --warm         热启动测试")
        print("  --report       生成对比报告")
        print("  --all          一键完整流程")
        print("\n使用 --help 查看完整说明。")


if __name__ == "__main__":
    main()
