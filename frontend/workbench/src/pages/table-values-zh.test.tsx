import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import type { ComponentProps } from 'react'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../components/OverlayCoordinator'
import LeadManagementPage from './LeadManagementPage'
import BusinessTable from '../components/BusinessTable'
import { buildSalesOrderTableColumns } from '../components/SalesOrderTableColumns'
import type { SalesOrderListItem } from '../services/api'
import { BusinessAuditPage } from './ManagementPages'
import { leadRelationTypesLabel, leadSnapshotLabel } from '../services/leadManagement'
import { exportTypeLabel, exportStatusLabel } from '../services/exportTaskDisplay'
import { workPlanPeriodLabel, workPlanTemplateStatusLabel } from '../services/workPlanDisplay'
import { subordinateTaskTypeLabel } from '../services/subordinateSales'
import { auditSourceLabel, auditCategoryLabel, auditResultLabel, notifyChannelLabel, notifyRecipientLabels, relationActionLabel } from '../services/managementDisplay'

const fixture = vi.hoisted(() => ({ rows: [] as object[] }))
vi.mock('../services/inboxLayout', () => ({ useInboxTableLayout: () => ({ isDesktop: true, useTableLayout: true }) }))
vi.mock('../components/RealtimeProvider', () => ({ useRealtimeEvent: () => undefined }))
vi.mock('../components/BusinessTable', async importOriginal => {
  const actual = await importOriginal<typeof import('../components/BusinessTable')>()
  return { default: (props: ComponentProps<typeof BusinessTable> & { tableKey: string }) =>
    <actual.default {...props} dataSource={fixture.rows} pagination={false} /> }
})

describe('table business values in Chinese', () => {
  it('renders actual Lead columns using stored labels and translated relationships', () => {
    fixture.rows = [{ id: 1, leadNo: 'TEST-LEAD', submittedName: '测试客资', sourceType: 'partner', sourceLabel: '兼职提交',
      leadCategory: 'legacy_category', leadCategoryLabelSnapshot: '历史分类', sourceChannel: 'legacy_channel', sourceChannelLabelSnapshot: '历史渠道',
      invalidReason: 'old_reason', relationTypes: ['submitter', 'owner'], operationalStatus: 'future_status' }]
    const html = renderToStaticMarkup(<MemoryRouter><ThemeProvider><OverlayCoordinatorProvider>
      <LeadManagementPage permissions={[]} />
    </OverlayCoordinatorProvider></ThemeProvider></MemoryRouter>)
    for (const label of ['历史分类', '历史渠道', '提交人 / 负责人', '未知状态', '历史标签缺失', '兼职提交']) expect(html).toContain(label)
    for (const code of ['legacy_category', 'legacy_channel', 'future_status', 'old_reason']) expect(html).not.toContain(code)
  })

  it('renders the actual audit columns while retaining diagnostic codes', () => {
    fixture.rows = [{ id: 1, sourceType: 'ADMIN', categoryCode: 'sensitive_read', resultStatus: 'SUCCESS',
      actionCode: 'read_test_record', targetType: 'test-record', requestMethod: 'GET', requestPath: '/test/record', traceId: 'TEST-TRACE' }]
    const html = renderToStaticMarkup(<BusinessAuditPage permissions={['zsjos:audit:query']} />)
    for (const label of ['管理端', '敏感读取', '成功', 'read_test_record', 'test-record', 'TEST-TRACE']) expect(html).toContain(label)
    for (const code of ['>ADMIN<', '>SUCCESS<', '>sensitive_read<']) expect(html).not.toContain(code)
  })

  it('renders order unknown values safely and retains a historical label through real ProTable', () => {
    fixture.rows = [{ id: 1, orderNo: 'TEST-ORDER', orderType: 'future_type', status: 'future_status',
      supervisorConfirmationStatus: 'future_supervisor', leadCategoryLabelSnapshot: '历史分类名称' }]
    const html = renderToStaticMarkup(<BusinessTable<SalesOrderListItem> tableKey="chinese-order-test" rowKey="id" columns={buildSalesOrderTableColumns(() => {})} />)
    for (const label of ['未知类型', '未知状态', '历史分类名称']) expect(html).toContain(label)
    for (const code of ['future_type', 'future_status', 'future_supervisor']) expect(html).not.toContain(code)
  })

  it('renders native values without converting ProTable ellipsis nodes', () => {
    fixture.rows = [{ id: 1, type: 'finance_order', task: 'lead_first_follow_up', period: 'quarter', relations: ['submitter', 'owner'] }]
    type Row = { id: number; type: string; task: string; period: string; relations: string[] }
    const html = renderToStaticMarkup(<BusinessTable<Row> tableKey="chinese-native-test" columnMode="native" mode="compact" rowKey="id" columns={[
      { title: '类型', dataIndex: 'type', render: (_, row) => exportTypeLabel(row.type) },
      { title: '任务', render: (_, row) => subordinateTaskTypeLabel(row.task) },
      { title: '周期', render: (_, row) => workPlanPeriodLabel(row.period) },
      { title: '关系', render: (_, row) => leadRelationTypesLabel(row.relations) },
    ]} />)
    for (const label of ['财务订单', '首次跟进', '季度', '提交人 / 负责人']) expect(html).toContain(label)
    expect(html).not.toContain('[object Object]')
  })

  it('never invents historical labels or changes the stored codes', () => {
    const row = Object.freeze({ code: 'old_category', snapshot: '原分类名称' })
    expect(leadSnapshotLabel(row.snapshot, row.code)).toBe('原分类名称')
    expect(leadSnapshotLabel(undefined, row.code)).toBe('历史标签缺失')
    expect(leadSnapshotLabel('English label entered by admin', row.code)).toBe('English label entered by admin')
    expect(leadSnapshotLabel(undefined, undefined)).toBe('—')
    expect(row.code).toBe('old_category')
    expect(leadRelationTypesLabel(['student_service_owner', 'future_role'])).toBe('学员服务负责人 / 未知关系')
    expect(leadRelationTypesLabel([])).toBe('—')
  })

  it.each([
    [exportTypeLabel, 'cashback', '返现', '未知类型'], [exportStatusLabel, 'queued', '排队中', '未知状态'],
    [workPlanPeriodLabel, 'custom', '自定义', '未知周期'], [workPlanTemplateStatusLabel, 'disabled', '已停用', '未知状态'],
    [subordinateTaskTypeLabel, 'lead_follow_up_reminder', '跟进提醒', '未知类型'],
    [auditSourceLabel, 'PUBLIC_CALLBACK', '公开回调', '未知来源'], [auditCategoryLabel, 'business', '业务操作', '未知类别'],
    [auditResultLabel, 'FAILURE', '失败', '未知状态'], [notifyChannelLabel, 'wecom', '企业微信', '未知渠道'],
    [relationActionLabel, 'replace', '替换', '未知操作'],
  ] as const)('maps a fixed protocol with empty and forward-compatible fallback (%s)', (format, code, label, unknown) => {
    expect(format(code)).toBe(label)
    expect(format('future_code')).toBe(unknown)
    expect(format('constructor')).toBe(unknown)
    expect(format(undefined)).toBe('—')
  })

  it('uses the scene API for recipient names, distinguishes loading/failure/missing metadata', () => {
    const rule = { sceneCode: 'test', recipientRoles: ['owner'] }
    const scenes = [{ code: 'test', name: '测试场景', recipientRoles: [{ code: 'owner', name: '服务负责人' }], allowedActions: [] }]
    expect(notifyRecipientLabels(rule, [], true, false)).toBe('角色名称加载中')
    expect(notifyRecipientLabels(rule, [], false, true)).toBe('角色名称加载失败')
    expect(notifyRecipientLabels(rule, scenes, false, false)).toBe('服务负责人')
    expect(notifyRecipientLabels(rule, [], false, false)).toBe('角色名称未配置')
    expect(rule.recipientRoles).toEqual(['owner'])
  })
})
