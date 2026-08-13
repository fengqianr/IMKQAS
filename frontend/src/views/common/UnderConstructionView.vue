<template>
  <div class="under-construction-container">
    <div class="under-construction-card">
      <span class="material-symbols-outlined uc-icon">construction</span>
      <h1 class="uc-title">该模块建设中</h1>
      <p class="uc-description">此功能正在开发中，敬请期待</p>
      <div class="uc-actions">
        <el-button type="primary" size="large" @click="$router.push('/qa')">返回智能问答</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 占位页：未开发模块的统一提示，无额外逻辑
</script>

<style scoped>
.under-construction-container {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xxl);
}

.under-construction-card {
  text-align: center;
  padding: var(--spacing-xxl) var(--spacing-xl);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-ambient);
  max-width: 500px;
  width: 100%;
  background-color: #ffffff;
}

.uc-icon {
  font-size: 3rem;
  color: var(--color-primary);
}

.uc-title {
  font-family: var(--font-family-headline);
  font-size: 1.5rem;
  color: var(--color-on-surface);
  margin: var(--spacing-md) 0;
}

.uc-description {
  color: var(--color-on-surface-variant);
  margin-bottom: var(--spacing-xl);
}

.uc-actions {
  display: flex;
  gap: var(--spacing-md);
  justify-content: center;
}
</style>
