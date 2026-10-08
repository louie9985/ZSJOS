import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'
export default defineConfig({
  cacheDir: 'node_modules/.cache/runtime-gifts', plugins: [vue()],
  optimizeDeps: { entries: ['test/runtime-gifts.html'] },
  resolve: { alias: [
    { find: '@/config/axios', replacement: resolve(process.cwd(), 'test/runtime-gifts-request.ts') },
    { find: '@', replacement: resolve(process.cwd(), 'src') }
  ] }, server: { host: '127.0.0.1', port: 5287, strictPort: true }
})
