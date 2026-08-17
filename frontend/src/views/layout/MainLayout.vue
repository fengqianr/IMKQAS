<template>
  <div
    class="main-layout"
    :class="{
      'sidebar-collapsed': sidebarCollapsed,
      'layout-clinical': variant === 'clinical',
      'layout-portal': variant === 'portal'
    }"
  >
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ 'sidebar-open': sidebarOpen }">
      <!-- 品牌区（三端一致，保持 hub 图标 + brand + Clinical AI System，仅配色随主题） -->
      <div class="sidebar-header">
        <span class="material-symbols-outlined brand-icon">hub</span>
        <div class="brand-text">
          <h1 class="brand-name">{{ brand }}</h1>
          <p class="brand-sub">Clinical AI System</p>
        </div>
      </div>

      <!-- 底部 CTA：医生端「紧急导诊」/ 患者端「新建咨询」/ 管理端无 -->
      <button v-if="ctaConfig" class="new-assessment" :class="`new-assessment-${ctaConfig.tone}`" @click="onCtaClick">
        <span class="material-symbols-outlined">{{ ctaConfig.icon }}</span>
        <span class="nav-label">{{ ctaConfig.label }}</span>
      </button>

      <!-- 角色动态菜单 -->
      <nav class="sidebar-nav">
        <div class="menu-group-label">主菜单</div>
        <ul>
          <li v-for="item in menuItems" :key="item.path">
            <router-link
              :to="item.path"
              :title="sidebarCollapsed ? item.title : undefined"
              class="menu-item"
              :class="isActive(route.path, item) ? 'menu-item-active' : 'menu-item-inactive'"
            >
              <span class="material-symbols-outlined menu-icon">{{ item.icon }}</span>
              <span class="nav-label menu-label">{{ item.title }}</span>
            </router-link>
          </li>
        </ul>
      </nav>

      <!-- 底部：当前角色 + 折叠切换 -->
      <div class="sidebar-footer">
        <div class="role-badge" :title="sidebarCollapsed ? roleLabel : undefined">
          <span class="material-symbols-outlined">verified_user</span>
          <span class="nav-label role-label">{{ roleLabel }}</span>
        </div>
        <button
          class="collapse-toggle"
          :title="sidebarCollapsed ? '展开菜单' : '折叠菜单'"
          @click="toggleSidebar"
        >
          <span class="material-symbols-outlined">{{ sidebarCollapsed ? 'chevron_right' : 'chevron_left' }}</span>
        </button>
      </div>
    </aside>

    <!-- 移动端抽屉遮罩 -->
    <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false" />

    <!-- 主体区 -->
    <div class="main-content">
      <!-- 顶栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <button class="menu-toggle" @click="sidebarOpen = !sidebarOpen">
            <span class="material-symbols-outlined">menu</span>
          </button>
          <span class="topbar-title">{{ pageTitle }}</span>
        </div>
        <div class="topbar-right">
          <button class="icon-btn" @click="showNotice">
            <span class="material-symbols-outlined">notifications</span>
          </button>
          <el-dropdown trigger="click" @command="handleUserCommand">
            <div class="avatar">
              {{ userName?.charAt(0) || 'U' }}
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="isPatientSide" command="profile">
                  <span class="material-symbols-outlined mr-2">person</span>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <span class="material-symbols-outlined mr-2">logout</span>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content">
        <slot />
      </main>

      <!-- 底部 -->
      <footer class="footer">
        <span>© Clinical Precision RAG | 当前角色：{{ roleLabel }}</span>
        <span class="footer-links">
          <a href="#">系统状态</a>
          <a href="#">合规声明</a>
          <a href="#">隐私政策</a>
        </span>
      </footer>
    </div>

    <!-- 患者端移动端底部导航（4 项固定） -->
    <nav v-if="variant === 'portal'" class="bottom-nav">
      <router-link
        v-for="item in portalBottomNav"
        :key="item.path"
        :to="item.path"
        class="bottom-nav-item"
        :class="isActive(route.path, item) ? 'bottom-nav-item-active' : ''"
      >
        <span class="material-symbols-outlined bottom-nav-icon">{{ item.icon }}</span>
        <span class="bottom-nav-label">{{ item.title }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'
import { ROLE_LABELS, ROLE_TO_LAYOUT, isActive, type MenuItem } from '@/config/menus'

/**
 * 布局 props：菜单列表由各端薄壳布局注入（patient/doctor/admin），
 * 品牌文案可自定义；variant 控制三端差异化配置（底部 CTA / 侧栏宽度 / 激活态），
 * 品牌 logo 与名字三端保持一致，仅配色随 data-theme 主题切换。
 */
const props = defineProps<{
  menus: MenuItem[]
  brand?: string
  variant?: 'portal' | 'clinical' | 'admin'
}>()

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 移动端抽屉开关
const sidebarOpen = ref(false)

// 桌面端侧边栏折叠状态（持久化到 localStorage，刷新后保持）
const SIDEBAR_COLLAPSE_KEY = 'imkqas:sidebar-collapsed'
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSE_KEY) === '1')
watch(sidebarCollapsed, (v) => {
  localStorage.setItem(SIDEBAR_COLLAPSE_KEY, v ? '1' : '0')
})

// 计算属性：菜单来自 props（由薄壳布局注入），角色徽标来自角色映射
const menuItems = computed(() => props.menus)
const roleLabel = computed(() => ROLE_LABELS[authStore.userRole] || '访客')
const userName = computed(() => authStore.user?.username || '用户')
// 个人中心是患者侧功能：仅患者侧角色（PATIENT/STUDENT/NURSE/HEALTH_MANAGER）显示入口，医生/管理员不显示
const isPatientSide = computed(() => ROLE_TO_LAYOUT[authStore.userRole] === 'patient')

/**
 * 三端变体配置：底部 CTA（tone 决定主/危险色）与激活态差异。
 * 品牌区（图标/名字/副标题）三端一致，不改。
 */
const VARIANT_CFG: Record<string, { cta: { label: string; icon: string; path: string; tone: 'danger' | 'primary' } | null }> = {
  clinical: { cta: { label: '紧急导诊', icon: 'emergency', path: '/triage', tone: 'danger' } },
  portal: { cta: { label: '新建咨询', icon: 'add', path: '/qa', tone: 'primary' } },
  admin: { cta: null }
}

const variantCfg = computed(() => VARIANT_CFG[props.variant || 'admin'])
const ctaConfig = computed(() => variantCfg.value.cta)

// 患者端移动端底部导航项（与 MENUS.patient 对应）
const portalBottomNav = [
  { path: '/qa', title: '智能问答', icon: 'forum' },
  { path: '/profile', title: '我的健康档案', icon: 'person' },
  { path: '/records', title: '问卷记录', icon: 'list_alt' },
  { path: '/user', title: '个人中心', icon: 'account_circle' }
]

// 顶栏标题：优先取当前路由 meta.title（各页面自有标题），缺失时回退品牌名
const pageTitle = computed(() => (route.meta.title as string) || props.brand || 'Clinical Precision RAG')

// 侧边栏折叠/展开切换
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 底部 CTA 跳转
const onCtaClick = () => {
  if (!ctaConfig.value) return
  sidebarOpen.value = false
  router.push(ctaConfig.value.path)
}

// 顶栏操作
const showNotice = () => ElMessage.info('暂无新通知')

const handleUserCommand = async (command: string) => {
  if (command === 'profile') {
    sidebarOpen.value = false
    router.push('/user')
  } else if (command === 'logout') {
    await handleLogout()
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch (error) {
    // 用户取消退出
  }
}
</script>

<style scoped>
/* ===== 布局容器 ===== */
.main-layout {
  --layout-sidebar-width: 260px;
  min-height: 100vh;
  background: var(--theme-background);
}

/* 患者端侧栏对齐设计稿 w-64（256px） */
.main-layout.layout-portal {
  --layout-sidebar-width: 256px;
}

.main-layout.sidebar-collapsed {
  --layout-sidebar-width: 80px;
}

/* ===== 侧边栏 ===== */
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--layout-sidebar-width);
  z-index: 30;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface-container-low); /* surface-container-low */
  border-right: 1px solid var(--theme-outline-variant);
  transition: width 0.25s ease;
  overflow: hidden;
}

/* 品牌区 */
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  min-height: 4rem;
  flex-shrink: 0;
  white-space: nowrap;
}

.brand-icon {
  font-size: 2rem;
  color: var(--theme-brand);
  flex-shrink: 0;
}

.brand-text {
  min-width: 0;
  overflow: hidden;
}

.brand-name {
  font-size: 1.0rem;
  font-weight: 700;
  color: var(--theme-brand);
  letter-spacing: -0.01em;
  margin: 0;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-sub {
  font-size: 0.75rem;
  color: var(--theme-on-surface-variant);
  margin: 0;
}

/* 底部 CTA 按钮（主色 / 危险色两态） */
.new-assessment {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin: 0 0.75rem 1.25rem;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  color: #ffffff;
  font-size: 0.875rem;
  font-weight: 600;
  border: none;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease;
}

.new-assessment-primary {
  background: var(--theme-primary);
}

.new-assessment-primary:hover {
  background: var(--theme-primary-strong);
}

.new-assessment-danger {
  background: var(--theme-error);
}

.new-assessment-danger:hover {
  background: #8a1414;
}

.new-assessment .material-symbols-outlined {
  font-size: 1.125rem;
}

/* ===== 导航菜单 ===== */
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0.25rem 0;
}

.menu-group-label {
  padding: 0 1rem 0.5rem;
  font-size: 0.6875rem;
  font-weight: 700;
  color: var(--theme-on-surface-variant);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  opacity: 0.7;
  white-space: nowrap;
}

.sidebar-nav ul {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0 0.5rem;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 0.875rem;
  border-radius: 0.5rem;
  border-left: 3px solid transparent;
  font-size: 0.875rem;
  font-weight: 500;
  white-space: nowrap;
  text-decoration: none;
  transition: all 0.15s ease;
}

.menu-icon {
  font-size: 1.25rem;
  flex-shrink: 0;
}

/* 激活态：医生/管理端左侧蓝条；患者端右侧绿条（设计稿 Gentle Care） */
.menu-item-active {
  color: var(--theme-primary);
  font-weight: 700;
  background: var(--theme-primary-soft);
  border-left-color: var(--theme-primary);
}

.layout-portal .menu-item-active {
  border-left-color: transparent;
  border-right: 3px solid var(--theme-healing-sage);
}

.sidebar-collapsed .layout-portal .menu-item-active {
  border-right-width: 0;
}

.menu-item-inactive {
  color: var(--theme-on-surface-variant);
}

.menu-item-inactive:hover {
  background: var(--theme-surface-container-high);
}

/* ===== 侧边栏底部 ===== */
.sidebar-footer {
  margin-top: auto;
  padding: 0.75rem;
  border-top: 1px solid var(--theme-outline-variant);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex-shrink: 0;
}

.role-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.5rem;
  background: var(--theme-surface-container-high);
  color: var(--theme-on-surface-variant);
  font-size: 0.8125rem;
  font-weight: 500;
  white-space: nowrap;
}

.role-badge .material-symbols-outlined {
  font-size: 1.125rem;
  flex-shrink: 0;
}

.collapse-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem;
  border-radius: 9999px;
  background: var(--theme-surface-container-high);
  color: var(--theme-on-surface-variant);
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}

.collapse-toggle:hover {
  color: var(--theme-brand);
  background: var(--theme-primary-container);
}

/* ===== 折叠态：隐藏文字，仅留图标 ===== */
.sidebar-collapsed .brand-text,
.sidebar-collapsed .menu-group-label,
.sidebar-collapsed .nav-label {
  display: none;
}

.sidebar-collapsed .sidebar-header {
  justify-content: center;
  padding: 1rem 0.5rem;
}

.sidebar-collapsed .new-assessment {
  justify-content: center;
  margin-left: 0.5rem;
  margin-right: 0.5rem;
  padding: 0.75rem 0;
}

.sidebar-collapsed .menu-item {
  justify-content: center;
  padding: 0.625rem 0;
  border-left-width: 0;
}

.sidebar-collapsed .sidebar-nav ul {
  padding: 0 0.375rem;
}

.sidebar-collapsed .role-badge {
  padding: 0.5rem 0;
}

/* ===== 移动端抽屉遮罩 ===== */
.sidebar-mask {
  position: fixed;
  inset: 0;
  z-index: 25;
  background: rgba(0, 0, 0, 0.3);
}

/* ===== 主体区 ===== */
.main-content {
  margin-left: var(--layout-sidebar-width);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  transition: margin-left 0.25s ease;
}

/* ===== 顶栏 ===== */
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 4rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  background: var(--theme-surface-bright);
  border-bottom: 1px solid var(--theme-outline-variant);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.topbar-title {
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--theme-brand);
  letter-spacing: -0.01em;
}

.menu-toggle {
  display: none;
  padding: 0.375rem;
  border-radius: 0.5rem;
  color: var(--theme-on-surface-variant);
  background: none;
  border: none;
  cursor: pointer;
}

.menu-toggle:hover {
  background: var(--theme-surface-container-high);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.icon-btn {
  padding: 0.5rem;
  border-radius: 9999px;
  color: var(--theme-on-surface-variant);
  background: none;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}

.icon-btn:hover {
  color: var(--theme-brand);
  background: var(--theme-surface-container-high);
}

.avatar {
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  background: var(--theme-brand);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
  user-select: none;
}

/* ===== 内容区 ===== */
.content {
  flex: 1;
  padding: 2rem;
  overflow-y: auto;
}

/* ===== 底部 ===== */
.footer {
  height: 3rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2rem;
  background: var(--theme-surface-bright);
  border-top: 1px solid var(--theme-outline-variant);
  font-size: 0.8125rem;
  color: var(--theme-outline);
}

.footer-links {
  display: flex;
  gap: 1rem;
}

.footer-links a {
  color: var(--theme-on-surface-variant);
}

.footer-links a:hover {
  color: var(--theme-brand);
}

/* ===== 患者端移动端底部导航 ===== */
.bottom-nav {
  display: none;
}

.bottom-nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--theme-soft-stone);
  text-decoration: none;
  font-size: 0.625rem;
  font-weight: 600;
}

.bottom-nav-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.bottom-nav-item-active {
  color: var(--theme-primary);
  font-weight: 700;
}

.bottom-nav-item-active .bottom-nav-icon {
  font-variation-settings: 'FILL' 1;
}

/* ===== 响应式：小屏收起侧边栏（抽屉） ===== */
@media (max-width: 1024px) {
  .main-layout,
  .main-layout.sidebar-collapsed {
    --layout-sidebar-width: 0px;
  }
  .sidebar {
    transform: translateX(-100%);
    transition: transform 0.25s ease;
  }
  .sidebar.sidebar-open {
    transform: translateX(0);
    width: 260px;
  }
  /* 折叠态在移动端抽屉展开时恢复全宽 */
  .sidebar-collapsed .sidebar.sidebar-open {
    width: 260px;
  }
  .main-content {
    margin-left: 0;
  }
  .menu-toggle {
    display: flex;
  }
}

/* 移动端（<768px）：患者端显示底部导航，隐藏页脚 */
@media (max-width: 767px) {
  .layout-portal .bottom-nav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: 4rem;
    z-index: 30;
    display: flex;
    background: var(--theme-surface-container-lowest);
    border-top: 1px solid var(--theme-outline-variant);
    box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.05);
  }

  .layout-portal .content {
    padding-bottom: calc(2rem + 4.5rem);
  }

  .layout-portal .footer {
    display: none;
  }
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
