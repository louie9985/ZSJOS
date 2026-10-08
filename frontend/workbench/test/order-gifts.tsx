// Isolated transport for the production order components. No real business writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { App, Button } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SalesOrderEntryModal from '../src/components/SalesOrderEntryModal'
import SalesOrderDetailCards from '../src/components/SalesOrderDetailCards'
import { api, http, type SalesOrder } from '../src/services/api'

import { workOrderApi } from '../src/services/workOrderApi'
import { noticeManagement } from '../src/services/noticeManagement'

const gift = { code: 'old', name: '原礼品', path: ['原分类', '原礼品'], snapshotAt: '2026-01-01' }
const order = {
  id: 100, orderNo: 'ORDER-TEST', orderType: 'first_purchase', status: 'revision_required', version: 1,
  studentName: '测试学员', buyerName: '测试学员', studentMobile: '13800138000', personId: 10,
  provinceCode: 'OTHER', provinceName: '其他省份', cityCode: 'OTHER', cityName: '其他城市',
  studentNature: 'test', studentNatureLabelSnapshot: '测试选项', servicePeriod: 'test', servicePeriodLabelSnapshot: '测试选项',
  studentSource: 'test', studentSourceLabelSnapshot: '测试选项', feeMode: 'test', feeModeLabelSnapshot: '测试选项',
  paymentMethod: 'test', paymentMethodLabelSnapshot: '测试选项', customerPaidAt: Date.now(), totalAmount: 100,
  submitterUserId: 20, collectionMode: 'offline_paid', transactionLocked: false,
  giftItems: JSON.stringify([JSON.stringify(gift)]), giftItemCodes: ['old'], giftItemSnapshots: [gift],
  giftItemsInvalid: location.search.includes('invalid'), giftShippingAddress: '历史收件地址',
  items: [{ id: 1, productRef: 'p1', productName: '历史课程', skuRef: 's1', skuName: '历史规格', actualAmount: 100, specs: [], categoryPath: [] }],
  paymentVouchers: [{ infraFileId: 1, originalName: '凭证.pdf', contentType: 'application/pdf' }]
} as SalesOrder
const fixture = { requests: [] as unknown[], submitted: false }
Object.assign(window, { orderGiftsFixture: fixture })
http.defaults.adapter = async () => { throw new Error('禁止真实接口请求') }
Object.assign(api, {
  areaTree: async () => [{ id: 990000000, name: '其他省份', selectionCode: 'OTHER', children: [{ id: 990000001, name: '其他城市', selectionCode: 'OTHER' }] }],
  salesOrderCatalog: async () => ({ categoryTree: [], spus: [], skus: [] }),
  giftConfigList: async () => [{ code: 'new', name: '新礼品', status: 0 }],
  dictDataByType: async () => [{ value: 'test', label: '测试选项', status: 0 }],
  salesOrder: async () => order,
  resubmitSalesOrder: async (_id: number, data: unknown) => { fixture.requests.push(data); return 101 }
})
function Fixture() {
  const [open, setOpen] = useState(false)
  return <main style={{ padding: 16 }}><Button onClick={() => setOpen(true)}>编辑礼品</Button>
    <SalesOrderDetailCards order={order} mode="mine" />
    <SalesOrderEntryModal lead={{ id: 1, submittedName: '测试学员' }} orderId={100} open={open}
      onClose={() => setOpen(false)} onSubmitted={() => { fixture.submitted = true; setOpen(false) }} />
  </main>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><Fixture /></BrowserRouter></App></ThemeProvider>)

Object.assign(window, { verifyUploadTimeouts: async () => {
  const configurations: { url?: string; timeout?: number }[] = []
  const original = http.defaults.adapter
  http.defaults.adapter = async config => {
    configurations.push({ url: config.url, timeout: config.timeout })
    return { config, data: { code: 0, data: {} }, status: 200, statusText: 'OK', headers: {} }
  }
  try {
    const file = new File(['test'], 'attachment.txt')
    await Promise.all([workOrderApi.upload(file), noticeManagement.upload(file, () => {})])
    await http.post('/test/json', {})
    await http.post('/test/unlimited', new FormData(), { timeout: 0 })
    await http.post('/test/longer', new FormData(), { timeout: 1200000 })
    return configurations
  } finally { http.defaults.adapter = original }
} })
