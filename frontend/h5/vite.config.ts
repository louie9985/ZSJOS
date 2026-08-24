import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import UnoCSS from 'unocss/vite'
import postcssPxtorem from 'postcss-pxtorem'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [
    vue(),
    UnoCSS(),
    Components({
      resolvers: [VantResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 10086,
    strictPort: true,
    proxy: {
      '/app-api': {
        target: 'http://192.168.2.17:48080',
        changeOrigin: true
      }
    }
  },
  css: {
    postcss: {
      plugins: [
        postcssPxtorem({
          rootValue: 37.5,
          propList: ['*'],
          selectorBlackList: ['.norem']
        })
      ]
    }
  }
})
