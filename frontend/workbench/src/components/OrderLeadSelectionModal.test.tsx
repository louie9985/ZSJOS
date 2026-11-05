import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import OrderLeadSelectionModal from './OrderLeadSelectionModal'
import type { OrderLeadCandidate } from '../services/api'

const candidates: OrderLeadCandidate[] = [1, 2].map(id => ({
  id, status: id === 1 ? 'won' : 'closed',
  profile: { leadNo: `L-${id}`, submittedName: '测试客户', submittedMobile: '13800000000' },
}))
describe('order Lead cards', () => {
  it('marks exactly the chosen card and preserves both complete profiles', () => {
    const html = renderToStaticMarkup(<OrderLeadSelectionModal candidates={candidates} selectedId={2} onSelect={() => undefined}/>)
    expect(html.match(/order-lead-card is-selected/g)).toHaveLength(1)
    const checkedInputs = html.match(/<input[^>]*checked=""[^>]*>/g) || []
    expect(checkedInputs).toHaveLength(1)
    expect(checkedInputs[0]).toContain('value="2"')
    expect(html).toContain('L-1')
    expect(html).toContain('L-2')
    expect(html).toContain('客资提交时间')
    expect(html).toContain('渠道')
    expect(html).toContain('已选中')
    expect(html).not.toContain('<details')
    expect(html).not.toContain('展开完整档案')
  })
  it('disables selection while submitting', () => {
    const html = renderToStaticMarkup(<OrderLeadSelectionModal candidates={candidates} selectedId={1} onSelect={() => undefined} disabled/>)
    expect(html.match(/disabled=""/g)).toHaveLength(2)
  })
})
