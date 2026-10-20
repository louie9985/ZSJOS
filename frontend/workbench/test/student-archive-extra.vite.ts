import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig({ plugins: [react()], cacheDir: 'node_modules/.cache/student-archive-extra',
  server: { host: '127.0.0.1', port: 5352, strictPort: true, watch: { ignored: ['**/tmp/**'] } } })
