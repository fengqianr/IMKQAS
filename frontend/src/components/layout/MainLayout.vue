<template>
  <div class="main-layout">
    <!-- 顶部导航栏 -->
    <nav
      class="fixed top-0 w-full z-50 bg-subtleest/80 backdrop-blur-xl shadow-ambient"
    >
      <div class="flex justify-between items-center h-16 px-8 w-full max-w-[1920px] mx-auto">
        <div class="flex items-center gap-8">
          <span
            class="text-xl font-bold text-brand font-headline tracking-tight"
          >
            Clinical Precision RAG
          </span>
          <div class="hidden md:flex items-center gap-6">
            <router-link
              to="/qa"
              class="text-secondary hover:text-brand transition-colors font-semibold px-3 py-1.5 rounded-full"
              :class="{ 'bg-brand text-on-brand': $route.path === '/qa' }"
            >智能问答</router-link>
            <router-link
              to="/knowledge"
              class="text-secondary hover:text-brand transition-colors font-semibold px-3 py-1.5 rounded-full"
              :class="{ 'bg-brand text-on-brand': $route.path === '/knowledge' }"
            >知识库</router-link>
            <router-link
              to="/stats"
              class="text-secondary hover:text-brand transition-colors font-semibold px-3 py-1.5 rounded-full"
              :class="{ 'bg-brand text-on-brand': $route.path === '/stats' }"
            >数据分析</router-link>
            <router-link
              to="/user"
              class="text-secondary hover:text-brand transition-colors font-semibold px-3 py-1.5 rounded-full"
              :class="{ 'bg-brand text-on-brand': $route.path === '/user' }"
            >个人中心</router-link>
          </div>
        </div>
        <div class="flex items-center gap-4">
          <button
            class="bg-gradient-to-r from-brand to-brand-container text-on-brand px-5 py-2 rounded-full font-semibold active:scale-95 transition-all text-sm"
          >
            New Analysis
          </button>
          <div
            class="flex items-center gap-2 px-3 py-1.5 bg-subtle rounded-xl text-xs font-medium text-secondary"
          >
            <span class="w-2 h-2 rounded-full bg-processing animate-pulse"></span>
            System Status
          </div>
          <div class="flex items-center gap-3 ml-2">
            <button class="p-2 hover:bg-subtle rounded-lg transition-all text-secondary">
              <span class="material-symbols-outlined">notifications</span>
            </button>
            <button class="p-2 hover:bg-subtle rounded-lg transition-all text-secondary">
              <span class="material-symbols-outlined">settings</span>
            </button>
            <el-dropdown trigger="click" @command="handleUserCommand">
              <div class="w-8 h-8 rounded-full bg-surface overflow-hidden border border-outline-muted/20 cursor-pointer">
                <img
                  v-if="userAvatar"
                  alt="Profile"
                  :src="userAvatar"
                >
                <div v-else class="w-full h-full bg-brand flex items-center justify-center text-on-brand text-sm">
                  {{ userName?.charAt(0) || 'U' }}
                </div>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <span class="material-symbols-outlined mr-2">person</span>
                    个人资料
                  </el-dropdown-item>
                  <el-dropdown-item command="settings">
                    <span class="material-symbols-outlined mr-2">settings</span>
                    系统设置
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <span class="material-symbols-outlined mr-2">logout</span>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
      <div class="bg-outline-variant h-px w-full absolute bottom-0"></div>
    </nav>

    <!-- 主内容区 -->
    <main class="pt-20 pb-12 px-8 max-w-[1920px] mx-auto">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'

const router = useRouter()
const authStore = useAuthStore()

// 计算属性
const userName = computed(() => authStore.user?.username || '用户')
const userAvatar = computed(() => authStore.user?.avatar || '')

// 方法
const handleUserCommand = async (command: string) => {
  switch (command) {
    case 'profile':
      router.push('/user')
      break
    case 'settings':
      ElMessage.info('系统设置功能开发中')
      break
    case 'logout':
      await handleLogout()
      break
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要退出登录吗？',
      '退出确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch (error) {
    console.log('用户取消退出')
  }
}
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  background: var(--color-surface);
}

/* Material Symbols Outlined 字体设置 */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  nav {
    padding: 0 var(--spacing-md);
  }
}
</style>