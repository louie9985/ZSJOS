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

  it('uses a stable label and exposes unknown historical values', () => {
    expect(subordinateAssignmentStatusLabel('recycle_pending')).toBe('回收待处理')
    expect(subordinateAssignmentStatusLabel('legacy')).toBe('未知分配状态（legacy）')
  })
})
