#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
K值调优全局配置
"""

import os
import sys
import io

# 强制 UTF-8 输出，解决 Windows GBK 终端编码问题
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# ============================================================
# 服务器配置
# ============================================================
BASE_URL = os.environ.get("IMKQAS_BASE_URL", "http://8.138.40.200:8080")
RETRIEVAL_ENDPOINT = f"{BASE_URL}/api/rag/retrieval-test"
HEALTH_ENDPOINT = f"{BASE_URL}/api/rag/health"
REQUEST_TIMEOUT = 60  # 单次请求超时（秒）

# ============================================================
# 测试场景
# ============================================================
# 相对于项目根目录
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEST_SCENARIOS_PATH = os.path.join(PROJECT_ROOT, "scripts", "test_scenarios.json")

# ============================================================
# K 值采样策略（对数分布，低值密集、高值稀疏）
# ============================================================
K_VALUES = [2, 3, 5, 10, 15, 20, 30, 50, 80, 120, 200, 350, 500]

# ============================================================
# Ground Truth 构建参数
# ============================================================
GT_POOL_K = 500           # 候选池构建时的检索K值
GT_TOP_N = 20             # 每个查询选取的GT项数
GT_MIN_KEYWORD_MATCH = 1  # 候选chunk最少需要匹配的关键词数

# ============================================================
# 延迟测量参数
# ============================================================
MEASUREMENT_REPEATS = 3    # 每个(K, query, pathway)组合重复测量次数
WARMUP_REPEATS = 1         # 预热调用次数（结果丢弃）
RTT_PING_COUNT = 10        # RTT基线测量次数

# ============================================================
# 拐点检测参数
# ============================================================
MARGINAL_THRESHOLD = 0.5    # 边际收益阈值（百分点），低于此值视为拐点
SATURATION_THRESHOLD = 0.1  # 饱和点阈值（百分点）

# ============================================================
# 业务SLA约束（真实系统含网络+嵌入API调用，放宽至秒级）
# ============================================================
P99_LATENCY_SLA_MS = 1000.0   # P99延迟必须 < 1s
RECALL_SLA_MIN = 0.80         # 加权召回率最低 80%

# ============================================================
# 压力测试参数
# ============================================================
STRESS_ITERATIONS = 200       # 压力测试请求数
STRESS_PARALLEL = 4           # 并发数
STRESS_QUERY_SAMPLE = 5       # 随机选取的查询数

# ============================================================
# 输出配置
# ============================================================
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "scripts", "k_tuning_results")
OUTPUT_PNG = os.path.join(OUTPUT_DIR, "k_tuning_report.png")
OUTPUT_MD = os.path.join(OUTPUT_DIR, "k_tuning_report.md")
OUTPUT_JSON = os.path.join(OUTPUT_DIR, "k_tuning_results.json")

# 自动创建输出目录
os.makedirs(OUTPUT_DIR, exist_ok=True)
