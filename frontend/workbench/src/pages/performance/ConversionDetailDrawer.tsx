import { filterSelectDisplayProps } from "../../components/filterSelectDisplay"
import { usePerformanceModule } from './PerformanceModule'
import { Alert, Button, Collapse, Drawer, Empty, Input, Select, Space, Tag, Typography } from 'antd'
import { useRef, useState } from 'react'
import BusinessTable from '../../components/BusinessTable'
import { conversionRule, conversionSummary, sourceReviewRequired, money, percent, type ConversionOption, type ConversionQuery, type ConversionRow, type Metric, type Query } from '../../services/salesPerformance'
import { Resource, useResource } from './PerformanceShared'
import ConversionRecordDetail, { type ConversionSelection } from './ConversionRecordDetail'

export default function ConversionDetailDrawer({ query, metric, title, permissions, onClose, onDenied }: {
 query: Query; metric: Metric; title: string; permissions: string[]; onClose: () => void; onDenied: (error: unknown) => void
}) {
 const {api: performanceApi} = usePerformanceModule()
 const [filters, setFilters] = useState<ConversionQuery>(query), [page, setPage] = useState(1)
 const [keyword, setKeyword] = useState(''), [selection, setSelection] = useState<ConversionSelection>()
 const listRef = useRef<HTMLDivElement>(null)
 const state = useResource(signal => performanceApi.conversionDetails({ ...filters, pageNo: page, pageSize: 20 }, signal).catch(error => { onDenied(error); throw error }), [JSON.stringify(filters), page])
 const optionState = useRef(state.data?.options)
 if (state.data) optionState.current = state.data.options
 function filter(key: keyof ConversionQuery, value: string | undefined) { setFilters(current => ({ ...current, [key]: value })); setPage(1); listRef.current?.scrollTo(0, 0) }
 function selector(label: string, key: keyof ConversionQuery, options?: ConversionOption[]) {
  return <Select aria-label={label} placeholder={label} allowClear showSearch optionFilterProp="label" style={{ width: '100%' }}
   value={filters[key]} onChange={value => filter(key, value as string | undefined)} options={(options ?? []).map(option => ({ value: option.key, label: `${option.label} (${option.count})` }))} {...filterSelectDisplayProps}/>
 }
 const options = optionState.current
 const review = sourceReviewRequired(state.data?.summary ?? metric)
 const changed = !review && state.data && (state.data.summary.converted !== metric.converted || state.data.summary.denominator !== metric.denominator)
 const canLead = permissions.includes('zsjos:lead:query'), canOrder = permissions.includes('zsjos:sales-order:query-management')
 return <Drawer title={`${title} · ${metric.label}成交率明细`} open width="min(1180px, 100vw)" onClose={onClose} styles={{ body: { padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' } }}>
  <div style={{ padding: 16, flexShrink: 0 }}><Space wrap>
   {selection && <Button onClick={() => setSelection(undefined)}>返回明细列表</Button>}
   <Typography.Text strong>{review ? conversionSummary(state.data?.summary ?? metric) : `${percent(metric.rate)} · ${conversionSummary(metric)}`}</Typography.Text>
   <Tag>{selection ? selection.kind === 'lead' ? '客资详情 · 只读' : '订单详情 · 只读' : '按客资去重'}</Tag>
  </Space><div><Typography.Text type="secondary">{metric.start.replace('T', ' ')} 至 {metric.end.replace('T', ' ')} · 筛选不改变上方统计值</Typography.Text></div></div>
  <div ref={listRef} hidden={!!selection} style={{ overflow: 'auto', flex: 1, padding: 16, display: selection ? 'none' : undefined }}>
   <Collapse size="small" items={[{ key: 'rule', label: '统计口径与筛选说明', children: <><Typography.Paragraph>{conversionRule(query.periodKey)}</Typography.Paragraph><Typography.Paragraph>产品仅匹配本周期合格成交订单的商品快照，未成交客资无可筛选的订单商品。选项括号内为完整统计人口中的客资数。</Typography.Paragraph></> }]}/>
   {changed && <Alert type="warning" showIcon title={`明细数据已有更新：当前完整人口成交 ${state.data!.summary.converted} / ${state.data!.summary.denominator}。上方保留原卡片统计；关闭后刷新统计页可同步。`} style={{ marginTop: 12 }}/>}
   <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 300px), 1fr))', gap: 12, margin: '16px 0' }}>
    {selector('成交状态', 'conversionStatus', options?.conversionStatus)}
    {selector('统计归类', 'populationKind', options?.populationKind)}
    {selector('客资分类', 'categoryKey', options?.categories)}
    {selector('来源渠道', 'channelKey', options?.channels)}
    {selector('订单商品', 'productKey', options?.products)}
    <Input.Search aria-label="客资或订单编号" placeholder="客资 / 订单编号" value={keyword} onChange={e => setKeyword(e.target.value)} onSearch={value => filter('keyword', value.trim() || undefined)} allowClear/>
   </div>
   <Space wrap style={{ marginBottom: 12 }}><Button onClick={() => { setFilters(query); setKeyword(''); setPage(1); listRef.current?.scrollTo(0, 0) }}>重置筛选</Button><Typography.Text type="secondary">{state.data ? `筛选结果 ${state.data.total} 位客资 · 明细查询时间 ${state.data.asOf.replace('T', ' ')}` : '正在查询明细'}</Typography.Text></Space>
   <Resource state={state}>{review ? <Alert type="warning" showIcon title={conversionSummary(state.data?.summary ?? metric)} description="该范围的来源记录需核实，暂不展示成交率明细。订单金额统计不受影响。"/> : <BusinessTable<ConversionRow> tableKey="performance-conversion-details" mode="compact" rowKey="leadId" search={false}
    dataSource={state.data?.list ?? []} scroll={{ x: 1200 }} locale={{ emptyText: <Empty description="没有符合筛选条件的客资"/> }}
    pagination={{ current: page, pageSize: 20, showSizeChanger: false, total: state.data?.total ?? 0, onChange: value => { setPage(value); listRef.current?.scrollTo(0, 0) } }} columns={[
     { title: '客资编号', dataIndex: 'leadNo', width: 150, render: (_, row) => row.leadNo || '历史编号缺失' },
     { title: '客资分类', dataIndex: 'categoryLabel', width: 130 },
     { title: '来源渠道', dataIndex: 'channelLabel', width: 130 },
     { title: '统计归类', width: 110, render: (_, row) => options?.populationKind.find(option => option.key === row.populationKind)?.label ?? '归类未记录' },
     { title: '接收时间', width: 170, render: (_, row) => row.receivedAt?.replace('T', ' ') ?? '接收时间缺失' },
     { title: '计入本周期成交', width: 130, render: (_, row) => <Tag color={row.converted ? 'green' : 'default'}>{row.converted ? '是' : '否'}</Tag> },
     { title: '关联成交订单 / 商品', width: 260, render: (_, row) => row.order ? <><div>{row.order.number || '订单编号缺失'} · {money(row.order.amount)}</div><div>{row.order.submittedAt.replace('T', ' ')}</div><Typography.Text type="secondary">{row.order.products.map(product => product.label).join('、') || '商品快照缺失'}</Typography.Text></> : '无合格成交订单' },
     { title: '查看详情', width: 180, render: (_, row) => <Space wrap>{canLead && <Button type="link" onClick={() => setSelection({ kind: 'lead', id: row.leadId })}>客资详情</Button>}{canOrder && row.order && <Button type="link" onClick={() => setSelection({ kind: 'order', id: row.order!.id })}>订单详情</Button>}</Space> },
    ]}/>}</Resource>
  </div>
  {selection && <div style={{ overflow: 'auto', flex: 1, padding: 16 }}><ConversionRecordDetail key={`${selection.kind}:${selection.id}`} selection={selection}/></div>}
 </Drawer>
}
