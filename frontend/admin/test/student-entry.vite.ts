import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import { resolve } from 'node:path'
export default defineConfig({ cacheDir: 'node_modules/.cache/student-entry', plugins: [vue(), AutoImport({ imports: ['vue'], dts: false })],
  optimizeDeps: { entries: ['test/student-entry.html'] },
  resolve: { alias: [...['@/config/axios', '@/utils/permission', '@/utils'].map(find => ({ find, replacement: resolve(process.cwd(), 'test/student-entry-request.ts') })), { find: '@', replacement: resolve(process.cwd(), 'src') }] },
  server: { host: '127.0.0.1', port: 5297, strictPort: true } })
