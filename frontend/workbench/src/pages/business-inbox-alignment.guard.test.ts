import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const inboxSources = [
  'MessageInboxPage.tsx',
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
    for (const file of ['LeadComplaintPage.tsx']) {
      const source = readFileSync(`src/pages/${file}`, 'utf8')
      expect(source, file).not.toContain('<Table')
      expect(source, file).not.toContain('workspace-page-heading')
    }
  })

  it('routes approval-center business entry through the unified BPM target resolver', () => {
    const approvalCenter = readFileSync('src/pages/BpmApprovalCenterPage.tsx', 'utf8')
    const leadAppeal = readFileSync('src/pages/LeadAppealPage.tsx', 'utf8')
    const api = readFileSync('src/services/api.ts', 'utf8')

    expect(approvalCenter).toContain('api.bpmBusinessTaskTarget(task.id, view)')
    expect(approvalCenter).not.toContain('api.salesOrderApprovalTaskTarget(task.id)')
    expect(approvalCenter).toContain('当前账号无权打开该业务审批')
    expect(api).toContain('export type BpmBusinessTaskTarget')
    expect(api).toContain('processDefinitionKey?: string')
    expect(api).toContain('bpmBusinessTaskTarget: async (taskId: string, view: "todo" | "done")')
    expect(leadAppeal).toContain("useSearchParams")
    expect(leadAppeal).toContain("appealId")
    expect(leadAppeal).toContain("leadId")
    expect(leadAppeal).toContain("locateTarget")
  })

  it('keeps approval-center todo and done lists on the shared lazy-loading pattern', () => {
    const approvalCenter = readFileSync('src/pages/BpmApprovalCenterPage.tsx', 'utf8')

    expect(approvalCenter).toContain('new IntersectionObserver')
    expect(approvalCenter).toContain('rootMargin: "240px 0px"')
    expect(approvalCenter).toContain('bpm-approval-load-sentinel')
    expect(approvalCenter).toContain('api.bpmTaskPage(view, { pageNo: nextPage, pageSize: PAGE_SIZE })')
    expect(approvalCenter).toContain('setTasks(current => appendTasks(current, result.list))')
    expect(approvalCenter).not.toContain('<Pagination')
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
    const messageInbox = readFileSync('src/pages/MessageInboxPage.tsx', 'utf8')
    const styles = readFileSync('src/styles/pages/message-inbox.css', 'utf8')

    expect(messageInbox).toContain('IntersectionObserver')
    expect(messageInbox).toContain('buildNotifyMessageCursorParams(view, append ? cursor : undefined, CURSOR_LIMIT)')
    expect(messageInbox).toContain('message-inbox-load-more')
    expect(messageInbox).not.toContain('Pagination')

    expect(styles).toMatch(/\.message-center-item \{[^}]*flex: none;/)
    expect(styles).toMatch(/\.message-center-item-copy > \.message-center-item-summary \{[^}]*overflow-wrap: anywhere;[^}]*word-break: break-word;[^}]*-webkit-line-clamp: 2;/)
    expect(styles).toMatch(/\.message-inbox-detail \.message-detail-section \.ant-typography \{[^}]*word-break: break-word;/)

    // 「标签左、值右」与长值换行已提升为 DetailFieldGrid 的基础样式，
    // 此处的页面级覆盖随之删除（见 styles.guard.test.ts 的组件断言）。
    // 靠右用 grid 而非 text-align，否则回行的尾巴会被甩到右边。
    const detailFields = readFileSync('src/styles/components/detail-field-grid.css', 'utf8')
    expect(detailFields).toMatch(/\.detail-field dt \{[^}]*text-align: left;/)
    expect(detailFields).toMatch(/\.detail-field dd \{[\s\S]*?justify-items: end;[\s\S]*?word-break: break-word;/)
  })
})
