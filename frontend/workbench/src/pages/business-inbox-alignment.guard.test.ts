import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const inboxSources = [
  'MessageInboxPage.tsx',
  'LeadQualificationExceptionPage.tsx',
  'LeadAppealPage.tsx',
  'LeadComplaintPage.tsx',
  'SalesOrderApprovalPage.tsx'
].map(file => [file, readFileSync(`src/pages/${file}`, 'utf8')] as const)

describe('business inbox alignment', () => {
  it('uses the shared full-width master-detail skeleton', () => {
    for (const [file, source] of inboxSources) {
      expect(source, file).toContain('workspace-page business-inbox-page')
      expect(source, file).toContain('business-inbox-layout')
      expect(source, file).toContain('<main className="business-inbox-detail-pane">')
    }

    const personnel = readFileSync('src/pages/ManagementPages.tsx', 'utf8')
      .split('export function PersonnelPage')[1]
      .split('export function PartnerPage')[0]
    expect(personnel).toContain('workspace-page business-inbox-page personnel-page')
    expect(personnel).toContain('business-inbox-layout')
    expect(personnel).toContain('<main className="business-inbox-detail-pane">')
  })

  it('keeps list-and-detail pages out of table mode', () => {
    for (const file of ['LeadQualificationExceptionPage.tsx', 'LeadComplaintPage.tsx']) {
      const source = readFileSync(`src/pages/${file}`, 'utf8')
      expect(source, file).not.toContain('<Table')
      expect(source, file).not.toContain('workspace-page-heading')
    }
  })

  it('exposes refresh actions on confirmed business inboxes', () => {
    for (const [file, source] of inboxSources) {
      if (['MessageInboxPage.tsx', 'SalesOrderApprovalPage.tsx'].includes(file))
        expect(source, file).toContain('ReloadOutlined')
    }

    const supervisor = readFileSync('src/components/SalesOrderSupervisorInbox.tsx', 'utf8')
    expect(supervisor).toContain('ReloadOutlined')
    const personnel = readFileSync('src/pages/ManagementPages.tsx', 'utf8')
      .split('export function PersonnelPage')[1]
      .split('export function PartnerPage')[0]
    expect(personnel).not.toMatch(/>刷新<\/Button>/)
  })

  it('contains long message content and aligns message metadata', () => {
    const styles = readFileSync('src/styles/pages/message-inbox.css', 'utf8')

    expect(styles).toMatch(/\.message-center-item \{[^}]*flex: none;/)
    expect(styles).toMatch(/\.message-center-item-copy > \.message-center-item-summary \{[^}]*overflow-wrap: anywhere;[^}]*word-break: break-word;[^}]*-webkit-line-clamp: 2;/)
    expect(styles).toMatch(/\.message-inbox-detail \.message-detail-section \.ant-typography \{[^}]*word-break: break-word;/)
    expect(styles).toMatch(/\.message-detail-meta \.detail-field dt \{[^}]*text-align: left;/)
    expect(styles).toMatch(/\.message-detail-meta \.detail-field dd \{[^}]*text-align: right;[^}]*word-break: break-word;/)
  })
})
