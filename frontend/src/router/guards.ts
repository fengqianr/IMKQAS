import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'

export function setupRouterGuards(router: Router) {
  // 全局前置守卫
  router.beforeEach(async (to, _from, next) => {
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

    // 已登录但用户信息/角色尚未恢复（页面刷新、直接输入URL场景），等待认证恢复后再校验
    if (authStore.isAuthenticated && !authStore.user) {
      await authStore.initialize()
    }

    // 认证恢复后 token 已失效（校验失败被清除），重新跳登录
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

    // 角色权限校验：目标页是否对当前角色开放（meta.roles 白名单）
    const allowedRoles = to.meta.roles as string[] | undefined
    if (
      to.meta.requiresAuth &&
      authStore.isAuthenticated &&
      allowedRoles &&
      !allowedRoles.includes(authStore.userRole)
    ) {
      ElMessage.warning('您没有权限访问该页面')
      next({ path: '/qa' })
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
