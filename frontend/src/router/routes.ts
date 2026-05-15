import type { RouteRecordRaw } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import DashboardView from '@/views/DashboardView.vue'
import QaView from '@/views/QaView.vue'
import KnowledgeView from '@/views/KnowledgeView.vue'
import StatsView from '@/views/StatsView.vue'
import UserView from '@/views/UserView.vue'
import NotFoundView from '@/views/NotFoundView.vue'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { title: '登录', guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册', guestOnly: true }
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/ForgotPasswordView.vue'),
    meta: { title: '忘记密码', guestOnly: true }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: DashboardView,
    meta: { title: '仪表板', requiresAuth: true }
  },
  {
    path: '/qa',
    name: 'qa',
    component: QaView,
    meta: { title: '智能问答', requiresAuth: true, noLayout: true }
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: KnowledgeView,
    meta: { title: '知识库管理', requiresAuth: true, noLayout: true }
  },
  {
    path: '/stats',
    name: 'stats',
    component: StatsView,
    meta: { title: '数据统计', requiresAuth: true }
  },
  {
    path: '/user',
    name: 'user',
    component: UserView,
    meta: { title: '用户管理', requiresAuth: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面未找到' }
  }
]