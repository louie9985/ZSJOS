import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { flowAttachmentState, flowPanelState } from './LeadFlowHistoryPanel'
import flowHistorySource from './LeadFlowHistoryPanel.tsx?raw'

const flowHistoryStyles = readFileSync(new URL('../styles/components/lead-detail-v2.css', import.meta.url), 'utf8')

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

  it('uses the compact custom timeline and keeps all requested flow fields', () => {
    expect(flowHistorySource).not.toContain('<Timeline')
    expect(flowHistorySource).toContain('className="lead-flow-history-node"')
    expect(flowHistorySource).toContain('原归属销售')
    expect(flowHistorySource).toContain('新归属销售')
    expect(flowHistorySource).toContain("{value || '-'}")
    expect(flowHistorySource).not.toContain('不变')
    expect(flowHistorySource).toContain('客资状态变化')
    expect(flowHistorySource).toContain('分配状态变化')
    expect(flowHistorySource).toContain('<span>原因</span>')
    expect(flowHistorySource).toContain('<span>备注</span>')
    expect(flowHistorySource).not.toContain('原因 / 备注')
    expect(flowHistorySource).toContain('附件')
  })

  it('keeps compact fields on one row with labels left and values right', () => {
    expect(flowHistoryStyles).toMatch(/\.lead-flow-history-field\s*\{[^}]*display:\s*flex;/s)
    expect(flowHistoryStyles).toMatch(/\.lead-flow-history-field\s*\{[^}]*justify-content:\s*space-between;/s)
    expect(flowHistoryStyles).toMatch(/\.lead-flow-history-value[\s\S]*text-align:\s*right;/)
    expect(flowHistoryStyles).toMatch(/\.lead-flow-history-transition\s*\{[^}]*justify-content:\s*flex-end;/s)
  })
})
