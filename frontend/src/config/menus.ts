// 三端角色菜单与角色映射配置（单一事实来源）
// 布局组件、App.vue 均从此处读取，避免角色→菜单 / 角色→布局映射多处维护

/** 侧边导航菜单项 */
export interface MenuItem {
  path: string
  title: string
  icon: string
}

/** 三端菜单定义 */
export const MENUS: Record<string, MenuItem[]> = {
  patient: [
    { path: '/qa', title: '智能问答', icon: 'forum' },
    { path: '/profile', title: '我的健康档案', icon: 'person' },
    { path: '/records', title: '问卷记录', icon: 'list_alt' },
    { path: '/user', title: '个人中心', icon: 'account_circle' }
  ],
  doctor: [
    { path: '/qa', title: '智能问答', icon: 'forum' },
    { path: '/patients', title: '患者检索', icon: 'stethoscope' },
    { path: '/drugs', title: '药物查询', icon: 'medication' },
    { path: '/triage', title: '批量导诊', icon: 'Filter' },
    { path: '/contraindication-rules', title: '禁忌规则', icon: 'rule' }
  ],
  admin: [
    { path: '/dashboard', title: '系统统计', icon: 'dashboard' },
    { path: '/users', title: '用户管理', icon: 'manage_accounts' },
    { path: '/term-review', title: '词条审核', icon: 'fact_check' },
    { path: '/contraindication-rules', title: '禁忌规则', icon: 'rule' },
    { path: '/knowledge', title: '知识库', icon: 'library_books' },
    { path: '/qa', title: '智能问答', icon: 'forum' }
  ]
}

/** 后端角色枚举 → 菜单归属（6 角色归并为 3 类） */
export const ROLE_MENU_MAP: Record<string, MenuItem[]> = {
  PATIENT: MENUS.patient,
  STUDENT: MENUS.patient,
  NURSE: MENUS.patient,
  HEALTH_MANAGER: MENUS.patient,
  DOCTOR: MENUS.doctor,
  ADMIN: MENUS.admin
}

/** 后端角色枚举 → 布局归属 key（doctor/admin/其余回退 patient） */
export const ROLE_TO_LAYOUT: Record<string, string> = {
  PATIENT: 'patient',
  STUDENT: 'patient',
  NURSE: 'patient',
  HEALTH_MANAGER: 'patient',
  DOCTOR: 'doctor',
  ADMIN: 'admin'
}

/** 角色中文名 */
export const ROLE_LABELS: Record<string, string> = {
  PATIENT: '患者',
  DOCTOR: '医生',
  ADMIN: '管理员',
  STUDENT: '学生',
  NURSE: '护士',
  HEALTH_MANAGER: '健康管理师'
}

/** 菜单激活态：精确匹配或子路径匹配（如 /patients/:id） */
export function isActive(routePath: string, item: MenuItem): boolean {
  return routePath === item.path || routePath.startsWith(item.path + '/')
}
