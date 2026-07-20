import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// 前端单元测试配置：只覆盖确定性逻辑(归一化、store、格式化)，
// 不做脆弱的页面快照。与 vite.config.ts 分离，避免测试插件拖慢生产构建。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    include: ['src/**/*.{test,spec}.ts'],
    setupFiles: ['src/test/setup.ts']
  }
})
