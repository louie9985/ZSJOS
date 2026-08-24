import { describe, expect, it } from 'vitest'
import { leadSourceDispatchTag } from './LeadDetailOverview'

describe('Lead detail source dispatch tag', () => {
  it('labels new-media automatic and specified assignments', () => {
    expect(leadSourceDispatchTag({ sourceType: 'internal_new_media', dispatchMode: 'auto' }))
      .toEqual({ label: '自动分配', color: 'blue' })
    expect(leadSourceDispatchTag({ sourceType: 'internal_new_media', dispatchMode: 'specified' }))
      .toEqual({ label: '指定派单', color: 'orange' })
  })

  it('does not infer a label for other sources or missing historical dispatch mode', () => {
    expect(leadSourceDispatchTag({ sourceType: 'partner', dispatchMode: 'auto' })).toBeUndefined()
    expect(leadSourceDispatchTag({ sourceType: 'sales_self_sourced', dispatchMode: 'self' })).toBeUndefined()
    expect(leadSourceDispatchTag({ sourceType: 'internal_new_media' })).toBeUndefined()
  })
})
