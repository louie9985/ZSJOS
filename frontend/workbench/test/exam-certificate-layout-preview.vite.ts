import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  cacheDir: 'node_modules/.cache/exam-certificate-layout-preview',
  server: { host: '127.0.0.1', port: 5354, strictPort: true },
})
