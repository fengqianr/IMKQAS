#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ground Truth构建器 — 利用LOCAL/SNOMED_CT映射作为权威标注
"""

import json
import hashlib
import os
from . import config


def build_ground_truth(entries, client):
    """
    对每个词条调用 /lookup 端点，构建Ground Truth。

    返回: {
        "Q001_哮喘": {
            "term": "哮喘",
            "contextQuery": "...",
            "groundTruthTerm": "哮喘",    # 标准术语，来自LOCAL或SNOMED_CT
            "groundTruthSource": "SNOMED_CT",  # LOCAL / SNOMED_CT / NONE
            "hasAuthoritativeMapping": True,    # 是否有权威映射（可参与F1计算）
            ...
        }, ...
    }
    """
    gt = {}
    total = len(entries)
    authoritative_count = 0

    # 去重：相同term只查一次
    term_cache = {}

    for i, entry in enumerate(entries):
        term = entry["term"]
        key = f"{entry['scenarioId']}_{term}"

        if term in term_cache:
            # 复用之前查询结果
            cached = term_cache[term]
            gt[key] = {**entry, **cached}
            if cached["hasAuthoritativeMapping"]:
                authoritative_count += 1
            continue

        # 调 /lookup 端点
        result = client.lookup_term(term)

        if result and result.get("found"):
            gt_entry = {
                "term": term,
                "contextQuery": entry["contextQuery"],
                "scenarioId": entry["scenarioId"],
                "disease": entry.get("disease", ""),
                "groundTruthTerm": result.get("standardTerm"),
                "groundTruthSource": result.get("source", "NONE"),
                "groundTruthConfidence": result.get("confidence", 1.0),
                "hasAuthoritativeMapping": True
            }
            authoritative_count += 1
        else:
            gt_entry = {
                "term": term,
                "contextQuery": entry["contextQuery"],
                "scenarioId": entry["scenarioId"],
                "disease": entry.get("disease", ""),
                "groundTruthTerm": None,
                "groundTruthSource": "NONE",
                "groundTruthConfidence": 0.0,
                "hasAuthoritativeMapping": False
            }

        gt[key] = gt_entry
        term_cache[term] = {
            "groundTruthTerm": gt_entry["groundTruthTerm"],
            "groundTruthSource": gt_entry["groundTruthSource"],
            "hasAuthoritativeMapping": gt_entry["hasAuthoritativeMapping"]
        }

        if (i + 1) % 50 == 0:
            print(f"  GT构建进度: {i + 1}/{total}")

    coverage = authoritative_count / total * 100 if total > 0 else 0
    print(f"  GT构建完成: {total}条, 权威标注{authoritative_count}条 ({coverage:.1f}%)")

    return gt


def save_ground_truth(gt, path=None):
    """保存Ground Truth到JSON文件"""
    if path is None:
        path = config.GROUND_TRUTH_FILE
    with open(path, "w", encoding="utf-8") as f:
        json.dump(gt, f, ensure_ascii=False, indent=2)
    print(f"  Ground Truth已保存: {path}")


def load_ground_truth(path=None):
    """从JSON文件加载Ground Truth"""
    if path is None:
        path = config.GROUND_TRUTH_FILE
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def check_llm_correctness(llm_standard_term, ground_truth_term):
    """
    判定LLM结果是否正确。
    规则：精确匹配 或 双向子串匹配
    """
    if not llm_standard_term or not ground_truth_term:
        return False

    lt = llm_standard_term.lower().strip()
    gt = ground_truth_term.lower().strip()

    if lt == gt:
        return True

    # 双向子串匹配（如 "对乙酰氨基酚片" vs "对乙酰氨基酚"）
    if gt in lt or lt in gt:
        return True

    return False
