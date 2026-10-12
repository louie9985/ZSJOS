import { describe, expect, it } from 'vitest'
import { canDecideAppeal, groupAppealCycles, selectAppealRecord } from './leadAppealReview'
import type { LeadAppeal } from './api'
const record = (id: number, invalidEventId: number, currentCycle: boolean) => ({ id, invalidEventId, currentCycle, taskId: 'task', status: 'sales_manager_reviewing' } as LeadAppeal)
describe('appeal review target and cycles', () => {
  it('only permits the explicitly targeted current pending task', () => {
    const item = record(2, 20, true)
    expect(canDecideAppeal(item, 2)).toBe(true)
    expect(canDecideAppeal(item, 1)).toBe(false)
    expect(canDecideAppeal({ ...item, currentCycle: false }, 2)).toBe(false)
    expect(canDecideAppeal({ ...item, taskId: undefined }, 2)).toBe(false)
    expect(canDecideAppeal({ ...item, status: 'upheld' }, 2)).toBe(false)
  })
  it('does not silently replace an unavailable target with another appeal', () => {
    const rows = [record(1, 10, false), record(2, 20, true)]
    expect(selectAppealRecord(rows, 1)?.id).toBe(1)
    expect(selectAppealRecord(rows, 3)).toBeUndefined()
    expect(selectAppealRecord(rows)?.id).toBe(2)
  })
  it('keeps repeated round numbers in separate invalid-decision cycles', () => {
    const groups = groupAppealCycles([record(1, 10, false), record(2, 20, true), record(3, 20, true)])
    expect(groups.map(group => group.key)).toEqual(['20', '10'])
    expect(groups[0].items.map(item => item.id)).toEqual([2, 3])
  })
})
