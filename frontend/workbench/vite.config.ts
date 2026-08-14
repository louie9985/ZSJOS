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
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/admin-api': 'http://192.168.2.38:48080',
      '/infra/ws': { target: 'http://192.168.2.38:48080', ws: true }
    }
  }
})
