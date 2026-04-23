<template>
  <div id="app">
    <template v-if="showLayout">
      <MainLayout>
        <RouterView />
      </MainLayout>
    </template>
    <template v-else>
      <RouterView />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MainLayout from '@/components/layout/MainLayout.vue'

const route = useRoute()

// 需要显示布局的路由（认证页面且不是登录页）
const showLayout = computed(() => {
  return route.meta.requiresAuth && route.path !== '/login' && !route.meta.noLayout
})
</script>

<style scoped>
#app {
  min-height: 100vh;
}
</style>