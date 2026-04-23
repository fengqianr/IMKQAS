module.exports = {
    darkMode: "class",
    theme: {
        extend: {
            colors: {
                // 主色系统
                "primary": "#00478d",
                "on-primary": "#ffffff",
                "primary-container": "#005eb8",
                "on-primary-container": "#c8daff",
                "primary-fixed": "#d6e3ff",
                "on-primary-fixed": "#001b3d",
                "primary-fixed-dim": "#a9c7ff",
                "inverse-primary": "#a9c7ff",
                // 辅色
                "secondary": "#4a5f83",
                "on-secondary": "#ffffff",
                "secondary-container": "#c0d5ff",
                "on-secondary-container": "#475c80",
                "secondary-fixed": "#d6e3ff",
                "on-secondary-fixed": "#021b3c",
                "secondary-fixed-dim": "#b2c7f0",
                "on-secondary-fixed-variant": "#32476a",
                // 三级色
                "tertiary": "#793100",
                "on-tertiary": "#ffffff",
                "tertiary-container": "#9f4300",
                "on-tertiary-container": "#ffcfb9",
                "tertiary-fixed": "#ffdbcb",
                "on-tertiary-fixed": "#341100",
                "tertiary-fixed-dim": "#ffb691",
                "on-tertiary-fixed-variant": "#793100",
                // 错误
                "error": "#ba1a1a",
                "on-error": "#ffffff",
                "error-container": "#ffdad6",
                "on-error-container": "#93000a",
                // 表面/背景
                "background": "#f8f9fa",
                "on-background": "#191c1d",
                "surface": "#f8f9fa",
                "on-surface": "#191c1d",
                "surface-variant": "#e1e3e4",
                "on-surface-variant": "#424752",
                "surface-tint": "#005db6",
                "inverse-surface": "#2e3132",
                "inverse-on-surface": "#f0f1f2",
                "surface-dim": "#d9dadb",
                "surface-bright": "#f8f9fa",
                "surface-container-lowest": "#ffffff",
                "surface-container-low": "#f3f4f5",
                "surface-container": "#edeeef",
                "surface-container-high": "#e7e8e9",
                "surface-container-highest": "#e1e3e4",
                // 轮廓
                "outline": "#727783",
                "outline-variant": "#c2c6d4",
            },
            borderRadius: {
                DEFAULT: "0.125rem",
                lg: "0.25rem",
                xl: "0.5rem",
                full: "0.75rem",
            },
            fontFamily: {
                headline: ["Manrope", "sans-serif"],
                body: ["Inter", "sans-serif"],
                label: ["Inter", "sans-serif"],
            },
            // 可选：添加自定义阴影以匹配设计中的使用
            boxShadow: {
                'card': '0 12px 40px rgba(0,71,141,0.03)',
                'nav': '0 12px 40px rgba(0,71,141,0.06)',
            },
        },
    },
    plugins: [],
}