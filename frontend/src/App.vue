<template>
  <div id="app" :data-theme="themeKey">
    <template v-if="layoutComponent">
      <component :is="layoutComponent"><RouterView /></component>
    </template>
    <template v-else>
      <RouterView />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, type Component } from 'vue'
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

/**
 * 主题切换：按角色映射到三套色板（doctor→深藏青 / patient→灰绿 / admin→现有蓝）。
 * 访客（未登录）落到 admin 默认蓝，保证 /qa 游客视觉与现状一致。
 * theme-colors.css 用 :root[data-theme] 定义变量，故同步写到 <html>；
 * 这样 Element Plus 弹窗（teleport 到 body）也能继承主题变量换肤。
 */
const themeKey = computed(() => {
  const layout = ROLE_TO_LAYOUT[authStore.userRole]
  if (layout === 'doctor') return 'clinical'
  if (layout === 'patient') return 'portal'
  return 'admin'
})

watch(themeKey, (val) => {
  document.documentElement.setAttribute('data-theme', val)
}, { immediate: true })
</script>

<style scoped>
#app {
  min-height: 100vh;
}
</style>
