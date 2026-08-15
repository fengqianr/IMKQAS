<template>
  <div class="main-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ 'sidebar-open': sidebarOpen }">
      <!-- 品牌区 -->
      <div class="sidebar-header">
        <span class="material-symbols-outlined brand-icon">hub</span>
        <div>
          <h1 class="brand-name">{{ brand }}</h1>
          <p class="brand-sub">Clinical AI System</p>
        </div>
      </div>

      <!-- 角色动态菜单 -->
      <nav class="sidebar-nav">
        <div class="menu-group-label">主菜单</div>
        <ul>
          <li v-for="item in menuItems" :key="item.path">
            <router-link
              :to="item.path"
              class="menu-item"
              :class="isActive(route.path, item) ? 'menu-item-active' : 'menu-item-inactive'"
            >
              <span class="material-symbols-outlined menu-icon">{{ item.icon }}</span>
              <span>{{ item.title }}</span>
            </router-link>
          </li>
        </ul>
      </nav>

      <!-- 底部：当前角色 -->
      <div class="sidebar-footer">
        <div class="role-badge">
          <span class="material-symbols-outlined">verified_user</span>
          <span>{{ roleLabel }}</span>
        </div>
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
          <span class="topbar-title">Clinical Precision RAG</span>
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
        <span>© 2024 Clinical Precision RAG | 当前角色：{{ roleLabel }}</span>
        <span class="footer-links">
          <a href="#">系统状态</a>
          <a href="#">合规声明</a>
          <a href="#">隐私政策</a>
        </span>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'
import { ROLE_LABELS, ROLE_TO_LAYOUT, isActive, type MenuItem } from '@/config/menus'

/**
 * 布局 props：菜单列表由各端薄壳布局注入（patient/doctor/admin），
 * 品牌文案可自定义，variant 预留各端密度微调
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

// 计算属性：菜单来自 props（由薄壳布局注入），角色徽标来自角色映射
const menuItems = computed(() => props.menus)
const roleLabel = computed(() => ROLE_LABELS[authStore.userRole] || '访客')
const userName = computed(() => authStore.user?.username || '用户')
// 个人中心是患者侧功能：仅患者侧角色（PATIENT/STUDENT/NURSE/HEALTH_MANAGER）显示入口，医生/管理员不显示
const isPatientSide = computed(() => ROLE_TO_LAYOUT[authStore.userRole] === 'patient')

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
  background: #f8f9fa;
}

/* ===== 侧边栏 ===== */
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 260px;
  z-index: 30;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #c2c6d4;
  transition: transform 0.25s ease;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  height: 4rem;
  border-bottom: 1px solid #c2c6d4;
}

.brand-icon {
  font-size: 2rem;
  color: #005eb8;
}

.brand-name {
  font-size: 1.0rem;
  font-weight: 700;
  color: #005eb8;
  letter-spacing: -0.01em;
  /* 重置 h1 默认外边距与行高，并禁止换行，防止在固定高度品牌区向上溢出被裁剪 */
  margin: 0;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 品牌文字容器允许在 flex 中收缩，配合 h1 的省略号截断 */
.sidebar-header > div {
  min-width: 0;
  overflow: hidden;
}

.brand-sub {
  font-size: 0.75rem;
  color: #4a5f83;
}

/* ===== 导航菜单 ===== */
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem 0;
}

.menu-group-label {
  padding: 0 1rem 0.5rem;
  font-size: 0.6875rem;
  font-weight: 700;
  color: #4a5f83;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  opacity: 0.7;
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
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  font-size: 0.875rem;
  font-weight: 500;
  border-left: 3px solid transparent;
  transition: all 0.15s ease;
}

.menu-icon {
  font-size: 1.25rem;
}

.menu-item-active {
  color: #005eb8;
  font-weight: 700;
  background: #e7e8e9;
  border-left-color: #005eb8;
}

.menu-item-inactive {
  color: #4a5f83;
}

.menu-item-inactive:hover {
  background: #f1f5f9;
}

/* ===== 侧边栏底部 ===== */
.sidebar-footer {
  margin-top: auto;
  padding: 0.5rem 1rem;
  border-top: 1px solid #c2c6d4;
}

.role-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.5rem;
  background: #f1f5f9;
  color: #4a5f83;
  font-size: 0.8125rem;
  font-weight: 500;
}

.role-badge .material-symbols-outlined {
  font-size: 1.125rem;
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
}

/* ===== 顶栏 ===== */
.topbar {
  /* position: sticky; */
  top: 0;
  z-index: 20;
  height: 4rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  background: #ffffff;
  border-bottom: 1px solid #c2c6d4;
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
  color: #005eb8;
  letter-spacing: -0.01em;
}

.menu-toggle {
  display: none;
  padding: 0.375rem;
  border-radius: 0.5rem;
  color: #4a5f83;
  background: none;
  border: none;
  cursor: pointer;
}

.menu-toggle:hover {
  background: #f1f5f9;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.icon-btn {
  padding: 0.5rem;
  border-radius: 9999px;
  color: #4a5f83;
  background: none;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}

.icon-btn:hover {
  color: #005eb8;
  background: #f1f5f9;
}

.avatar {
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  background: #005eb8;
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
  background: #ffffff;
  border-top: 1px solid #c2c6d4;
  font-size: 0.8125rem;
  color: #727783;
}

.footer-links {
  display: flex;
  gap: 1rem;
}

.footer-links a {
  color: #424752;
}

.footer-links a:hover {
  color: #005eb8;
}

/* ===== 响应式：小屏收起侧边栏 ===== */
@media (max-width: 1024px) {
  .main-layout {
    --layout-sidebar-width: 0px;
  }
  .sidebar {
    transform: translateX(-100%);
  }
  .sidebar.sidebar-open {
    transform: translateX(0);
  }
  .main-content {
    margin-left: 0;
  }
  .menu-toggle {
    display: flex;
  }
}

/* Material Symbols 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
