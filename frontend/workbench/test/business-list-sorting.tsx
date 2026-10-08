// UTF-8. Actual page components, isolated synthetic HTTP transport; no business writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { useTheme } from '../src/components/Theme/ThemeContext'
import MySalesOrderPage from '../src/pages/MySalesOrderPage'
import { MyStudentsPage } from '../src/pages/RegistrationPages'
import { CashbackPage, WithdrawalPage } from '../src/pages/ManagementPages'
import { http } from '../src/services/api'
import { STORAGE_KEYS } from '../src/constants'

const params = new URLSearchParams(location.search)
const scene = params.get('scene') || 'student'
localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify({ inboxLayoutMode: params.get('layout') || 'table' }))
const state = { queries: [] as Record<string, unknown>[], fail: false, delay: 0, empty: false }
Object.assign(window, { sortingFixture: state })
const rows = Array.from({ length: 65 }, (_, index) => {
  const id = index + 1
  return { id, personId: id, personNo: `P-${id}`, leadNo: `L-${id.toString().padStart(3,'0')}`,
    name: ['张','陈','李'][index % 3] + id, studentName: ['张','陈','李'][index % 3] + id,
    orderNo: `O-${id.toString().padStart(3,'0')}`, cashbackNo: `C-${id.toString().padStart(3,'0')}`, withdrawalNo: `W-${id.toString().padStart(3,'0')}`,
    totalAmount: 66-id, amount: 66-id, applicationAmount: 66-id, status: scene==='order'?'effective':scene==='cashback'?'available':'approved', type:'deal', orderType:'first_purchase',
    beneficiaryName: '合成受益人', partnerName: '合成合作方', applicantName:'合成申请人', accountNameSnapshot:'合成账户', bankNameSnapshot:'合成银行',
    productNameSnapshot:'合成课程', baseAmount:100, rateSnapshot:0.1, cashbackCount:1,
    submittedAt: `2026-10-${String(index%28+1).padStart(2,'0')}T10:00:00`, activatedAt: `2026-10-${String(index%28+1).padStart(2,'0')}T10:00:00`,
    services: [{ serviceRelationId:id, status:'active', courseName:'合成课程', className:'合成班级', orderNo:`O-${id}`, owner:false }],
    source:{ studentName:'合成学员', orderNo:`O-${id}` },
  }
})
http.defaults.adapter = async config => {
  const url = config.url || ''
  const query = { ...config.params, ...(config.data ? JSON.parse(config.data) : {}) }
  let data: unknown = []
  if (url.includes('/catalog')) data = { fields:[{ fieldKey:'cashback.type',options:[{value:'deal',label:'成交返现'}] },{fieldKey:'cashback.status',options:[{value:'available',label:'可提现'}]},{fieldKey:'withdrawal.status',options:[{value:'approved',label:'待打款'}]}] }
  else if (url.includes('/management-status-counts')) data = { total:65,effective:65,pendingApproval:0,revisionRequired:0,superseded:0 }
  else if (['/zsjos/student/my-page','/zsjos/student/my/search-page','/zsjos/sales-order/management-page','/zsjos/sales-order/management-search-page','/zsjos/sales-order/management-cursor','/zsjos/sales-order/management-search-cursor','/zsjos/cashback/page','/zsjos/cashback/search-page','/zsjos/withdrawal/page','/zsjos/withdrawal/search-page'].includes(url)) {
    state.queries.push({ ...query, url })
    const delay=state.delay
    if (delay) await new Promise(resolve=>setTimeout(resolve,delay))
    if (state.fail) throw new Error('排序列表读取失败，请重试')
    const field=query.sortField as keyof typeof rows[number]
    const sorted=[...rows].sort((a,b)=> {
      if (!field) return a.id-b.id
      const av=a[field],bv=b[field]
      const value=typeof av==='number' && typeof bv==='number'?av-bv:String(av||'').localeCompare(String(bv||''),'zh-CN')
      return value*(query.sortOrder==='descend'?-1:1) || b.id-a.id
    })
    const size=Number(query.pageSize||query.limit||20)
    const offset=url.endsWith('cursor')?Number(query.cursor||0):(Number(query.pageNo||1)-1)*size
    data=url.endsWith('cursor')?{list:state.empty?[]:sorted.slice(offset,offset+size),hasMore:offset+size<65,nextCursor:offset+size<65?String(offset+size):undefined}:{list:state.empty?[]:sorted.slice(offset,offset+size),total:state.empty?0:65}
  } else if (/\/student\/my\/\d+$/.test(url)) data=rows.find(row=>row.id===Number(url.split('/').pop()))
  else if (/\/sales-order\/management\/\d+$/.test(url)) throw new Error('此夹具仅验证列表')
  else if (url.includes('contact-context')) data={availableActions:[],visibleTabs:[],version:1}
  else if (url.endsWith('/contact-records')) data={list:[],total:0}
  else if (url.endsWith('/page') || url.endsWith('/my-page')) data={list:[],total:0}
  return {config,status:200,statusText:'OK',headers:{},data:{code:0,data}}
}
const studentPermissions: string[] = []
function Fixture() {
  const theme=useTheme()
  return <><button id="switch-layout" onClick={()=>theme.setInboxLayoutMode(theme.inboxLayoutMode==='table'?'split':'table')}>切换列表模式</button>
    {scene==='student'?<MyStudentsPage permissions={studentPermissions}/>:scene==='order'?<MySalesOrderPage/>:scene==='cashback'?<CashbackPage permissions={['zsjos:cashback:finance-query']}/>:<WithdrawalPage permissions={['zsjos:withdrawal:finance-query','zsjos:withdrawal:payout']}/>}</>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><ConfigProvider locale={zhCN}><App><Fixture/></App></ConfigProvider></ThemeProvider></MemoryRouter>)
