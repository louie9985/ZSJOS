import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('new media workflow pages', () => {
  const page = readFileSync('src/pages/MediaFeaturePage.tsx', 'utf8')
  const host = readFileSync('src/layouts/RouteHost.tsx', 'utf8')
  const style = readFileSync('src/styles/pages/media-feature.css', 'utf8')
  it('registers independent page entry points for all seven routes', () => {
    for (const name of ['AccountsPage', 'ContentPage', 'ProductionTicketsPage', 'PositioningPage', 'HandoversPage', 'StudentOpsPage', 'ReviewsPage']) expect(page).toContain(`export function ${name}`)
    expect(host).not.toContain('MediaWorkflowPage')
  })
  it('uses the master-detail UI recipe and server projected actions', () => {
    expect(page).toContain('media-feature-inbox-layout')
    expect(page).toContain('media-feature-detail-pane')
    expect(page).toContain('selected.availableActions')
    expect(style).toContain('grid-template-columns:repeat(12,minmax(0,1fr))')
  })
  it('keeps business action labels user-facing', () => {
    expect(page).toContain('actionLabels')
    expect(page).toContain('actionText(action)')
    expect(page).not.toContain('<Typography.Paragraph type="secondary">数据范围和操作入口')
    for (const label of ['编辑账号', '周诊断', '挽救处理', '申请换绑']) expect(page).toContain(label)
    expect(page).toContain('AccountActionModal')
  })
  it('opens message deep links at the requested business object', () => {
    for (const query of ['accountId', 'contentId', 'ticketId', 'positioningCardId', 'handoverId']) {
      expect(page).toContain(`'${query}'`)
    }
    expect(page).toContain('loadDetail(feature, preferredId)')
  })
})
