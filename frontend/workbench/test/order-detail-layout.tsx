// UTF-8. Real management page with synthetic API data; all other transport blocked.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import MySalesOrderPage from '../src/pages/MySalesOrderPage'
import SalesOrderDetailCards from '../src/components/SalesOrderDetailCards'
import { api, http, type SalesOrder } from '../src/services/api'

const state = { failure: false }
Object.assign(window, { orderLayoutFixture: state })
http.defaults.adapter = async config => { throw new Error(`Fixture blocks transport: ${config.url}`) }
const base: SalesOrder = {
  id: 100, orderNo: 'OR-LAYOUT-100', personId: 10, orderType: 'first_purchase', status: 'effective',
  submitterUserId: 20, buyerName: '布局测试学员', studentName: '布局测试学员', studentMobile: '13800000000',
  studentWechatId: 'fixture-wechat', provinceName: '湖北省', cityName: '宜昌市',
  totalAmount: 398, customerPaidAt: 1788748740000, submittedAt: 1788760440000, effectiveAt: 1788748740000,
  classType: '冲刺课', agreedExamTime: '26年9月', approvalRoundNo: 1, version: 0, approvalRoundVersion: 0,
  paymentMethodLabelSnapshot: '历史支付名称（现已停用）', submitterUserName: '测试录单人', formalSalesUserName: '测试成交人',
  remark: '较长订单备注用于检查换行。'.repeat(12), studentSpecialRequirements: '保留完整特殊要求',
  materialDeliveryContact: '测试收件人 · 测试地址', giftShippingAddress: '测试礼品地址',
  items: [{ id: 1, productRef: 'p1', skuRef: 's1', productName: '公共营养师', skuName: '历史课程规格名称', actualAmount: 398,
    categoryPath: ['职业考证', '营养健康'], specs: [{ attrKey: 'level', attrName: '考试等级', value: '2', label: '二级' }] }],
  paymentVouchers: [{ infraFileId: 1, originalName: '测试凭证.png', contentType: 'image/png',
    fileUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ahtcAAAAASUVORK5CYII=' }],
  registrationApproval: { status: 'approved' }, financeApproval: { status: 'approved' },
  historyMissingFields: { leadProfile: 'history_not_recorded' }
}
const profile: SalesOrder = { ...base, id: 101, orderNo: 'OR-LAYOUT-101', studentName: '档案测试学员', historyMissingFields: undefined,
  leadId: 1, leadProfile: { leadNo: 'LD-LAYOUT-1', submittedName: '档案测试学员', submittedMobile: '13800000000', submittedWechatId: 'fixture-wechat',
    sourceLabel: '历史业务来源', sourceUserName: '测试来源人员', ownerUserName: '测试负责人', sourceType: 'internal_new_media', dispatchMode: 'auto' } }
Object.assign(api, {
  managementSalesOrderCursor: async () => ({ list: [base, profile], hasMore: false }),
  managementSalesOrderStatusCounts: async () => ({ total: 2, effective: 2, pendingApproval: 0, revisionRequired: 0, superseded: 0 }),
  managementSalesOrder: async (id: number) => { if (state.failure) throw new Error('测试详情加载失败'); return id === 101 ? profile : base }
})
const shared = new URLSearchParams(location.search).has('shared')
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter>
  {shared ? <SalesOrderDetailCards order={base} mode="team"/> : <MySalesOrderPage/>}
</BrowserRouter></App></ThemeProvider>)
