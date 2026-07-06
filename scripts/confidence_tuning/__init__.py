"""
LLM置信度阈值离线调优工具包

使用标注数据集 + 缓存策略，在离线状态下完成LLM置信度阈值调优，
避免反复调用LLM API产生成本。

工作流:
  1. 从 test_scenarios.json 提取候选医学词条
  2. 利用 LOCAL/SNOMED_CT 映射构建 Ground Truth
  3. 仅调用一次LLM，缓存所有置信度分数
  4. 离线扫描不同阈值，纯计算得出最优值

用法:
  python -m scripts.confidence_tuning.main --sample-size 150
  python -m scripts.confidence_tuning.main --use-cache --sample-size 500
"""

__version__ = "1.0.0"
