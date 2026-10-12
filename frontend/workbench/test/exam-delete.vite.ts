import { defineConfig, mergeConfig } from 'vite'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import config from '../vite.config'
export default defineConfig(env => mergeConfig(config(env), {
  cacheDir: join(tmpdir(), 'zsjos-exam-delete-vite-cache'),
  server: { host: '127.0.0.1', port: 5251, strictPort: true }
}))
