import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import type { App } from 'vue'

export function setupElementPlus(app: App) {
  app.use(ElementPlus)

  // 注册所有图标
  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }

  // 全局配置
  app.provide('$ELEMENT', {
    size: 'default',
    zIndex: 2000
  })
}