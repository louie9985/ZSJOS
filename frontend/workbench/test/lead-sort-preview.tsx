// UTF-8. Preview-only synthetic transport; no production requests or business writes.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Empty, Space, Tag, Typography } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { AdvancedFilterToolbar } from '../src/components/AdvancedFilter'
import { InboxAvatarControls } from '../src/components/InboxAvatarRail'
import { NameAvatar } from '../src/components/LeadDetailOverview'
import { http, type LeadSortField, type AdvancedFilterGroup } from '../src/services/api'
import '../src/styles/index.css'
import './lead-sort-preview.css'
import LeadInboxSortControls from '../src/components/LeadInboxSortControls'

http.defaults.adapter = async config => {
  if (!config.url?.endsWith('/advanced-filter/catalog')) throw new Error('Preview blocks unexpected requests')
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data: { fields: [
    { fieldKey: 'submitter', label: '员工提交人', group: '归属信息', valueType: 'enum', operators: ['in', 'not_in'], options: [{ value: 'demo', label: '示例员工' }, { value: 'other', label: '其他示例员工' }] }
  ], relativeDateOptions: [] } } }
}
const records = [
  { name: '示例客户 A', no: 'KZ20261028003', date: '2026-10-28 10:32', submitter: 'demo' },
  { name: '示例客户 B', no: 'KZ20261028002', date: '2026-10-28 09:18', submitter: 'demo' },
  { name: '示例客户 C', no: 'KZ20261027001', date: '2026-10-27 16:45', submitter: 'other' }
]
function Preview() {
  const [filter, setFilter] = useState<AdvancedFilterGroup | undefined>({ logic: 'AND', conditions: [{ fieldKey: 'submitter', operator: 'in', value: ['demo'] }], groups: [] })
  const [keyword, setKeyword] = useState('')
  const [field, setField] = useState<LeadSortField | undefined>('lastActivityAt')
  const [order, setOrder] = useState<'ascend' | 'descend' | undefined>('descend')
  const [collapsed, setCollapsed] = useState(false)
  const [selected, setSelected] = useState(records[0].no)
  const rows = records.filter(r => (!keyword || `${r.name}${r.no}`.includes(keyword)) && (filter?.conditions || []).every(c => {
    const contains = (Array.isArray(c.value) ? c.value : [c.value]).includes(r.submitter)
    return c.operator === 'not_in' ? !contains : contains
  }))
  if (field && order === 'ascend') rows.reverse()
  return <main className="sort-preview">
    <Typography.Text type="secondary">中世健 · 客资主从视图预览（示例数据）</Typography.Text>
    <section className={`preview-pane ${collapsed ? 'collapsed' : ''}`}>
      <div className="preview-search">
        <InboxAvatarControls label="客资" listId="preview-leads" collapsed={collapsed} filtered={Boolean(filter || keyword)} onChange={setCollapsed} />
        {!collapsed && <AdvancedFilterToolbar scene="lead" placeholder="搜索客资…" keyword={keyword} onKeyword={setKeyword} value={filter} onChange={setFilter} />}
      </div>
      {!collapsed && <>
        <LeadInboxSortControls field={field} order={order} onChange={(nextField, nextOrder) => { setField(nextField); setOrder(nextOrder) }} />
        <div className="preview-count" aria-live="polite"><span>{filter || keyword ? '筛选结果' : '全部客资'} <strong>{rows.length}</strong> 条</span><Typography.Text type="secondary">已加载 {rows.length} 条</Typography.Text></div>
      </>}
      <div id="preview-leads">{rows.map(r => <button key={r.no} className={`lead-inbox-item ${selected === r.no ? 'is-active' : ''}`} onClick={() => setSelected(r.no)}>
        <NameAvatar name={r.name} seed={r.no} size={36} />
        {!collapsed && <><div className="preview-lead-content"><Typography.Text strong>{r.name}</Typography.Text><Typography.Text type="secondary">{r.no}</Typography.Text><Typography.Text type="secondary">最近活动 {r.date}</Typography.Text></div><Tag color="blue">待跟进</Tag></>}
      </button>)}{!rows.length && <Empty description="当前筛选下暂无客资" />}</div>
      {!collapsed && rows.length > 0 && <Space className="preview-end"><Typography.Text type="secondary">已加载全部 {rows.length} 条客资</Typography.Text></Space>}
    </section>
  </main>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><OverlayCoordinatorProvider><Preview /></OverlayCoordinatorProvider></App></ThemeProvider>)
