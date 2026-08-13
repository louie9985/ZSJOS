import { describe, expect, it } from 'vitest'
import { THEME_METAS } from '../../constants'
import { readFileSync } from 'node:fs'

/**
 * Static wiring guard. ThemeProvider must call every preset hook
 * unconditionally (React hook rules), so the configs record is built inline and
 * cannot be imported for inspection — hence a source-level assertion.
 *
 * Failure mode this catches: a preset added to THEME_METAS but never wired into
 * ThemeProvider silently resolves to default-light via the `?? configs[...]`
 * fallback, so the switcher shows an option that does nothing.
 */
const source = readFileSync('src/components/Theme/ThemeProvider.tsx', 'utf8')

describe('ThemeProvider wiring', () => {
  it('wires every preset advertised in THEME_METAS', () => {
    const missing = THEME_METAS
      .map(meta => meta.key)
      .filter(key => !new RegExp(`(^|[\\s{,])'?${key}'?\\s*:`, 'm').test(source))
    expect(missing).toEqual([])
  })

  it('covers all 13 presets', () => {
    expect(THEME_METAS).toHaveLength(13)
  })

  it('keeps a default fallback for unknown presets', () => {
    expect(source).toMatch(/configs\[preset\]\s*\?\?\s*configs\['default-light'\]/)
  })

  it('mounts ThemeVars inside ConfigProvider', () => {
    // the bridge reads useToken(), which only returns preset-merged tokens
    // when it sits *below* ConfigProvider
    const provider = source.indexOf('<ConfigProvider')
    const vars = source.indexOf('<ThemeVars')
    expect(provider).toBeGreaterThan(-1)
    expect(vars).toBeGreaterThan(provider)
  })
})
