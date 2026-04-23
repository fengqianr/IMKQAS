# Clinical Precision RAG — 设计语言

## 品牌概述
医疗级临床决策支持系统，采用冷静、专业、可信赖的视觉风格。基于Material Design 3扩展色彩系统，强调信息层级与可读性。

## 颜色系统

### 主色 (Primary)
- `primary`: #00478d — 主按钮、重要链接、品牌强调
- `on-primary`: #ffffff — 主色上的文字/图标
- `primary-container`: #005eb8 — 容器背景（如选中状态）
- `on-primary-container`: #c8daff — 容器上的文字
- `primary-fixed`: #d6e3ff — 固定主色变体
- `on-primary-fixed`: #001b3d
- `primary-fixed-dim`: #a9c7ff
- `inverse-primary`: #a9c7ff — 深色模式下的主色

### 辅色 (Secondary)
- `secondary`: #4a5f83
- `on-secondary`: #ffffff
- `secondary-container`: #c0d5ff
- `on-secondary-container`: #475c80
- `secondary-fixed`: #d6e3ff
- `on-secondary-fixed`: #021b3c
- `secondary-fixed-dim`: #b2c7f0
- `on-secondary-fixed-variant`: #32476a

### 三级色 (Tertiary)
- `tertiary`: #793100
- `on-tertiary`: #ffffff
- `tertiary-container`: #9f4300
- `on-tertiary-container`: #ffcfb9
- `tertiary-fixed`: #ffdbcb
- `on-tertiary-fixed`: #341100
- `tertiary-fixed-dim`: #ffb691
- `on-tertiary-fixed-variant`: #793100

### 错误色 (Error)
- `error`: #ba1a1a
- `on-error`: #ffffff
- `error-container`: #ffdad6
- `on-error-container`: #93000a

### 表面与背景 (Surface/Background)
- `background`: #f8f9fa — 整体页面背景
- `on-background`: #191c1d
- `surface`: #f8f9fa
- `on-surface`: #191c1d
- `surface-variant`: #e1e3e4
- `on-surface-variant`: #424752
- `surface-tint`: #005db6
- `inverse-surface`: #2e3132
- `inverse-on-surface`: #f0f1f2
- `surface-dim`: #d9dadb
- `surface-bright`: #f8f9fa
- `surface-container-lowest`: #ffffff
- `surface-container-low`: #f3f4f5
- `surface-container`: #edeeef
- `surface-container-high`: #e7e8e9
- `surface-container-highest`: #e1e3e4

### 轮廓与边框 (Outline)
- `outline`: #727783
- `outline-variant`: #c2c6d4

## 字体系统

### 字族 (Font Family)
- **标题 (Headline)**: `Manrope`, sans-serif
- **正文 (Body)**: `Inter`, sans-serif
- **标签 (Label)**: `Inter`, sans-serif

### 字重与行高 (建议)
- 标题: 600–800, 行高 1.2–1.3
- 正文: 400–500, 行高 1.5
- 小字/辅助: 400, 行高 1.4

## 圆角 (Border Radius)
- `DEFAULT`: 0.125rem (2px) — 极小元素
- `lg`: 0.25rem (4px) — 按钮、小卡片
- `xl`: 0.5rem (8px) — 普通卡片
- `full`: 0.75rem (12px) — 大型容器、头像

## 间距 (Spacing)
基于 4px 网格系统，常用值：
- 4px, 8px, 12px, 16px, 24px, 32px, 48px, 64px

（实际使用中通过 Tailwind 的 `p-*`、`m-*` 等类实现）

## 阴影 (Shadows)
- 轻量卡片阴影: `0 12px 40px rgba(0,71,141,0.03)` — 柔和、几乎无感
- 导航栏阴影: `0 12px 40px rgba(0,71,141,0.06)`
- 悬浮效果: 无独立阴影，依赖背景色变化（如 `hover:bg-surface-container-low`）

## 图标
- 使用 Material Symbols Outlined (`material-symbols-outlined`)
- 字重: 400 (常规), 图标填充可切换

## 动效 (Motion)
- 过渡时长: 150ms–200ms
- 缓动函数: ease (默认), 缩放按钮使用 `active:scale-95`

## 可访问性 (A11y)
- WCAG 对比度: 所有主色与背景组合通过 AA 标准（≥4.5:1）
- 焦点指示器: 使用 `focus:ring-2 focus:ring-primary/20`

## 组件模式
- 卡片: 白色背景 (`surface-container-lowest`)，圆角 `xl`，内边距 `p-6`
- 按钮: 圆角 `full` (12px)，内边距 `px-5 py-2`
- 输入框: 圆角 `full`，背景 `surface-container-low`，无边框但有细微环
- 表格行: hover 时背景变为 `surface-container-low/30`
- 分类导航: 激活项使用 `primary-fixed` 背景 + `rounded-full`

## 设计评分 (模拟)
- 一致性: 高 (Color, spacing, typography 统一)
- 可维护性: 高 (Token 化)
- WCAG 对比度: 100% 通过