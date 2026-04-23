import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, hideNav: true }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { requiresAuth: true, title: '仪表板' }
  },
  // 问答模块路由（占位）
  {
    path: '/qa',
    name: 'Qa',
    component: () => import('@/views/QaView.vue'),
    meta: { requiresAuth: true, title: '智能问答' }
  },
  // 知识库模块路由（占位）
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/KnowledgeView.vue'),
    meta: { requiresAuth: true, title: '知识库管理' }
  },
  // 数据分析模块路由（占位）
  {
    path: '/stats',
    name: 'Stats',
    component: () => import('@/views/StatsView.vue'),
    meta: { requiresAuth: true, title: '数据分析' }
  },
  // 用户管理模块路由（占位）
  {
    path: '/user',
    name: 'User',
    component: () => import('@/views/UserView.vue'),
    meta: { requiresAuth: true, title: '用户管理' }
  },
  // 重定向
  {
    path: '/',
    redirect: '/dashboard'
  },
  // 404页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { requiresAuth: false }
  }
]