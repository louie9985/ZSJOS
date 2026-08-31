import { describe, expect, it } from 'vitest'
import { theme } from 'antd'
import type { ThemeConfig } from 'antd'
import { buildDefaultConfig } from './presets'
import { buildCrmVars } from './themeTokens'

/**
 * Bridge coverage for the two runtime-tunable presets.
 *
 * The other 11 presets are exposed as React hooks, so they cannot be invoked
 * outside a renderer; ThemeProvider wiring for those is asserted separately in
 * themeProvider.test.ts. Actual rendered appearance still needs a visual pass.
 */
const varsFor = (isDark: boolean, primary = '#1677ff', compact = false) => {
  const config = buildDefaultConfig(isDark, primary, compact, 10).theme as ThemeConfig
  return buildCrmVars(theme.getDesignToken(config), { hasBackground: false })
}

describe('buildCrmVars across default presets', () => {
  it('resolves the configured primary colour', () => {
    expect(varsFor(false)['--crm-color-primary']).toBe('#1677ff')
  })

  it('produces genuinely different surfaces in dark mode', () => {
    const light = varsFor(false)
    const dark = varsFor(true)
    // this is what replaces the removed .dark-shell rules: dark adaptation now
    // rides on tokens, so these must not be equal
    for (const name of ['--crm-bg-container', '--crm-bg-layout', '--crm-text', '--crm-border']) {
      expect(dark[name], name).not.toBe(light[name])
    }
  })

  it('derives the whole primary family from a custom primary colour', () => {
    const vars = varsFor(false, '#ed4192')
    expect(vars['--crm-color-primary']).toBe('#ed4192')
    // hover / bg / border are derived, so they must shift away from the blue defaults
    const blue = varsFor(false)
    for (const name of ['--crm-color-primary-hover', '--crm-color-primary-bg', '--crm-color-primary-border']) {
      expect(vars[name], name).not.toBe(blue[name])
    }
  })

  it('never emits an empty value', () => {
    for (const isDark of [false, true]) {
      for (const [name, value] of Object.entries(varsFor(isDark))) {
        expect(value, `${name} (dark=${isDark})`).toBeTruthy()
      }
    }
  })

  it('glass mode overrides only surfaces, leaving text and primary intact', () => {
    const token = theme.getDesignToken(buildDefaultConfig(false, '#1677ff', false, 10).theme as ThemeConfig)
    const plain = buildCrmVars(token, { hasBackground: false })
    const glass = buildCrmVars(token, { hasBackground: true })
    const changed = Object.keys(plain).filter(k => plain[k] !== glass[k])
    expect(changed.sort()).toEqual(['--crm-bg-chrome', '--crm-bg-container', '--crm-bg-elevated', '--crm-bg-layout', '--crm-bg-mask', '--crm-bg-overlay', '--crm-bg-sunken', '--crm-border', '--crm-glass-blur', '--crm-glass-blur-strong', '--crm-glass-edge', '--crm-shadow-inset'])
  })
})
