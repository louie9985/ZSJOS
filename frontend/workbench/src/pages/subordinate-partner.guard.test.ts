import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('subordinate Partner surface', () => {
  it('registers the server-owned route and native component', () => {
    const constants = readFileSync('src/constants.ts', 'utf8')
    const registry = readFileSync('src/services/menuComponentRegistry.ts', 'utf8')
    const host = readFileSync('src/layouts/RouteHost.tsx', 'utf8')
    expect(constants).toContain("SUBORDINATE_PARTNERS: '/zsjos/subordinate-partners'")
    expect(registry).toContain("'zsjos/subordinatePartner/index'")
    expect(host).toContain('<SubordinatePartnerPage permissions={permissions}/>')
  })

  it('uses scoped endpoints and the readonly Lead detail mode', () => {
    const api = readFileSync('src/services/managementApi.ts', 'utf8')
    const page = readFileSync('src/pages/SubordinatePartnerPage.tsx', 'utf8')
    expect(api).toContain('/zsjos/partner/${partnerId}/leads/page')
    expect(api).toContain('/zsjos/partner/leads/${leadId}')
    expect(page).toContain('mode="manager-readonly"')
    expect(page).toContain("lead.partnerOwnerNameSnapshot || '未记录'")
    expect(page).toContain("zsjos:partner:manage")
    expect(page).not.toContain('转为员工')
  })

  it('submits search state and rejects stale partner, lead, and detail requests', () => {
    const page = readFileSync('src/pages/SubordinatePartnerPage.tsx', 'utf8')
    expect(page).toContain('setAppliedKeyword(keyword.trim())')
    expect(page).toContain('requestId !== partnerRequestRef.current')
    expect(page).toContain('requestId !== leadRequestRef.current')
    expect(page).toContain('requestId === detailRequestRef.current')
    expect(page).toContain('++partnerRequestRef.current; ++leadRequestRef.current; ++detailRequestRef.current')
  })
})
