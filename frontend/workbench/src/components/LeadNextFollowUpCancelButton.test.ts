import { describe, expect, it } from 'vitest'
import { canCancelLeadNextFollowUp } from './LeadNextFollowUpCancelButton'
import type { ManagedLead } from '../services/api'

describe('lead next follow-up cancellation permission', () => {
  const lead: Pick<ManagedLead, 'status' | 'nextFollowUpAt'> = { status: 'won', nextFollowUpAt: Date.parse('2026-12-01T10:00:00+08:00') }
  it('accepts the dedicated configured permission', () => {
    expect(canCancelLeadNextFollowUp(lead, ['zsjos:lead-follow-up:cancel-next-time'])).toBe(true)
    expect(canCancelLeadNextFollowUp(lead, ['*:*:*'])).toBe(true)
  })
  it('rejects missing permission and the old generic permission combination', () => {
    expect(canCancelLeadNextFollowUp(lead, [])).toBe(false)
    expect(canCancelLeadNextFollowUp(lead, ['zsjos:lead-follow-up:create', 'zsjos:business-task:cancel'])).toBe(false)
  })
  it('requires a won lead with a next follow-up time', () => {
    const permissions = ['zsjos:lead-follow-up:cancel-next-time']
    expect(canCancelLeadNextFollowUp({ ...lead, status: 'valid' }, permissions)).toBe(false)
    expect(canCancelLeadNextFollowUp({ ...lead, nextFollowUpAt: undefined }, permissions)).toBe(false)
  })
})
