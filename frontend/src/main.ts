import { createApp } from 'vue'
import App from './App.vue'
import { setupElementPlus } from './plugins/element-plus'
import router from './router'
import { createPinia } from 'pinia'
import './api/interceptors/auth.interceptor'
import './assets/styles/variables.css'
import './assets/styles/design-system.css'
import './assets/styles/brand-colors.css'
import './assets/styles/element-plus-overrides.css'
import './assets/styles/tailwind.css'

const app = createApp(App)
const pinia = createPinia()

// 配置Element Plus
setupElementPlus(app)

// 配置路由
app.use(router)
// 配置状态管理
app.use(pinia)

app.mount('#app')

// 初始化认证状态
import { useAuthStore } from './stores/auth.store'
const authStore = useAuthStore()
authStore.initialize().catch((error) => {
  console.error('认证初始化失败:', error)
})