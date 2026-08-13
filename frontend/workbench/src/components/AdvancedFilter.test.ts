import { describe, expect, it } from 'vitest'
import { filterCount } from './AdvancedFilter'

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
