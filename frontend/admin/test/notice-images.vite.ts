import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [vue()],
  cacheDir: resolve(tmpdir(), 'notice-images-vite-admin'),
  optimizeDeps: { entries: ['test/notice-images.html'] },
  server: { host: '127.0.0.1', port: 5198, strictPort: true }
})
