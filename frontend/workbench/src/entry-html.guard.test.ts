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

/** 取出 viewport 声明的 content 值。注释里也会提到这些关键词，
    故断言必须限定在实际生效的属性串上，否则注释自己就能让检查通过或失败。 */
const viewportContent = html.match(/<meta\s+name="viewport"\s+content="([^"]*)"/)?.[1] ?? ''

describe('workbench entry html', () => {
  it('declares the mobile viewport so responsive rules can match', () => {
    expect(html).toMatch(/<meta\s+name="viewport"/)
    expect(viewportContent).toMatch(/width=device-width/)
    expect(viewportContent).toMatch(/initial-scale=1(\.0)?/)
  })

  it('leaves user zoom enabled so browser autozoom is fixed at its cause', () => {
    // 聚焦自动放大由表单控件字号 <16px 引起，修字号而不是禁缩放：
    // user-scalable=no / maximum-scale 既在 iOS 15+ 上不可靠，也剥夺了
    // 视力不佳用户主动放大的能力。
    expect(viewportContent).not.toMatch(/user-scalable\s*=\s*no/)
    expect(viewportContent).not.toMatch(/maximum-scale/)
    // viewport-fit=cover 需要贯穿 shell 高度与安全区补偿才能安全启用，
    // 未完成前不得引入，否则刘海区内容会被状态栏压住。
    expect(viewportContent).not.toMatch(/viewport-fit\s*=\s*cover/)
  })

  it('keeps the document shell and charset explicit', () => {
    expect(html).toMatch(/<meta\s+charset="UTF-8"\s*\/>/i)
    expect(html).toMatch(/<html[^>]*lang=/i)
    expect(html).toContain('<div id="root"></div>')
    expect(html).toContain('/src/main.tsx')
  })
})
