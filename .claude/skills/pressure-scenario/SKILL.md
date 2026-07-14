---
name: pressure-scenario
description: 使用Playwright MCP分析原型图HTML与Vue页面的样式差异，自动修改Vue组件使其与原型图视觉完全一致。当用户提到"pressure-scenario"、"原型图对比"、"样式对齐原型"、"Playwright样式分析"或提供原型图HTML和Vue文件要求对齐样式时触发。
---

# 压力场景：Playwright样式分析 + Vue文件修改

## 概述

本技能用于将原型图 HTML 的视觉样式精确迁移到 Vue 组件。通过 Playwright MCP 打开原型图和 Vue 页面，提取计算样式，逐元素对比差异，然后修改 Vue 组件使其与原型图完全一致。

## 输入

- **原型图文件**：`frontend/image/html/<name>.html`（本地 HTML 文件）
- **Vue 页面文件**：`frontend/src/views/<Name>.vue`
- **Vue 项目根目录**：`frontend/`

## 执行流程

### 阶段 1：原型图样式提取

1. 使用 `mcp__filesystem__read_text_file` 或 `Read` 工具完整读取原型图 HTML 文件
2. 解析 HTML，提取以下信息：
   - 布局结构（header / main / section / footer 层级关系）
   - 每个元素的 Tailwind 类名
   - 内联样式和 `<style>` 块
   - 颜色、字体、间距、圆角、阴影等设计 token
   - 组件层级（导航栏、统计卡片、筛选栏、表格、分页等）

### 阶段 2：Vue 页面分析

1. 使用 `Read` 工具读取 Vue 组件文件
2. 提取：
   - `<template>` 结构和组件层级
   - 所有使用的 CSS 类名（Tailwind 和自定义类）
   - `<script setup>` 中的业务逻辑（不可修改部分）
   - 动态类绑定和条件渲染

### 阶段 3：差异对比

逐区域对比原型图和 Vue 页面：

| 对比维度 | 说明 |
|----------|------|
| 布局结构 | 元素是否缺失、位置是否偏移 |
| 颜色 | 背景色、文字色、边框色 |
| 字体 | 字号、粗细、行高、字体族 |
| 间距 | padding、margin、gap |
| 圆角 | border-radius |
| 阴影 | box-shadow |
| 尺寸 | 宽高、最大宽度 |

### 阶段 4：修改 Vue 组件

按以下优先级修改：

1. **补充缺失元素**：原型图有但 Vue 没有的 DOM 结构（如统计卡片、面包屑、背景装饰）
2. **调整样式类名**：将不匹配的 Tailwind 类替换为正确值
3. **修正布局细节**：grid 列数、flex 对齐方式、间距等
4. **统一设计 token**：颜色、字体、圆角使用项目约定的 token
5. **保留业务逻辑**：`v-model`、`@click`、`v-for`、`v-if` 等保持不变

### 阶段 5：验证

1. 运行 `npx vue-tsc --noEmit` 进行类型检查
2. 确认 `<script setup>` 导入和引用完整
3. 对比最终 Vue 模板与原型图 HTML 结构一致性

## 约束

- **绝对不能**修改 `<script setup>` 中的业务逻辑（API 调用、状态管理、事件处理）
- **必须**保留所有 `v-model`、`@click`、`@change`、`v-for`、`v-if` 等 Vue 指令
- **必须**保持与后端 API 接口的兼容性
- 可以添加新的响应式变量和计算属性以支持新增 UI 元素
- 可以使用原型图中的 Tailwind 类名，但颜色优先使用项目约定 token

## 常见差异模式

| 原型图特征 | Vue 常见缺失 | 修复策略 |
|-----------|-------------|---------|
| 统计概览卡片 | 无数据展示区 | 添加 bento grid + `loadStats()` |
| ATC 编码 | 仅显示药物名 | 添加 `atcCode` 字段和副标题行 |
| 页码分页 | 仅上/下页按钮 | 替换为页码数组 + 省略号逻辑 |
| 背景装饰 | 无 | 添加 fixed 定位渐变模糊 div |
| 面包屑 | 无 | 添加导航路径文字 |
| 人群标签着色 | 纯文本 | 添加彩色背景 pill 样式 |
| 圆形操作按钮 | 文字按钮 | 替换为 icon-only 圆形按钮 |

## 输出

修改完成后向用户报告：
- 修改了哪些文件
- 新增了哪些 UI 模块
- 样式调整项数量
- 类型检查结果
