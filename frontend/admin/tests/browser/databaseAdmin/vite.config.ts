import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import { fileURLToPath } from 'node:url'

const path = (value: string) => fileURLToPath(new URL(value, import.meta.url)).replaceAll('\\', '/')

export default defineConfig({
  root: path('./'),
  cacheDir: path('../../../node_modules/.cache/database-admin-browser'),
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', { [path('./fixture.ts')]: ['useMessage', 'useI18n'] }],
      dts: false
    })
  ],
  resolve: {
    alias: [
      { find: '@/api/infra/databaseAdmin', replacement: path('./fixture.ts') },
      { find: '@/api/infra/dataSourceConfig', replacement: path('./fixture.ts') },
      { find: '@', replacement: path('../../../src') }
    ]
  },
  css: { preprocessorOptions: { scss: { additionalData: '$elNamespace: el;\n' } } },
  server: { host: '127.0.0.1', port: 5188, strictPort: true, fs: { allow: [path('../../../')] } }
})
