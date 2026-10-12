// UTF-8. Synthetic transport for browser regression only; never connects to business APIs.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { BrowserRouter, useLocation, useNavigate } from 'react-router-dom'
import { App, Button, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import RetainedReviewRoute from '../src/layouts/RetainedReviewRoutes'
import SalesPerformancePage from '../src/pages/SalesPerformancePage'
import { http } from '../src/services/api'

const asOf = '2026-10-10T12:00:00'
const metric = { key: 'month', label: '本月', start: '2026-10-01T00:00:00', end: asOf, amount: 50400, orders: 48, converted: 24, denominator: 48, rate: .5, average: 1280, averageAmount: 1280, averageOrders: 1 }
const options = { conversionStatus: [{ key: 'converted', label: '已成交', count: 24 }, { key: 'unconverted', label: '未成交', count: 24 }], populationKind: [{ key: 'current', label: '期间新接', count: 48 }, { key: 'previous', label: '往期补入', count: 0 }], categories: [{ key: 'cat', label: '分类快照甲', count: 48 }], channels: [{ key: 'channel', label: '渠道快照甲', count: 48 }], products: [{ key: 'product', label: '商品快照甲', count: 24 }] }
let mode = 'ok'
const orderRows = Array.from({ length: 48 }, (_, index) => ({ id: index + 100, number: `DD-${index + 100}`, leadId: index === 47 ? null : index + 1, leadNo: index === 47 ? null : `KZ-${index + 1}`, sourceLabel: index % 2 ? '非引流' : '线上引流', sourceKey: index % 2 ? 'self' : 'inbound', orderTypeKey: index % 2 ? 'repurchase' : 'first', orderTypeLabel: index % 2 ? '复购' : '首购', categoryKey: 'cat', categoryLabel: '分类快照甲', channelKey: 'channel', channelLabel: '订单渠道旧名', submittedAt: asOf, amount: index % 2 ? 100 : 2000, totalAmount: index % 2 ? 100 : 2000, products: [{ key: 'a', ref: 'a', label: '商品快照甲' }, ...(index % 2 ? [] : [{ key: 'b', ref: 'b', label: '商品快照乙' }])] }))
const total = (rows: typeof orderRows) => ({ orders: rows.length, amount: rows.reduce((sum, row) => sum + row.amount, 0) })

const requests: { url?: string; params: Record<string, unknown> }[] = []
const allRows = Array.from({ length: 48 }, (_, index) => ({ leadId: index + 1, leadNo: `KZ-${index + 1}`, categoryKey: 'cat', categoryLabel: '分类快照甲', channelKey: 'channel', channelLabel: '渠道快照甲', populationKind: 'current', receivedAt: asOf, converted: index % 2 === 0, order: index % 2 === 0 ? { id: index + 100, number: `DD-${index + 100}`, submittedAt: asOf, amount: 1280, products: [{ key: 'product', ref: 'p', label: '商品快照甲', snapshot: '{"name":"商品快照甲"}' }] } : null }))
http.defaults.adapter = async config => {
 const url = config.url ?? '', params = config.params ?? {}
 requests.push({ url, params })
 if (config.method !== 'get') throw new Error('只读流程不允许业务写入')
 let data: unknown = []
 if (url.endsWith('/tree')) data = [{ key: 'USER:1', title: '验证销售', scopeType: 'USER', scopeId: 1, selectable: true }, { key: 'DEPT:12', title: '验证部门', scopeType: 'DEPT', scopeId: 12, selectable: true }]
 else if (url.endsWith('/overview')) data = { asOf, attributionAvailableSince: asOf, targets: [], performance: ['today','week','month','lastMonth','quarter','year','last7','last30','last60','last90'].map(key => ({...metric,key,label:({today:'今日',week:'本周',month:'本月',lastMonth:'上月',quarter:'本季度',year:'本年',last7:'近7日',last30:'近30日',last60:'近60日',last90:'近90日'} as Record<string,string>)[key]})), conversion: [metric], pending: { accept: 1 }, missingAttributionOrders: 0, missingAttributionAmount: 0, canDetail: true }
 else if (url.endsWith('/analysis')) data = { asOf, start: metric.start, end: asOf, averages: [metric], trend: [], sources: [{key:'inbound|线上引流',label:'线上引流',amount:48000,count:24,share:.95},{key:'self|非引流',label:'非引流',amount:2400,count:24,share:.05}], products:[{key:'a|商品快照甲',label:'商品快照甲',amount:26400,count:48},{key:'b|商品快照乙',label:'商品快照乙',amount:24000,count:24}], contributors: [], contributionMetrics:[{userId:1,name:'验证销售',actual:metric}], averageTrends: {}, target:null }
 else if (url.endsWith('/order-details')) {
  if (mode === 'error') throw new Error('模拟订单明细网络失败')
  if (mode === 'denied') return { data: { code: 1900090001, msg: '无权查看该业绩范围' }, status: 200, statusText: 'OK', headers: {}, config }
  const entryRows = orderRows.filter(row => params.dimension !== 'source' || row.sourceKey === String(params.groupKey).split('|')[0]).filter(row => params.dimension !== 'contributor' || params.groupKey === '1').filter(row => params.dimension !== 'product' || row.products.some(p => p.ref === String(params.groupKey).split('|')[0])).map(row => params.dimension === 'product' ? {...row, amount:row.totalAmount === 2000 ? 1000 : 100} : row)
  const filtered = entryRows.filter(row => (!params.sourceKey || row.sourceKey === params.sourceKey) && (!params.orderTypeKey || row.orderTypeKey === params.orderTypeKey) && (!params.categoryKey || row.categoryKey === params.categoryKey) && (!params.channelKey || row.channelKey === params.channelKey) && (!params.productKey || row.products.some(p => p.key === params.productKey)) && (!params.keyword || `${row.leadNo ?? ''} ${row.number}`.toLowerCase().includes(String(params.keyword).toLowerCase())))
  data = { list: filtered.slice((params.pageNo - 1) * params.pageSize, params.pageNo * params.pageSize), total: filtered.length, start: metric.start, end: asOf, asOf, amountKind: params.dimension === 'product' ? 'product' : 'order', summary: total(entryRows), filteredSummary: total(filtered), options: { sources: [{key:'inbound',label:'线上引流',count:24},{key:'self',label:'非引流',count:24}], orderTypes:[{key:'first',label:'首购',count:24},{key:'repurchase',label:'复购',count:24}],categories:options.categories,channels:[{key:'channel',label:'订单渠道旧名',count:48}],products:[{key:'a',label:'商品快照甲',count:48},{key:'b',label:'商品快照乙',count:24}] } } } else if (url.endsWith('/lead/get')) {
  data = { id: params.id, leadNo: `KZ-${params.id}`, personId: 1, submittedName: '验证客资', sourceType: 'internal_new_media', status: 'invalid', assignmentStatus: 'assigned', handlingStage: 'following', qualificationStatus: 'invalid', operationalStatus: 'active', submittedAt: asOf, createTime: asOf, updateTime: asOf, relationTypes: ['submitter'], visibleTabs: ['overview', 'follow-ups', 'appeals', 'flow-history'], availableActions: [{ code: 'ADD_FOLLOW_UP' }, { code: 'EDIT_BASIC_INFO' }], leadCategoryLabelSnapshot: '分类快照甲', sourceChannelLabelSnapshot: '渠道快照甲', attachments: [] }
 } else if (url.includes('/sales-order/management/')) {
  if (mode === 'order-error') throw new Error('无权查询该订单')
  const id = Number(url.split('/').at(-1))
  data = { id, orderNo: `DD-${id}`, status: 'effective', studentName: '验证学员', totalAmount: 1280, submittedAt: asOf, items: [{ id: 1, productName: '商品快照甲', actualAmount: 1280 }], paymentVouchers: [], canRevise: true, canTerminate: true }
 } else if (url.endsWith('/details')) data = { list: [{ id: 100, number: 'KZ-TASK', kind: 'task', label: '待接收', occurredAt: asOf, state: 'pending' }], total: 1 }
 else if (url.includes('follow-up') && url.endsWith('/page')) data = { list: [], total: 0 }
 return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}

function Fixture() {
 const navigate = useNavigate(), location = useLocation()
 const [details, setDetails] = useState(true)
 const permissions = ['zsjos:sales-performance:query', 'zsjos:sales-performance:detail', ...(details ? ['zsjos:lead:query', 'zsjos:sales-order:query-management'] : [])]
 Object.assign(window, { orderFixture: { requests, mode: (value: string) => { mode = value }, navigate: () => navigate('/test/conversion-other'), back: () => navigate('/test/order-details.html'), revokeDetails: () => setDetails(false) } })
 const active = location.pathname === '/test/order-details.html'
 return <><Space><Button onClick={() => navigate(active ? '/test/conversion-other' : '/test/order-details.html')}>切换验证页面</Button></Space>
  <RetainedReviewRoute active={active}><SalesPerformancePage permissions={permissions}/></RetainedReviewRoute>{!active && <h1>其他页面</h1>}</>
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><Fixture/></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
