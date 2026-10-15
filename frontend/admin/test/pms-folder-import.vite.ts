import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import { resolve } from 'node:path'
export default defineConfig({
  cacheDir: 'node_modules/.cache/pms-folder-import-vite',
  plugins: [
    vue(),
    vueJsx(),
    UnoCSS(),
    AutoImport({
      imports: [
        'vue',
        'vue-router',
        {
          '@/hooks/web/useMessage': ['useMessage'],
          '@/hooks/web/useI18n': ['useI18n'],
          '@/hooks/web/useTable': ['useTable'],
          '@/hooks/web/useCrudSchemas': ['useCrudSchemas']
        }
      ],
      dts: false
    })
  ],
  resolve: { alias: { '@': resolve(process.cwd(), 'src') } },
  css: {
    preprocessorOptions: { scss: { additionalData: '@use "@/styles/variables.scss" as *;' } }
  },
  optimizeDeps: { entries: ['test/pms-folder-import.html'] },
  server: { host: '127.0.0.1', port: 5308, strictPort: true }
})
