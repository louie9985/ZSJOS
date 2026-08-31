import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

/**
 * 入口 HTML 守卫。
 *
 * 缺少 viewport 声明时，移动浏览器按约 980px 的桌面宽度布局再整体缩放，
 * 于是整套 `@media (max-width: 768px)` 与 `window.matchMedia` 移动分支
 * 全部不命中 —— 移动适配看起来"没生效"，但 CSS 本身是对的。
 * 该缺失完全静默，故在此固定。
 */
const html = readFileSync('index.html', 'utf8')

describe('workbench entry html', () => {
  it('declares the mobile viewport so responsive rules can match', () => {
    expect(html).toMatch(/<meta\s+name="viewport"/)
    expect(html).toMatch(/width=device-width/)
    expect(html).toMatch(/initial-scale=1(\.0)?/)
  })

  it('keeps the document shell and charset explicit', () => {
    expect(html).toMatch(/<meta\s+charset="UTF-8"\s*\/>/i)
    expect(html).toMatch(/<html[^>]*lang=/i)
    expect(html).toContain('<div id="root"></div>')
    expect(html).toContain('/src/main.tsx')
  })
})
