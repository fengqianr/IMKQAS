#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
词条提取器 — 从 test_scenarios.json 的 userQuery 中提取候选医学词条
"""

import json
import re


def load_scenarios(path):
    """加载测试场景文件"""
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data.get("scenarios", [])


def extract_terms_from_query(query, expected_keywords):
    """
    从查询中提取候选医学词条。
    策略：检查 expectedKeywords 是否作为子串出现在 userQuery 中。
    同时尝试用简单的关键词边界匹配提取。
    """
    terms = []
    seen = set()

    for kw in expected_keywords:
        if kw in query and kw not in seen:
            terms.append(kw)
            seen.add(kw)

    # 尝试提取英文缩写词（如 ORS、SpO2）
    english_pattern = re.compile(r'[A-Za-z][A-Za-z0-9]{1,}')
    for match in english_pattern.finditer(query):
        word = match.group()
        if word not in seen and len(word) >= 2:
            terms.append(word)
            seen.add(word)

    return terms


def extract_all_terms(scenarios):
    """
    从所有场景中提取候选词条。
    返回: [
        {
            "scenarioId": "Q001",
            "term": "哮喘",
            "contextQuery": "我家宝宝儿童哮喘怎么治，着急！",
            "disease": "哮喘",
            "expectedKeywords": [...]
        }, ...
    ]
    """
    entries = []
    seen_pairs = set()

    for scenario in scenarios:
        sid = scenario["id"]
        query = scenario["userQuery"]
        keywords = scenario.get("expectedKeywords", [])
        disease = scenario.get("disease", "")

        extracted = extract_terms_from_query(query, keywords)

        for term in extracted:
            pair_key = (term, query)
            if pair_key in seen_pairs:
                continue
            seen_pairs.add(pair_key)

            entries.append({
                "scenarioId": sid,
                "term": term,
                "contextQuery": query,
                "disease": disease,
                "expectedKeywords": keywords
            })

    return entries


def compute_term_frequencies(entries):
    """
    计算每个词条在所有场景中的出现频率。
    返回: {term: frequency}
    """
    freq = {}
    for entry in entries:
        term = entry["term"]
        freq[term] = freq.get(term, 0) + 1
    return freq


def classify_frequency(term, freq, high_th=5, medium_th=2):
    """
    按频率分类：high / medium / low
    """
    f = freq.get(term, 0)
    if f >= high_th:
        return "high"
    elif f >= medium_th:
        return "medium"
    return "low"
