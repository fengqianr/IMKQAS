import type { RouteRecordRaw } from 'vue-router'
import LoginView from '@/views/auth/LoginView.vue'
import QaView from '@/views/chat/QaView.vue'
import KnowledgeView from '@/views/knowledge/KnowledgeView.vue'
import NotFoundView from '@/views/common/NotFoundView.vue'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/qa',
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
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', guestOnly: true }
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/auth/ForgotPasswordView.vue'),
    meta: { title: '忘记密码', guestOnly: true }
  },
  {
    path: '/qa',
    name: 'qa',
    component: QaView,
    meta: { title: '智能问答', requiresAuth: false, noLayout: true }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/patient/ProfileView.vue'),
    meta: { title: '我的健康档案', requiresAuth: true }
  },
  {
    path: '/records',
    name: 'records',
    component: () => import('@/views/patient/RecordsView.vue'),
    meta: { title: '问卷记录', requiresAuth: true }
  },
  {
    path: '/patients',
    name: 'patients',
    component: () => import('@/views/doctor/PatientSearchView.vue'),
    meta: { title: '患者检索', requiresAuth: true }
  },
  {
    path: '/patients/:id',
    name: 'patient-detail',
    component: () => import('@/views/doctor/PatientDetailView.vue'),
    meta: { title: '患者详情', requiresAuth: true }
  },
  {
    path: '/drugs',
    name: 'drugs',
    component: () => import('@/views/doctor/DrugSearchView.vue'),
    meta: { title: '药物查询', requiresAuth: true }
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: KnowledgeView,
    meta: { title: '知识库管理', requiresAuth: false, noLayout: true }
  },
  {
    path: '/contraindication-rules',
    name: 'contraindication-rules',
    component: () => import('@/views/clinical/ContraindicationRules.vue'),
    meta: { title: '禁忌规则', requiresAuth: true, noLayout: true }
  },
  {
    path: '/term-review',
    name: 'term-review',
    component: () => import('@/views/clinical/TermReview.vue'),
    meta: { title: '词条审核', requiresAuth: true, noLayout: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面未找到' }
  }
]