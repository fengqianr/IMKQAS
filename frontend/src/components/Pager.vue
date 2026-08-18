<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 当前页（1 起始） */
  current: number
  /** 总页数 */
  totalPages: number
  /** 左侧信息文案（如：显示 1-10 条，共 100 条） */
  info?: string
}>()

const emit = defineEmits<{
  (e: 'update:current', value: number): void
  (e: 'change', value: number): void
}>()

const go = (p: number) => {
  if (p < 1 || p > props.totalPages || p === props.current) return
  emit('update:current', p)
  emit('change', p)
}

/**
 * 智能页码：总页数 ≤ 7 时全显示；否则首尾 + 当前页±1，中间缺口以省略号占位
 * -1 为省略号占位符
 */
const pageItems = computed<number[]>(() => {
  const { current, totalPages } = props
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1)
  const pages = new Set([1, totalPages, current - 1, current, current + 1])
  const sorted = Array.from(pages)
    .filter((p) => p >= 1 && p <= totalPages)
    .sort((a, b) => a - b)
  const items: number[] = []
  for (let i = 0; i < sorted.length; i++) {
    if (i > 0 && sorted[i] - sorted[i - 1] > 1) items.push(-1)
    items.push(sorted[i])
  }
  return items
})
</script>

<template>
  <div class="pagination-bar">
    <span v-if="info" class="page-info">{{ info }}</span>
    <div class="page-actions">
      <button class="page-btn" type="button" :disabled="current <= 1" :aria-label="'上一页'" @click="go(current - 1)">
        <span class="material-symbols-outlined">chevron_left</span>
      </button>
      <template v-for="p in pageItems" :key="p">
        <span v-if="p === -1" class="page-ellipsis">…</span>
        <button v-else type="button" class="page-num" :class="p === current ? 'page-num-active' : ''" @click="go(p)">
          {{ p }}
        </button>
      </template>
      <button
        class="page-btn"
        type="button"
        :disabled="current >= totalPages"
        :aria-label="'下一页'"
        @click="go(current + 1)"
      >
        <span class="material-symbols-outlined">chevron_right</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 分页条：信息文案 + 页码按钮（引用 --theme-* 双主题自动生效） */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
  border-top: 1px solid var(--theme-outline-variant);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.25rem;
  color: var(--theme-on-surface-variant);
  border: none;
  background: none;
  border-radius: 0.25rem;
  cursor: pointer;
  transition: background-color 150ms;
}
.page-btn:hover:not(:disabled) {
  background-color: var(--theme-surface-container);
}
.page-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.page-num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 0.25rem;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
  transition: background-color 150ms;
}
.page-num:hover {
  background-color: var(--theme-surface-container);
}
.page-num-active,
.page-num-active:hover {
  background-color: var(--theme-primary);
  color: var(--theme-on-primary);
}

.page-ellipsis {
  padding: 0 0.25rem;
  color: var(--theme-on-surface-variant);
}
</style>
