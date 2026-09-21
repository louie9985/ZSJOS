// UTF-8. Reuse the existing Workbench test runner without adding an Admin dependency.
import { fileURLToPath } from 'node:url'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

export default {
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('../src', import.meta.url)),
      vitest: fileURLToPath(new URL('../../workbench/node_modules/vitest/dist/index.js', import.meta.url))
    }
  },
  test: { include: ['tests/workbenchAuth.test.ts'] },
  cacheDir: join(tmpdir(), 'zsjos-admin-mobile-auth-vite')
}
