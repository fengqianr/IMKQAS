<template>
  <div id="app">
    <template v-if="layoutComponent">
      <component :is="layoutComponent"><RouterView /></component>
    </template>
    <template v-else>
      <RouterView />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { ROLE_TO_LAYOUT } from '@/config/menus'
import PatientLayout from '@/views/layout/PatientLayout.vue'
import DoctorLayout from '@/views/layout/DoctorLayout.vue'
import AdminLayout from '@/views/layout/AdminLayout.vue'

const route = useRoute()
const authStore = useAuthStore()

// 布局 key → 组件映射（key 与 config/menus.ts 的 ROLE_TO_LAYOUT 对应）
const LAYOUT_MAP: Record<string, Component> = {
  patient: PatientLayout,
  doctor: DoctorLayout,
  admin: AdminLayout
}

/**
 * 按当前登录角色渲染对应布局框架：
 * - noLayout（/qa）与 guestOnly（登录/注册/找回密码）页面裸渲染
 * - 未标记 requiresAuth 的路由（如 404）裸渲染
 * - 其余认证页面按角色进入患者门户 / 医生工作台 / 管理后台
 */
const layoutComponent = computed(() => {
  if (route.meta.noLayout === true) return null
  if (route.meta.guestOnly === true) return null
  if (route.meta.requiresAuth !== true) return null
  const key = ROLE_TO_LAYOUT[authStore.userRole] || 'patient'
  return LAYOUT_MAP[key] || PatientLayout
})
</script>

<style scoped>
#app {
  min-height: 100vh;
}
</style>
