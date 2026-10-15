import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig({
  cacheDir: 'node_modules/.cache/order-revision-gifts',
  plugins: [
    { name: 'isolate-unrelated-order-panels', enforce: 'pre', resolveId(source, importer) {
      if (importer?.replaceAll('\\', '/').endsWith('/mySalesOrder/index.vue')
        && /(?:WorkbenchListPage|OrderProductSummary|OrderHistoryFacts)\.vue$/.test(source)) return '\0order-panel-fixture'
    }, load(id) { if (id === '\0order-panel-fixture') return 'export default { render(){ return null } }' } },
    vue()
  ],
  optimizeDeps: { entries: ['test/order-revision-gifts.html'] },
  resolve: { alias: [
    { find: '@/config/axios', replacement: resolve(process.cwd(), 'test/order-revision-gifts-request.ts') },
    { find: '@/hooks/web/useMessage', replacement: resolve(process.cwd(), 'test/order-revision-gifts-request.ts') },
    { find: '@/api/system/dict/dict.data', replacement: resolve(process.cwd(), 'test/order-revision-gifts-request.ts') },
    { find: '@', replacement: resolve(process.cwd(), 'src') }
  ] },
  server: { host: '127.0.0.1', port: 5298, strictPort: true }
})
