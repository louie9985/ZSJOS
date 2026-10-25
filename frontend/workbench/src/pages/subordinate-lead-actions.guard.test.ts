import { describe, expect, it } from 'vitest'
import { expectSourceToContainTokens } from '../test/sourceGuard'
import detailSource from '../components/LeadDetail.tsx?raw'
import pageSource from './SubordinateSalesPage.tsx?raw'
import managementSource from './LeadManagementPage.tsx?raw'
import apiSource from '../services/api.ts?raw'

describe('subordinate Lead supervisor actions', () => {
  it('keeps manager detail actions server-projected and distinguishes both release targets', () => {
    expect(detailSource).toContain("item.code.startsWith('SUPERVISOR_')")
    expect(detailSource).toContain("label: '释放至抢单池'")
    expect(detailSource).toContain("label: '释放至公海池'")
    expect(detailSource).toContain("openQualificationAction('releasePublicSea', true)")
    expectSourceToContainTokens(detailSource, 'managerMode || supervisorAction')
    expect(detailSource).toContain('api.supervisorRestoreLead')
    expect(detailSource).toContain('api.supervisorReleasePublicSeaLead')
    expect(detailSource).toContain("api.subordinateTransferCandidates(action === 'transfer' ? lead.id : undefined)")
  })

  it('shows batch commands only from their server permission identifiers', () => {
    expect(managementSource).toContain('zsjos:subordinate-sales:lead-transfer')
    expect(managementSource).toContain('zsjos:subordinate-sales:lead-restore')
    expect(managementSource).toContain('zsjos:subordinate-sales:lead-recycle')
    expect(managementSource).toContain('zsjos:subordinate-sales:lead-release-claim-pool')
    expect(managementSource).toContain('zsjos:subordinate-sales:lead-release-public-sea')
  })

  it('shows assignment state and blocks an inapplicable mixed selection as a whole', () => {
    expect(pageSource).toContain('<LeadManagementPage')
    expect(pageSource).toContain('salesUserId={sales.userId}')
    expect(managementSource).toContain('supervisorLeadSelectionEligibility')
    expect(managementSource).toContain('api.subordinateBatchTransferCandidates(selectedRowKeys.map(Number))')
    expect(managementSource).toContain('api.subordinateSalesLeads(salesUserId, params)')
    expect(managementSource).toContain('mode={audience}')
    expect(managementSource).toContain('preserveSelectedRowKeys: true')
    expect(managementSource).toContain('salesUserId !== undefined')
    expect(managementSource).toContain('filterProfile.groups.map')
    expect(managementSource).toContain('section.options.map')
  })

  it('declares all server-projected supervisor detail actions in the API type', () => {
    expect(apiSource).toMatch(/["']SUPERVISOR_RESTORE["']/)
    expect(apiSource).toMatch(/["']SUPERVISOR_TRANSFER["']/)
    expect(apiSource).toMatch(/["']SUPERVISOR_RECYCLE["']/)
    expect(apiSource).toMatch(/["']SUPERVISOR_RELEASE_CLAIM_POOL["']/)
    expect(apiSource).toMatch(/["']SUPERVISOR_RELEASE_PUBLIC_SEA["']/)
  })
})
