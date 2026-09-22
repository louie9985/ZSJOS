// UTF-8. Synthetic fixtures only; HTTP is blocked.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import SalesOrderEntryModal from '../src/components/SalesOrderEntryModal'
import { api, http, type SalesOrder } from '../src/services/api'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'
const mode = new URLSearchParams(location.search).get('mode') || 'offline'
http.defaults.adapter = async () => { throw new Error('Fixture blocks HTTP') }
const order = { id: 1, orderNo: 'TEST-REVISION', status: 'revision_required', collectionMode: mode === 'online' ? 'online_link' : mode === 'unknown' ? undefined : 'offline_paid', transactionLocked: mode === 'online', paymentStatus: 'paid', studentName: '测试学员', studentMobile: '13800138000', provinceCode: '110000', cityCode: '110100', provinceName: '北京市', cityName: '北京市', customerPaidAt: Date.now(), totalAmount: 500, items: [{ id: 1, productRef: 'spu-1', skuRef: 'sku-1', productName: '测试课程', skuName: '原规格', actualAmount: 500, specs: [] }], paymentVouchers: [{ infraFileId: 1, originalName: '测试凭证.png', fileUrl: '', contentType: 'image/png', fileSize: 10 }], studentNature: 'test', studentNatureLabelSnapshot: '测试', servicePeriod: 'test', servicePeriodLabelSnapshot: '测试', studentSource: 'test', studentSourceLabelSnapshot: '测试', feeMode: 'test', feeModeLabelSnapshot: '测试', paymentMethod: 'test', paymentMethodLabelSnapshot: '测试' } as unknown as SalesOrder
api.salesOrder = async () => { if (mode === 'error') throw new Error('测试加载失败'); return order }
api.areaTree = async () => [{ id: 110000, name: '北京市', children: [{ id: 110100, name: '北京市' }] }] as never
api.salesOrderCatalog = async () => ({ categoryTree: [], spus: [], skus: [] }) as never
api.giftConfigList = async () => []
api.dictDataByType = async () => []
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><SalesOrderEntryModal lead={{ id: 1, submittedName: '测试学员' }} orderId={1} open onClose={() => {}} onSubmitted={() => {}} /></App></ThemeProvider>)
