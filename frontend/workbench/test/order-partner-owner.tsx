// UTF-8. Synthetic transport; no authenticated requests or business writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import MySalesOrderPage from '../src/pages/MySalesOrderPage'
import { api, http, type SalesOrder } from '../src/services/api'
import { salesOrderDetailToListItem } from '../src/services/salesOrder'
const scenario = new URLSearchParams(location.search).get('scenario') || 'assigned'
const sourceType = scenario === 'other' ? 'internal_new_media' : 'partner'
const partnerOwnerSnapshot = scenario === 'historical' ? undefined : scenario === 'unassigned' ? {} : { employeeUserId: 80, employeeName: '提交时新媒体员工甲' }
const order = { id: 1, orderNo: 'OR-OWNER-TEST', personId: 1, status: 'effective', orderType: 'first_purchase', studentName: '快照测试学员', buyerName: '测试', totalAmount: 100, submittedAt: 1791800000000, version: 0, approvalRoundVersion: 0, items: [], paymentVouchers: [], leadProfile: { leadNo: 'LD-OWNER-TEST', submittedName: '快照测试学员', sourceType, sourceLabel: sourceType === 'partner' ? '兼职提交' : '新媒体提交', sourceUserName: '测试兼职', partnerOwnerSnapshot } } as SalesOrder
http.defaults.adapter = async config => { throw new Error(`Fixture blocks transport: ${config.url}`) }
Object.assign(api, {
 managementSalesOrderCursor: async () => ({ list: [salesOrderDetailToListItem(order)], hasMore: false }),
 managementSalesOrderPage: async () => ({ list: [salesOrderDetailToListItem(order)], total: 1 }),
 managementSalesOrderStatusCounts: async () => ({ total: 1, effective: 1, pendingApproval: 0, revisionRequired: 0, superseded: 0 }),
 managementSalesOrder: async () => order
})
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><MySalesOrderPage/></BrowserRouter></App></ThemeProvider>)
