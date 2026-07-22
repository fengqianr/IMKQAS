# -*- coding: utf-8 -*-
"""
安全门控置信度双阈值离线调优 — 主入口

工作流:
  [0/6] 连接检查
  [1/6] 加载测试场景
  [2/6] 采集QA响应 (调用 /api/qa/ask，含LLM置信度+答案)
  [3/6] 答案质量标注 (启发式/LLM辅助)
  [4/6] 双阈值网格扫描
  [5/6] 报告生成
"""

import argparse
import sys
import os
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from safety_gate_tuning import config
from safety_gate_tuning.client import SafetyGateTestClient
from safety_gate_tuning.collector import (
    collect_qa_responses, load_cache, save_cache, extract_queries_from_scenarios,
    get_cache_stats, get_labeled_entries
)
from safety_gate_tuning.labeler import (
    label_entries, load_labels, save_labels, get_label_stats
)
from safety_gate_tuning.tuner import run_tuning
from safety_gate_tuning.metrics import generate_recommendation
from safety_gate_tuning.report import (
    generate_console_report, generate_json_report, generate_markdown_report
)
from safety_gate_tuning.visualizer import plot_tuning_results, plot_confidence_distribution


def load_scenarios(path):
    """加载测试场景JSON文件"""
    import json
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def main():
    parser = argparse.ArgumentParser(
        description="安全门控置信度双阈值离线调优工具",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--base-url", default=config.BASE_URL,
                        help=f"后端服务地址 (默认: {config.BASE_URL})")
    parser.add_argument("--scenarios", default=config.TEST_SCENARIOS_PATH,
                        help="test_scenarios.json路径")
    parser.add_argument("--sample-size", type=int, default=config.QUICK_SAMPLE_SIZE,
                        help=f"采样数量: 20(快速) / 50(标准) / 200(全量) (默认: {config.QUICK_SAMPLE_SIZE})")
    parser.add_argument("--use-cache", action="store_true",
                        help="使用已有QA缓存，不重新调用QA接口")
    parser.add_argument("--no-cache", action="store_true",
                        help="强制重新采集QA数据")
    parser.add_argument("--label-method", default="heuristic",
                        choices=["heuristic", "llm"],
                        help="标注方法 (默认: heuristic)")
    parser.add_argument("--objective", default="balanced",
                        choices=["safety_first", "balanced", "f1", "user_experience"],
                        help="优化目标 (默认: balanced)")
    parser.add_argument("--min-range", nargs=3, type=float,
                        metavar=("MIN_START", "MIN_END", "MIN_STEP"),
                        help="最小阈值扫描范围")
    parser.add_argument("--warn-range", nargs=3, type=float,
                        metavar=("WARN_START", "WARN_END", "WARN_STEP"),
                        help="警告阈值扫描范围")
    parser.add_argument("--current-min", type=float, default=config.DEFAULT_MIN_THRESHOLD,
                        help=f"当前最小阈值 (默认: {config.DEFAULT_MIN_THRESHOLD})")
    parser.add_argument("--current-warn", type=float, default=config.DEFAULT_WARNING_THRESHOLD,
                        help=f"当前警告阈值 (默认: {config.DEFAULT_WARNING_THRESHOLD})")
    parser.add_argument("--output-dir", default=config.OUTPUT_DIR,
                        help=f"输出目录 (默认: {config.OUTPUT_DIR})")
    parser.add_argument("--scenario-ids", nargs="*",
                        help="指定测试场景ID列表（如: Q001 Q002 Q003），不指定则使用全部")

    args = parser.parse_args()

    # 覆盖配置
    if args.base_url:
        config.BASE_URL = args.base_url
        config.QA_ENDPOINT = f"{args.base_url}/api/qa/ask"
        config.HEALTH_ENDPOINT = f"{args.base_url}/api/rag/health"
    if args.min_range:
        config.MIN_THRESHOLD_RANGE = tuple(args.min_range)
    if args.warn_range:
        config.WARNING_THRESHOLD_RANGE = tuple(args.warn_range)
    if args.current_min is not None:
        config.DEFAULT_MIN_THRESHOLD = args.current_min
    if args.current_warn is not None:
        config.DEFAULT_WARNING_THRESHOLD = args.current_warn

    config.OUTPUT_DIR = args.output_dir
    config.OUTPUT_JSON = os.path.join(args.output_dir, "tuning_results.json")
    config.OUTPUT_MD = os.path.join(args.output_dir, "tuning_report.md")
    config.OUTPUT_PNG = os.path.join(args.output_dir, "tuning_report.png")
    os.makedirs(config.OUTPUT_DIR, exist_ok=True)

    print("=" * 65)
    print("  安全门控置信度双阈值离线调优工具")
    print(f"  服务器: {config.BASE_URL}")
    print(f"  采样数: {args.sample_size}")
    print(f"  优化目标: {args.objective}")
    print("=" * 65)

    # ============================================================
    # [0/6] 连接检查
    # ============================================================
    print("\n[0/6] 连接检查...")
    client = SafetyGateTestClient(config.BASE_URL)
    if not client.check_health():
        print("  [ERROR] 无法连接到服务器，请确认服务已启动")
        sys.exit(1)
    rtt = client.measure_rtt()
    print(f"  服务器连通正常, 平均RTT: {rtt:.1f}ms")

    # ============================================================
    # [1/6] 加载测试场景
    # ============================================================
    print("\n[1/6] 加载测试场景...")
    try:
        data = load_scenarios(args.scenarios)
    except FileNotFoundError:
        print(f"  [ERROR] 找不到测试场景文件: {args.scenarios}")
        sys.exit(1)

    scenarios = data.get("scenarios", [])
    print(f"  已加载 {len(scenarios)} 条测试场景")

    # 按ID过滤
    if args.scenario_ids:
        id_set = set(args.scenario_ids)
        scenarios = [s for s in scenarios if s.get("id") in id_set]
        print(f"  按ID过滤后: {len(scenarios)} 条")

    # 提取查询
    query_list = extract_queries_from_scenarios(scenarios, args.sample_size)
    queries = [q for _, q in query_list]
    print(f"  采样查询: {len(queries)} 条")

    if len(queries) == 0:
        print("  [ERROR] 没有有效查询")
        sys.exit(1)

    # ============================================================
    # [2/6] 采集QA响应
    # ============================================================
    print("\n[2/6] 采集QA响应 (含LLM置信度+答案)...")
    cache = None

    if args.use_cache:
        cache = load_cache()
        if cache:
            print(f"  --use-cache: 使用已有缓存 ({cache.get('total_entries', 0)}条)")
        else:
            print("  --use-cache: 未找到缓存，将重新采集")

    if cache is None:
        print(f"  开始调用QA接口 (共{len(queries)}条，预计耗时{len(queries) * 3:.0f}秒)...")
        start_time = time.time()
        cache = collect_qa_responses(queries, client, use_cache=not args.no_cache)
        elapsed = time.time() - start_time
        print(f"  采集耗时: {elapsed:.1f}秒")

    stats = get_cache_stats(cache)
    print(f"  缓存统计: 总计{stats['total']}条, 已标注{stats['withLabel']}条, "
          f"平均置信度={stats['meanConfidence']:.3f}")

    # ============================================================
    # [3/6] 答案质量标注
    # ============================================================
    print(f"\n[3/6] 答案质量标注 (方法: {args.label_method})...")

    if args.label_method == "llm":
        api_key = os.environ.get("OPENAI_API_KEY")
        if not api_key:
            print("  [WARN] OPENAI_API_KEY未设置，回退到启发式标注")
            args.label_method = "heuristic"

    cache = label_entries(cache, method=args.label_method)
    save_cache(cache)

    label_stats = get_label_stats(cache)
    print(f"  标注统计: 总计{label_stats['total']}条, 安全{label_stats['safe']}条, "
          f"不安全{label_stats['unsafe']}条, 安全率={label_stats['safeRate']:.2%}")

    # ============================================================
    # [4/6] 双阈值网格扫描
    # ============================================================
    print(f"\n[4/6] 双阈值网格扫描 (目标: {args.objective})...")

    labeled_entries = get_labeled_entries(cache)
    print(f"  已标注条目: {len(labeled_entries)}条")

    if len(labeled_entries) == 0:
        print("  [ERROR] 没有已标注的条目，请先运行标注")
        sys.exit(1)

    tuning_result = run_tuning(labeled_entries, objective=args.objective)

    if tuning_result is None:
        print("  [ERROR] 调优失败，请检查数据")
        sys.exit(1)

    # ============================================================
    # [5/6] 报告生成
    # ============================================================
    print(f"\n[5/6] 报告生成...")

    generate_console_report(tuning_result)

    # 推荐
    current_metrics = tuning_result.get("currentMetrics", {})
    print(generate_recommendation(
        tuning_result.get("optimal"),
        current_metrics,
        tuning_result.get("allMetrics", [])
    ))

    # 文件报告
    generate_json_report(tuning_result, config.OUTPUT_JSON)
    generate_markdown_report(tuning_result, config.OUTPUT_MD)

    # 图表
    try:
        plot_tuning_results(tuning_result, config.OUTPUT_PNG)
    except Exception as e:
        print(f"  [WARN] 热力图生成失败: {e}")

    # 置信度分布图
    try:
        plot_confidence_distribution(labeled_entries, config.OUTPUT_DIR)
    except Exception as e:
        print(f"  [WARN] 分布图生成失败: {e}")

    print()
    print(f"所有报告已保存至: {config.OUTPUT_DIR}")
    print("完成!")


if __name__ == "__main__":
    main()
