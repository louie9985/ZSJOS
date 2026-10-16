import { usePerformanceModule } from './PerformanceModule'
import { Alert, Button, Collapse, Drawer, Empty, Input, Select, Space, Tag, Typography } from 'antd'
import { useRef, useState } from 'react'
import BusinessTable from '../../components/BusinessTable'
import { money, type ConversionOption, type OrderDetailEntry, type OrderDetailQuery, type OrderDetailRow } from '../../services/salesPerformance'
import { Resource, useResource } from './PerformanceShared'
import ConversionRecordDetail, { type ConversionSelection } from './ConversionRecordDetail'

export default function OrderDetailDrawer({ entry, title, permissions, onClose, onDenied }: {
 entry: OrderDetailEntry; title: string; permissions: string[]; onClose: () => void; onDenied: (error: unknown) => void
}) {
 const {api: performanceApi} = usePerformanceModule()
 const [filters, setFilters] = useState<OrderDetailQuery>(entry.query), [page, setPage] = useState(1)
 const [keyword, setKeyword] = useState(''), [selection, setSelection] = useState<ConversionSelection>()
 const listRef = useRef<HTMLDivElement>(null)
 const state = useResource(signal => performanceApi.orderDetails({ ...filters, pageNo: page, pageSize: 20 }, signal)
  .catch(error => { onDenied(error); throw error }), [JSON.stringify(filters), page])
 const lastData = useRef(state.data)
 if (state.data) lastData.current = state.data
 const options = lastData.current?.options
 const amountLabel = entry.query.dimension === 'product' ? '所选产品金额' : '整单金额'
 const changed = state.data && (state.data.summary.orders !== entry.summary.orders || Number(state.data.summary.amount) !== Number(entry.summary.amount))
 function filter(key: keyof OrderDetailQuery, value: string | undefined) {
  setFilters(current => ({ ...current, [key]: value })); setPage(1); listRef.current?.scrollTo(0, 0)
 }
 function selector(label: string, key: keyof OrderDetailQuery, values?: ConversionOption[]) {
  return <Select aria-label={label} placeholder={label} allowClear showSearch optionFilterProp="label" style={{ width: '100%' }} value={filters[key]}
   onChange={value => filter(key, value as string | undefined)} options={(values ?? []).map(option => ({ value: option.key, label: `${option.label} (${option.count})` }))}/>
 }
 return <Drawer title={`${title} · ${entry.label} · 订单明细`} open width="min(1180px, 100vw)" onClose={onClose}
  styles={{ body: { padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' } }}>
  <div style={{ padding: 'var(--crm-pane-pad)', flexShrink: 0 }}>
   <Space wrap>{selection && <Button onClick={() => setSelection(undefined)}>返回明细列表</Button>}
    <Typography.Text strong>入口统计：{entry.summary.orders} 笔订单 · {amountLabel} {money(entry.summary.amount)}</Typography.Text>
    <Tag>{selection ? selection.kind === 'lead' ? '客资详情 · 只读' : '订单详情 · 只读' : '生效订单'}</Tag>
   </Space>
   <div><Typography.Text type="secondary">{entry.label} · {lastData.current ? `${lastData.current.start.replace('T', ' ')} 至 ${lastData.current.end.replace('T', ' ')}` : '正在查询实际统计时间'} · 筛选不改变入口统计值</Typography.Text></div>
  </div>
  <div ref={listRef} data-order-detail-list hidden={!!selection} style={{ overflow: 'auto', flex: 1, padding: 'var(--crm-pane-pad)', display: selection ? 'none' : undefined }}>
   <Collapse size="small" items={[{ key: 'rule', label: '统计口径与筛选说明', children: <>
    <Typography.Paragraph>按生效订单的提交时间统计，包含首购、复购及零元订单。复购计入非引流。选项括号内为原入口范围内的订单数，商品数量不直接相加。</Typography.Paragraph>
    <Typography.Paragraph>{entry.query.dimension === 'product' ? '本入口始终只累计原先选定产品的成交金额，追加商品筛选仅筛选订单。' : '商品筛选匹配包含该商品的订单，金额仍为整单金额，包含订单中的其他商品。'} 重置筛选保留原入口的周期、组织、人员及下钻条件。</Typography.Paragraph>
   </> }]}/>
   {changed && <Alert type="warning" showIcon title={`明细数据已有更新：当前入口范围 ${state.data!.summary.orders} 笔订单 · ${amountLabel} ${money(state.data!.summary.amount)}。上方保留原入口统计，关闭后刷新统计页可同步。`} style={{ marginTop: 12 }}/>}
   <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 300px), 1fr))', gap: 12, margin: '16px 0' }}>
    {selector('业绩来源', 'sourceKey', options?.sources)}
    {selector('订单类型', 'orderTypeKey', options?.orderTypes)}
    {selector('客资分类', 'categoryKey', options?.categories)}
    {selector('来源渠道', 'channelKey', options?.channels)}
    {selector('订单商品', 'productKey', options?.products)}
    <Input.Search aria-label="客资或订单编号" placeholder="客资 / 订单编号" maxLength={100} value={keyword} allowClear
     onChange={event => setKeyword(event.target.value)} onSearch={value => filter('keyword', value.trim() || undefined)}/>
   </div>
   <Space wrap style={{ marginBottom: 12 }}><Button onClick={() => { setFilters(entry.query); setKeyword(''); setPage(1); listRef.current?.scrollTo(0, 0) }}>重置筛选</Button>
    <Typography.Text type="secondary">{state.data ? `筛选结果 ${state.data.total} 笔订单 · ${amountLabel}合计 ${money(state.data.filteredSummary.amount)} · 明细查询时间 ${state.data.asOf.replace('T', ' ')}` : '正在查询明细'}</Typography.Text>
   </Space>
   <Resource state={state}><BusinessTable<OrderDetailRow> tableKey="performance-order-details" mode="compact" rowKey="id" search={false}
    dataSource={state.data?.list ?? []} scroll={{ x: 1560 }} locale={{ emptyText: <Empty description="没有符合筛选条件的订单"/> }}
    pagination={{ current: page, pageSize: 20, showSizeChanger: false, total: state.data?.total ?? 0, onChange: value => { setPage(value); listRef.current?.scrollTo(0, 0) } }} columns={[
     { title: '订单编号', dataIndex: 'number', width: 150, render: (_, row) => row.number || '历史订单编号缺失' },
     { title: '客资编号', dataIndex: 'leadNo', width: 150, render: (_, row) => row.leadNo || '历史客资编号缺失' },
     { title: '业绩来源', dataIndex: 'sourceLabel', width: 110 },
     { title: '订单类型', dataIndex: 'orderTypeLabel', width: 100 },
     { title: '客资分类', dataIndex: 'categoryLabel', width: 140 },
     { title: '来源渠道', dataIndex: 'channelLabel', width: 140 },
     { title: '订单商品', width: 220, render: (_, row) => row.products.map(product => product.label).join('、') || '商品快照缺失' },
     { title: '提交时间', width: 170, render: (_, row) => row.submittedAt.replace('T', ' ') },
     { title: amountLabel, width: 140, render: (_, row) => money(row.amount) },
     { title: '查看详情', width: 190, render: (_, row) => <Space wrap>
      {permissions.includes('zsjos:sales-order:query-management') && <Button type="link" onClick={() => setSelection({ kind: 'order', id: row.id })}>订单详情</Button>}
      {permissions.includes('zsjos:lead:query') && row.leadId != null && <Button type="link" onClick={() => setSelection({ kind: 'lead', id: row.leadId! })}>客资详情</Button>}
     </Space> },
    ]}/></Resource>
  </div>
  {selection && <div style={{ overflow: 'auto', flex: 1, padding: 'var(--crm-pane-pad)' }}><ConversionRecordDetail key={`${selection.kind}:${selection.id}`} selection={selection}/></div>}
 </Drawer>
}
