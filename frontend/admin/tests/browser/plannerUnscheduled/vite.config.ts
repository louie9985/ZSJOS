import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import { fileURLToPath } from 'node:url'
const path = (value: string) => fileURLToPath(new URL(value, import.meta.url)).replaceAll('\\', '/')
export default defineConfig({
  root: path('./'), cacheDir: path('../../../node_modules/.cache/planner-unscheduled-browser'),
  plugins: [
    { name: 'isolated-auxiliary-components', enforce: 'pre', resolveId(id) {
      if (/\/(RegistrationStudentInfo|ZsjosAdvancedFilter)\.vue$/.test(id) || id.includes('ClipboardUploadActions.vue')) return path('./stub.ts')
    } },
    vue(), AutoImport({ imports: ['vue', { [path('./fixture.ts')]: ['useMessage'] }], dts: false })
  ],
  resolve: { alias: [
    { find: '@/config/axios', replacement: path('./fixture.ts') },
    { find: '@/hooks/web/useMessage', replacement: path('./fixture.ts') },
    { find: '@/store/modules/user', replacement: path('./fixture.ts') },
    { find: '@/utils/permission', replacement: path('./fixture.ts') },
    { find: '@', replacement: path('../../../src') }
  ] },
  server: { host: '127.0.0.1', port: 5215, strictPort: true, fs: { allow: [path('../../../')] } }
})
