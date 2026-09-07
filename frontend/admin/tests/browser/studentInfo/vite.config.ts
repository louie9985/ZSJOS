import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import { fileURLToPath } from 'node:url'
const path = (value: string) => fileURLToPath(new URL(value, import.meta.url)).replaceAll('\\', '/')
export default defineConfig({
  root: path('./'), cacheDir: path('../../../node_modules/.cache/student-info-browser'),
  plugins: [vue(), AutoImport({ imports: ['vue', { [path('./fixture.ts')]: ['useMessage'] }], dts: false })],
  resolve: { alias: [
    { find: '@/api/zsjos/studentInfoFormConfig', replacement: path('./fixture.ts') },
    { find: '@/api/system/dict/dict.type', replacement: path('./fixture.ts') },
    { find: '@/utils/permission', replacement: path('./fixture.ts') },
    { find: '@', replacement: path('../../../src') }
  ] },
  server: { host: '127.0.0.1', port: 5187, strictPort: true, fs: { allow: [path('../../../')] } }
})
