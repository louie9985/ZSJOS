import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [react()],
  cacheDir: resolve('node_modules/.vite-notice-layout'),
  optimizeDeps: { entries: ['test/notice-layout.html', 'test/notice-table-overflow.html'] },
  build: { outDir: 'tmp/notice-layout-build', rollupOptions: { input: ['test/notice-layout.html', 'test/notice-table-overflow.html'] } },
  server: { host: '127.0.0.1', port: 5197, strictPort: true }
})
