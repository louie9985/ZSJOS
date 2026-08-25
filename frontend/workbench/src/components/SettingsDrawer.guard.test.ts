import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync(new URL('./SettingsDrawer.tsx', import.meta.url), 'utf8')

describe('SettingsDrawer layout previews', () => {
  it('keeps an SVG branch for every supported layout mode', () => {
    for (const mode of ['side', 'top', 'top-only', 'single-sider', 'mini-float']) {
      expect(source).toContain(`case '${mode}':`)
    }
  })
})
