import { describe, expect, it } from 'vitest'
import type { PendingLead } from './api'
import {
  ASSIGNMENT_REFRESH_RETRY_DELAYS_MS,
  formatCountdown,
  hasPendingLead,
  isPendingLeadExpired,
  shouldFocusAssignmentEvent,
  shouldShowAssignmentModal,
  sortPendingLeads
} from './leadAssignment'

const pending = (id: number, remainingSeconds?: number, submittedAt = 1786240800000): PendingLead => ({
  id, leadNo: `KZ20260814000000${String(id).padStart(4, '0')}`,
  dispatchMode: remainingSeconds == null ? 'specified' : 'auto', maskedName: `客户 ${id}`,
  provinceName: '浙江省', cityName: '杭州市', intendedProducts: [], sourceChannel: '抖音',
  leadCategory: '成人学历', attachmentUrls: [], submittedAt, remainingSeconds,
  rejectable: remainingSeconds != null, deferrable: remainingSeconds == null
})

describe('lead assignment queue', () => {
  it('prioritizes the most urgent automatic assignment before specified assignments', () => {
    expect(sortPendingLeads([pending(1), pending(2, 80), pending(3, 20)]).map(item => item.id))
      .toEqual([3, 2, 1])
  })

  it('removes expired automatic assignments while keeping specified assignments', () => {
    expect(isPendingLeadExpired(pending(1, 5), 5)).toBe(true)
    expect(sortPendingLeads([pending(1, 5), pending(2)], 5).map(item => item.id)).toEqual([2])
  })

  it('formats countdowns without changing layout width', () => {
    expect(formatCountdown(65)).toBe('01:05')
    expect(formatCountdown(0)).toBe('00:00')
  })

  it('does not show the assignment modal over a business editing overlay', () => {
    expect(shouldShowAssignmentModal(true, 1)).toBe(false)
    expect(shouldShowAssignmentModal(true, 0)).toBe(true)
  })

  it('focuses only events that create a new pending assignment', () => {
    expect(shouldFocusAssignmentEvent('assigned')).toBe(true)
    expect(shouldFocusAssignmentEvent('reassigned')).toBe(true)
    expect(shouldFocusAssignmentEvent('transferred')).toBe(true)
    expect(shouldFocusAssignmentEvent('accepted')).toBe(false)
    expect(shouldFocusAssignmentEvent('expired')).toBe(false)
  })

  it('retries a targeted refresh until the assigned lead becomes visible', () => {
    expect(ASSIGNMENT_REFRESH_RETRY_DELAYS_MS).toEqual([0, 300, 900])
    expect(hasPendingLead([pending(1), pending(2)], 2)).toBe(true)
    expect(hasPendingLead([pending(1)], 2)).toBe(false)
  })
})
