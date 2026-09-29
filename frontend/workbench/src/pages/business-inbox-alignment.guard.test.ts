import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { expectSourceNotToContainTokens, expectSourceToContainTokens } from '../test/sourceGuard'

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
      expect(source, file).toContain('business-inbox-detail-pane')
    }

    const personnel = readFileSync('src/pages/ManagementPages.tsx', 'utf8')
      .split('export function PersonnelPage')[1]
      .split('export function PartnerPage')[0]
    expect(personnel).toContain('workspace-page business-inbox-page personnel-page')
    expect(personnel).toContain('business-inbox-layout')
    expect(personnel).toContain('business-inbox-detail-pane')
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

    expectSourceToContainTokens(approvalCenter, 'api.bpmBusinessTaskTarget(task.id, view)')
    expectSourceNotToContainTokens(approvalCenter, 'api.salesOrderApprovalTaskTarget(task.id)')
    expect(approvalCenter).toContain('当前账号无权打开该业务审批')
    expect(api).toContain('export type BpmBusinessTaskTarget')
    expect(api).toContain('processDefinitionKey?: string')
    expect(api).toMatch(/bpmBusinessTaskTarget:\s*async\s*\(taskId:\s*string,\s*view:\s*["']todo["']\s*\|\s*["']done["']\)/)
    expect(leadAppeal).toContain("useSearchParams")
    expect(leadAppeal).toContain("appealId")
    expect(leadAppeal).toContain("leadId")
    expect(leadAppeal).toContain("locateTarget")
  })

  it('keeps approval-center todo and done lists on the shared lazy-loading pattern', () => {
    const approvalCenter = readFileSync('src/pages/BpmApprovalCenterPage.tsx', 'utf8')

    expect(approvalCenter).toContain('new IntersectionObserver')
    expectSourceToContainTokens(approvalCenter, 'rootMargin: "240px 0px"')
    expect(approvalCenter).toContain('bpm-approval-load-sentinel')
    expect(approvalCenter).toContain('api.bpmTaskPage(view, {')
    expect(approvalCenter).toContain('pageNo: nextPage')
    expectSourceToContainTokens(approvalCenter, 'name: keyword.trim() || undefined')
    expectSourceToContainTokens(approvalCenter, 'setTasks(current => appendTasks(current, result.list))')
    expect(approvalCenter).not.toContain('<Pagination')
  })

  it('keeps table layouts out of inbox append loading', () => {
    const messageInbox = readFileSync('src/pages/MessageInboxPage.tsx', 'utf8')
    const announcements = readFileSync('src/pages/AnnouncementCenterPage.tsx', 'utf8')
    const appeals = readFileSync('src/pages/LeadAppealPage.tsx', 'utf8')
    const duplicateReviews = readFileSync('src/pages/LeadDuplicateReviewPage.tsx', 'utf8')

    // 消息中心的游标加载收进共享 hook，页面只保留哨兵接线与表格模式短路。
    expectSourceToContainTokens(messageInbox, 'if (useTableLayout || !node || !feed.hasMore || feed.loading || feed.loadingMore) return')
    expectSourceToContainTokens(announcements, 'if (useTableLayout || !node || !hasMore || loading || loadingMore) return')
    expectSourceToContainTokens(appeals, 'if (useTableLayout || !node || !hasMore || loading || loadingMore || !cursor) return')
    expect(duplicateReviews).toContain('loadedPageRef.current + 1')
    expectSourceToContainTokens(duplicateReviews, 'setItems(current => useTableLayout || !append')
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

  it('keeps finance export available in both approval layouts', () => {
    const approval = readFileSync('src/pages/SalesOrderApprovalPage.tsx', 'utf8')
    expect(approval.match(/exportFinanceOrders/g)?.length).toBeGreaterThanOrEqual(3)
    expect(approval).toContain("permissions.includes('zsjos:export:finance-order')")
  })

  it('contains long message content and aligns message metadata', () => {
    const messageInbox = readFileSync('src/pages/MessageInboxPage.tsx', 'utf8')
    const feed = readFileSync('src/services/useNotifyMessageFeed.ts', 'utf8')
    const styles = readFileSync('src/styles/pages/message-inbox.css', 'utf8')
    const sharedStyles = readFileSync('src/styles/components/business-inbox.css', 'utf8')

    expect(messageInbox).toContain('IntersectionObserver')
    // 游标参数构造与加载收在共享 hook，弹窗与列表页共用同一份。
    expect(feed).toContain('buildNotifyMessageCursorParams')
    expect(messageInbox).toContain('api.myNotifyMessagePage')
    expect(messageInbox).toContain('pagination={{ current: tablePage')
    // 表格模式改为裸 BusinessTable：去掉仅用于撑高的包裹层后，共享层
    // `.business-inbox-table-page > .business-inbox-table{flex:1}` 的直接子选择器才能生效。
    expect(messageInbox).not.toContain('message-inbox-table-shell')
    expect(messageInbox).toContain('business-inbox-mobile-drawer')
    expect(messageInbox).toContain('message-inbox-load-more')
    expect(messageInbox).toContain('BusinessTable')
    expect(messageInbox).toContain('columnsState')

    expect(styles).toMatch(/\.message-center-item \{[^}]*flex: none;/)
    expect(styles).toMatch(/\.message-center-item-copy > \.message-center-item-summary \{[^}]*overflow-wrap: anywhere;[^}]*word-break: break-word;[^}]*line-clamp: 2;/)
    expect(styles).toMatch(/\.message-inbox-detail \.message-detail-section \.ant-typography \{[^}]*word-break: break-word;/)
    // 列表独立；统一底色的详情面板内部正文九列、状态三列。
    expect(styles).toMatch(/\.message-detail-layout \{[^}]*grid-template-columns: repeat\(12, minmax\(0, 1fr\)\)/)
    expect(styles).toMatch(/\.message-detail-layout > \.message-detail-main,[^}]*grid-column: span 9/)
    expect(styles).toMatch(/\.message-detail-layout > \.message-detail-side \{[^}]*grid-column: span 3/)
    expect(messageInbox).not.toContain('message-inbox-content-grid')
    expect(sharedStyles).toMatch(/\.business-inbox-detail-pane \{[^}]*background:/)
    expect(styles).not.toContain('--message-detail-side-w')
    expect(styles).toContain('@container message-detail (width < 700px)')
    const detail = readFileSync('src/components/MessageDetail.tsx', 'utf8')
    expect(detail).not.toContain('DetailFieldGrid')
    expect(detail).not.toContain('business-inbox-card message-detail-side')
    expect(detail).toContain('<dl className="message-status-fields">')
    expect(messageInbox).toContain('<main className="business-inbox-detail-pane"><MessageDetail {...detailProps} layout="pane"/></main>')
    expect(detail).toContain("props.layout === 'pane' ? 'message-detail-pane-content' : 'message-detail-drawer-content'")
    // 表格行状态已提升到共享层，与列表项的 .business-inbox-item 语义对齐。
    expect(sharedStyles).toMatch(/\.business-inbox-table \.ant-table-tbody > tr\.active > td \{/)
    expect(sharedStyles).toMatch(/\.business-inbox-table \.ant-table-tbody > tr\.unread > td \{/)
    expect(sharedStyles).toMatch(/\.business-inbox-item\.unread \{/)

    // 「标签左、值右」与长值换行已提升为 DetailFieldGrid 的基础样式，
    // 此处的页面级覆盖随之删除（见 styles.guard.test.ts 的组件断言）。
    // 靠右用 grid 而非 text-align，否则回行的尾巴会被甩到右边。
    const detailFields = readFileSync('src/styles/components/detail-field-grid.css', 'utf8')
    expect(detailFields).toMatch(/\.detail-field dt \{[^}]*text-align: left;/)
    expect(detailFields).toMatch(/\.detail-field dd \{[\s\S]*?justify-items: end;[\s\S]*?word-break: break-word;/)
  })
})
