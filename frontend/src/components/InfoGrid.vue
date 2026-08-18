<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 每列最小宽度（px），默认 220 */
    minColWidth?: number
  }>(),
  { minColWidth: 220 }
)
</script>

<template>
  <div
    class="info-grid"
    :style="{ gridTemplateColumns: `repeat(auto-fill, minmax(${minColWidth}px, 1fr))` }"
  >
    <slot />
  </div>
</template>

<style scoped>
/* 信息网格：label 在上、value 在下 的竖排字段组（引用 --theme-* 双主题自动生效） */
.info-grid {
  display: grid;
  gap: 0.875rem 1.5rem;
}
</style>
