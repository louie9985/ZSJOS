import { describe, expect, it } from 'vitest'
import detailSource from '../components/LeadDetail.tsx?raw'
import pageSource from './SubordinateSalesPage.tsx?raw'

describe('subordinate Lead supervisor actions', () => {
  it('keeps manager detail actions server-projected and distinguishes both release targets', () => {
    expect(detailSource).toContain("item.code.startsWith('SUPERVISOR_')")
    expect(detailSource).toContain("label: '释放至抢单池'")
    expect(detailSource).toContain("label: '释放至公海池'")
    expect(detailSource).toContain('api.supervisorRestoreLead')
    expect(detailSource).toContain('api.supervisorReleasePublicSeaLead')
  })

  it('shows batch commands only from their server permission identifiers', () => {
    expect(pageSource).toContain('zsjos:subordinate-sales:lead-transfer')
    expect(pageSource).toContain('zsjos:subordinate-sales:lead-restore')
    expect(pageSource).toContain('zsjos:subordinate-sales:lead-recycle')
    expect(pageSource).toContain('zsjos:subordinate-sales:lead-release-claim-pool')
    expect(pageSource).toContain('zsjos:subordinate-sales:lead-release-public-sea')
  })
})
