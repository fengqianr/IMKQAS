#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IMKQAS 双路召回最优 K 值调优工具包

用法:
    python -m scripts.k_tuning.main
    或
    cd scripts && python k_tuning/main.py
"""

__version__ = "1.0.0"
__all__ = [
    "config", "client", "ground_truth", "tuner",
    "metrics", "stress_test", "visualizer", "report", "main",
]
