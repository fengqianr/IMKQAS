# -*- coding: utf-8 -*-
"""
安全门控置信度阈值离线调优 — 全局配置
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
QA_ENDPOINT = f"{BASE_URL}/api/qa/ask"
HEALTH_ENDPOINT = f"{BASE_URL}/api/rag/health"
REQUEST_TIMEOUT = 120

# ============================================================
# 路径配置
# ============================================================
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
TEST_SCENARIOS_PATH = os.path.join(PROJECT_ROOT, "scripts", "test_scenarios.json")

CACHE_DIR = os.path.join(PROJECT_ROOT, "scripts", "safety_gate_tuning_cache")
QA_CACHE_FILE = os.path.join(CACHE_DIR, "qa_confidence_cache.json")
LABELS_FILE = os.path.join(CACHE_DIR, "answer_labels.json")

OUTPUT_DIR = os.path.join(PROJECT_ROOT, "scripts", "safety_gate_tuning_results")
OUTPUT_JSON = os.path.join(OUTPUT_DIR, "tuning_results.json")
OUTPUT_MD = os.path.join(OUTPUT_DIR, "tuning_report.md")
OUTPUT_PNG = os.path.join(OUTPUT_DIR, "tuning_report.png")

os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ============================================================
# 采样配置
# ============================================================
FULL_SAMPLE_SIZE = 200
STRATIFIED_SAMPLE_SIZE = 50
QUICK_SAMPLE_SIZE = 20

# ============================================================
# 当前默认阈值
# ============================================================
DEFAULT_MIN_THRESHOLD = 0.50
DEFAULT_WARNING_THRESHOLD = 0.55

# ============================================================
# 阈值扫描范围（双阈值网格搜索）
# ============================================================
MIN_THRESHOLD_RANGE = (0.30, 0.80, 0.05)
WARNING_THRESHOLD_RANGE = (0.40, 0.90, 0.05)

# ============================================================
# LLM调用控制
# ============================================================
LLM_CALL_DELAY_SEC = 1.0
BATCH_SIZE = 5

# ============================================================
# 标注配置
# ============================================================
LABELING_MODEL = "gpt-4o-mini"
