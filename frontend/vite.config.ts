import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// unplugin-vue-components 为纯 ESM 包，vite.config.ts 在 CJS 项目中被 require 加载，
// 故使用动态 import 在运行时解析（避免顶层静态导入触发 externalize-deps 报错）
export default defineConfig(async () => {
  const Components = (await import('unplugin-vue-components/vite')).default
  const { ElementPlusResolver } = await import('unplugin-vue-components/resolvers')

  return {
    plugins: [
      vue(),
      Components({
        // 关键：自定义组件一律显式局部 import，不做自动全局注册
        dirs: [],
        // 编译期按组件引入 Element Plus 组件与样式
        resolvers: [ElementPlusResolver({ importStyle: 'css' })],
        dts: 'src/components.d.ts',
        include: [/\.vue$/, /\.vue\?vue/, /\.tsx$/]
      })
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    css: {},
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false
    }
  }
})
