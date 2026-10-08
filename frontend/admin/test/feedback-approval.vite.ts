import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'
export default defineConfig({ plugins: [vue()], optimizeDeps: { entries: ['test/feedback-approval.html'] }, resolve: { alias: [
  { find: '@/config/axios', replacement: resolve(process.cwd(), 'test/feedback-approval-request.ts') },
  { find: '@/utils/dict', replacement: resolve(process.cwd(), 'test/feedback-approval-dict.ts') },
  { find: '@', replacement: resolve(process.cwd(), 'src') }
] }, server: { host: '127.0.0.1', port: 5295, strictPort: true } })
