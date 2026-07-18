#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LLM置信度阈值离线调优 — 主入口

工作流:
  [0/5] 连接检查
  [1/5] 加载测试场景 + 提取词条
  [2/5] 构建Ground Truth (调用 /lookup 端点)
  [3/5] 采集LLM置信度 (调用 /batch-llm-infer，支持缓存)
  [4/5] 分层抽样 + 阈值扫描
  [5/5] 报告生成
"""

import argparse
import sys
import os
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from confidence_tuning import config
from confidence_tuning.client import SynonymTestClient
from confidence_tuning.term_extractor import load_scenarios, extract_all_terms, compute_term_frequencies
from confidence_tuning.ground_truth import (
    build_ground_truth, save_ground_truth, load_ground_truth, check_llm_correctness
)
from confidence_tuning.collector import (
    collect_confidence, load_cache, save_cache,
    get_entries_with_confidence, get_authoritative_entries, get_cache_stats
)
from confidence_tuning.sampler import stratified_sample
from confidence_tuning.tuner import run_tuning, generate_recommendation
from confidence_tuning.report import (
    generate_console_report, generate_markdown_report, generate_json_report
)
from confidence_tuning.visualizer import plot_tuning_results


def main():
    parser = argparse.ArgumentParser(
        description="LLM置信度阈值离线调优工具",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--base-url", default=config.BASE_URL,
                        help=f"后端服务地址 (默认: {config.BASE_URL})")
    parser.add_argument("--scenarios", default=config.TEST_SCENARIOS_PATH,
                        help="test_scenarios.json路径")
    parser.add_argument("--use-cache", action="store_true",
                        help="跳过LLM调用，使用已有缓存数据")
    parser.add_argument("--no-cache", action="store_true",
                        help="强制重新采集，覆盖已有缓存")
    parser.add_argument("--sample-size", type=int, default=config.STRATIFIED_SAMPLE_SIZE,
                        help=f"采样数量: 50(快速) / 150(标准) / 500(全量) (默认: {config.STRATIFIED_SAMPLE_SIZE})")
    parser.add_argument("--threshold-range", nargs=3, type=float,
                        metavar=("MIN", "MAX", "STEP"),
                        help=f"自定义阈值扫描范围 (默认: {config.THRESHOLD_MIN} {config.THRESHOLD_MAX} {config.THRESHOLD_STEP})")
    parser.add_argument("--objective", default="f1",
                        choices=["f1", "balanced", "precision_min_recall"],
                        help="优化目标 (默认: f1)")
    parser.add_argument("--min-precision", type=float, default=0.0,
                        help="最低精度约束")
    parser.add_argument("--output-dir", default=config.OUTPUT_DIR,
                        help=f"输出目录 (默认: {config.OUTPUT_DIR})")

    args = parser.parse_args()

    # 覆盖配置
    if args.base_url:
        config.BASE_URL = args.base_url
        config.SYNONYM_TEST_ENDPOINT = f"{args.base_url}/api/rag/synonym-test"
    if args.threshold_range:
        config.THRESHOLD_MIN, config.THRESHOLD_MAX, config.THRESHOLD_STEP = args.threshold_range

    config.OUTPUT_DIR = args.output_dir
    config.OUTPUT_JSON = os.path.join(args.output_dir, "tuning_results.json")
    config.OUTPUT_MD = os.path.join(args.output_dir, "tuning_report.md")
    config.OUTPUT_PNG = os.path.join(args.output_dir, "tuning_report.png")

    print("=" * 60)
    print("  LLM置信度阈值离线调优工具")
    print(f"  服务器: {config.BASE_URL}")
    print(f"  采样数: {args.sample_size}")
    print("=" * 60)

    # ============================================================
    # [0/5] 连接检查
    # ============================================================
    print("\n[0/5] 连接检查...")
    client = SynonymTestClient(config.BASE_URL)
    if not client.check_health():
        print("  [ERROR] 无法连接到服务器，请检查服务是否启动")
        sys.exit(1)
    rtt = client.measure_rtt()
    print(f"  服务器连通正常, 平均RTT: {rtt:.1f}ms")

    # ============================================================
    # [1/5] 加载测试场景 + 提取词条
    # ============================================================
    print("\n[1/5] 加载测试场景并提取候选词条...")
    try:
        scenarios = load_scenarios(args.scenarios)
    except FileNotFoundError:
        print(f"  [ERROR] 找不到测试场景文件: {args.scenarios}")
        sys.exit(1)
    print(f"  已加载 {len(scenarios)} 条测试场景")

    entries = extract_all_terms(scenarios)
    freq = compute_term_frequencies(entries)
    unique_terms = len(freq)
    print(f"  提取到 {len(entries)} 个候选词条 ({unique_terms} 个唯一术语)")

    if len(entries) == 0:
        print("  [ERROR] 未提取到任何候选词条")
        sys.exit(1)

    # ============================================================
    # [2/5] 构建Ground Truth
    # ============================================================
    print("\n[2/5] 构建Ground Truth (调用 /lookup 端点)...")
    gt_data = load_ground_truth()
    if gt_data is None:
        gt_data = build_ground_truth(entries, client)
        save_ground_truth(gt_data)
    else:
        auth_count = sum(1 for v in gt_data.values() if v.get("hasAuthoritativeMapping"))
        print(f"  从缓存加载Ground Truth: {len(gt_data)}条, 权威标注{auth_count}条")

    # 将GT信息合并到entries中用于后续抽样
    for entry in entries:
        key = f"{entry['scenarioId']}_{entry['term']}"
        gt_entry = gt_data.get(key, {})
        entry["hasAuthoritativeMapping"] = gt_entry.get("hasAuthoritativeMapping", False)
        entry["groundTruthTerm"] = gt_entry.get("groundTruthTerm")
        entry["groundTruthSource"] = gt_entry.get("groundTruthSource", "NONE")

    # ============================================================
    # [3/5] 采集LLM置信度
    # ============================================================
    print("\n[3/5] 采集LLM置信度...")
    cache = load_cache()

    if args.use_cache and cache:
        print(f"  --use-cache: 使用已有缓存 ({cache.get('total_entries', 0)}条)")
        stats = get_cache_stats(cache)
        print(f"  缓存统计: 总计{stats['total']}条, 权威{stats['authoritative']}条, "
              f"正确{stats['correct']}条, 错误{stats['incorrect']}条")
    elif args.no_cache or cache is None:
        print(f"  开始采集LLM置信度 (共{len(entries)}条)...")
        start_time = time.time()
        cache = collect_confidence(entries, gt_data, client, use_cache=False)
        elapsed = time.time() - start_time
        print(f"  采集耗时: {elapsed:.1f}秒")
        stats = get_cache_stats(cache)
        print(f"  缓存统计: 总计{stats['total']}条, 权威{stats['authoritative']}条, "
              f"正确{stats['correct']}条, 错误{stats['incorrect']}条")
    else:
        print(f"  使用已有缓存 ({cache.get('total_entries', 0)}条)，增量采集新词条...")
        cache = collect_confidence(entries, gt_data, client, use_cache=True)
        stats = get_cache_stats(cache)
        print(f"  缓存统计: 总计{stats['total']}条, 权威{stats['authoritative']}条, "
              f"正确{stats['correct']}条, 错误{stats['incorrect']}条")

    # ============================================================
    # [4/5] 分层抽样 + 阈值扫描
    # ============================================================
    print(f"\n[4/5] 分层抽样 ({args.sample_size}条) + 阈值扫描...")

    # 抽样
    if args.sample_size >= len(entries):
        sampled_entries = entries
        sample_stats = {"sampleSize": len(entries), "message": "使用全量数据"}
    else:
        sampled_entries, sample_stats = stratified_sample(entries, args.sample_size)

    # 从缓存中提取采样条目的数据
    cached_entries = get_entries_with_confidence(cache, sampled_entries)
    print(f"  采样条目中已缓存的: {len(cached_entries)}条")

    if len(cached_entries) == 0:
        print("  [ERROR] 采样条目无LLM置信度数据，请先运行采集（不用--use-cache）")
        sys.exit(1)

    # 阈值扫描
    constraint = None
    if args.min_precision > 0:
        constraint = {"minPrecision": args.min_precision}

    min_t = config.THRESHOLD_MIN
    max_t = config.THRESHOLD_MAX
    step = config.THRESHOLD_STEP

    tuning_result = run_tuning(cached_entries, args.objective, constraint)

    if tuning_result is None:
        sys.exit(1)

    # 补充采样统计到结果
    tuning_result["sampleStats"] = sample_stats
    tuning_result["config"] = {
        "baseUrl": config.BASE_URL,
        "scenariosPath": args.scenarios,
        "sampleSize": args.sample_size,
        "thresholdRange": [min_t, max_t, step],
        "objective": args.objective,
        "useCache": args.use_cache or not args.no_cache
    }

    # ============================================================
    # [5/5] 报告生成
    # ============================================================
    print(f"\n[5/5] 报告生成...")

    # 控制台报告
    generate_console_report(tuning_result)

    # 推荐
    print(generate_recommendation(tuning_result))

    # 生成文件报告
    generate_json_report(tuning_result, config.OUTPUT_JSON)
    generate_markdown_report(tuning_result, config.OUTPUT_MD)

    # 生成图表
    try:
        plot_tuning_results(tuning_result, config.OUTPUT_PNG)
    except Exception as e:
        print(f"  [WARN] 图表生成失败: {e}")

    print()
    print(f"所有报告已保存至: {config.OUTPUT_DIR}")
    print("完成!")


if __name__ == "__main__":
    main()
