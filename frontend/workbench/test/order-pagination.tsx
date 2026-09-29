// UTF-8. Synthetic transport only; request counts are exposed to browser acceptance.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import MySalesOrderPage from '../src/pages/MySalesOrderPage'
import { api, http, type SalesOrderListItem } from '../src/services/api'
import { STORAGE_KEYS } from '../src/constants'
localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify({ inboxLayoutMode: 'table' }))
const fixture = { counts: 0, pages: [] as Array<{pageNo?: number; keyword?: string}>, cursors: 0, fail: false, slow: false }
Object.assign(window, { orderPagination: fixture })
const rows = Array.from({length: 65}, (_, i) => ({ id: i+1, orderNo: 'ORDER-PAGE-'+(i+1), studentName: '测试学员'+(i+1), status: 'effective', totalAmount: 100, orderType: 'first_purchase' } as SalesOrderListItem))
api.managementSalesOrderStatusCounts = async () => { fixture.counts++; return { total: 65, pendingApproval: 0, revisionRequired: 0, effective: 65, superseded: 0 } }
api.managementSalesOrderPage = async query => {
 fixture.pages.push(query)
 if (fixture.slow && query.pageNo === 2) await new Promise(resolve => setTimeout(resolve, 500))
 if (fixture.fail) throw new Error('订单列表暂时不可用')
 const offset = ((query.pageNo || 1)-1)*(query.pageSize || 20)
 return { list: rows.slice(offset, offset+(query.pageSize || 20)), total: rows.length }
}
api.managementSalesOrderCursor = async () => { fixture.cursors++; return { list: rows.slice(0,20), hasMore: false } }
api.managementSalesOrder = async () => { throw new Error('测试仅覆盖列表') }
http.defaults.adapter = async config => ({ config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data: config.url?.includes('/catalog') ? { scene: 'order', fields: [] } : [] } })
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><App><MySalesOrderPage /></App></ThemeProvider></MemoryRouter>)
