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
const metric = { key: 'month', label: '本月', start: '2026-10-01T00:00:00', end: asOf, amount: 1280, orders: 1, converted: 24, denominator: 48, rate: .5, average: 1280, averageAmount: 1280, averageOrders: 1 }
const options = { conversionStatus: [{ key: 'converted', label: '已成交', count: 24 }, { key: 'unconverted', label: '未成交', count: 24 }], populationKind: [{ key: 'current', label: '期间新接', count: 48 }, { key: 'previous', label: '往期补入', count: 0 }], categories: [{ key: 'cat', label: '分类快照甲', count: 48 }], channels: [{ key: 'channel', label: '渠道快照甲', count: 48 }], products: [{ key: 'product', label: '商品快照甲', count: 24 }] }
let mode = new URLSearchParams(location.search).get('mode') ?? 'ok'
const reviewMetric = { ...metric, converted: null, denominator: null, rate: null, sourceStatus: 'review_required', sourceReviewCount: 3 }
const requests: { url?: string; params: Record<string, unknown> }[] = []
const allRows = Array.from({ length: 48 }, (_, index) => ({ leadId: index + 1, leadNo: `KZ-${index + 1}`, categoryKey: 'cat', categoryLabel: '分类快照甲', channelKey: 'channel', channelLabel: '渠道快照甲', populationKind: 'current', receivedAt: asOf, converted: index % 2 === 0, order: index % 2 === 0 ? { id: index + 100, number: `DD-${index + 100}`, submittedAt: asOf, amount: 1280, products: [{ key: 'product', ref: 'p', label: '商品快照甲', snapshot: '{"name":"商品快照甲"}' }] } : null }))
http.defaults.adapter = async config => {
 const url = config.url ?? '', params = config.params ?? {}
 requests.push({ url, params })
 if (config.method !== 'get') throw new Error('只读流程不允许业务写入')
 let data: unknown = []
 if (url.endsWith('/tree')) data = [{ key: 'USER:1', title: '验证销售', scopeType: 'USER', scopeId: 1, selectable: true }]
 else if (url.endsWith('/overview')) data = { asOf, attributionAvailableSince: asOf, targets: [], performance: [metric], conversion: [mode === 'review' ? reviewMetric : metric], pending: {}, missingAttributionOrders: 0, missingAttributionAmount: 0, canDetail: true }
 else if (url.endsWith('/conversion-details')) {
  if (mode === 'error') throw new Error('模拟明细网络失败')
  if (mode === 'denied') return { data: { code: 1900090001, msg: '无权查看该业绩范围' }, status: 200, statusText: 'OK', headers: {}, config }
  const filtered = allRows.filter(row => (!params.conversionStatus || (row.converted ? 'converted' : 'unconverted') === params.conversionStatus)
   && (!params.populationKind || params.populationKind === row.populationKind)
   && (!params.productKey || row.order) && (!params.keyword || `${row.leadNo} ${row.order?.number ?? ''}`.includes(params.keyword)))
  data = { list: filtered.slice((params.pageNo - 1) * params.pageSize, params.pageNo * params.pageSize), total: filtered.length, start: metric.start, end: asOf, asOf, summary: metric, options }
  if (mode === 'review' || mode === 'detail-review') data = { list: [], total: 0, start: metric.start, end: asOf, asOf, summary: reviewMetric, options: { conversionStatus: [], populationKind: [], categories: [], channels: [], products: [] } }
 } else if (url.endsWith('/lead/get')) {
  data = { id: params.id, leadNo: `KZ-${params.id}`, personId: 1, submittedName: '验证客资', sourceType: 'internal_new_media', status: 'invalid', assignmentStatus: 'assigned', handlingStage: 'following', qualificationStatus: 'invalid', operationalStatus: 'active', submittedAt: asOf, createTime: asOf, updateTime: asOf, relationTypes: ['submitter'], visibleTabs: ['overview', 'follow-ups', 'appeals', 'flow-history'], availableActions: [{ code: 'ADD_FOLLOW_UP' }, { code: 'EDIT_BASIC_INFO' }], leadCategoryLabelSnapshot: '分类快照甲', sourceChannelLabelSnapshot: '渠道快照甲', attachments: [] }
 } else if (url.includes('/sales-order/management/')) {
  if (mode === 'order-error') throw new Error('无权查询该订单')
  const id = Number(url.split('/').at(-1))
  data = { id, orderNo: `DD-${id}`, status: 'effective', studentName: '验证学员', totalAmount: 1280, submittedAt: asOf, items: [{ id: 1, productName: '商品快照甲', actualAmount: 1280 }], paymentVouchers: [], canRevise: true, canTerminate: true }
 } else if (url.endsWith('/order-details')) data = { list: [{ id: 100, number: 'DD-100', leadId: 1, leadNo: 'KZ-1', sourceLabel: '线上引流', orderTypeLabel: '首购', categoryLabel: '分类快照甲', channelLabel: '渠道快照甲', submittedAt: asOf, amount: 1280, totalAmount: 1280, products: [] }], total: 1, asOf, start: metric.start, end: asOf, amountKind: 'order', summary: metric, filteredSummary: metric, options: { sources: [], orderTypes: [], categories: [], channels: [], products: [] } }
 else if (url.endsWith('/details')) data = { list: [{ id: 100, number: 'DD-100', kind: 'order', label: '线上引流', occurredAt: asOf, amount: 1280, state: '审批通过' }], total: 1 }
 else if (url.includes('follow-up') && url.endsWith('/page')) data = { list: [], total: 0 }
 return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}

function Fixture() {
 const navigate = useNavigate(), location = useLocation()
 const [details, setDetails] = useState(true)
 const permissions = ['zsjos:sales-performance:query', 'zsjos:sales-performance:detail', ...(details ? ['zsjos:lead:query', 'zsjos:sales-order:query-management'] : [])]
 Object.assign(window, { conversionFixture: { requests, mode: (value: string) => { mode = value }, navigate: () => navigate('/test/conversion-other'), back: () => navigate('/test/conversion-details.html'), revokeDetails: () => setDetails(false) } })
 const active = location.pathname === '/test/conversion-details.html'
 return <><Space><Button onClick={() => navigate(active ? '/test/conversion-other' : '/test/conversion-details.html')}>切换验证页面</Button></Space>
  <RetainedReviewRoute active={active}><SalesPerformancePage permissions={permissions}/></RetainedReviewRoute>{!active && <h1>其他页面</h1>}</>
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><Fixture/></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
