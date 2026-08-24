<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 字段标签 */
    label: string
    /** 字段值（空值时渲染 —，也可用默认插槽自定义） */
    value?: string | number
    /** 值字号：sm=0.875rem（默认）；lg=1rem */
    valueSize?: 'sm' | 'lg'
  }>(),
  { value: '', valueSize: 'sm' }
)
</script>

<template>
  <div class="info-item">
    <span class="info-label">{{ label }}</span>
    <span class="info-value" :class="`value-${valueSize}`">
      <slot>{{ value || '—' }}</slot>
    </span>
  </div>
</template>

<style scoped>
/* 信息字段项：label 在上、value 在下（引用 --theme-* 双主题自动生效） */
.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.info-label {
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
}

.info-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  color: var(--theme-on-surface);
}

.value-sm {
  font-size: 0.875rem;
}

.value-lg {
  font-size: 1rem;
}
</style>
