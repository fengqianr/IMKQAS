---
name: frontend-design-compliance
description: 在每次修改前端代码时强制遵循DESIGN.md的设计规范，包括颜色、字体、圆角、间距、阴影等系统。该技能会在生成或修改任何前端代码（.vue, .ts, .js, .css文件）时自动触发，确保视觉一致性和专业性。
---

# 前端设计规范合规性检查

## 角色定义

你是一个资深的前端开发工程师，精通 **Vue 3**、**TypeScript**、**Tailwind CSS**。你的核心职责是在每次修改或创建前端代码时，严格遵循 `docs/frontend/DESIGN.md` 中定义的设计规范，确保整个系统保持一致的视觉风格和专业性。

## 设计规范（Clinical Precision 医疗精密色彩方案）

### 1. 品牌核心色

| Token | 色值 | 正确用法 |
|-------|------|----------|
| `brand` | `#005eb8` | 主要按钮、导航栏活动状态、关键数据指标 |
| `on-brand` | `#ffffff` | 品牌色上的文字 |
| `brand-hover` | `#004a9e` | 悬停状态 |

### 2. 语义化状态色

| Token | 色值 | 正确用法 |
|-------|------|----------|
| `danger` | `#d32f2f` | 过敏史、紧急提醒 |
| `success` | `#2e7d32` | 已完成状态、正向趋势 |
| `processing` | `#ed6c02` | 处理中状态、向量计算 |

### 3. 中性色与空间感

| Token | 色值 | 正确用法 |
|-------|------|----------|
| `background` | `#f8fafc` | 整体页面背景 |
| `surface` | `#ffffff` | 卡片、容器 |
| `subtle` | `#f1f5f9` | 侧边栏、次要表面 |
| `text-primary` | `#0f172a` | 标题、核心数据 |
| `text-secondary` | `#64748b` | 标注、次要描述 |

### 4. 轮廓与边框

| Token | 色值 | 正确用法 |
|-------|------|----------|
| `outline` | `#e2e8f0` | 细描边 |
| `outline-muted` | `#cbd5e1` | 次要描边 |

### 5. 悬停与交互

| Token | 样式 | 用途 |
|-------|------|------|
| `hover-overlay` | `rgba(0, 94, 184, 0.06)` | 悬停背景 |
| `focus-ring` | `rgba(0, 94, 184, 0.2)` | 焦点状态 |

## 常见错误自动修复

| 错误模式 | 修复方案 |
|----------|----------|
| `text-gray-900` | → `text-primary` |
| `bg-white` | → `surface` |
| `bg-gray-100` | → `subtle` |
| `border-gray-300` | → `outline` |
| `rounded-lg` | → `rounded-xl` |
| `shadow-lg` | → `shadow-card` |
| `font-sans` | → `font-body` |
| `#000000` | → `text-primary` |
| `text-blue-700` | → `brand` |
| `text-slate-500` | → `text-secondary` |

## 执行步骤

1. **检测修改文件**：确定哪些前端文件被修改
2. **读取设计规范**：加载最新的 DESIGN.md 内容
3. **扫描问题**：检查是否使用了不符合规范的硬编码值
4. **自动修复**：将错误用法替换为设计 Token
5. **报告结果**：告知用户做了哪些修改

## 注意事项

1. 本技能在每次前端代码修改时自动执行，无需用户额外指令
2. 优先使用设计系统 Token，而非硬编码值
3. 保持与现有代码风格一致
4. 如果设计规范中有特殊规定，优先遵循特殊规定