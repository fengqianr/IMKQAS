<template>
  <div class="not-found-container">
    <div class="not-found-content glass">
      <h1 class="display-lg">404</h1>
      <p class="title">页面未找到</p>
      <p class="description">您访问的页面不存在或已被移除</p>
      <div class="actions">
        <el-button type="primary" size="large" @click="$router.push('/')">返回首页</el-button>
        <el-button size="large" @click="$router.go(-1)">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 404页面逻辑
</script>

<style scoped>
.not-found-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-surface), var(--color-surface-container-low));
  padding: var(--spacing-xl);
}

.not-found-content {
  text-align: center;
  padding: var(--spacing-xxl);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-ambient);
  max-width: 500px;
  width: 100%;
}

.title {
  font-size: 1.5rem;
  color: var(--color-on-surface);
  margin: var(--spacing-md) 0;
}

.description {
  color: var(--color-on-surface-variant);
  margin-bottom: var(--spacing-xl);
}

.actions {
  display: flex;
  gap: var(--spacing-md);
  justify-content: center;
}
</style>