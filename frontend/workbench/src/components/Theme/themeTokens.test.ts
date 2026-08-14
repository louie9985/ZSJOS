import { describe, expect, it } from 'vitest'
import type { GlobalToken } from 'antd'
import { buildCrmVars } from './themeTokens'

/** 仅取桥接用到的字段；GlobalToken 字段数百个，测试无需构造完整对象。 */
const token = {
  colorPrimary: '#ed4192',
  colorPrimaryHover: '#f06ead',
  colorPrimaryBg: '#fff0f6',
  colorPrimaryBorder: '#ffadd2',
  colorSuccess: '#52c41a',
  colorWarning: '#faad14',
  colorError: '#ff4d4f',
  colorBgContainer: '#fffafc',
  colorBgLayout: '#fff0f6',
  colorBgElevated: '#ffffff',
  colorBgMask: 'rgba(0, 0, 0, 0.45)',
  colorBorderSecondary: '#ffd6e7',
  colorBorder: '#ffadd2',
  colorText: 'rgba(0, 0, 0, 0.88)',
  colorTextSecondary: 'rgba(0, 0, 0, 0.65)',
  colorTextTertiary: 'rgba(0, 0, 0, 0.45)',
  colorFillTertiary: 'rgba(0, 0, 0, 0.04)',
  colorFillQuaternary: 'rgba(0, 0, 0, 0.02)',
  boxShadowTertiary: '0 1px 2px 0 rgba(0, 0, 0, 0.03)',
  boxShadowSecondary: '0 6px 16px 0 rgba(0, 0, 0, 0.08)'
} as GlobalToken

describe('buildCrmVars', () => {
  it('derives colours from the active preset token', () => {
    const vars = buildCrmVars(token, { hasBackground: false })
    // 切到 blossom 这类非蓝色 preset 时，主色族必须跟随，不能残留 #1677ff
    expect(vars['--crm-color-primary']).toBe('#ed4192')
    expect(vars['--crm-color-primary-hover']).toBe('#f06ead')
    expect(vars['--crm-color-primary-bg']).toBe('#fff0f6')
    expect(vars['--crm-text-secondary']).toBe('rgba(0, 0, 0, 0.65)')
    expect(vars['--crm-shadow']).toBe('0 1px 2px 0 rgba(0, 0, 0, 0.03)')
  })

  it('uses token backgrounds and borders when no custom background is set', () => {
    const vars = buildCrmVars(token, { hasBackground: false })
    expect(vars['--crm-bg-container']).toBe('#fffafc')
    expect(vars['--crm-bg-layout']).toBe('#fff0f6')
    expect(vars['--crm-border']).toBe('#ffd6e7')
  })

  it('switches to glass values when a gradient background is active', () => {
    const vars = buildCrmVars(token, { hasBackground: true })
    // 渐变背景下容器需半透明、布局层透明，否则渐变被遮住
    expect(vars['--crm-bg-container']).toContain('color-mix(in srgb,')
    expect(vars['--crm-bg-container']).toContain('60%')
    expect(vars['--crm-bg-layout']).toBe('transparent')
    expect(vars['--crm-border']).toContain('color-mix(in srgb,')
    expect(vars['--crm-border']).toContain('55%')
    // 新增 chrome 与 elevated 也走玻璃
    expect(vars['--crm-bg-chrome']).toContain('78%')
    expect(vars['--crm-bg-elevated']).toContain('85%')
    // 玻璃态不影响主色与文字
    expect(vars['--crm-color-primary']).toBe('#ed4192')
    expect(vars['--crm-text']).toBe('rgba(0, 0, 0, 0.88)')
  })

  it('keeps the three legacy variable names consumed by existing styles', () => {
    const vars = buildCrmVars(token, { hasBackground: false })
    // styles.css 现有 60 处引用这三个名字，迁移期间不可改名
    expect(vars).toHaveProperty('--crm-bg-container')
    expect(vars).toHaveProperty('--crm-bg-layout')
    expect(vars).toHaveProperty('--crm-border')
  })

  it('emits only --crm- prefixed custom properties', () => {
    const vars = buildCrmVars(token, { hasBackground: false })
    const offenders = Object.keys(vars).filter(name => !name.startsWith('--crm-'))
    expect(offenders).toEqual([])
  })

  describe('对话框遮罩（mask）', () => {
    it('lightens the mask under glass so the gradient shows through', () => {
      // 45% 纯黑遮罩铺满视口时，浮层背后是一层均匀的黑，模糊等于没有效果
      const plain = buildCrmVars(token, { hasBackground: false })
      const glass = buildCrmVars(token, { hasBackground: true })
      expect(plain['--crm-bg-mask']).toBe(token.colorBgMask)
      expect(glass['--crm-bg-mask']).toContain('color-mix(in srgb,')
      expect(glass['--crm-bg-mask']).toContain('55%')
    })
  })

  describe('导航栏底色（chrome）', () => {
    it('tints chrome toward the layout colour so it is not the same white as cards', () => {
      // 色阶意图是 layout → sunken → chrome → container → elevated，
      // chrome 若直接等于 container，导航栏就和白卡片糊成一片
      const vars = buildCrmVars(token, { hasBackground: false })
      expect(vars['--crm-bg-chrome']).not.toBe(vars['--crm-bg-container'])
      expect(vars['--crm-bg-chrome']).toContain(token.colorBgLayout)
    })
  })

  describe('背景模糊（glassBlur）', () => {
    it('defaults to 20px and gives overlays 4px more', () => {
      const vars = buildCrmVars(token, { hasBackground: true })
      expect(vars['--crm-glass-blur']).toBe('20px')
      expect(vars['--crm-glass-blur-strong']).toBe('24px')
    })

    it('follows the configured radius', () => {
      const vars = buildCrmVars(token, { hasBackground: true, glassBlur: 36 })
      expect(vars['--crm-glass-blur']).toBe('36px')
      expect(vars['--crm-glass-blur-strong']).toBe('40px')
    })

    it('zeroes out blur when no custom background is active', () => {
      // 纯色背景下没有可透视的内容，模糊只会白白多出合成层
      const vars = buildCrmVars(token, { hasBackground: false, glassBlur: 30 })
      expect(vars['--crm-glass-blur']).toBe('0px')
      expect(vars['--crm-glass-blur-strong']).toBe('0px')
    })

    it('keeps the strong radius at 0 rather than 4px when blur is off', () => {
      const vars = buildCrmVars(token, { hasBackground: true, glassBlur: 0 })
      expect(vars['--crm-glass-blur']).toBe('0px')
      expect(vars['--crm-glass-blur-strong']).toBe('0px')
    })
  })

  describe('玻璃边缘高光（glassEdge）', () => {
    it('strengthens the highlight as blur increases', () => {
      const light = buildCrmVars(token, { hasBackground: true, glassBlur: 8 })
      const heavy = buildCrmVars(token, { hasBackground: true, glassBlur: 40 })
      const alphaOf = (v: string) => Number(v.match(/,\s*([\d.]+)\)/)?.[1])
      expect(alphaOf(light['--crm-glass-edge'])).toBeLessThan(
        alphaOf(heavy['--crm-glass-edge']),
      )
      expect(heavy['--crm-glass-edge']).toContain('inset 0 1px 0 0 rgba(255, 255, 255,')
    })

    it('falls back to a transparent shadow, never `none`', () => {
      // 该变量会被拼进 box-shadow 列表，`none` 不是合法成员，整条声明会被丢弃
      for (const vars of [
        buildCrmVars(token, { hasBackground: false }),
        buildCrmVars(token, { hasBackground: true, glassBlur: 0 }),
      ]) {
        expect(vars['--crm-glass-edge']).not.toContain('none')
        expect(vars['--crm-glass-edge']).toBe('inset 0 0 0 0 transparent')
      }
    })
  })
})
