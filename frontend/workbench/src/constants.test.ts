import { describe, expect, it } from 'vitest'
import { APP_ROUTES, DEFAULT_THEME, LAYOUT_MODE_OPTIONS, RENDERABLE_APP_ROUTES } from './constants'

describe('renderable Workbench routes', () => {
  it('registers configuration pages and existing pages that previously lacked renderability', () => {
    const routes = [
      APP_ROUTES.LEAD_RULE,
      APP_ROUTES.LEAD_FILTER,
      APP_ROUTES.LEAD_FOLLOW_UP_RULE,
      APP_ROUTES.PRODUCT_CONFIG,
      APP_ROUTES.WORK_PLAN_CONFIG,
      APP_ROUTES.LEAD_AGING_POOL,
      APP_ROUTES.SUBORDINATE_SALES
    ]

    expect(routes.every((route) => RENDERABLE_APP_ROUTES.has(route))).toBe(true)
  })
})

describe('Workbench navigation layouts', () => {
  it('keeps the five supported layout modes', () => {
    expect(LAYOUT_MODE_OPTIONS.map(option => option.value)).toEqual([
      'side', 'top', 'top-only', 'single-sider', 'mini-float'
    ])
  })

  it('enables the shared page tabs by default', () => {
    expect(DEFAULT_THEME.tabs).toBe(true)
  })
})
