import { defineConfig, mergeConfig } from 'vite'
import base from './pms-attachments.vite'
export default mergeConfig(base, defineConfig({
  cacheDir: 'node_modules/.cache/knowledge-excel-vite',
  optimizeDeps: { entries: ['test/knowledge-excel.html'] },
  server: { host: '127.0.0.1', port: 5329, strictPort: true }
}))
