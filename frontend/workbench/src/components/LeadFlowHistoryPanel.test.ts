import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { flowAttachmentState, flowPanelState } from './LeadFlowHistoryPanel'
import flowHistorySource from './LeadFlowHistoryPanel.tsx?raw'

const flowHistoryStyles = readFileSync(new URL('../styles/components/flow-history.css', import.meta.url), 'utf8')

describe('Lead flow-history attachment presentation', () => {
  it('distinguishes unsupported, unavailable, image, and PDF attachments', () => {
    expect(flowAttachmentState({ previewable: false, available: false, contentType: 'application/msword' }))
      .toBe('unsupported')
    expect(flowAttachmentState({ previewable: true, available: false, contentType: 'image/png' }))
      .toBe('unavailable')
    expect(flowAttachmentState({ previewable: true, available: true, contentType: 'image/png', previewUrl: '/image' }))
      .toBe('image')
    expect(flowAttachmentState({ previewable: true, available: true, contentType: 'application/pdf', previewUrl: '/pdf' }))
      .toBe('pdf')
  })

  it('selects loading, error, empty, and ready panel states in priority order', () => {
    expect(flowPanelState(true, 'failed', 2)).toBe('loading')
    expect(flowPanelState(false, 'failed', 2)).toBe('error')
    expect(flowPanelState(false, '', 0)).toBe('empty')
    expect(flowPanelState(false, '', 2)).toBe('ready')
  })

  it('uses the follow-up-style custom timeline and keeps flow details readable', () => {
    expect(flowHistorySource).not.toContain('<Timeline')
    expect(flowHistorySource).toContain('className="flow-history-node"')
    expect(flowHistorySource).toContain('className="flow-history-card"')
    expect(flowHistorySource).toContain('归属销售')
    expect(flowHistorySource).toContain('客资状态')
    expect(flowHistorySource).toContain('分配状态')
    expect(flowHistorySource).toContain('原因：')
    expect(flowHistorySource).toContain('备注：')
    expect(flowHistorySource).toContain('附件')
    expect(flowHistorySource).toContain('{items.map(item => <FlowItem')
    expect(flowHistorySource).not.toContain('items.sort(')
    expect(flowHistorySource).not.toContain('lead-flow-history-node')
  })

  it('keeps the flow timeline aligned with follow-up card rhythm', () => {
    expect(flowHistoryStyles).toMatch(/\.flow-history-node\s*\{[^}]*grid-template-columns: 24px minmax\(0, 1fr\)/s)
    expect(flowHistoryStyles).toMatch(/\.flow-history-marker::after\s*\{[^}]*background: var\(--crm-border-strong\)/s)
    expect(flowHistoryStyles).toMatch(/\.flow-history-card\s*\{[^}]*box-shadow: var\(--crm-shadow-card\)/s)
    expect(flowHistoryStyles).toMatch(/\.flow-history-change\s*\{[^}]*background: var\(--crm-bg-sunken\)/s)
  })
})
