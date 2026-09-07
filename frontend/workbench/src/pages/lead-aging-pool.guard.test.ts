import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceNotToContainTokens, expectSourceToContainTokens } from '../test/sourceGuard'

describe('Lead aging pool page', () => {
  const page = readFileSync('src/pages/LeadAgingPoolPage.tsx', 'utf8')

  it('reuses the Lead master-detail presentation while retaining pool actions', () => {
    expect(page).toContain('lead-inbox-layout')
    expect(page).toContain('lead-inbox-list-pane')
    expect(page).toContain('lead-inbox-detail-pane')
    expect(page).toContain('<LeadDetail')
    expect(page).not.toContain('<Card')
    expect(page).not.toContain('<List')
    expect(page).not.toContain('<Pagination')
    expectSourceToContainTokens(page, 'selected.availableActions.includes("ASSIGN")')
    expectSourceToContainTokens(page, 'selected.availableActions.includes("EXIT")')
    expectSourceToContainTokens(page, 'selected.availableActions.includes("REQUEST_TRANSFER")')
    expectSourceNotToContainTokens(page, 'selected.availableActions.includes("ENTER_DEAL")')
    expect(page).toContain('contextToolbarActions=')
    expect(page).toContain('aging-pool-assign')
    expect(page).toContain('aging-pool-exit')
    expect(page).toContain('aging-pool-transfer-request')
    expect(page).toContain('isLeadInboxUnauthorized(error)')
    expect(page).toContain('无权查看公海池')
    expect(page).toContain('无权查看该客资')
  })
})
