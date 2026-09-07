import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { RemarksAndAttachments } from './LeadDetailOverview'
import type { ManagedLead } from '../services/api'

describe('lead remark rendering', () => {
  it('renders all entries and does not duplicate the compatibility field', () => {
    const lead = { remark: 'fallback-must-not-render', remarkHistory: [
      { id: '1', kind: 'submission', content: 'initial-text' },
      { id: '2', kind: 'supplement', content: 'appended-text' },
      { id: '3', kind: 'supplement', content: 'appended-text' }
    ] } as ManagedLead
    const html = renderToStaticMarkup(<RemarksAndAttachments lead={lead} />)
    expect(html).toContain('initial-text')
    expect(html.split('appended-text')).toHaveLength(3)
    expect(html).not.toContain('fallback-must-not-render')
  })
  it('falls back only when history is absent and reports incomplete evidence', () => {
    expect(renderToStaticMarkup(<RemarksAndAttachments lead={{ remark: 'old-text' } as ManagedLead} />)).toContain('old-text')
    const html = renderToStaticMarkup(<RemarksAndAttachments lead={{ remark: 'old-text', remarkHistory: [], remarkHistoryIncomplete: true } as unknown as ManagedLead} />)
    expect(html).not.toContain('old-text')
    expect(html).toContain('部分历史备注无法还原')
  })
})
