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
})
