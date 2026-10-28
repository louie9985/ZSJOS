import { describe, expect, it } from 'vitest'
import {
  isSupervisorLeadActionAllowed,
  subordinateAssignmentStatusLabel,
  supervisorLeadSelectionEligibility,
} from './subordinateLeadActions'

const lead = (status: string, assignmentStatus: string, closedAt?: number) => ({ status, assignmentStatus, closedAt })

describe('supervisor Lead action eligibility', () => {
  it('matches the backend state matrix', () => {
    expect(isSupervisorLeadActionAllowed('transfer', lead('submitted', 'owned'))).toBe(true)
    expect(isSupervisorLeadActionAllowed('restore', lead('submitted', 'owned'))).toBe(false)
    expect(isSupervisorLeadActionAllowed('recycle', lead('suspended', 'owned'))).toBe(true)
    expect(isSupervisorLeadActionAllowed('claimPool', lead('submitted', 'recycle_pending'))).toBe(true)
    expect(isSupervisorLeadActionAllowed('claimPool', lead('suspended', 'owned'))).toBe(true)
    expect(isSupervisorLeadActionAllowed('claimPool', lead('suspended', 'recycle_pending'))).toBe(false)
    expect(isSupervisorLeadActionAllowed('publicSea', lead('valid', 'owned'))).toBe(true)
    expect(isSupervisorLeadActionAllowed('publicSea', lead('valid', 'owned', Date.now()))).toBe(false)
    expect(isSupervisorLeadActionAllowed('transfer', lead('won', 'owned'))).toBe(false)
  })

  it('fails the complete selection when one Lead is not applicable', () => {
    expect(supervisorLeadSelectionEligibility('restore', 2, [
      lead('suspended', 'owned'),
      lead('submitted', 'owned'),
    ])).toEqual({
      allowed: false,
      unavailableCount: 1,
      message: '已选 2 条，其中 1 条不支持恢复，请调整选择',
    })
  })

  it('allows owned Leads to be recycled across business states and closed timestamps', () => {
    for (const status of ['submitted', 'suspended', 'valid', 'converted', 'invalid', 'won', 'closed']) {
      expect(isSupervisorLeadActionAllowed('recycle', lead(status, 'owned', Date.now()))).toBe(true)
      expect(isSupervisorLeadActionAllowed('transfer', lead(status, 'recycle_pending', Date.now()))).toBe(true)
    }
    expect(isSupervisorLeadActionAllowed('recycle', lead('valid', 'recycle_pending'))).toBe(false)
    expect(isSupervisorLeadActionAllowed('recycle', lead('valid', 'public_pool'))).toBe(false)
    expect(supervisorLeadSelectionEligibility('recycle', 2, [lead('valid', 'owned'), lead('won', 'owned')]).allowed).toBe(true)
    expect(supervisorLeadSelectionEligibility('recycle', 2, [lead('valid', 'owned'), lead('valid', 'recycle_pending')]).allowed).toBe(false)
  })

  it('uses a stable label and exposes unknown historical values', () => {
    expect(subordinateAssignmentStatusLabel('recycle_pending')).toBe('回收待处理')
    expect(subordinateAssignmentStatusLabel('legacy')).toBe('未知状态')
  })
})
