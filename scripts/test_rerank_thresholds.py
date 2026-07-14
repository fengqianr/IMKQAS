#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import json, os, sys, time, random, argparse, math
from collections import defaultdict, OrderedDict
from datetime import datetime

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
TEST_SCENARIOS_PATH = os.path.join(SCRIPT_DIR, "test_scenarios.json")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "confidence_tuning_results")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 设置控制台UTF-8编码（Windows兼容）
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# ============================================================
# 配置常量
# ============================================================
BASE_URL = os.environ.get("IMKQAS_BASE_URL", "http://localhost:8080")
QA_ENDPOINT = f"{BASE_URL}/api/qa/ask"
REQUEST_TIMEOUT = 120
CALL_DELAY = 0.3  # 请求间隔（秒），避免压垮服务器

# 阈值扫描范围
MIN_THRESHOLD_MIN = 0.10
MIN_THRESHOLD_MAX = 0.70
MIN_THRESHOLD_STEP = 0.05
WARN_THRESHOLD_MIN = 0.15
WARN_THRESHOLD_MAX = 0.95
WARN_THRESHOLD_STEP = 0.05

# 当前生产环境阈值（用于对比基线）
CURRENT_MIN_THRESHOLD = 0.35
CURRENT_WARNING_THRESHOLD = 0.60

# ============================================================
# 8 个儿科疾病的模拟评分参数
# 基于各疾病的医学证据成熟度设定
# noise_std 范围较大，模拟真实场景的分数方差
# ============================================================
DISEASE_SCORE_PARAMS = {
    "肺炎": {
        "authority_base": 0.78,
        "timeliness_base": 0.82,
        "semantic_center": 0.72,
        "intent_base": 0.75,
        "noise_std": 0.18,  # 较大噪声模拟真实变异
        "low_prob": 0.08,  # 8%的概率产生低分（模拟不匹配查询）
        "high_prob": 0.65,  # 65%的概率产生高分
    },
    "腹泻": {
        "authority_base": 0.72,
        "timeliness_base": 0.78,
        "semantic_center": 0.68,
        "intent_base": 0.72,
        "noise_std": 0.20,
        "low_prob": 0.10,
        "high_prob": 0.60,
    },
    "哮喘": {
        "authority_base": 0.82,
        "timeliness_base": 0.78,
        "semantic_center": 0.76,
        "intent_base": 0.78,
        "noise_std": 0.16,
        "low_prob": 0.06,
        "high_prob": 0.70,
    },
    "毛细支气管炎": {
        "authority_base": 0.66,
        "timeliness_base": 0.72,
        "semantic_center": 0.60,
        "intent_base": 0.64,
        "noise_std": 0.22,
        "low_prob": 0.15,
        "high_prob": 0.50,
    },
    "特应性皮炎": {
        "authority_base": 0.68,
        "timeliness_base": 0.74,
        "semantic_center": 0.64,
        "intent_base": 0.68,
        "noise_std": 0.20,
        "low_prob": 0.12,
        "high_prob": 0.55,
    },
    "手足口病": {
        "authority_base": 0.60,
        "timeliness_base": 0.68,
        "semantic_center": 0.56,
        "intent_base": 0.60,
        "noise_std": 0.24,
        "low_prob": 0.18,
        "high_prob": 0.45,
    },
    "热性惊厥": {
        "authority_base": 0.68,
        "timeliness_base": 0.74,
        "semantic_center": 0.62,
        "intent_base": 0.66,
        "noise_std": 0.22,
        "low_prob": 0.14,
        "high_prob": 0.50,
    },
    "缺铁性贫血": {
        "authority_base": 0.76,
        "timeliness_base": 0.80,
        "semantic_center": 0.70,
        "intent_base": 0.74,
        "noise_std": 0.18,
        "low_prob": 0.08,
        "high_prob": 0.65,
    },
}

# 各知识类型的半衰期（年），与Java端一致
HALF_LIVES = {
    "treatment": 3.0, "diagnosis": 6.0, "device": 5.0,
    "basic_science": 18.0, "public_health": 4.0,
    "classic_research": 100.0, "unknown": 5.0,
}

# 多因子权重，与Java端 MultiFactorRerankServiceImpl 一致
WEIGHT_AUTHORITY = 0.3
WEIGHT_TIMELINESS = 0.15
WEIGHT_SEMANTIC = 0.35
WEIGHT_INTENT = 0.2

# 证据金字塔权威性映射（来源类型 → 分数）
AUTHORITY_SOURCE_MAP = {
    "systematic_review": 1.0, "guideline": 1.0, "meta_analysis": 1.0,
    "drug_label": 0.95, "RCT": 0.9, "cohort_study": 0.7,
    "case_control": 0.6, "case_report": 0.4, "expert_opinion": 0.3,
    "textbook": 0.85, "review": 0.65, "forum": 0.1, "unknown": 0.5,
}


# ============================================================
# 离线评分模拟器
# ============================================================

def clamp(value, low=0.0, high=1.0):
    return max(low, min(high, value))

def calc_authority_from_content(content):
    """从内容关键词推断权威性，与Java端逻辑一致"""
    if not content:
        return 0.5
    high_auth = ["指南", "共识", "Cochrane", "NICE", "WHO", "FDA", "NMPA",
                 "中华医学会", "meta分析", "系统评价", "systematic review"]
    rct_auth = ["随机对照", "RCT", "双盲", "随机分组", "randomized"]
    label_auth = ["说明书", "适应症", "用法用量", "不良反应", "禁忌", "药品名称", "批准文号"]
    research_auth = ["研究", "试验", "临床", "队列", "病例对照"]
    popular_auth = ["科普", "百科", "UpToDate", "默沙东", "梅奥", "Mayo"]

    for kw in high_auth:
        if kw in content:
            return 1.0
    for kw in label_auth:
        if kw in content:
            return 0.95
    for kw in rct_auth:
        if kw in content:
            return 0.9
    for kw in research_auth:
        if kw in content:
            return 0.7
    for kw in popular_auth:
        if kw in content:
            return 0.6
    return 0.5

def calc_timeliness(content, disease=None):
    """计算时效性：指数衰减 e^(-lambda * deltaYears)"""
    year = None
    if content:
        import re
        m = re.search(r"\b(19\d{2}|20\d{2})\s*年", content)
        if m:
            year = int(m.group(1))
    if year is None:
        # 模拟年份：各疾病有不同典型出版年份
        base_years = {
            "肺炎": 2022, "腹泻": 2021, "哮喘": 2023,
            "毛细支气管炎": 2020, "特应性皮炎": 2022,
            "手足口病": 2019, "热性惊厥": 2021, "缺铁性贫血": 2023,
        }
        year = base_years.get(disease, 2021)

    current_year = 2026
    delta = current_year - year
    if delta < 0:
        return 1.0

    half_life = HALF_LIVES.get("treatment", 3.0)
    lam = math.log(2) / half_life
    return clamp(math.exp(-lam * delta))

def calc_intent_match(query, disease_keywords):
    """计算查询意图与疾病关键词的匹配度"""
    if not query:
        return 0.3
    hits = sum(1 for kw in disease_keywords if kw in query)
    return clamp(hits * 0.2)

def simulate_max_score(query, disease, disease_keywords):
    """
    模拟多因子重排序后的最高分，产生真实的分数分布
    - ~10-18% 低分 (<0.35): 模拟检索不匹配/离题
    - ~15-25% 中分 (0.35-0.60): 模拟模糊/边缘查询
    - ~55-70% 高分 (>=0.60): 模拟良好匹配

    公式: finalScore = 0.3*authority + 0.15*timeliness + 0.35*semantic + 0.2*intent
    """
    params = DISEASE_SCORE_PARAMS.get(disease, DISEASE_SCORE_PARAMS["肺炎"])

    # 确定本次查询的质量等级
    roll = random.random()
    if roll < params["low_prob"]:
        quality = "low"  # 低质量匹配
    elif roll < params["low_prob"] + (1.0 - params["high_prob"] - params["low_prob"]):
        quality = "medium"  # 中等质量匹配
    else:
        quality = "high"  # 高质量匹配

    if quality == "low":
        # 低质量：大幅降低各维度分数
        authority = clamp(params["authority_base"] * 0.4 + random.gauss(0, 0.15))
        timeliness = clamp(params["timeliness_base"] * 0.5 + random.gauss(0, 0.12))
        semantic = clamp(params["semantic_center"] * 0.3 + random.gauss(0, 0.15))
        intent = clamp(params["intent_base"] * 0.35 + random.gauss(0, 0.12))
    elif quality == "medium":
        # 中等质量：微调分数到中等范围
        authority = clamp(params["authority_base"] * 0.7 + random.gauss(0, 0.10))
        timeliness = clamp(params["timeliness_base"] * 0.75 + random.gauss(0, 0.08))
        semantic = clamp(params["semantic_center"] * 0.65 + random.gauss(0, 0.12))
        intent = clamp(params["intent_base"] * 0.65 + random.gauss(0, 0.10))
    else:
        # 高质量：接近基准分数
        authority = clamp(params["authority_base"] + random.gauss(0, 0.08))
        timeliness = clamp(params["timeliness_base"] + random.gauss(0, 0.06))
        semantic = clamp(params["semantic_center"] + random.gauss(0, 0.10))
        intent = clamp(params["intent_base"] + random.gauss(0, 0.08))

    final_score = (WEIGHT_AUTHORITY * authority +
                   WEIGHT_TIMELINESS * timeliness +
                   WEIGHT_SEMANTIC * semantic +
                   WEIGHT_INTENT * intent)

    # 加入额外扰动
    final_score = clamp(final_score + random.gauss(0, params["noise_std"] * 0.4))

    return {
        "maxScore": round(final_score, 4),
        "authority": round(authority, 4),
        "timeliness": round(timeliness, 4),
        "semantic": round(semantic, 4),
        "intent": round(intent, 4),
        "quality": quality,
    }

def run_offline_collection(scenarios):
    """离线模式：为所有题目模拟评分"""
    results = []
    disease_keywords_map = {
        "肺炎": ["肺炎", "发热", "咳嗽", "呼吸急促", "低氧", "抗菌药", "住院", "X光", "CT"],
        "腹泻": ["腹泻", "脱水", "口服补液盐", "补锌", "益生菌", "大便", "呕吐", "血便"],
        "哮喘": ["哮喘", "喘息", "咳嗽", "胸闷", "气促", "吸入", "峰流速", "过敏原", "肺功能"],
        "毛细支气管炎": ["毛细支气管炎", "婴儿", "住院", "氧饱和度", "RSV", "喘息", "低氧"],
        "特应性皮炎": ["特应性皮炎", "湿疹", "瘙痒", "润肤", "激素", "保湿", "过敏", "皮肤"],
        "手足口病": ["手足口病", "皮疹", "发热", "重症", "隔离", "HFMD", "水疱", "消毒"],
        "热性惊厥": ["热性惊厥", "抽搐", "发热", "体温", "单纯性", "复杂性", "急救", "癫痫"],
        "缺铁性贫血": ["缺铁性贫血", "贫血", "补铁", "铁蛋白", "血红蛋白", "辅食", "铁剂"],
    }

    for scenario in scenarios:
        query = scenario.get("userQuery", "")
        disease = scenario.get("disease", "肺炎")
        keywords = disease_keywords_map.get(disease, [])
        score_info = simulate_max_score(query, disease, keywords)
        score_info["query"] = query
        score_info["disease"] = disease
        score_info["scenarioId"] = scenario.get("id", "")
        results.append(score_info)

    return results


# ============================================================
# 在线 QA 客户端
# ============================================================

class QaClient:
    """调用 /api/qa/ask 端点采集真实分数"""

    def __init__(self, base_url=None):
        self.base_url = base_url or BASE_URL
        self.endpoint = f"{self.base_url}/api/qa/ask"
        self.health_url = f"{self.base_url}/api/rag/health"
        self.total = 0
        self.success = 0
        self.fail = 0

    def check_health(self):
        """检查服务器连通性"""
        try:
            import urllib.request
            req = urllib.request.Request(self.health_url, method="GET")
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status == 200
        except Exception:
            return False

    def ask(self, query, user_id=1, conversation_id=1):
        """发送单个问答请求，返回响应JSON"""
        import urllib.request
        import urllib.error
        payload = json.dumps({
            "query": query,
            "userId": user_id,
            "conversationId": conversation_id,
        }).encode("utf-8")
        req = urllib.request.Request(
            self.endpoint,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            return {"error": f"HTTP {e.code}", "query": query}
        except Exception as e:
            return {"error": str(e), "query": query}

    def collect_scores(self, scenarios, limit=None):
        """批量采集，返回分数列表"""
        results = []
        scenarios_to_process = scenarios[:limit] if limit else scenarios
        total = len(scenarios_to_process)

        print(f"  开始采集 {total} 道题目的重排序分数...")
        for i, scenario in enumerate(scenarios_to_process):
            query = scenario.get("userQuery", "")
            disease = scenario.get("disease", "")
            scenario_id = scenario.get("id", "")

            resp = self.ask(query)
            self.total += 1

            score_info = {
                "query": query,
                "disease": disease,
                "scenarioId": scenario_id,
            }

            if "error" in resp:
                self.fail += 1
                score_info["maxScore"] = None
                score_info["error"] = resp["error"]
                score_info["confidence"] = 0.0
                score_info["blocked"] = None
            else:
                self.success += 1
                data = resp.get("data", resp)
                confidence = data.get("confidence", 0.0)
                answer = data.get("answer", "")
                # 根据 answer 判断是否被阻断
                blocked = "无法从现有医学资料库中找到" in answer or \
                          "相关性较低" in answer
                score_info["maxScore"] = None  # API 不直接返回 maxScore
                score_info["confidence"] = confidence
                score_info["blocked"] = blocked
                score_info["answerLen"] = len(answer) if answer else 0

            results.append(score_info)

            # 进度显示
            if (i + 1) % 50 == 0 or i == total - 1:
                pct = (i + 1) / total * 100
                print(f"    进度: {i+1}/{total} ({pct:.0f}%) "
                      f"成功={self.success} 失败={self.fail}")

            time.sleep(CALL_DELAY)

        return results


# ============================================================
# 双阈值扫描引擎
# ============================================================

def assess_confidence(max_score, min_threshold, warning_threshold):
    """
    复现 SafetyGuardService.assessConfidence() 的逻辑
    返回: "BLOCK" | "WARNING" | "PASS"
    """
    if max_score is None:
        return "BLOCK"
    if max_score < min_threshold:
        return "BLOCK"
    elif max_score < warning_threshold:
        return "WARNING"
    else:
        return "PASS"

def generate_threshold_combinations():
    """生成所有合法的 (minThreshold, warningThreshold) 组合"""
    combos = []
    min_t = MIN_THRESHOLD_MIN
    while min_t <= MIN_THRESHOLD_MAX + 0.001:
        warn_t = min_t + WARN_THRESHOLD_STEP
        while warn_t <= WARN_THRESHOLD_MAX + 0.001:
            combos.append((round(min_t, 3), round(warn_t, 3)))
            warn_t += WARN_THRESHOLD_STEP
        min_t += MIN_THRESHOLD_STEP
    return combos

def run_threshold_sweep(score_results):
    """
    对采集到的分数运行双阈值网格扫描
    score_results: [{"maxScore": float, "disease": str, ...}, ...]
    """
    valid_results = [r for r in score_results if r.get("maxScore") is not None]
    if not valid_results:
        print("  [ERROR] 没有有效的 maxScore 数据")
        return None

    combos = generate_threshold_combinations()
    print(f"  阈值组合数: {len(combos)}")
    print(f"  有效数据点: {len(valid_results)}")

    all_sweep_results = []
    for min_t, warn_t in combos:
        counts = {"BLOCK": 0, "WARNING": 0, "PASS": 0}
        disease_counts = defaultdict(lambda: {"BLOCK": 0, "WARNING": 0, "PASS": 0, "total": 0})

        for r in valid_results:
            decision = assess_confidence(r["maxScore"], min_t, warn_t)
            counts[decision] += 1
            disease = r.get("disease", "unknown")
            disease_counts[disease][decision] += 1
            disease_counts[disease]["total"] += 1

        total = sum(counts.values())
        utility = compute_utility(counts, disease_counts, valid_results, min_t, warn_t)

        all_sweep_results.append({
            "minThreshold": min_t,
            "warningThreshold": warn_t,
            "blockCount": counts["BLOCK"],
            "warningCount": counts["WARNING"],
            "passCount": counts["PASS"],
            "blockRate": round(counts["BLOCK"] / total, 4) if total > 0 else 0,
            "warningRate": round(counts["WARNING"] / total, 4) if total > 0 else 0,
            "passRate": round(counts["PASS"] / total, 4) if total > 0 else 0,
            "utility": round(utility, 4),
            "diseaseCounts": {d: dict(c) for d, c in disease_counts.items()},
        })

    # 按效用排序
    all_sweep_results.sort(key=lambda x: x["utility"], reverse=True)
    return all_sweep_results

# 独立于阈值的"ground truth"分数区间
GROUND_TRUTH_BLOCK_MAX = 0.25   # 低于此值的查询应被阻断
GROUND_TRUTH_PASS_MIN = 0.70    # 高于此值的查询应被放行
# 0.25~0.70 之间为灰色地带

def compute_utility(counts, disease_counts, score_results, min_t, warn_t):
    """
    综合效用分 = 安全 * 准确 * 效率 * 分离度

    - 安全: 极低分被BLOCK的比例
    - 准确: 高分被PASS的比例
    - 效率: 惩罚过高的WARNING率（频繁的免责声明影响用户体验）
    - 分离度: WARNING区间宽度适当（太窄→缺乏缓冲，太宽→过度警告）
    """
    total = sum(counts.values())
    if total == 0:
        return 0.0

    should_block = sum(1 for r in score_results
                       if r.get("maxScore", 0) < GROUND_TRUTH_BLOCK_MAX)
    should_pass = sum(1 for r in score_results
                      if r.get("maxScore", 0) >= GROUND_TRUTH_PASS_MIN)

    # 安全分 (0-1): 应阻断的被阻断
    correctly_blocked = sum(1 for r in score_results
                            if r.get("maxScore", 0) < GROUND_TRUTH_BLOCK_MAX
                            and assess_confidence(r["maxScore"], min_t, warn_t) == "BLOCK")
    safety = correctly_blocked / should_block if should_block > 0 else 1.0

    # 准确分 (0-1): 应放行的被放行
    correctly_passed = sum(1 for r in score_results
                           if r.get("maxScore", 0) >= GROUND_TRUTH_PASS_MIN
                           and assess_confidence(r["maxScore"], min_t, warn_t) == "PASS")
    accuracy = correctly_passed / should_pass if should_pass > 0 else 1.0

    # 效率分 (0-1): 警告率在 15%-40% 为理想区间
    warn_rate = counts["WARNING"] / total
    if 0.15 <= warn_rate <= 0.40:
        efficiency = 1.0
    elif warn_rate < 0.15:
        efficiency = 1.0 - (0.15 - warn_rate) * 2  # WARNING太少→安全兜底不足
    else:
        efficiency = 1.0 - (warn_rate - 0.40) * 1.5  # WARNING太多→用户体验差

    # 误伤惩罚 (0-1): 高分查询被WARNING/BLOCK是不好的
    high_but_not_pass = sum(1 for r in score_results
                            if r.get("maxScore", 0) >= 0.60
                            and assess_confidence(r["maxScore"], min_t, warn_t) != "PASS")
    total_high = sum(1 for r in score_results if r.get("maxScore", 0) >= 0.60)
    false_positive_penalty = high_but_not_pass / total_high if total_high > 0 else 0
    precision = 1.0 - false_positive_penalty

    # 间隔合理性 (0-1): 警告区间宽度在 0.15-0.30 之间理想
    gap = warn_t - min_t
    if 0.15 <= gap <= 0.30:
        gap_score = 1.0
    elif gap < 0.05:
        gap_score = 0.3  # 间隔太小，没有缓冲
    elif gap < 0.15:
        gap_score = 0.7
    elif gap > 0.50:
        gap_score = 0.5  # 间隔太大，过于保守
    else:
        gap_score = 1.0 - (gap - 0.30) * 0.5

    # 阻断率合理性 (0-1): BLOCK率在 5%-20% 之间理想（医疗场景不能太高）
    block_rate = counts["BLOCK"] / total
    if 0.05 <= block_rate <= 0.20:
        block_score = 1.0
    elif block_rate < 0.02:
        block_score = 0.4  # 几乎不阻断，不安全
    elif block_rate > 0.40:
        block_score = 0.3  # 阻断太多，过度保守
    else:
        block_score = 1.0 - abs(block_rate - 0.12) * 2

    # 综合效用
    utility = (safety * 0.30 + accuracy * 0.25 + efficiency * 0.15 +
               precision * 0.12 + gap_score * 0.10 + block_score * 0.08)
    return round(utility, 4)


# ============================================================
# 报告生成
# ============================================================

def print_score_distribution(score_results):
    valid = [r["maxScore"] for r in score_results if r.get("maxScore") is not None]
    if not valid:
        print("  无有效分数数据")
        return

    bins = [
        (0.00, 0.19, "极低 (可能离题/无关)"),
        (0.20, 0.34, "低 (应阻断)"),
        (0.35, 0.59, "中等 (应警告)"),
        (0.60, 0.79, "较高 (可放行)"),
        (0.80, 1.00, "高 (放心放行)"),
    ]

    print("\n" + "-" * 70)
    print("  分数分布")
    print("-" * 70)
    print(f"  {'最高分范围':<20} {'数量':>6} {'占比':>8}  {'说明'}")
    print(f"  {'-'*18}  {'-'*5}  {'-'*7}  {'-'*20}")
    total = len(valid)
    for low, high, desc in bins:
        count = sum(1 for s in valid if low <= s <= high)
        pct = count / total * 100 if total > 0 else 0
        bar = "#" * int(pct / 2)
        print(f"  {low:.2f}-{high:.2f}             {count:>5}  {pct:>6.1f}%  {desc} {bar}")
    print(f"  {'总计':<20} {total:>5}")
    print(f"  平均分: {sum(valid)/len(valid):.3f}  中位数: {sorted(valid)[len(valid)//2]:.3f}")

def print_current_threshold_performance(sweep_results, score_results):
    current = None
    for r in sweep_results:
        if (abs(r["minThreshold"] - CURRENT_MIN_THRESHOLD) < 0.001 and
                abs(r["warningThreshold"] - CURRENT_WARNING_THRESHOLD) < 0.001):
            current = r
            break

    print("\n" + "-" * 70)
    print(f"  当前阈值表现 (min={CURRENT_MIN_THRESHOLD}, warn={CURRENT_WARNING_THRESHOLD})")
    print("-" * 70)
    if current:
        total = (current["blockCount"] + current["warningCount"] + current["passCount"])
        print(f"  BLOCK:   {current['blockCount']:>5} ({current['blockRate']*100:.1f}%)")
        print(f"  WARNING: {current['warningCount']:>5} ({current['warningRate']*100:.1f}%)")
        print(f"  PASS:    {current['passCount']:>5} ({current['passRate']*100:.1f}%)")
        print(f"  综合效用: {current['utility']:.4f}")
    else:
        print("  (当前阈值组合不在扫描范围内)")

def print_top_combinations(sweep_results, top_n=15):
    print("\n" + "-" * 70)
    print(f"  Top-{top_n} 阈值组合 (按综合效用排序)")
    print("-" * 70)
    header = f"  {'排名':<5} {'min':<8} {'warn':<8} {'BLOCK%':<9} {'WARN%':<9} {'PASS%':<9} {'效用':<8}"
    print(header)
    print(f"  {'-'*len(header)}")
    for i, r in enumerate(sweep_results[:top_n]):
        marker = ""
        if (abs(r["minThreshold"] - CURRENT_MIN_THRESHOLD) < 0.001 and
                abs(r["warningThreshold"] - CURRENT_WARNING_THRESHOLD) < 0.001):
            marker = " <-- 当前"
        print(f"  {i+1:<5} {r['minThreshold']:<8.2f} {r['warningThreshold']:<8.2f} "
              f"{r['blockRate']*100:<8.1f}% {r['warningRate']*100:<8.1f}% "
              f"{r['passRate']*100:<8.1f}% {r['utility']:<8.4f}{marker}")

def print_disease_breakdown(sweep_results, score_results, min_t, warn_t):
    target = None
    for r in sweep_results:
        if abs(r["minThreshold"] - min_t) < 0.001 and abs(r["warningThreshold"] - warn_t) < 0.001:
            target = r
            break

    if not target:
        print(f"  未找到阈值组合 (min={min_t}, warn={warn_t})")
        return

    print(f"\n  各疾病类别表现 (min={min_t}, warn={warn_t}):")
    print(f"  {'疾病':<16} {'题目数':>6} {'BLOCK%':>9} {'WARN%':>9} {'PASS%':>9} {'平均分':>8}")
    print(f"  {'-'*16} {'-'*5} {'-'*8} {'-'*8} {'-'*8} {'-'*7}")

    for disease in ["肺炎", "腹泻", "哮喘", "毛细支气管炎", "特应性皮炎", "手足口病", "热性惊厥", "缺铁性贫血"]:
        dc = target.get("diseaseCounts", {}).get(disease, {})
        total_d = dc.get("total", 0)
        if total_d == 0:
            continue
        block_pct = dc.get("BLOCK", 0) / total_d * 100
        warn_pct = dc.get("WARNING", 0) / total_d * 100
        pass_pct = dc.get("PASS", 0) / total_d * 100
        scores_d = [r["maxScore"] for r in score_results
                    if r.get("disease") == disease and r.get("maxScore") is not None]
        avg_score = sum(scores_d) / len(scores_d) if scores_d else 0
        print(f"  {disease:<16} {total_d:>5}  {block_pct:>7.1f}% {warn_pct:>7.1f}% {pass_pct:>7.1f}% {avg_score:>7.3f}")

def print_recommendations(sweep_results):
    if not sweep_results:
        print("\n  无法生成推荐")
        return

    best = sweep_results[0]
    current = None
    for r in sweep_results:
        if (abs(r["minThreshold"] - CURRENT_MIN_THRESHOLD) < 0.001 and
                abs(r["warningThreshold"] - CURRENT_WARNING_THRESHOLD) < 0.001):
            current = r
            break

    print("\n" + "=" * 70)
    print("  最终推荐")
    print("=" * 70)

    print(f"\n  最优: minThreshold={best['minThreshold']:.2f}, "
          f"warningThreshold={best['warningThreshold']:.2f} "
          f"(效用={best['utility']:.4f})")
    print(f"    BLOCK={best['blockRate']*100:.1f}% "
          f"WARNING={best['warningRate']*100:.1f}% "
          f"PASS={best['passRate']*100:.1f}%")

    if current:
        utility_diff = best["utility"] - current["utility"]
        if utility_diff > 0.01:
            print(f"    相比当前阈值效用提升: +{utility_diff:.4f}")
        elif utility_diff < -0.01:
            print(f"    注意: 效用低于当前阈值 {utility_diff:.4f}")

    conservative = None
    for r in sweep_results:
        if r["blockRate"] >= 0.10 and r["passRate"] <= 0.80:
            conservative = r
            break
    if conservative:
        print(f"\n  保守: minThreshold={conservative['minThreshold']:.2f}, "
              f"warningThreshold={conservative['warningThreshold']:.2f} "
              f"(效用={conservative['utility']:.4f})")
        print(f"    更高的安全边际，适合生产环境")

    permissive = None
    for r in reversed(sweep_results):
        if r["blockRate"] <= 0.08 and r["passRate"] >= 0.75:
            permissive = r
            break
    if permissive:
        print(f"\n  宽松: minThreshold={permissive['minThreshold']:.2f}, "
              f"warningThreshold={permissive['warningThreshold']:.2f} "
              f"(效用={permissive['utility']:.4f})")
        print(f"    更少的阻断，适合内部测试环境")

    print(f"\n  修改方法: 编辑 application.yml")
    print(f"    imkqas.rag.safety.confidence.min-threshold: {best['minThreshold']:.2f}")
    print(f"    imkqas.rag.safety.confidence.warning-threshold: {best['warningThreshold']:.2f}")

def save_results(sweep_results, score_results, mode, sample_size, output_dir=None):
    output_dir = output_dir or OUTPUT_DIR
    output_path = os.path.join(output_dir, "rerank_threshold_results.json")
    valid_scores = [r["maxScore"] for r in score_results if r.get("maxScore") is not None]
    output = {
        "generatedAt": datetime.now().isoformat(),
        "mode": mode,
        "sampleSize": sample_size,
        "currentThresholds": {
            "minThreshold": CURRENT_MIN_THRESHOLD,
            "warningThreshold": CURRENT_WARNING_THRESHOLD,
        },
        "topCombinations": sweep_results[:20] if sweep_results else [],
        "scoreDistribution": {
            "count": len(valid_scores),
            "min": min(valid_scores) if valid_scores else 0,
            "max": max(valid_scores) if valid_scores else 0,
            "mean": round(sum(valid_scores) / max(1, len(valid_scores)), 4),
        },
        "diseaseBreakdown": {},
    }
    diseases = set(r.get("disease", "") for r in score_results)
    for d in sorted(diseases):
        scores = [r["maxScore"] for r in score_results if r.get("disease") == d and r.get("maxScore") is not None]
        if scores:
            output["diseaseBreakdown"][d] = {
                "count": len(scores),
                "meanScore": round(sum(scores) / len(scores), 4),
                "minScore": round(min(scores), 4),
                "maxScore": round(max(scores), 4),
            }
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n  详细结果已保存至: {output_path}")

# ============================================================
# 主入口
# ============================================================

def load_scenarios(path=None):
    path = path or TEST_SCENARIOS_PATH
    if not os.path.exists(path):
        print(f"[ERROR] 找不到测试场景文件: {path}")
        print("  请先运行 generate_500_questions.py 生成题目")
        sys.exit(1)
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    scenarios = data.get("scenarios", data) if isinstance(data, dict) else data
    return scenarios

def main():
    parser = argparse.ArgumentParser(
        description="LLM重排序置信度阈值测试工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--mode", default="offline", choices=["offline", "online"],
                        help="测试模式: offline(离线模拟) / online(在线调用QA API)")
    parser.add_argument("--base-url", default=BASE_URL,
                        help=f"后端服务地址 (默认: {BASE_URL})")
    parser.add_argument("--scenarios", default=TEST_SCENARIOS_PATH,
                        help="test_scenarios.json路径")
    parser.add_argument("--sample-size", type=int, default=0,
                        help="采样数量: 0=全部, 50=快速, 150=标准, 500=全量")
    parser.add_argument("--output-dir", default=OUTPUT_DIR,
                        help=f"输出目录 (默认: {OUTPUT_DIR})")

    args = parser.parse_args()

    base_url = args.base_url
    qa_endpoint = f"{args.base_url}/api/qa/ask"
    output_dir = args.output_dir
    os.makedirs(output_dir, exist_ok=True)

    print("=" * 70)
    print("  LLM 重排序置信度阈值测试工具")
    print(f"  模式: {args.mode}")
    if args.mode == "online":
        print(f"  服务器: {base_url}")
    print(f"  题目路径: {args.scenarios}")
    print("=" * 70)

    # [1] 加载测试场景
    print("\n[1] 加载测试场景...")
    scenarios = load_scenarios(args.scenarios)
    print(f"  已加载 {len(scenarios)} 道题目")

    # 疾病分布统计
    disease_counts = defaultdict(int)
    for s in scenarios:
        disease_counts[s.get("disease", "unknown")] += 1
    print("  疾病分布:", ", ".join(f"{d}:{c}" for d, c in sorted(disease_counts.items())))

    # 采样
    sample_size = args.sample_size if args.sample_size > 0 else len(scenarios)
    if sample_size < len(scenarios):
        random.shuffle(scenarios)
        scenarios = scenarios[:sample_size]
        print(f"  采样 {sample_size} 道题目")
    else:
        sample_size = len(scenarios)

    # [2] 采集分数
    print(f"\n[2] 采集重排序分数 (模式: {args.mode})...")
    if args.mode == "offline":
        score_results = run_offline_collection(scenarios)
        print(f"  离线模拟完成: {len(score_results)} 条分数数据")
    else:
        client = QaClient(args.base_url)
        if not client.check_health():
            print("  [ERROR] 无法连接到服务器，请确认服务已启动")
            print(f"  健康检查: {client.health_url}")
            sys.exit(1)
        print(f"  服务器连通正常")
        score_results = client.collect_scores(scenarios, limit=sample_size)

    # [3] 双阈值扫描
    print(f"\n[3] 双阈值网格扫描...")
    sweep_results = run_threshold_sweep(score_results)
    if not sweep_results:
        print("  [ERROR] 扫描失败")
        sys.exit(1)
    print(f"  扫描完成: {len(sweep_results)} 个阈值组合")

    # [4] 报告
    print("\n[4] 生成报告...")
    print("=" * 70)
    print("  LLM 重排序置信度阈值测试报告")
    print(f"  测试题目: {len(score_results)} 道 ({len(disease_counts)} 个儿科疾病)")
    print(f"  测试模式: {args.mode}")
    if args.mode == "online":
        print(f"  服务器: {base_url}")
    print(f"  生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    # 分数分布
    print_score_distribution(score_results)

    # 当前阈值表现
    print_current_threshold_performance(sweep_results, score_results)

    # Top 阈值组合
    print_top_combinations(sweep_results, top_n=15)

    # 最优阈值下各疾病表现
    best = sweep_results[0]
    print_disease_breakdown(sweep_results, score_results,
                            best["minThreshold"], best["warningThreshold"])

    # 推荐
    print_recommendations(sweep_results)

    # 保存结果
    save_results(sweep_results, score_results, args.mode, sample_size, output_dir)

    print("\n完成!")

if __name__ == "__main__":
    main()
