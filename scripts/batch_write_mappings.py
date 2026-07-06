#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""将用户确认的LLM推断映射批量写入数据库"""
import json
import requests

BATCH_URL = "http://localhost:8080/api/admin/mappings/batch-upsert"

# 读取LLM推断结果
with open("D:/Java/代码/IMKQAS/scripts/confidence_tuning_cache/_llm_inferred_terms.json", "r", encoding="utf-8") as f:
    data = json.load(f)

inferred = {i["term"]: i for i in data["llm_inferred"]}

# ============================================================
# 用户选择的映射汇总:
#   全部"无需映射"组 (term==standardTerm, 保留原词)
#   全部"好映射"组 13个
#   用户从"需人工判断"组选择的4个
# ============================================================

# 1. 无需映射的（term==standardTerm，写入自身映射以加速缓存命中）
identity_terms = [
    "肺炎", "手足口病", "热性惊厥", "腹泻", "毛细支气管炎",
    "贫血", "缺铁性贫血", "咳嗽", "喘息", "发热", "早产",
    "皮疹", "鼻塞", "益生菌", "口腔", "消毒", "口服补液盐",
    "特应性皮炎", "脱水", "单纯性"
]

# 2. LLM推断的好映射
good_mappings = [
    ("哮喘", "支气管哮喘"),
    ("湿疹", "特应性皮炎"),
    ("RSV", "呼吸道合胞病毒"),
    ("发烧", "发热"),
    ("抽搐", "癫痫发作"),
    ("补铁", "铁剂补充"),
    ("补锌", "锌补充治疗"),
    ("铁剂", "铁补充剂"),
    ("头晕", "眩晕"),
    ("阶梯治疗", "阶梯式治疗"),
    ("危险征象", "警示征象"),
    ("居家护理", "家庭护理"),
    ("吸入", "吸入给药"),
]

# 3. 用户从"需人工判断"组挑选的
user_selected = [
    ("保湿", "皮肤屏障功能维护"),
    ("润肤", "皮肤保湿"),
    ("急救", "紧急医疗救治"),
    ("脸色", "面部色泽"),
]

# 合并所有映射
mappings = []

for term in identity_terms:
    mappings.append({"colloquialTerm": term, "standardTerm": term, "confidence": 1.0})

for term, std in good_mappings:
    conf = inferred.get(term, {}).get("confidence", 0.95)
    mappings.append({"colloquialTerm": term, "standardTerm": std, "confidence": conf})

for term, std in user_selected:
    conf = inferred.get(term, {}).get("confidence", 0.85)
    mappings.append({"colloquialTerm": term, "standardTerm": std, "confidence": conf})

print(f"准备写入 {len(mappings)} 条映射:")
print(f"  自身映射(identity): {len(identity_terms)} 条")
print(f"  好映射(good): {len(good_mappings)} 条")
print(f"  用户选择(user): {len(user_selected)} 条")
print()

# 批量写入（每批50条）
batch_size = 50
inserted = 0
for i in range(0, len(mappings), batch_size):
    batch = mappings[i:i + batch_size]
    r = requests.post(BATCH_URL, json=batch, timeout=30)
    if r.status_code == 200:
        result = r.json()
        if result.get("success"):
            n = result.get("data", {}).get("inserted", 0)
            inserted += n
            print(f"  batch {i // batch_size + 1}/{(len(mappings) - 1) // batch_size + 1}: "
                  f"提交{len(batch)}条, 写入{n}条")
        else:
            print(f"  batch {i // batch_size + 1} 失败: {result}")
    else:
        print(f"  batch {i // batch_size + 1} HTTP {r.status_code}: {r.text[:200]}")

print(f"\n写入完成! 总计提交 {len(mappings)} 条, 实际写入 {inserted} 条")
