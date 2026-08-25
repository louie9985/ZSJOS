// import { defineConfig } from 'vite'
// import react from '@vitejs/plugin-react'
// export default defineConfig({
//   plugins: [react()],
//   server: {
//     port: 5174,
//     proxy: {
//       '/admin-api': 'http://localhost:48080',
//       '/infra/ws': { target: 'http://localhost:48080', ws: true }
//     }
//   }
// })
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_BACKEND_PROXY_TARGET || 'http://192.168.2.17:48080'
  const adminTarget = env.VITE_ADMIN_EMBED_PROXY_TARGET || 'http://192.168.2.17:80'
  return {
    plugins: [react()],
    server: {
      port: Number(env.VITE_PORT || 5174),
      proxy: {
        '/admin-embed': { target: adminTarget, changeOrigin: true, ws: true },
        '/admin-api': { target: backendTarget, changeOrigin: true },
        '/infra/ws': { target: backendTarget, changeOrigin: true, ws: true }
      }
    }
  }
})
