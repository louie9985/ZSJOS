import { describe, expect, it } from 'vitest'
import { deadlinePresentation, ticketSnapshotText } from './productionTicketPresentation'
describe('production deadline presentation', () => {
  const now = Date.UTC(2026, 8, 22, 0)
  it('uses the 24 hour boundary and reports overdue without a negative countdown', () => {
    expect(deadlinePresentation(now + 86400001, 'accepted', now).tone).toBe('primary')
    expect(deadlinePresentation(now + 86400000, 'accepted', now).tone).toBe('warning')
    expect(deadlinePresentation(now - 60000, 'accepted', now)).toMatchObject({ tone: 'error', text: '已超时 1分钟' })
    expect(deadlinePresentation(now, 'accepted', now).tone).toBe('error')
  })
  it('stops at terminal states and distinguishes missing deadlines', () => {
    expect(deadlinePresentation(now - 1000, 'completed', now).text).toBe('已结束')
    expect(deadlinePresentation(now - 1000, 'cancelled', now).tone).toBe('neutral')
    expect(deadlinePresentation(undefined, 'accepted', now).text).toBe('暂无截止要求')
  })
  it('renders immutable dictionary/entity labels without exposing IDs or resolving current labels', () => {
    expect(ticketSnapshotText({ value: 'legacy', label: '当时名称' })).toBe('当时名称')
    expect(ticketSnapshotText([{ value: 1, label: '甲' }, { value: 2, label: '乙' }])).toBe('甲、乙')
    expect(ticketSnapshotText({ value: 7 })).toBe('历史显示内容未记录')
    expect(ticketSnapshotText(0)).toBe('0')
  })
})
