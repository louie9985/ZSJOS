import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Stylesheet guards.
 *
 * Files are read with node:fs, not import.meta.glob('?raw'): Vite routes CSS
 * through its style pipeline, so ?raw yields empty strings under Vitest and
 * every assertion would pass vacuously.
 */
const ROOT = 'src/styles'
const EXEMPT = new Set(['index.css'])

function collect(dir: string): string[] {
  return readdirSync(dir).flatMap(entry => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) return collect(path)
    return path.endsWith('.css') ? [path] : []
  })
}

/** 递归收集 .ts / .tsx，用于扫描 tsx 内联样式里的 var(--crm-*) */
function collectTs(dir: string): string[] {
  return readdirSync(dir).flatMap(entry => {
    if (entry === 'node_modules') return []
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) return collectTs(path)
    return /\.tsx?$/.test(path) ? [path] : []
  })
}

const all = collect(ROOT)
const tokens = readFileSync(join(ROOT, 'tokens.css'), 'utf8')
// tokens.css holds the static fallback palette on purpose, so it is excluded
// from the colour checks but needed for the density checks.
const business = all
  .filter(path => !EXEMPT.has(path.split('/').pop() ?? '') && !path.endsWith('tokens.css'))
  .map(path => [path, readFileSync(path, 'utf8')] as const)

const stripComments = (text: string) => text.replace(/\/\*[\s\S]*?\*\//g, '')
const LITERAL = /#[0-9a-fA-F]{3,8}\b|rgba?\(\s*\d/

describe('stylesheet colour hygiene', () => {
  it('finds the split stylesheets with real content', () => {
    expect(business.length).toBeGreaterThan(10)
    // self-check: an empty read would make every assertion below vacuous
    for (const [path, text] of business) expect(text.length, path).toBeGreaterThan(0)
  })

  it('contains no hardcoded colour literals', () => {
    const offenders: string[] = []
    for (const [path, text] of business) {
      stripComments(text).split('\n').forEach((line, i) => {
        if (LITERAL.test(line)) offenders.push(`${path}:${i + 1}  ${line.trim()}`)
      })
    }
    expect(offenders).toEqual([])
  })

  it('routes every colour through a --crm-* variable or an allowed keyword', () => {
    const allowed = /var\(--crm-|transparent|currentColor|inherit|none|color-mix\(/
    const suspicious: string[] = []
    for (const [path, text] of business) {
      stripComments(text).split('\n').forEach((line, i) => {
        const match = line.match(/^\s*(color|background|background-color|border-color|box-shadow)\s*:\s*(.+);/)
        if (match && !allowed.test(match[2])) suspicious.push(`${path}:${i + 1}  ${line.trim()}`)
      })
    }
    expect(suspicious).toEqual([])
  })
})

describe('density token wiring', () => {
  it('defines both non-default density tiers', () => {
    expect(tokens).toContain("html[data-crm-density='loose']")
    expect(tokens).toContain("html[data-crm-density='compact']")
    // the default tier is the :root value and must not be redeclared
    expect(tokens).not.toContain("html[data-crm-density='default']")
  })

  it('defines both non-default font tiers', () => {
    expect(tokens).toContain("html[data-crm-font='small']")
    expect(tokens).toContain("html[data-crm-font='large']")
    expect(tokens).not.toContain("html[data-crm-font='default']")
  })

  it('overrides an identical variable set in both density tiers', () => {
    const varsOf = (selector: string) => {
      const body = tokens.split(selector)[1]?.split('}')[0] ?? ''
      return [...body.matchAll(/--crm-[a-z0-9-]+/g)].map(m => m[0]).sort()
    }
    const loose = varsOf("html[data-crm-density='loose']")
    const compact = varsOf("html[data-crm-density='compact']")
    expect(loose.length).toBeGreaterThan(0)
    // mismatched sets would leave a property stuck at the other tier's value
    expect(loose).toEqual(compact)
  })

  it('guards the compact tier on narrow viewports', () => {
    // compact row height and control height fall below touch-target guidance
    const media = tokens.split('@media (max-width: 768px)')[1] ?? ''
    expect(media).toContain("html[data-crm-density='compact']")
  })

  it('declares every density variable in :root as a fallback', () => {
    const root = tokens.split(':root')[1]?.split('}')[0] ?? ''
    const tierVars = new Set(
      [...(tokens.split("html[data-crm-density='compact']")[1]?.split('}')[0] ?? '')
        .matchAll(/--crm-[a-z0-9-]+/g)].map(m => m[0])
    )
    const missing = [...tierVars].filter(name => !root.includes(`${name}:`))
    expect(missing).toEqual([])
  })
})

describe('spacing and sizing anchors', () => {
  const joined = business.map(([, text]) => text).join('\n')

  it('keeps page and pane padding on density variables', () => {
    // 页面外边距只由 .workspace-page 提供（见 layout.css）——页面类不再各自
    // 重复声明，重复项已删。详情区仍逐个锚定，它们各自是独立的滚动容器。
    const anchors = [
      /\.workspace-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.eam-category-detail-pane \{[^}]*padding: var\(--crm-pane-pad\)/,
      /\.lead-inbox-detail-pane \{[^}]*padding: var\(--crm-pane-pad\)/,
      /\.message-inbox-detail-pane \{[^}]*padding: var\(--crm-pane-pad\)/,
      /\.sales-order-detail-pane \{[^}]*padding: var\(--crm-pane-pad\)/,
      /\.work-plan-detail-pane \{[^}]*padding: var\(--crm-pane-pad\)/,
      /\.subordinate-sales-detail-pane \{[^}]*padding: var\(--crm-pane-pad\)/,
      /\.business-inbox-detail-pane \{[^}]*padding: var\(--crm-pane-pad\)/,
      /\.media-students-detail-pane \{[^}]*padding: var\(--crm-pane-pad\)/
      ,/\.media-feature-detail-pane \{[^}]*padding:\s*var\(--crm-pane-pad\)/
    ]
    expect(anchors.filter(re => !re.test(joined)).map(re => re.source)).toEqual([])
  })

  it('lets .workspace-page own the page padding without per-page duplicates', () => {
    // 19 个页面类曾与父类同值重复声明 padding，纯冗余且切档时两处都要改
    const offenders: string[] = []
    for (const [path, text] of business) {
      const rules = stripComments(text).matchAll(/(\.[a-z0-9-]+-page(?:\s*,\s*\.[a-z0-9-]+-page)*)\s*\{([^}]*)\}/g)
      for (const [, selector, body] of rules) {
        // .workspace-page 本身就是那个唯一出口，它当然要声明
        if (selector.trim() === '.workspace-page') continue
        if (/padding:\s*var\(--crm-page-pad\)\s*;/.test(body)) offenders.push(`${path}  ${selector.trim()}`)
        // 父类已不限宽，反解就没有意义了
        if (/max-width:\s*none/.test(body)) offenders.push(`${path}  ${selector.trim()} (max-width: none)`)
      }
    }
    expect(offenders).toEqual([])
  })

  it('keeps my sales orders on the approved compact top and start edges', () => {
    const salesOrder = business.find(([path]) => path.endsWith('sales-order.css'))?.[1] ?? ''

    // 刻意比通用页面更紧，故显式覆盖父类的 page-pad
    expect(salesOrder).toMatch(/\.sales-order-inbox-page \{[^}]*padding: var\(--crm-sp-1\) var\(--crm-page-pad\)/)
    expect(salesOrder).toMatch(/\.sales-order-inbox-actions \{[^}]*display: flex[^}]*align-items: center/)
  })

  it('unifies the master-detail list column width', () => {
    // scoped to the four master-detail layouts; other grids (e.g. the
    // description-list label column) legitimately use fixed widths
    const layouts = [
      'lead-inbox-layout',
      'message-inbox-layout',
      'sales-order-inbox-layout',
      'work-plan-layout',
      'subordinate-inbox-layout',
      'aging-pool-layout',
      'business-inbox-layout',
      'media-students-inbox-layout'
      ,'media-feature-inbox-layout'
      ,'eam-category-layout'
    ]
    const offenders = layouts.filter(name => {
      const body = joined.split(`.${name} {`)[1]?.split('}')[0] ?? ''
      return !/grid-template-columns: var\(--crm-list-pane-w\)/.test(body)
    })
    expect(offenders).toEqual([])
  })

  it('ties control-aligned heights to the antd controlHeight token', () => {
    // these exist purely to line up with adjacent antd controls, so a literal
    // would desynchronise the moment density changes
    expect(joined).toMatch(/\.dispatch-status-tag \{[^}]*height: var\(--crm-control-h\)/)
    expect(joined).toMatch(/\.dispatch-mode-button \{[^}]*height: var\(--crm-control-h\)/)
    expect(joined).toMatch(/\.lead-product-checkbox \{[^}]*min-height: var\(--crm-control-h\)/)
  })

  it('keeps semantic detail fields responsive and token driven', () => {
    const detailFields = readFileSync(join(ROOT, 'components/detail-field-grid.css'), 'utf8')
    expect(detailFields).toMatch(/\.detail-field-grid \{[^}]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/)
    expect(detailFields).toMatch(/\.detail-field \{[^}]*padding: var\(--crm-sp-2\) var\(--crm-sp-3\)/)
    expect(detailFields).toMatch(/@media \(max-width: 768px\)[\s\S]*\.detail-field-grid\.columns-3[\s\S]*grid-template-columns: minmax\(0, 1fr\)/)
  })

  it('aligns detail field labels left and values right in the component itself', () => {
    // 曾由 message-inbox 与 sales-order 各自覆盖，其余 10 处调用方是左左对齐。
    // 现统一到组件基础样式，页面不该再打补丁。
    const detailFields = readFileSync(join(ROOT, 'components/detail-field-grid.css'), 'utf8')
    expect(detailFields).toMatch(/\.detail-field dt \{[^}]*text-align: left/)
    // 值靠右用 grid + justify-items 而非 text-align —— 后者会把回行的尾巴
    // 也甩到右边（"…6217 0012 / 3456 7890"）。整块靠右、行内左排才读得顺。
    expect(detailFields).toMatch(/\.detail-field dd \{[^}]*justify-items: end/)
    expect(detailFields).toMatch(/\.detail-field dd \{[^}]*text-align: left/)
    // 值的字号必须显式声明，否则继承父级 → 同一组件跨页面字号不一致
    expect(detailFields).toMatch(/\.detail-field dd \{[^}]*font-size: var\(--crm-font-base\)/)
    // 整行长文本（备注、地址）不做右收拢，否则文字块远离标签
    expect(detailFields).toMatch(/\.detail-field\.span-2 dd \{[^}]*justify-items: start/)

    const offenders = business
      .filter(([path]) => !path.endsWith('detail-field-grid.css'))
      .filter(([, text]) => /\.detail-field d[td][^{]*\{[^}]*text-align/.test(stripComments(text)))
      .map(([path]) => path)
    expect(offenders).toEqual([])
  })

  it('keeps detail fields raised rather than sunken', () => {
    // 字段块几乎总是嵌在大卡片内，凹陷会读成「洞中洞」，层次反了
    const detailFields = readFileSync(join(ROOT, 'components/detail-field-grid.css'), 'utf8')
    const base = detailFields.split('.detail-field {')[1]?.split('}')[0] ?? ''
    expect(base).toContain('box-shadow: var(--crm-shadow)')
    expect(base).not.toContain('shadow-inset')
    expect(base).not.toContain('bg-sunken')
  })

  it('keeps intended-product checkbox hit areas on the control and label', () => {
    expect(joined).toMatch(/\.lead-product-checkbox-control \{[^}]*width: fit-content;[^}]*justify-self: start/)
  })

  it('routes spacing through the sp-* ladder or a semantic alias', () => {
    // 1px / 2px 是描边级微调（分隔线端点、1px 位移），0 与 auto 无档位含义
    const ALLOWED = /^(0|auto|inherit|1px|2px|[\d.]+%|[\d.]+(?:vw|vh|em|rem|fr)|var\(--crm-)/
    const offenders: string[] = []
    for (const [path, text] of business) {
      stripComments(text).split('\n').forEach((line, i) => {
        const m = line.match(/^\s*(padding|margin|gap|row-gap|column-gap)(?:-[a-z-]+)?\s*:\s*([^;!]+?)(?:\s*!important)?\s*;/)
        if (!m) return
        const parts = m[2].trim().split(/\s+/)
        if (parts.every(p => ALLOWED.test(p))) return
        offenders.push(`${path}:${i + 1}  ${line.trim()}`)
      })
    }
    expect(offenders).toEqual([])
  })

  it('routes every font-size through a token', () => {
    // 旧版豁免名单是 ['10','11','12','13','16','18','30'] —— 恰好覆盖了当时
    // 出现的全部取值，等于空转。现在文字走 --crm-font-*、图标走 --crm-icon-*
    // （后者不随字号档缩放，否则会破坏 antd Menu / Button 的内部对齐）。
    const offenders: string[] = []
    for (const [path, text] of business) {
      stripComments(text).split('\n').forEach((line, i) => {
        if (/font-size:\s*\d+px/.test(line)) offenders.push(`${path}:${i + 1}  ${line.trim()}`)
      })
    }
    expect(offenders).toEqual([])
  })
})

describe('token single source of truth', () => {
  const constantsSrc = readFileSync('src/constants.ts', 'utf8')

  /** 读 tokens.css 某个选择器块里的 px 取值 */
  const readPx = (selector: string, name: string) => {
    const body = tokens.split(selector)[1]?.split('}')[0] ?? ''
    return body.match(new RegExp(`${name}:\\s*(\\d+)px`))?.[1]
  }

  it('keeps border radius identical between constants.ts and tokens.css', () => {
    // 两个真值源：constants 注入 antd 的 borderRadius token（管 antd 组件），
    // tokens.css 管自有 CSS。small 档曾是 4/6/8 vs 6/8/10，靠 antd-overrides
    // 的 !important 强拉部分组件回来，未列举的组件留在另一个值上。
    const block = constantsSrc.split('BORDER_RADIUS_VALUES')[1]?.split('\n}')[0] ?? ''
    const jsValues = Object.fromEntries(
      [...block.matchAll(/(\w+):\s*\{\s*sm:\s*(\d+),\s*md:\s*(\d+),\s*lg:\s*(\d+)\s*\}/g)]
        .map(m => [m[1], { sm: m[2], md: m[3], lg: m[4] }])
    )
    expect(Object.keys(jsValues).sort()).toEqual(['full', 'round', 'sharp', 'small'])

    const cssSelector: Record<string, string> = {
      sharp: "html[data-crm-radius='sharp']",
      small: ':root',
      round: "html[data-crm-radius='round']",
      full: "html[data-crm-radius='full']"
    }
    const mismatches: string[] = []
    for (const [tier, js] of Object.entries(jsValues)) {
      for (const size of ['sm', 'md', 'lg'] as const) {
        const css = readPx(cssSelector[tier], `--crm-radius-${size}`)
        if (css !== js[size]) mismatches.push(`${tier}.${size}: js=${js[size]} css=${css}`)
      }
    }
    expect(mismatches).toEqual([])
  })

  it('keeps sider widths identical between constants.ts and tokens.css', () => {
    // antd Sider 的 width prop 只接受 number，故 JS 侧持有真值，CSS 侧是镜像
    const block = constantsSrc.split('LAYOUT_SIZES = {')[1]?.split('}')[0] ?? ''
    const num = (key: string) => block.match(new RegExp(`${key}:\\s*(\\d+)`))?.[1]
    const pairs: Array<[string, string | undefined, string | undefined]> = [
      ['sider-1-w', num('PRIMARY_SIDER_W'), readPx(':root', '--crm-sider-1-w')],
      ['sider-1-collapsed', num('PRIMARY_SIDER_COLLAPSED'), readPx(':root', '--crm-sider-1-collapsed')],
      ['sider-2-w', num('SECONDARY_SIDER_W'), readPx(':root', '--crm-sider-2-w')],
      ['sider-2-collapsed', num('SECONDARY_SIDER_COLLAPSED'), readPx(':root', '--crm-sider-2-collapsed')],
      ['sider-single-w', num('SINGLE_SIDER_W'), readPx(':root', '--crm-sider-single-w')],
      ['aside-w', num('AI_SIDER_W'), readPx(':root', '--crm-aside-w')]
    ]
    expect(pairs.filter(([, js, css]) => js === undefined || js !== css)).toEqual([])
  })

  it('defines every --crm-* variable that is referenced anywhere', () => {
    // 幽灵变量（用了但没定义）会让整条声明在 computed-value 阶段失效，
    // 且完全静默 —— FMS 凭证页的分隔线与合计金额曾因此不显示。
    const bridged = readFileSync('src/components/Theme/themeTokens.ts', 'utf8')
    const defined = new Set([
      ...[...tokens.matchAll(/^\s*(--crm-[a-z0-9-]+):/gm)].map(m => m[1]),
      ...[...bridged.matchAll(/'(--crm-[a-z0-9-]+)':/g)].map(m => m[1])
    ])

    const sources = [...collect(ROOT), ...collectTs('src')]
    const missing = new Set<string>()
    for (const path of sources) {
      const text = readFileSync(path, 'utf8')
      for (const [, name] of text.matchAll(/var\((--crm-[a-z0-9-]+)/g)) {
        if (!defined.has(name)) missing.add(`${name}  (${path})`)
      }
    }
    expect([...missing]).toEqual([])
  })
})

describe('scrollbar styling', () => {
  const base = readFileSync(join(ROOT, 'base.css'), 'utf8')

  it('styles the scrollbar through crm tokens rather than the OS default', () => {
    expect(base).toContain('::-webkit-scrollbar')
    expect(base).toMatch(/scrollbar-color:\s*var\(--crm-/)
  })

  it('confines the always-on bar to fine pointers', () => {
    // declaring a width flips macOS/iOS from overlay to space-consuming
    // classic bars, which would steal 8px on touch screens
    const block = base.split('@media (any-pointer: fine)')[1] ?? ''
    expect(block).toContain('::-webkit-scrollbar')
  })

  it('declares the scrollbar tokens in :root', () => {
    const root = tokens.split(':root')[1]?.split('}')[0] ?? ''
    const missing = ['--crm-scrollbar-size', '--crm-scrollbar-thumb'].filter(
      name => !root.includes(`${name}:`)
    )
    expect(missing).toEqual([])
  })
})
