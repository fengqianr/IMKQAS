<script setup lang="ts">
import { computed } from 'vue'

/** 状态徽章语义色调 */
export type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'critical'

const props = withDefaults(
  defineProps<{
    /** 语义色调（颜色引用 --theme-*，双主题自动生效） */
    tone?: StatusTone
    /** 是否显示圆点指示 */
    dot?: boolean
    /** Material Symbols 图标名（如 check / block） */
    icon?: string
    /** 是否带边框 */
    border?: boolean
    /** 圆点脉冲动画（处理中状态用） */
    pulse?: boolean
    /** sm 更小号（0.6875rem、更紧凑内边距） */
    size?: 'sm' | 'md'
    /** 文案（也可用默认插槽自定义内容） */
    text?: string
  }>(),
  { tone: 'neutral', dot: false, icon: '', border: false, pulse: false, size: 'md', text: '' }
)

const cls = computed(() => [
  `tone-${props.tone}`,
  `size-${props.size}`,
  { 'is-border': props.border, 'is-pulse': props.pulse }
])
</script>

<template>
  <span class="status-badge" :class="cls">
    <span v-if="dot" class="status-dot" />
    <span v-if="icon" class="material-symbols-outlined status-icon">{{ icon }}</span>
    <slot>{{ text }}</slot>
  </span>
</template>

<style scoped>
/* 状态徽章：胶囊 + 浅底 + 语义色 */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.625rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 500;
  line-height: 1.2;
  white-space: nowrap;
  vertical-align: middle;
}

.size-sm {
  padding: 0.125rem 0.625rem;
  font-size: 0.6875rem;
}

.status-dot {
  flex: none;
  width: 0.375rem;
  height: 0.375rem;
  border-radius: 9999px;
}

.status-icon {
  flex: none;
  font-size: 0.9375rem;
  line-height: 1;
}

.is-border {
  border: 1px solid var(--theme-outline-variant);
}

/* ---- 语义色 ---- */
.tone-neutral {
  background: var(--theme-surface-container);
  color: var(--theme-on-surface-variant);
}
.tone-neutral .status-dot { background: var(--theme-outline); }

.tone-info {
  background: var(--theme-primary-soft);
  color: var(--theme-primary);
}
.tone-info .status-dot { background: var(--theme-primary); }

.tone-success {
  background: rgba(46, 125, 50, 0.12);
  color: var(--theme-success);
}
.tone-success .status-dot { background: var(--theme-success); }

.tone-warning {
  background: rgba(237, 108, 2, 0.12);
  color: var(--theme-processing);
}
.tone-warning .status-dot { background: var(--theme-processing); }

.tone-danger {
  background: rgba(186, 26, 26, 0.12);
  color: var(--theme-error);
}
.tone-danger .status-dot { background: var(--theme-error); }

/* 实心（危急） */
.tone-critical {
  background: var(--theme-error);
  color: var(--theme-on-error);
}
.tone-critical .status-dot { background: var(--theme-on-error); }

/* 处理中圆点脉冲 */
.is-pulse .status-dot {
  animation: status-pulse 1.5s ease-in-out infinite;
}
@keyframes status-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
</style>
