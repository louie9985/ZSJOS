import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

export default defineConfig({
  plugins: [react()],
  cacheDir: join(tmpdir(), 'zsjos-work-order-upload-vite'),
  server: { host: '127.0.0.1', port: 5281, strictPort: true },
})
