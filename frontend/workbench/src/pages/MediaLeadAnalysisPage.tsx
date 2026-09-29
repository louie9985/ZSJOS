import { Alert, Button, Card, Drawer, Empty, Input, Modal, Progress, Space, Spin, Tag, Tooltip, Tree, Typography } from 'antd'
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { DataNode } from 'antd/es/tree'
import { mediaLeadApi, percent, type CalendarDay, type Detail, type Group, type Member, type Overview, type Query, type ScopeNode } from '../services/mediaLeadAnalysis'
import { ApiError, AuthenticationError } from '../services/api'
import { formatTimestamp } from '../services/time'
import BusinessTable from '../components/BusinessTable'
import './performance/performance.css'

const date = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
const monthRange = (month: string): [string, string] => [month + '-01', month + '-' + new Date(Number(month.slice(0, 4)), Number(month.slice(5)), 0).getDate()]
function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) { return <Card size="small"><Typography.Text type="secondary">{label}</Typography.Text><Typography.Title level={3} style={{ margin: '8px 0 0' }}>{value}</Typography.Title>{hint && <Typography.Text type="secondary">{hint}</Typography.Text>}</Card> }
function MemberMetricTags({ submitted, valid, converted }: { submitted: number; valid?: number; converted: number }) {
  return <Space size={4} wrap className="media-lead-metric-tags">
    <Tag color="processing" style={{ marginInlineEnd: 0 }}>提交 {submitted}</Tag>
    {valid !== undefined && <Tag color="success" style={{ marginInlineEnd: 0 }}>有效 {valid}</Tag>}
    <Tag color="warning" style={{ marginInlineEnd: 0 }}>成交 {converted}</Tag>
  </Space>
}
const pageSizes = [20, 50, 100]
const slashFreePagination = {
  pageSize: 20,
  pageSizeOptions: pageSizes,
  showSizeChanger: { options: pageSizes.map(value => ({ value, label: value + ' 条每页' })) },
  showTotal: (total: number, [start, end]: [number, number]) => '第 ' + start + '–' + end + ' 条，共 ' + total + ' 条'
}
const colors = ['#1677ff', '#13c2c2', '#faad14', '#722ed1', '#eb2f96', '#52c41a', '#fa541c', '#2f54eb']
function periodRange(key: string, today: string): [string, string] | undefined {
  const current = new Date(today + 'T00:00:00+08:00')
  const iso = (value: Date) => value.toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
  const add = (days: number) => { const value = new Date(current); value.setDate(value.getDate() + days); return iso(value) }
  if (key === 'yesterday') return [add(-1), add(-1)]
  if (key === 'today') return [today, today]
  if (key === 'lastWeek' || key === 'week') {
    const monday = new Date(current); monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7))
    const start = key === 'lastWeek' ? new Date(monday.getTime() - 7 * 86400000) : monday
    const end = key === 'lastWeek' ? new Date(monday.getTime() - 86400000) : current
    return [iso(start), iso(end)]
  }
  if (key === 'lastTwoMonth' || key === 'lastMonth' || key === 'month') {
    const offset = key === 'lastTwoMonth' ? -2 : key === 'lastMonth' ? -1 : 0
    const start = new Date(current.getFullYear(), current.getMonth() + offset, 1)
    const end = key === 'month' ? current : new Date(current.getFullYear(), current.getMonth() + offset + 1, 0)
    return [iso(start), iso(end)]
  }
  if (key === 'year') return [today.slice(0, 4) + '-01-01', today]
  return key === 'all' ? undefined : [today.slice(0, 7) + '-01', today]
}
function Pie({ title, rows }: { title: string; rows: Group[] }) {
  const total = rows.reduce((sum, row) => sum + row.count, 0)
  let offset = 0
  const slices = rows.map((row, index) => { const from = offset; offset += row.count / total * 100; return `${colors[index % colors.length]} ${from}% ${offset}%` })
  return <Card size="small" title={title} style={{ minWidth: 250, flex: '1 1 250px' }}>{total === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无有效客资" /> : <Space align="start"><div role="img" aria-label={`${title}，共 ${total} 条`} style={{ width: 128, height: 128, flexShrink: 0, borderRadius: '50%', background: `conic-gradient(${slices.join(',')})` }} /><div>{rows.map((row, index) => <div key={row.label} style={{ whiteSpace: 'nowrap' }}><span style={{ display: 'inline-block', width: 9, height: 9, marginRight: 6, background: colors[index % colors.length] }} />{row.label} {row.count}（{percent(row.count / total)}）</div>)}</div></Space>}</Card>
}

function CalendarRing({ day }: { day: CalendarDay }) {
  const segments = [{ count: day.valid, color: '#397bee' }, { count: day.invalid, color: '#a8adb7' }, { count: day.pending, color: '#eebf45' }]
  let offset = 0
  return <svg viewBox="0 0 64 64" role="img" aria-label={'提交' + day.total + '，有效' + day.valid + '，无效' + day.invalid + '，待判或其他' + day.pending}>
    <circle cx="32" cy="32" r="24" fill="none" stroke="var(--crm-border)" strokeWidth="7" />
    {segments.map((segment, index) => {
      const length = day.total ? segment.count / day.total * 150.8 : 0
      const previous = offset; offset += length
      return <circle key={index} cx="32" cy="32" r="24" fill="none" stroke={segment.color} strokeWidth="7" strokeDasharray={length + ' ' + (150.8 - length)} strokeDashoffset={-previous} transform="rotate(-90 32 32)" />
    })}
    <text x="32" y="37" textAnchor="middle" fill="currentColor">{day.total}</text>
  </svg>
}

function MonthlyCohort({ data, month, canDetail, onDay }: { data: Overview; month: string; canDetail: boolean; onDay: (day: string) => void }) {
  const rows = data.funnel ? [{ key: 'submitted', label: '提交客资', count: data.funnel.submitted }, { key: 'valid', label: '判有效客资', count: data.funnel.valid }, { key: 'converted', label: '成交客资', count: data.funnel.converted }] : []
  const max = Math.max(1, ...rows.map(row => row.count))
  const days = new Map(data.calendar.map(day => [day.date, day]))
  const dayCount = new Date(Number(month.slice(0, 4)), Number(month.slice(5)), 0).getDate()
  const leading = (new Date(month + '-01T12:00:00').getDay() + 6) % 7
  return <div className="performance-cohort">
    <div><h4>提交批次转化漏斗</h4>{!data.funnel ? <Alert type="warning" showIcon title="月度漏斗暂不可用" description="请更新后端服务后重试，判定日历仍可查看。" /> : data.funnel.submitted === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="所选月份暂无提交客资" /> : <div className="performance-funnel">{rows.map((row, index) => {
      const top = 190 * row.count / max, bottom = 190 * (rows[index + 1]?.count ?? row.count) / max
      return <div className="performance-funnel-row" key={row.key}><div><strong>{row.label} {row.count}</strong><span>{index ? '较上一步 ' + percent(rows[index - 1].count ? row.count / rows[index - 1].count : null) : '所选月提交批次'}</span></div><svg viewBox="0 0 400 50" role="img" aria-label={row.label + ' ' + row.count}><polygon points={[200 - top, 0, 200 + top, 0, 200 + bottom, 48, 200 - bottom, 48].join(' ')} fill={['var(--ant-color-primary)', 'var(--ant-color-success)', 'var(--ant-color-warning)'][index]} /></svg></div>
    })}</div>}<p className="performance-subtle">按所选月份的提交批次统计；成交为其中已判有效且截至查询时已有生效首购的去重客资。</p></div>
    <div><h4>提交日期与判定情况</h4><div className="performance-legend"><span style={{ color: '#397bee' }}>● 有效</span><span style={{ color: '#a8adb7' }}>● 无效</span><span style={{ color: '#eebf45' }}>● 待判或其他</span><span>每日环形图中央为提交客资数</span></div>
      <div className="performance-calendar">{['一', '二', '三', '四', '五', '六', '日'].map(weekday => <strong key={weekday}>周{weekday}</strong>)}{Array.from({ length: leading }, (_, index) => <span key={'pad' + index} />)}{Array.from({ length: dayCount }, (_, index) => {
        const dayKey = month + '-' + String(index + 1).padStart(2, '0'), day = days.get(dayKey)
        return day ? <Tooltip key={dayKey} title={'提交' + day.total + ' · 有效' + day.valid + ' · 无效' + day.invalid + ' · 待判或其他' + day.pending}><button className="performance-day" disabled={!canDetail} onClick={() => onDay(dayKey)}><span>{index + 1}</span><CalendarRing day={day} /></button></Tooltip> : <div className="performance-day" key={dayKey} aria-label={dayKey + '尚未统计'}><span>{index + 1}</span><span className="performance-subtle">—</span></div>
      })}</div><p className="performance-subtle">判定使用客资当前状态；未来日期不统计，也不推算逾期未判。</p></div>
  </div>
}

function detailRequestMessage(cause: unknown) {
  if (cause instanceof AuthenticationError || cause instanceof ApiError && cause.code === 401) return '登录已失效，请重新登录后再查看客资明细。'
  if (cause instanceof ApiError && cause.code === 403) return '当前账号没有“查看新媒体客资明细”权限，请联系管理员配置后重试。'
  if (cause instanceof ApiError && cause.code === 404) return '当前后端尚未提供客资明细接口，请更新并重启后端服务后重试。'
  return cause instanceof Error ? cause.message : '客资明细加载失败，请稍后重试。'
}

function DetailModal({ open, title, loading, error, rows, page, pageSize, total, onChange, onRetry, onClose }: { open: boolean; title: string; loading: boolean; error: string; rows: Detail[]; page: number; pageSize: number; total: number; onChange: (page: number, size: number) => void; onRetry: () => void; onClose: () => void }) {
  return <Modal title={title} open={open} width={1080} footer={null} onCancel={onClose}>{loading ? <Spin /> : error ? <Alert type="error" showIcon title={error} action={<Button onClick={onRetry}>重试</Button>} /> : rows.length === 0 && total === 0 ? <Empty description="暂无客资明细" /> : <BusinessTable tableKey="media-lead-details" mode="compact" columnMode="native" rowKey="leadNo" scroll={{ x: 950 }} pagination={{ ...slashFreePagination, current: page, pageSize, total, onChange }} dataSource={rows} columns={[{ title: '客资编号', dataIndex: 'leadNo' }, { title: '提交时间', dataIndex: 'submittedAt', render: (v: Detail['submittedAt']) => formatTimestamp(v, '—', 'second') }, { title: '冻结引流贡献人', dataIndex: 'contributorName' }, { title: '判定', dataIndex: 'statusLabel' }, { title: '来源渠道', dataIndex: 'channelLabel' }, { title: '客资分类', dataIndex: 'categoryLabel' }, { title: '成交', render: (_: unknown, row: Detail) => row.converted ? <Tag color="success">已成交</Tag> : <Tag>未成交</Tag> }, { title: '首购生效时间', dataIndex: 'orderEffectiveAt', render: (v: Detail['orderEffectiveAt']) => formatTimestamp(v, '—', 'second') }]} />}</Modal>
}

export default function MediaLeadAnalysisPage({ permissions }: { permissions: string[] }) {
  const [nodes, setNodes] = useState<ScopeNode[]>([]), [scope, setScope] = useState<ScopeNode>(), [data, setData] = useState<Overview>(), [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [month, setMonth] = useState(() => date().slice(0, 7))
  const requestId = useRef(0)
  const [keyword, setKeyword] = useState(''), [collapsed, setCollapsed] = useState(false), [drawerOpen, setDrawerOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false), [detailTitle, setDetailTitle] = useState(''), [detailRows, setDetailRows] = useState<Detail[]>([]), [detailLoading, setDetailLoading] = useState(false), [detailError, setDetailError] = useState('')
  const [detailQuery, setDetailQuery] = useState<Query>()
  const [detailPage, setDetailPage] = useState(1), [detailPageSize, setDetailPageSize] = useState(20), [detailTotal, setDetailTotal] = useState(0)
  const detailRequest = useRef<AbortController | undefined>(undefined)
  useEffect(() => () => detailRequest.current?.abort(), [])
  const closeDetails = () => { detailRequest.current?.abort(); setDetailOpen(false) }
  const loadDetails = async (query: Query, page: number, size: number) => {
    detailRequest.current?.abort()
    const controller = new AbortController()
    detailRequest.current = controller
    setDetailPage(page); setDetailPageSize(size); setDetailRows([]); setDetailLoading(true); setDetailError('')
    try {
      const result = await mediaLeadApi.detailPage({ ...query, pageNo: page, pageSize: size }, controller.signal)
      if (!controller.signal.aborted) { setDetailRows(result.list); setDetailTotal(result.total) }
    } catch (cause) {
      if (!controller.signal.aborted) setDetailError(detailRequestMessage(cause))
    } finally { if (!controller.signal.aborted) setDetailLoading(false) }
  }
  const load = async (selected?: ScopeNode, selectedMonth = month) => {
    closeDetails()
    const request = ++requestId.current
    setLoading(true); setError('')
    try {
      const tree = await mediaLeadApi.tree()
      if (request !== requestId.current) return
      setNodes(tree)
      const next = tree.find(node => node.key === selected?.key) ?? tree[0]
      setScope(next)
      if (!next) { setData(undefined); return }
      const [start, end] = monthRange(selectedMonth)
      const query: Query = { scopeType: next.scopeType, scopeId: next.scopeId, start, end }
      const overview = await mediaLeadApi.overview(query)
      if (request === requestId.current) setData(overview)
    } catch (cause) {
      if (request === requestId.current) { setData(undefined); setError(cause instanceof Error ? cause.message : '加载失败，请重试') }
    } finally { if (request === requestId.current) setLoading(false) }
  }
  useEffect(() => { if (permissions.includes('zsjos:media-lead-analysis:query')) void load() }, [])
  const treeData = useMemo(() => {
    const byKey = new Map(nodes.map(node => [node.key, node]))
    const visible = new Set<string>()
    for (const node of nodes) {
      if (keyword && !node.title.includes(keyword)) continue
      let current: ScopeNode | undefined = node
      while (current && !visible.has(current.key)) {
        visible.add(current.key)
        current = current.parentKey ? byKey.get(current.parentKey) : undefined
      }
    }
    const build = (parentKey?: string): DataNode[] => nodes
      .filter(node => visible.has(node.key) && (parentKey ? node.parentKey === parentKey : !node.parentKey || !byKey.has(node.parentKey)))
      .map(node => ({ key: node.key, title: node.title, children: build(node.key) }))
    return build()
  }, [nodes, keyword])
  if (!permissions.includes('zsjos:media-lead-analysis:query')) return <Alert type="warning" showIcon title="暂无新媒体客资分析权限" />
  const openDetails = async (title: string, range: [string, string]) => {
    if (!scope || !permissions.includes('zsjos:media-lead-analysis:detail')) return
    const query: Query = { scopeType: scope.scopeType, scopeId: scope.scopeId, start: range[0], end: range[1] }
    setDetailOpen(true); setDetailTitle(title); setDetailQuery(query); setDetailTotal(0)
    await loadDetails(query, 1, detailPageSize)
  }
  const scopeTree = <><Input.Search aria-label="搜索组织或人员" placeholder="搜索组织或人员" value={keyword} onChange={event => setKeyword(event.target.value)} allowClear />
    {treeData.length ? <Tree key={keyword} blockNode defaultExpandAll treeData={treeData} selectedKeys={scope ? [scope.key] : []} onSelect={keys => {
      const node = nodes.find(item => item.key === keys[0])
      if (node) { setDrawerOpen(false); if (node.key !== scope?.key) void load(node) }
    }} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={keyword ? '未找到匹配的组织或人员' : '暂无可选范围'} />}</>
  return <div className={`performance-shell ${collapsed ? 'is-collapsed' : ''}`}>
    <aside className="performance-tree"><Button type="text" aria-label={collapsed ? '展开组织树' : '收起组织树'} icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={() => setCollapsed(value => !value)} />{!collapsed && scopeTree}</aside>
    <main className="performance-main"><div className="performance-mobile-tree"><Button icon={<MenuUnfoldOutlined />} onClick={() => setDrawerOpen(true)}>{scope?.title ?? '选择组织或人员'}</Button></div>
    <Drawer title="统计范围" placement="left" open={drawerOpen} onClose={() => setDrawerOpen(false)}>{scopeTree}</Drawer>
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div className="performance-heading"><div><Typography.Title level={3} style={{ margin: 0 }}>新媒体客资分析大盘</Typography.Title><Typography.Text type="secondary">按冻结引流贡献人统计，数据使用北京时间</Typography.Text></div><Button onClick={() => void load(scope)}>刷新</Button></div>
      {error && <Alert type="error" showIcon title={error} action={<Button size="small" onClick={() => void load(scope)}>重试</Button>} />}
      {loading ? <Card><Spin /></Card> : !data ? <Empty description="暂无数据" /> : <>
        <Card title="团队大盘"><Space wrap style={{ width: '100%' }}><Stat label="本月有效客资目标" value={data.target.targetCount ?? '未设置'} /><Stat label="本月有效客资" value={data.target.actualCount} hint={`完成率 ${percent(data.target.targetCount ? data.target.actualCount / data.target.targetCount : null)}`} /></Space><Progress style={{ width: '100%' }} percent={Math.min(100, data.target.targetCount ? data.target.actualCount / data.target.targetCount * 100 : 0)} format={() => percent(data.target.targetCount ? data.target.actualCount / data.target.targetCount : null)} status={data.target.targetCount == null ? 'exception' : undefined} /><BusinessTable tableKey="media-lead-periods" mode="compact" columnMode="native" rowKey="key" size="small" scroll={{ x: 750 }} pagination={false} dataSource={data.periods} columns={[{ title: '期间', dataIndex: 'label' }, { title: '客资总数', dataIndex: 'total' }, { title: '有效客资', dataIndex: 'valid' }, { title: '无效客资', dataIndex: 'invalid' }, { title: '客资有效率', dataIndex: 'validRate', render: (v: number) => percent(v) }, { title: '成交客资', dataIndex: 'converted' }, { title: '成交率', dataIndex: 'convertedRate', render: (v: number) => percent(v) }]} /></Card>
        <Card title="团队成员进度表">
          <BusinessTable<Member>
            tableKey="media-lead-members" mode="compact" columnMode="native" rowKey="userId" size="small"
            scroll={{ x: 1650 }} pagination={slashFreePagination} dataSource={data.members}
            columns={[
              { title: '成员', dataIndex: 'name', fixed: 'left' },
              { title: '本月目标', dataIndex: 'targetCount', render: (v: number | null) => v ?? '未设置' },
              { title: '昨日', render: (_: unknown, x: Member) => <MemberMetricTags submitted={x.yesterday} converted={x.yesterdayConverted} /> },
              { title: '今日', render: (_: unknown, x: Member) => <MemberMetricTags submitted={x.today} converted={x.todayConverted} /> },
              { title: '本周', render: (_: unknown, x: Member) => <MemberMetricTags submitted={x.week} valid={x.weekValid} converted={x.weekConverted} /> },
              { title: '上周', render: (_: unknown, x: Member) => <MemberMetricTags submitted={x.lastWeek} valid={x.lastWeekValid} converted={x.lastWeekConverted} /> },
              { title: '本月', render: (_: unknown, x: Member) => <MemberMetricTags submitted={x.month} valid={x.monthValid} converted={x.monthConverted} /> },
              { title: '上月', render: (_: unknown, x: Member) => <MemberMetricTags submitted={x.lastMonth} valid={x.lastMonthValid} converted={x.lastMonthConverted} /> },
              { title: '月度完成率', dataIndex: 'monthProgress', render: (v: number | null) => <Progress percent={v == null ? 0 : Math.min(100, v * 100)} format={() => percent(v)} /> }
            ]}
          />
        </Card>
        <Card title="提交日期与判定" extra={<Input type="month" aria-label="客资统计月份" value={month} max={date().slice(0, 7)} style={{ width: 150 }} onChange={event => { const next = event.target.value; if (next) { setMonth(next); void load(scope, next) } }} />}><MonthlyCohort data={data} month={month} canDetail={permissions.includes('zsjos:media-lead-analysis:detail')} onDay={day => void openDetails(day + ' 提交客资明细', [day, day])} /></Card>
        <Card title="引流数据分析"><div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}><Pie title="本月有效客资来源渠道" rows={data.currentMonthChannels} /><Pie title="上月有效客资来源渠道" rows={data.lastMonthChannels} /><Pie title="本月有效客资分类" rows={data.currentMonthCategories} /><Pie title="上月有效客资分类" rows={data.lastMonthCategories} /></div></Card>
      </>}
      {permissions.includes('zsjos:media-lead-analysis:detail') && <Space wrap><Typography.Text type="secondary">按提交期间查看客资明细：</Typography.Text>{data?.periods.filter(x => periodRange(x.key, data.asOf)).map(x => <Button key={x.key} aria-label={'查看' + x.label + '客资明细'} size="small" onClick={() => { const range = periodRange(x.key, data.asOf); if (range) void openDetails(x.label + '提交客资明细', range) }}>{x.label}</Button>)}</Space>}
      <DetailModal open={detailOpen} title={detailTitle} loading={detailLoading} error={detailError} rows={detailRows} page={detailPage} pageSize={detailPageSize} total={detailTotal} onChange={(page, size) => { if (detailQuery) void loadDetails(detailQuery, size === detailPageSize ? page : 1, size) }} onRetry={() => { if (detailQuery) void loadDetails(detailQuery, detailPage, detailPageSize) }} onClose={closeDetails} />
    </Space>
    </main>
  </div>
}
