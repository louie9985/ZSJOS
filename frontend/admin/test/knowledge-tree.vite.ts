import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import UnoCSS from 'unocss/vite'
import { resolve } from 'node:path'

export default defineConfig({
  cacheDir: 'node_modules/.cache/knowledge-tree-vite',
  plugins: [vue(), AutoImport({ imports: ['vue'], dts: false }), UnoCSS()],
  optimizeDeps: { entries: ['test/knowledge-tree.html'] },
  resolve: { alias: { '@': resolve(process.cwd(), 'src') } },
  server: { host: '127.0.0.1', port: 5306, strictPort: true, forwardConsole: false }
})
