import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import type { ConfigProviderProps } from 'antd'
import { withScale } from './scaleTokens'
import { withGlassSurface } from './glassSurface'
import { DENSITY_SCALE, DENSITIES, FONT_SCALE_TABLE, FONT_SCALES } from '../../constants'

describe('withScale', () => {
  it('injects card padding from the density tier', () => {
    const result = withScale({ theme: { components: {} } }, 'compact', 'default')
    const card = (result.theme as any)?.components?.Card
    expect(card.bodyPadding).toBe(DENSITY_SCALE.compact.cardPad)
    expect(card.bodyPaddingSM).toBe(DENSITY_SCALE.compact.cardPadSM)
    // header 与 body 同档，否则标题区与内容区左右留白对不齐
    expect(card.headerPadding).toBe(card.bodyPadding)
  })

  it('injects table cell padding from the font tier', () => {
    const result = withScale({ theme: { components: {} } }, 'default', 'large')
    const table = (result.theme as any)?.components?.Table
    expect(table.cellPaddingBlock).toBe(FONT_SCALE_TABLE.large.cellBlock)
    expect(table.cellPaddingInline).toBe(FONT_SCALE_TABLE.large.cellInline)
  })

  it('preserves existing preset component fields via shallow merge', () => {
    // serene 在 components.Table 里存着自己的配色，注入尺度不能把它冲掉
    const config: ConfigProviderProps = {
      theme: {
        components: {
          Table: { headerColor: '#766a60', borderColor: '#e2dcd5' },
          Card: { borderRadiusLG: 18 }
        }
      }
    }
    const components = (withScale(config, 'default', 'default').theme as any)?.components
    expect(components.Table.headerColor).toBe('#766a60')
    expect(components.Table.borderColor).toBe('#e2dcd5')
    expect(components.Card.borderRadiusLG).toBe(18)
    expect(components.Table.cellPaddingBlock).toBe(FONT_SCALE_TABLE.default.cellBlock)
  })

  it('composes with withGlassSurface in the ThemeProvider order without losing either', () => {
    // ThemeProvider 的真实嵌套：withGlassSurface(withScale(...))。
    // 顺序写反时玻璃态 headerBg 会被冲掉，表头在玻璃主题下退回实色。
    const config: ConfigProviderProps = {
      theme: { token: { colorBgContainer: '#ffffff' }, components: {} }
    }
    const result = withGlassSurface(withScale(config, 'loose', 'small'), true, 60)
    const table = (result.theme as any)?.components?.Table
    expect(table.headerBg).toContain('color-mix(in srgb,')
    expect(table.cellPaddingBlock).toBe(FONT_SCALE_TABLE.small.cellBlock)
  })
})

describe('scale token single source of truth', () => {
  const tokens = readFileSync('src/styles/tokens.css', 'utf8')

  /** 读某个选择器块里的变量取值（default 档读 :root） */
  const readVar = (selector: string, name: string) => {
    const body = tokens.split(selector)[1]?.split('}')[0] ?? ''
    return body.match(new RegExp(`${name}:\\s*(\\d+)px`))?.[1]
  }

  it('keeps card padding identical between constants.ts and tokens.css', () => {
    // antd Card 的 bodyPadding 取自 DENSITY_SCALE，自绘卡片取自 --crm-card-pad。
    // 两边漂移就会出现「antd 卡片与自绘卡片留白不一致」
    const selectors: Record<string, string> = {
      loose: "html[data-crm-density='loose']",
      default: ':root',
      compact: "html[data-crm-density='compact']"
    }
    for (const density of DENSITIES) {
      expect(readVar(selectors[density], '--crm-card-pad'), density)
        .toBe(String(DENSITY_SCALE[density].cardPad))
    }
  })

  it('orders the density tiers loose > default > compact', () => {
    const pads = DENSITIES.map(d => DENSITY_SCALE[d].cardPad)
    expect(pads).toEqual([...pads].sort((a, b) => b - a))
  })

  it('orders the font tiers small < default < large', () => {
    const blocks = FONT_SCALES.map(f => FONT_SCALE_TABLE[f].cellBlock)
    expect(blocks).toEqual([...blocks].sort((a, b) => a - b))
  })
})
