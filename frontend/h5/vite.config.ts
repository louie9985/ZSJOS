import { defineConfig } from 'vite'
// @ts-ignore
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import UnoCSS from 'unocss/vite'
import postcssPxtorem from 'postcss-pxtorem'
import { resolve } from 'node:path'
import { randomUUID } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { parseVersionManifest } from './src/utils/version'

const packageInfo = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))
const releases = JSON.parse(readFileSync(new URL('./releases.json', import.meta.url), 'utf8'))
const versionManifest = parseVersionManifest({
  app: 'zsjos-partner-h5',
  version: packageInfo.version,
  buildId: randomUUID(),
  publishedAt: new Date().toISOString(),
  releases
})
if (releases[0]?.version !== packageInfo.version) {
  throw new Error('releases.json 首条版本必须与 package.json 一致')
}

export default defineConfig(({ command }) => ({
  define: { __H5_VERSION__: JSON.stringify(versionManifest) },
  plugins: [
    {
      name: 'h5-version-manifest',
      generateBundle() {
        this.emitFile({ type: 'asset', fileName: 'version.json', source: JSON.stringify(versionManifest) })
      },
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url?.split('?')[0] !== '/version.json') return next()
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.setHeader('Cache-Control', 'no-store')
          res.end(JSON.stringify(versionManifest))
        })
      }
    },
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
        target: 'http://192.168.2.17:48080',
        changeOrigin: true
      },
      '/part-api': {
        target: 'http://192.168.2.17:48080',
        changeOrigin: true
      },
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
}))
