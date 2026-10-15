import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import SubordinateSalesCard from './SubordinateSalesCard'
import type { SubordinateSales } from '../services/api'

const row = { name: '测试销售', username: 'test', accountStatus: 0, presence: 'online', accepting: true,
  canReceiveNewLeads: true, validLeadCount: 12, convertedLeadCount: 3, effectiveOrderAmount: 500,
  todayAssignedCount: 8, todayMissedCount: 1, todayReceivedCount: 7, todayQualifiedCount: 2,
  todayFollowUpRecordCount: 9, todayOrderAmount: 100, pendingQualificationCount: 4,
} as SubordinateSales
const render = (fields: Partial<SubordinateSales>) => renderToStaticMarkup(
  <SubordinateSalesCard sales={{ ...row, ...fields }} selected={false} onSelect={() => {}} />)

describe('subordinate daily card', () => {
  it('shows remaining / total rather than completed / total', () => {
    const html = render({ todayFollowUpRemainingCount: 2, todayFollowUpTotalCount: 8 })
    expect(html).toContain('2 / 8')
    expect(html).toContain('剩余未判定')
    expect(html).not.toContain('风险')
    expect(html).not.toContain('3 日未判定')
    expect(html).toContain('有效客资 12')
    expect(html).toContain('成交 3 /')
  })
  it.each([0, 8])('shows a green completed tag for zero remaining with total %s', total => {
    const html = render({ todayFollowUpRemainingCount: 0, todayFollowUpTotalCount: total })
    expect(html).toContain('已完成')
    expect(html).toContain('ant-tag-success')
  })
  it('does not invent zeros or completion when an older backend omits daily fields', () => {
    const html = render({ todayAssignedCount: undefined, todayOrderAmount: undefined })
    expect(html).toContain('状态待更新')
    expect(html).toContain('—')
    expect(html).not.toContain('已完成')
  })
})
