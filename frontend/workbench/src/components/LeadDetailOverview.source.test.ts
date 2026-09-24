import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { leadSourceDispatchTag } from './LeadDetailOverview'
import { expectSourceToContainTokens } from '../test/sourceGuard'

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

  it('hides the provider row from lead detail overview output', () => {
    const source = readFileSync('src/components/LeadDetailOverview.tsx', 'utf8')
    expect(source).toContain('hideProviderOwner?: boolean')
    expectSourceToContainTokens(source, '!hideProviderOwner && <div className="lead-profile-row">')
    expectSourceToContainTokens(source, 'providerOwnerNameSnapshot || \'-\'')
    const page = readFileSync('src/pages/LeadManagementPage.tsx', 'utf8')
    expect(page).toContain('hideProviderOwner/>')
  })

  it('keeps the mobile priority block separate from the auxiliary sidebar', () => {
    const source = readFileSync('src/components/LeadDetailOverview.tsx', 'utf8')
    expectSourceToContainTokens(source, '<div className="lead-overview-side">')
    expectSourceToContainTokens(source, '<div className="lead-overview-priority">')
    expectSourceToContainTokens(source, '<aside className="lead-overview-aside">')
    expect(source.indexOf('lead-overview-priority')).toBeLessThan(source.indexOf('lead-overview-aside'))

    const styles = readFileSync('src/styles/components/lead-detail-v2.css', 'utf8')
    expectSourceToContainTokens(styles, '.lead-overview-side {')
    expectSourceToContainTokens(styles, 'display: contents;')
    expectSourceToContainTokens(styles, '.lead-overview-priority {')
    expectSourceToContainTokens(styles, 'order: -1;')
    expectSourceToContainTokens(styles, '.lead-overview-aside {')
    expectSourceToContainTokens(styles, 'order: 1;')
  })
})
