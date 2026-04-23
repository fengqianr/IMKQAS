/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  darkMode: 'media',
  theme: {
    extend: {
      colors: {
        // 颜色配置已移除，使用自定义CSS类替代
        // 所有颜色类已迁移到 src/assets/styles/brand-colors.css
      },
      borderRadius: {
        'DEFAULT': '0.125rem',  // 2px - 极小元素
        'sm': '0.375rem',       // 6px - 嵌套元素
        'md': '0.75rem',        // 12px - 主卡片
        'lg': '0.25rem',        // 4px - 按钮、小卡片
        'xl': '0.5rem',         // 8px - 普通卡片
        'xxl': '2.5rem',        // 40px - 大型容器
        'full': '0.75rem',      // 12px - 药丸按钮、头像
      },
      fontFamily: {
        'headline': ['Manrope', 'sans-serif'],
        'body': ['Inter', 'sans-serif'],
        'label': ['Inter', 'sans-serif'],
      },
      boxShadow: {
        'ambient': '0 12px 40px rgba(0, 94, 184, 0.06)',
        'floating': '0 8px 30px rgba(0, 0, 0, 0.04)',
        'soft': '0 4px 12px rgba(0, 102, 204, 0.08)',
        'medium': '0 8px 24px rgba(0, 102, 204, 0.12)',
        'hard': '0 12px 40px rgba(0, 102, 204, 0.16)',
        'glow': '0 0 0 1px rgba(0, 102, 204, 0.1), 0 8px 32px rgba(0, 102, 204, 0.2)',
        'card': '0 4px 24px rgba(0, 0, 0, 0.08)',
      },
      transitionDuration: {
        'fast': '150ms',
        'medium': '250ms',
        'slow': '350ms',
        'bounce': '500ms',
      },
      animation: {
        'pulse': 'pulse 1.5s infinite',
      },
      spacing: {
        'xxs': '2px',
        'xs': '4px',
        'sm': '8px',
        'md': '16px',
        'lg': '24px',
        'xl': '32px',
        'xxl': '48px',
        'xxxl': '64px',
      },
    },
  },
  plugins: [],
}