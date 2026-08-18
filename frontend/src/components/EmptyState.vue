<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 空态标题（必填） */
    title: string
    /** 描述文案（可选） */
    description?: string
    /** Material Symbols 图标名，默认 inbox */
    icon?: string
    /** plain=无容器（管理端风格）；panel=带浅底边框容器、更大图标标题（患者端风格） */
    variant?: 'plain' | 'panel'
    /** 覆盖容器最小高度（默认按 variant） */
    minHeight?: string
  }>(),
  { description: '', icon: 'inbox', variant: 'plain', minHeight: '' }
)
</script>

<template>
  <div
    class="empty-box"
    :class="`is-${variant}`"
    :style="minHeight ? { minHeight } : undefined"
  >
    <div class="empty-icon">
      <span class="material-symbols-outlined">{{ icon }}</span>
    </div>
    <h3 class="empty-title">{{ title }}</h3>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <div class="empty-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
/* 空态占位：图标 + 标题 + 描述 + 操作区（引用 --theme-* 双主题自动生效） */
.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
}

/* panel 变体：患者端浅底/边框容器 */
.is-panel {
  min-height: 24rem;
  padding: 2rem;
  background: var(--theme-surface-container-lowest);
  border: 1px solid var(--theme-outline-variant);
  border-radius: 0.75rem;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3.5rem;
  height: 3.5rem;
  margin-bottom: 1rem;
  border-radius: 9999px;
  background: var(--theme-primary-soft);
  color: var(--theme-on-surface-variant);
}
.empty-icon .material-symbols-outlined {
  font-size: 1.75rem;
}

.is-panel .empty-icon {
  width: 4rem;
  height: 4rem;
  margin-bottom: 0.5rem;
}
.is-panel .empty-icon .material-symbols-outlined {
  font-size: 2.25rem;
}

.empty-title {
  margin-bottom: 0.5rem;
  font-size: 1rem;
  font-weight: 700;
  color: var(--theme-on-surface);
}
.is-panel .empty-title {
  font-size: 1.25rem;
  font-weight: 600;
}

.empty-desc {
  max-width: 28rem;
  margin-bottom: 1.25rem;
  font-size: 0.8125rem;
  color: var(--theme-on-surface-variant);
}
.is-panel .empty-desc {
  font-size: 0.875rem;
}

.empty-actions {
  display: flex;
  gap: 0.5rem;
}
</style>
