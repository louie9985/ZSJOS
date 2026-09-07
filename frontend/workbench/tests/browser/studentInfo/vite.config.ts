import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
const path = (value: string) => fileURLToPath(new URL(value, import.meta.url))
export default defineConfig({
  root: path('./'), plugins: [react()], cacheDir: path('../../../node_modules/.cache/student-info-browser'),
  resolve: { alias: [{ find: '../services/studentInfo', replacement: path('./fixture.ts') }] },
  server: { host: '127.0.0.1', port: 5189, strictPort: true, fs: { allow: [path('../../../')] } }
})
