import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

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
    expect(page).toContain('selected.availableActions.includes("ASSIGN")')
    expect(page).toContain('selected.availableActions.includes("EXIT")')
    expect(page).toContain('selected.availableActions.includes("REQUEST_TRANSFER")')
    expect(page).toContain('isLeadInboxUnauthorized(error)')
    expect(page).toContain('无权查看公海池')
    expect(page).toContain('无权查看该客资')
  })
})
