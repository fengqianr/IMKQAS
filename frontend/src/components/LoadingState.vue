<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 加载文案 */
    text?: string
    /** 整页/大面板容器（min-height 24rem + 浅底边框） */
    full?: boolean
    /** 图标样式：icon=旋转刷新图标；ring=环形 spinner */
    type?: 'icon' | 'ring'
  }>(),
  { text: '加载中...', full: false, type: 'icon' }
)

const cls = computed(() => ({ 'is-full': props.full }))
</script>

<template>
  <div class="loading-state" :class="cls">
    <span v-if="type === 'ring'" class="loading-ring" />
    <span v-else class="material-symbols-outlined loading-icon">refresh</span>
    <p class="loading-text">{{ text }}</p>
  </div>
</template>

<style scoped>
/* 加载态：图标/环形 + 文案；full 时铺满面板容器（引用 --theme-* 双主题自动生效） */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 3rem 2rem;
  color: var(--theme-on-surface-variant);
}

.is-full {
  min-height: 24rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
}

.loading-icon {
  font-size: 1.75rem;
  animation: loading-spin 1s linear infinite;
}

.loading-ring {
  width: 2rem;
  height: 2rem;
  border: 2px solid var(--theme-surface-container);
  border-top-color: var(--theme-primary);
  border-radius: 9999px;
  animation: loading-spin 0.6s linear infinite;
}

.loading-text {
  font-size: 0.875rem;
}

@keyframes loading-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
