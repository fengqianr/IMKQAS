import type { RouteRecordRaw } from 'vue-router'
import LoginView from '@/views/auth/LoginView.vue'
import QaView from '@/views/chat/QaView.vue'
import KnowledgeView from '@/views/knowledge/KnowledgeView.vue'
import NotFoundView from '@/views/common/NotFoundView.vue'

// 角色组常量（与后端 User.Role 枚举及 config/menus.ts 的归并一致）
const PATIENT_ROLES = ['PATIENT', 'STUDENT', 'NURSE', 'HEALTH_MANAGER']
const ALL_ROLES = [...PATIENT_ROLES, 'DOCTOR', 'ADMIN']

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
    meta: { title: '我的健康档案', requiresAuth: true, roles: ALL_ROLES }
  },
  {
    path: '/records',
    name: 'records',
    component: () => import('@/views/patient/RecordsView.vue'),
    meta: { title: '问卷记录', requiresAuth: true, roles: PATIENT_ROLES }
  },
  {
    path: '/patients',
    name: 'patients',
    component: () => import('@/views/doctor/PatientSearchView.vue'),
    meta: { title: '患者检索', requiresAuth: true, roles: ['DOCTOR'] }
  },
  {
    path: '/patients/:id',
    name: 'patient-detail',
    component: () => import('@/views/doctor/PatientDetailView.vue'),
    meta: { title: '患者详情', requiresAuth: true, roles: ['DOCTOR'] }
  },
  {
    path: '/drugs',
    name: 'drugs',
    component: () => import('@/views/doctor/DrugSearchView.vue'),
    meta: { title: '药物查询', requiresAuth: true, roles: ['DOCTOR'] }
  },
  {
    path: '/triage',
    name: 'triage',
    component: () => import('@/views/doctor/TriageView.vue'),
    meta: { title: '批量导诊', requiresAuth: true, roles: ['DOCTOR'] }
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: KnowledgeView,
    meta: { title: '知识库管理', requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/contraindication-rules',
    name: 'contraindication-rules',
    component: () => import('@/views/clinical/ContraindicationRules.vue'),
    meta: { title: '禁忌规则', requiresAuth: true, roles: ['DOCTOR', 'ADMIN'] }
  },
  {
    path: '/term-review',
    name: 'term-review',
    component: () => import('@/views/clinical/TermReview.vue'),
    meta: { title: '词条审核', requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/common/UnderConstructionView.vue'),
    meta: { title: '系统统计', requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/users',
    name: 'users',
    component: () => import('@/views/common/UnderConstructionView.vue'),
    meta: { title: '用户管理', requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面未找到' }
  }
]