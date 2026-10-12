import { describe, expect, it } from 'vitest'
import { isLeadAppealPending, leadAppealSummaryLabel } from './leadAppealSummary'
import type { ManagedLead } from './api'
describe('appeal lifecycle presentation', () => {
  it('keeps no appeal separate from active and completed appeals', () => {
    expect(isLeadAppealPending({})).toBe(false)
    expect(leadAppealSummaryLabel({})).toBe('暂无申诉')
    for (const status of ['sales_manager_reviewing', 'quality_reviewing', 'chairman_reviewing', 'upheld', 'overturned', 'withdrawn']) {
      const lead = { appealSummary: { status, roundNo: 2 } } as ManagedLead
      expect(isLeadAppealPending(lead)).toBe(status.endsWith('_reviewing'))
      expect(leadAppealSummaryLabel(lead)).toContain('第 2 轮')
      expect(leadAppealSummaryLabel(lead)).not.toContain('未知')
    }
  })
})
