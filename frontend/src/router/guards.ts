import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'

export function setupRouterGuards(router: Router) {
  // 全局前置守卫
  router.beforeEach((to, _from, next) => {
    const authStore = useAuthStore()

    // 设置页面标题
    if (to.meta.title) {
      document.title = `${to.meta.title} - IMKQAS`
    } else {
      document.title = 'IMKQAS - 医疗知识问答系统'
    }

    // 检查是否需要认证
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      })
      return
    }

    // 如果已登录且访问登录页，重定向到首页
    if (to.path === '/login' && authStore.isAuthenticated) {
      next({ path: '/' })
      return
    }

    // 检查仅允许未登录用户访问的页面（如登录页）
    if (to.meta.guestOnly && authStore.isAuthenticated) {
      next({ path: '/' })
      return
    }

    next()
  })

  // 全局后置钩子
  router.afterEach((to) => {
    // 可以在这里添加页面访问统计等
    console.log(`Route changed to: ${to.path}`)
  })
}