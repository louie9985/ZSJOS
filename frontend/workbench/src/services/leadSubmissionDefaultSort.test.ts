import { describe, expect, it } from 'vitest'
import { sortLeadsBySubmission } from './leadManagement'

describe('Lead submission default order', () => {
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
