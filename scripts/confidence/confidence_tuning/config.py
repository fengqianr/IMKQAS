#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LLM置信度阈值离线调优 — 全局配置
"""

import os
import sys
import io

if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# ============================================================
# 服务器配置
# ============================================================
BASE_URL = os.environ.get("IMKQAS_BASE_URL", "http://localhost:8080")
SYNONYM_TEST_ENDPOINT = f"{BASE_URL}/api/rag/synonym-test"
REQUEST_TIMEOUT = 60

# ============================================================
# 路径配置
# ============================================================
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEST_SCENARIOS_PATH = os.path.join(PROJECT_ROOT, "scripts", "test_scenarios.json")

CACHE_DIR = os.path.join(PROJECT_ROOT, "scripts", "confidence_tuning_cache")
CONFIDENCE_CACHE_FILE = os.path.join(CACHE_DIR, "llm_confidence_cache.json")
GROUND_TRUTH_FILE = os.path.join(CACHE_DIR, "ground_truth_terms.json")

OUTPUT_DIR = os.path.join(PROJECT_ROOT, "scripts", "confidence_tuning_results")
OUTPUT_JSON = os.path.join(OUTPUT_DIR, "tuning_results.json")
OUTPUT_MD = os.path.join(OUTPUT_DIR, "tuning_report.md")
OUTPUT_PNG = os.path.join(OUTPUT_DIR, "tuning_report.png")

os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ============================================================
# 采样配置
# ============================================================
FULL_SAMPLE_SIZE = 500
STRATIFIED_SAMPLE_SIZE = 150
QUICK_SAMPLE_SIZE = 50

# ============================================================
# 词频分层阈值
# ============================================================
FREQ_HIGH_THRESHOLD = 5
FREQ_MEDIUM_THRESHOLD = 2

# ============================================================
# LLM调用控制
# ============================================================
LLM_CALL_DELAY_SEC = 0.5
BATCH_SIZE = 10

# ============================================================
# 阈值扫描范围
# ============================================================
THRESHOLD_MIN = 0.50
THRESHOLD_MAX = 0.98
THRESHOLD_STEP = 0.02

# ============================================================
# 拐点检测
# ============================================================
MARGINAL_THRESHOLD = 0.5
