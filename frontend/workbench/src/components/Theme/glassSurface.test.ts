import { describe, expect, it } from 'vitest'
import type { ConfigProviderProps } from 'antd'
import { withGlassSurface } from './glassSurface'

describe('withGlassSurface', () => {
  it('returns the original config when hasBackground is false', () => {
    const config: ConfigProviderProps = {
      theme: { token: { colorPrimary: '#ed4192' }, components: { Card: { colorBgContainer: '#FFF4E6' } } }
    }
    const result = withGlassSurface(config, false)
    expect(result).toBe(config)
  })

  it('injects glass tokens into Card when hasBackground is true', () => {
    const config: ConfigProviderProps = {
      theme: { token: { colorBgContainer: '#ffffff' }, components: {} }
    }
    const result = withGlassSurface(config, true)
    const card = (result.theme as any)?.components?.Card
    expect(card.colorBgContainer).toContain('color-mix(in srgb,')
    expect(card.colorBgContainer).toContain('60%')
  })

  it('preserves existing preset component fields via shallow merge', () => {
    // cartoon has Card.colorBgContainer already — glass should override it but not lose other fields
    const config: ConfigProviderProps = {
      theme: {
        token: { colorBgContainer: '#ffffff' },
        components: {
          Card: { colorBgContainer: '#FFF4E6', borderRadiusLG: 18 },
          Table: { headerColor: '#766a60', borderColor: '#e2dcd5' }
        }
      }
    }
    const result = withGlassSurface(config, true)
    const components = (result.theme as any)?.components
    // Card: colorBgContainer overridden, but borderRadiusLG preserved
    expect(components.Card.colorBgContainer).toContain('60%')
    expect(components.Card.borderRadiusLG).toBe(18)
    // Table: headerBg injected, existing headerColor + borderColor preserved
    expect(components.Table.headerBg).toContain('50%')
    expect(components.Table.headerColor).toBe('#766a60')
    expect(components.Table.borderColor).toBe('#e2dcd5')
  })

  it('applies elevated (85%) to Modal and Drawer', () => {
    const config: ConfigProviderProps = {
      theme: { token: { colorBgElevated: '#fcfaf8' }, components: {} }
    }
    const result = withGlassSurface(config, true)
    const components = (result.theme as any)?.components
    expect(components.Modal.contentBg).toContain('85%')
    expect(components.Drawer.colorBgElevated).toContain('85%')
  })

  it('applies input strength (68%) to Input / InputNumber', () => {
    const config: ConfigProviderProps = {
      theme: { token: { colorBgContainer: '#ffffff' }, components: {} }
    }
    const result = withGlassSurface(config, true)
    const components = (result.theme as any)?.components
    expect(components.Input.colorBgContainer).toContain('68%')
    expect(components.InputNumber.colorBgContainer).toContain('68%')
  })

  it('does not mutate the input config', () => {
    const original = { Card: { borderRadiusLG: 18 } }
    const config: ConfigProviderProps = {
      theme: { token: { colorBgContainer: '#fff' }, components: { ...original } }
    }
    withGlassSurface(config, true)
    expect((config.theme as any).components.Card).toEqual({ borderRadiusLG: 18 })
  })
})
