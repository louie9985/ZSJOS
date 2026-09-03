import { defineConfig } from 'vite'
// @ts-ignore
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import UnoCSS from 'unocss/vite'
import postcssPxtorem from 'postcss-pxtorem'
import { resolve } from 'node:path'

export default defineConfig(({ command }) => ({
  plugins: [
    vue(),
    UnoCSS(),
    Components({
      resolvers: [VantResolver()],
      dts: command === 'serve'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 10086,
    strictPort: true,
    proxy: {
      '/public-api': {
        target: 'http://127.0.0.1:48080',
        changeOrigin: true
      },
      '/part-api': {
        target: 'http://127.0.0.1:48080',
        changeOrigin: true
      },
      '/app-api': {
        target: 'http://127.0.0.1:48080',
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
}))
