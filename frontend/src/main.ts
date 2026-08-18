import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import '@/api/request'
// Element Plus 按需化：组件样式由 unplugin-vue-components 编译期引入，
// 仅 ElMessage/ElMessageBox（命令式调用，非模板组件）需手动导入样式副作用
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import './assets/styles/variables.css'
import './assets/styles/design-system.css'
import './assets/styles/brand-colors.css'
import './assets/styles/element-plus-overrides.css'
import './assets/styles/tailwind.css'
import './assets/styles/theme-colors.css'

const app = createApp(App)
const pinia = createPinia()

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
