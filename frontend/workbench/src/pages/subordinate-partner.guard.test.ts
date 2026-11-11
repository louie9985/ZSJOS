import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceToContainTokens } from '../test/sourceGuard'

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
    const leadPage = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')
    expect(api).toContain('/zsjos/partner/${partnerId}/leads/page')
    expect(api).toContain('/zsjos/partner/leads/${leadId}')
    expectSourceToContainTokens(page, '<LeadManagementPage key={selected.id} permissions={permissions} partnerId={selected.id}/>')
    expectSourceToContainTokens(leadPage, 'partnerId !== undefined ? await managementApi.partnerLeads(partnerId, params)')
    expectSourceToContainTokens(leadPage, 'partnerId !== undefined ? await managementApi.partnerLead(id)')
    expectSourceToContainTokens(leadPage, 'mode={partnerId !== undefined || operationsReadOnly ? "manager-readonly" : audience}')
    expectSourceToContainTokens(leadPage, "lead.partnerOwnerNameSnapshot || '未记录'")
    expect(page).toContain("zsjos:partner:manage-all")
    expect(page).toContain("zsjos:partner:query")
    expect(page).not.toContain('转为员工')
  })

  it('submits search state and invalidates stale partner and lead-count requests', () => {
    const page = readFileSync('src/pages/SubordinatePartnerPage.tsx', 'utf8')
    expect(page).toContain('setAppliedKeyword(keyword.trim())')
    expect(page).toContain('requestId !== partnerRequestRef.current')
    expect(page).toContain('requestId !== leadRequestRef.current')
    expectSourceToContainTokens(page, '++partnerRequestRef.current; ++leadRequestRef.current')
  })

  it('guards delegated Lead list and detail requests in their owning component', () => {
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')
    expectSourceToContainTokens(page, 'const version = ++requestVersion.current')
    expectSourceToContainTokens(page, 'if (version !== requestVersion.current) return')
    expectSourceToContainTokens(page, 'const version = ++detailRequestVersion.current')
    expectSourceToContainTokens(page, 'if (version !== detailRequestVersion.current) return')
    expectSourceToContainTokens(page, 'if (version === detailRequestVersion.current) setDetailLoading(false)')
    expectSourceToContainTokens(page, 'return () => { detailRequestVersion.current++ }')
  })
})
