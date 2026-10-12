// UTF-8. Real order component; isolated synthetic transport only.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button } from 'antd'
import SalesOrderEntryModal from '../src/components/SalesOrderEntryModal'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { api, http } from '../src/services/api'
import '../src/styles/index.css'
const mode = new URLSearchParams(location.search).get('mode') || 'fresh'
const state = { orders: [] as unknown[], drafts: [] as unknown[] }
Object.assign(window, { contactFixture: state })
http.defaults.adapter = async () => { throw new Error('Contact fixture blocks HTTP') }
const base = { studentName: '测试学员', studentNature: 'test', regionPath: ['OTHER', 'OTHER'], servicePeriod: 'test', studentSource: 'test',
  customerPaidAt: Date.now(), feeMode: 'test', paymentMethod: 'test', items: [{ courseKey: 'p1::s1', actualAmount: 100 }] }
const current = mode === 'fresh' || mode === 'blank' ? undefined : { id: 55, purchaseType: 'lead_first_purchase',
  collectionMode: mode === 'locked' ? 'online_link' : 'offline_paid', paymentLocked: mode === 'locked', paymentStatus: 'paid', version: 1,
  draft: { ...base, mobile: mode === 'locked' ? '13800138000' : '  ', wechatId: mode === 'locked' ? '' : 'draft-wx' } }
Object.assign(api, {
  areaTree: async () => [{ id: 1, name: '其他省份', selectionCode: 'OTHER', children: [{ id: 2, name: '其他城市', selectionCode: 'OTHER' }] }],
  salesOrderCatalog: async () => ({ categoryTree: [{ id: 1, name: '测试分类', children: [] }],
    spus: [{ spuRef: 'p1', spuName: '测试课程', categoryId: 1, categoryPath: [{ id: 1, name: '测试分类' }], attrs: [] }],
    skus: [{ spuRef: 'p1', skuRef: 's1', skuName: '测试方案', price: 100, attrValues: {} }] }),
  giftConfigList: async () => [], dictDataByType: async () => [{ value: 'test', label: '测试选项', status: 0 }],
  currentPurchaseIntent: async () => { if (mode === 'error') throw new Error('草稿加载失败'); return current },
  savePurchaseIntentDraft: async (payload: unknown) => { state.drafts.push(payload); return { ...current, id: 55, version: 2 } },
  uploadSalesOrderVoucher: async () => ({ infraFileId: 100, originalName: 'voucher.pdf', fileUrl: '', contentType: 'application/pdf' }),
  submitSalesOrder: async (_id: number, payload: unknown) => { state.orders.push(payload); return 99 },
  salesOrder: async () => ({ id: 99, orderNo: 'TEST-ORDER', orderType: 'first_purchase' }),
})
function Fixture() {
  const [version, setVersion] = useState(0)
  return <><Button onClick={() => setVersion(value => value + 1)}>刷新客资资料</Button><SalesOrderEntryModal open
    lead={{ id: 1, submittedName: '测试学员', submittedMobile: mode === 'blank' ? undefined : version ? '13900139000' : '13800138000',
      submittedWechatId: ['blank', 'locked'].includes(mode) ? undefined : 'lead-wx' }} onClose={() => {}} onSubmitted={() => {}}/></>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture/></App></ThemeProvider>)
