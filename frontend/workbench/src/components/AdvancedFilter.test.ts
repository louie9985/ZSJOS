import { describe, expect, it } from 'vitest'
import { cloneFilterGroup, conditionCount, filterCount, removeFilterAtPath } from './AdvancedFilter'

describe('filterCount', () => {
  it('counts only complete conditions across the two supported levels', () => {
    expect(filterCount({
      logic: 'AND',
      conditions: [
        { fieldKey: 'person.name', operator: 'contains', value: '张' },
        { fieldKey: 'lead.status', operator: 'in', value: [] },
        { fieldKey: 'lead.owner', operator: 'is_not_empty' }
      ],
      groups: [{
        logic: 'OR',
        conditions: [
          { fieldKey: 'order.amount', operator: 'between', valueFrom: 100, valueTo: 500 },
          { fieldKey: 'order.paidAt', operator: 'between', valueFrom: 1 }
        ],
        groups: []
      }]
    })).toBe(3)
  })

  it('returns zero for a newly added condition without a value', () => {
    expect(filterCount({
      logic: 'AND',
      conditions: [{ fieldKey: 'person.name', operator: 'contains' }],
      groups: []
    })).toBe(0)
  })
})

describe('advanced filter draft helpers', () => {
  const applied = {
    logic: 'AND' as const,
    conditions: [{ fieldKey: 'person.name', operator: 'contains', value: '张' }],
    groups: [{
      logic: 'OR' as const,
      conditions: [{ fieldKey: 'lead.leadNo', operator: 'eq', value: 'L-1' }],
      groups: []
    }]
  }

  it('clones drafts without mutating the applied filter on cancel', () => {
    const draft = cloneFilterGroup(applied)
    draft.conditions[0].value = '李'
    expect(applied.conditions[0].value).toBe('张')
  })

  it('counts incomplete rows toward the 20-condition editor limit', () => {
    const draft = cloneFilterGroup(applied)
    draft.conditions.push({ fieldKey: 'person.mobile', operator: 'contains' })
    expect(conditionCount(draft)).toBe(3)
    expect(filterCount(draft)).toBe(2)
  })

  it('removes root and child-group tags by their stable paths', () => {
    const withoutRoot = removeFilterAtPath(cloneFilterGroup(applied), [0])
    expect(withoutRoot.conditions).toEqual([])
    const withoutChild = removeFilterAtPath(cloneFilterGroup(applied), [-1, 0])
    expect(withoutChild.groups[0].conditions).toEqual([])
  })
})
