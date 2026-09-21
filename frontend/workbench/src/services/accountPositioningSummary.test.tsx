import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import type { PositioningCard } from './api'
import { accountPositioningSummary, positioningSummaryTags } from './accountPositioningSummary'
import AccountPositioningSummary from '../components/AccountPositioningSummary'

const card: PositioningCard = { id: 1, cardNo: 'TEST', status: 'confirmed', version: 1, availableActions: [],
  fieldsSnapshot: [
    { key: 'pc_primary_track', title: '主赛道', type: 'multi_select', required: false, enabled: true, systemField: false, sort: 3 },
    { key: 'pc_risk', title: '执行主要风险', type: 'textarea', required: false, enabled: true, systemField: false, sort: 11 },
  ],
  valuesSnapshot: { pc_account_name: '不属于摘要的字段', pc_join_goal: '已应用版本的目标', pc_primary_track: 'nutrition', pc_risk: '每周可拍摄时间不足' },
  dictSnapshot: { pc_primary_track: { labelSnapshot: '历史营养标签' } },
}
describe('account positioning summary contract', () => {
  it('maps only the eleven approved positioning fields in historical order', () => {
    expect(accountPositioningSummary(card).map(row => row.key)).toEqual(['pc_join_goal', 'pc_learning_stage', 'pc_primary_track', 'pc_secondary_track',
      'pc_cooperation', 'pc_shoot_time', 'pc_appearance', 'pc_expression', 'pc_assets', 'pc_trust', 'pc_risk'])
    expect(accountPositioningSummary(card)[0].value).toBe('已应用版本的目标')
    expect(accountPositioningSummary(card).at(-1)?.value).toBe('每周可拍摄时间不足')
  })
  it('uses frozen dictionary labels and does not invent missing values', () => {
    expect(accountPositioningSummary(card)[2].value).toBe('历史营养标签')
    expect(accountPositioningSummary(card)[1].value).toBe('未填写')
    expect(accountPositioningSummary().every(row => row.value === '尚未应用定位卡')).toBe(true)
  })
  it('renders exactly fourteen stacked rows with identity and history, without editing hints or full-table fields', () => {
    const html = renderToStaticMarkup(<AccountPositioningSummary card={card} studentName="测试学员" studentContact="测试联系方式" history="历史记录" />)
    expect(html.match(/data-positioning-summary-key=/g)).toHaveLength(14)
    expect(html).toContain('测试学员'); expect(html).toContain('测试联系方式'); expect(html).toContain('已应用版本的目标')
    expect(html).not.toContain('<table'); expect(html).not.toContain('填写提示'); expect(html).not.toContain('不属于摘要的字段')
  })
  it('renders configured dictionary values as chips and free text as plain text', () => {
    const rows = accountPositioningSummary(card)
    expect(rows[2].tags).toEqual(['历史营养标签'])
    expect(rows.at(-1)?.tags).toEqual([])
    const html = renderToStaticMarkup(<AccountPositioningSummary card={card} />)
    expect(html).toContain('account-value-tags')
    expect(html).toContain('>历史营养标签<')
    // The free-text row keeps its rendered value rather than becoming a chip.
    expect(html).toContain('每周可拍摄时间不足')
  })
  it('splits one combined legacy label into one chip per selection and list dictionaries per entry', () => {
    expect(positioningSummaryTags({ ...card, dictSnapshot: { pc_primary_track: { labelSnapshot: 'T7 中医师承专长、T5 中药学' } } }, 'pc_primary_track'))
      .toEqual(['T7 中医师承专长', 'T5 中药学'])
    expect(positioningSummaryTags({ ...card, dictSnapshot: { pc_primary_track: [{ labelSnapshot: 'A' }, { labelSnapshot: 'B' }] } }, 'pc_primary_track'))
      .toEqual(['A', 'B'])
    expect(positioningSummaryTags(card, 'pc_risk')).toEqual([])
  })
})
