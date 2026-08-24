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
      sourcemap: false,
      chunkSizeWarningLimit: 700,
      rollupOptions: {
        output: {
          // 函数式 manualChunks：仅分组「全站共享」的 vue 生态与「admin 专属」的 echarts。
          // element-plus 刻意不强制分组——若统一打入单一 chunk，患者/访客首屏也要下载全部按需组件
          //（实测 gzip 292KB，远超阶段三分散时的 224KB）；交由 Rollup 自动提取跨页共享的 EP 组件为
          // 共享 chunk、页面专属组件随页面懒加载，首屏只加载登录页实际用到的少量组件。
          // pnpm 下真实路径含 .pnpm/<pkg>@x/node_modules/<pkg>/，故按 node_modules 后首个路径段匹配包名。
          manualChunks(id: string) {
            if (!id.includes('node_modules')) return
            const rest = id.split('node_modules/').pop()!
            const seg = rest.split('/')
            const pkg = seg[0].startsWith('@') ? `${seg[0]}/${seg[1]}` : seg[0]
            if (pkg === 'echarts') return 'echarts'
            if (['vue', 'vue-router', 'pinia', 'axios', 'dayjs'].includes(pkg)) return 'vue-vendor'
          }
        }
      }
    }
  }
})
