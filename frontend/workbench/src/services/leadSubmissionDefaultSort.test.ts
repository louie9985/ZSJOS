import { describe, expect, it } from 'vitest'
import { prioritizeLeads, sortLeadsBySubmission } from './leadManagement'

describe('Lead submission default order', () => {
  it('keeps pending tasks first, sorts each group by submission and restores ordinary order on completion', () => {
    const rows = [
      { id: 1, submittedAt: 100, pendingTasks: [{}] },
      { id: 2, submittedAt: 300, pendingTasks: [] },
      { id: 3, submittedAt: 200, pendingTasks: [{}] },
      { id: 4, submittedAt: 200, pendingTasks: [{}] },
      { id: 5, submittedAt: undefined, pendingTasks: [{}] },
    ]
    expect(prioritizeLeads(sortLeadsBySubmission(rows), []).map(row => row.id)).toEqual([4, 3, 1, 5, 2])
    const completed = rows.map(row => ({ ...row, pendingTasks: [] }))
    expect(prioritizeLeads(sortLeadsBySubmission(completed), []).map(row => row.id)).toEqual([2, 4, 3, 1, 5])
    expect(rows.map(row => row.id)).toEqual([1, 2, 3, 4, 5])
  })
  it('keeps submission order despite task, unread or activity changes and resolves ties by ID', () => {
    const rows = [
      { id: 1, submittedAt: 100, lastActivityAt: 999, pendingTasks: [{}] },
      { id: 2, submittedAt: 200, lastActivityAt: 1 },
      { id: 3, submittedAt: 200, lastActivityAt: 2 },
      { id: 4, submittedAt: undefined, pendingTasks: [{}] },
    ]
    expect(sortLeadsBySubmission(rows).map(row => row.id)).toEqual([3, 2, 1, 4])
    expect(rows.map(row => row.id)).toEqual([1, 2, 3, 4])
  })
})
