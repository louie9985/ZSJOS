import { describe, it, expect } from 'vitest'
import { prioritizeLeads } from './leadManagement'
describe('review regressions', () => {
  it('keeps server order in subordinate views while management can prioritize tasks', () => {
    const rows = [{ id: 1, pendingTasks: [] }, { id: 2, pendingTasks: [{}] }]
    expect(prioritizeLeads(rows, [], false).map(r => r.id)).toEqual([1, 2])
    expect(prioritizeLeads(rows, []).map(r => r.id)).toEqual([2, 1])
  })
})
