// UTF-8. Checks fixture behavior, not production API guarantees.
import { describe, expect, it } from 'vitest'
import { filterBatches, initialBatches, longScript } from './content-review-layout-data'

describe('content review isolated layout fixtures', () => {
  it('defaults to both approval stages and uses the latest visible status', () => {
    expect(filterBatches(initialBatches, 'PENDING', '', { mine: false }).map(batch => batch.id)).toEqual([1, 2, 3])
    expect(filterBatches(initialBatches, 'PENDING', '', { mine: false, stage: 'FINAL_REVIEW' }).map(batch => batch.id)).toEqual([2])
    expect(filterBatches(initialBatches, 'COMPLETED', '', { mine: false }).every(batch => batch.status === 'COMPLETED')).toBe(true)
  })
  it('searches student, account, title, topic, script and batch number', () => {
    for (const keyword of ['小禾', '小禾的厨房', '十分钟早餐', '晨间生活方式', '提前分装食材', 'CR-DEMO-20260922-01']) {
      expect(filterBatches(initialBatches, 'PENDING', keyword, { mine: false }).map(batch => batch.id)).toContain(1)
    }
    expect(filterBatches(initialBatches, 'PENDING', '不存在的词语', { mine: false })).toHaveLength(0)
  })
  it('requires combined account filters to match the same account', () => {
    expect(filterBatches([initialBatches[0]], 'ALL', '', { mine: false, operator: '运营甲', platform: '小红书' })).toHaveLength(0)
    expect(filterBatches([initialBatches[0]], 'ALL', '', { mine: false, operator: '运营乙', director: '编导乙', platform: '小红书' })).toHaveLength(1)
  })
  it('includes the full submitted date and excludes unsubmitted drafts', () => {
    expect(filterBatches(initialBatches, 'ALL', '', { mine: false, from: '2026-09-22', to: '2026-09-22' }).map(batch => batch.id)).toEqual([1, 2])
    expect(filterBatches(initialBatches, 'DRAFT', '', { mine: false, to: '2026-09-22' })).toHaveLength(0)
  })
  it('contains exactly 10000 characters with a long unbroken line and an end marker', () => {
    expect(longScript).toHaveLength(10000)
    expect(longScript).toContain('long-unbroken-text-'.repeat(40))
    expect(longScript.endsWith('【全文结束：最后一段完整可读】')).toBe(true)
  })
})
