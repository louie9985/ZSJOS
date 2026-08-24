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
    const anchors = [
      /\.workspace-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.lead-management-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.message-inbox-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.sales-order-inbox-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.work-plan-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.claim-pool-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.subordinate-sales-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.business-inbox-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.registration-page,.registration-config-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.media-students-page \{[^}]*padding: var\(--crm-page-pad\)/,
      /\.media-accounts-page[^}]*padding:\s*var\(--crm-page-pad\)/,
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

  it('keeps my sales orders on the approved compact top and start edges', () => {
    const salesOrder = business.find(([path]) => path.endsWith('sales-order.css'))?.[1] ?? ''

    expect(salesOrder).toMatch(/\.sales-order-inbox-page \{[^}]*max-width: none/)
    expect(salesOrder).toMatch(/\.sales-order-inbox-page \{[^}]*padding-block-start: var\(--crm-sp-1\)[^}]*padding-inline-start: var\(--crm-sp-1\)/)
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

  it('aligns order detail labels left and values right', () => {
    expect(joined).toMatch(/\.sales-order-detail \.detail-field dt \{[^}]*text-align: left/)
    expect(joined).toMatch(/\.sales-order-detail \.detail-field dd \{[^}]*text-align: right/)
  })

  it('keeps intended-product checkbox hit areas on the control and label', () => {
    expect(joined).toMatch(/\.lead-product-checkbox-control \{[^}]*width: fit-content;[^}]*justify-self: start/)
  })

  it('leaves only deliberate font-size literals', () => {
    const offenders: string[] = []
    for (const [path, text] of business) {
      stripComments(text).split('\n').forEach((line, i) => {
        const match = line.match(/font-size:\s*(\d+)px/)
        if (!match) return
        // 13px brand must fit a 144px sider; 16px/30px are icon glyph sizes;
        // 11px is deliberately below the sm tier; 10px is tab-close and
        // primary-nav label; 12px is the brand mark, 18px is menu icons
        if (['10', '11', '12', '13', '16', '18', '30'].includes(match[1])) return
        offenders.push(`${path}:${i + 1}  ${line.trim()}`)
      })
    }
    expect(offenders).toEqual([])
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
