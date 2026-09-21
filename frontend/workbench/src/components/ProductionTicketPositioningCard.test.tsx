import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import ProductionTicketPositioningCard, { formatPositioningSnapshotValue } from './ProductionTicketPositioningCard'

describe('ProductionTicketPositioningCard', () => {
  it('renders frozen field titles and dictionary labels without raw JSON', () => {
    const html = renderToStaticMarkup(<ProductionTicketPositioningCard snapshot={{
      submissionNo: 3,
      fields: [
        { key: 'persona', title: '账号人设', type: 'textarea', required: true, enabled: true, systemField: false, sort: 2 },
        { key: 'platform', title: '主阵地', type: 'dict', required: true, enabled: true, systemField: false, sort: 1 },
      ],
      values: { persona: '一线营养师', platform: 'douyin' },
      dict: { platform: { value: 'douyin', labelSnapshot: '抖音' } },
      feasibility: { '落地难度': '中等' },
    }} />)

    expect(html).toContain('第 3 次提交')
    expect(html).toContain('定位卡项目')
    expect(html).toContain('计划交付内容确定')
    expect(html).toContain('主阵地 *</strong>')
    expect(html).toContain('一线营养师')
    expect(html).toContain('可行性评估')
    expect(html).not.toContain('&quot;platform&quot;')
    expect(html).not.toContain('{')
  })

  it('formats booleans, arrays and object display values for people', () => {
    expect(formatPositioningSnapshotValue(true)).toBe('是')
    expect(formatPositioningSnapshotValue(['专业', '真实'])).toBe('专业、真实')
    expect(formatPositioningSnapshotValue({ displayValue: '浙江省 / 杭州市' })).toBe('浙江省 / 杭州市')
    expect(formatPositioningSnapshotValue(null)).toBe('未填写')
  })
})
