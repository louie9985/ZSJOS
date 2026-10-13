import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SalesOrderApprovalPage from '../src/pages/SalesOrderApprovalPage'
import { api, http, type SalesOrder, type SalesOrderListItem } from '../src/services/api'
const state = new URLSearchParams(location.search).get('state')
http.defaults.adapter = async () => { throw new Error('Isolated fixture: no live API') }
const orderStates = [ ['all','全部'],['center_pending','当前中心待审核'],['pending_approval','待审核'],['revision_required','待重提'],['effective','已通过'],['terminated','已终止'],['superseded','历史旧单（已重提）'] ].map(([key,label]) => ({key,label}))
let profileAttempts = 0, attempts = 0
const requests: unknown[] = []
Object.assign(window, { approvalRequests: requests })
api.salesOrderApprovalFilterProfile = async () => {
  if (state === 'profile-error' && profileAttempts++ === 0) throw new Error('筛选方案暂不可用')
  return { groups: [], centers: [{key:'registration',label:'报名履约中心'},{key:'finance',label:'财务中心'}], orderStates }
}
api.advancedFilterCatalog = async () => ({ scene:'order', fields:[] })
api.advancedFilterTemplates = async () => []
const rows: SalesOrderListItem[] = Array.from({length:25}, (_,i) => ({ id:i+1,orderNo:`OD-EXAMPLE-${i+1}`,status:'pending_approval',orderType:'first_purchase',studentName:'测试学员',totalAmount:100,submittedAt:1791518400000,taskId:`task-${i+1}`,taskDefinitionKey:'registrationReview',taskCreateTime:1791518400000,taskEndTime:i===1?1791518500000:undefined,approvalReasonRequired:false }))
rows.push(...orderStates.slice(3).map((item,i) => ({ ...rows[0],id:30+i,orderNo:`OD-${item.key}`,status:item.key as SalesOrder['status'],taskId:`task-${30+i}`,taskEndTime:1791518500000 })))
async function load(params: {orderState?:string;pageNo?:number;pageSize?:number;center?:string;cursor?:string}) {
  requests.push(params)
  if(state==='slow') await new Promise(resolve => setTimeout(resolve, params.orderState==='revision_required'?300:40))
  if(state==='error' && attempts++ === 0) throw new Error('审批列表暂不可用')
  const filtered=state==='empty'?[]:rows.filter(row=>params.orderState==='center_pending' ? row.status==='pending_approval' && row.id!==(params.center==='finance'?1:2) : params.orderState==='all'||row.status===params.orderState)
  const start=(params.pageNo?params.pageNo-1:Number(params.cursor||0))*(params.pageSize||20)
  return {list:filtered.slice(start,start+(params.pageSize||20)).map(row => ({...row, taskDefinitionKey:params.center==='finance'?'financeReview':'registrationReview', taskEndTime:params.orderState==='center_pending'?undefined:row.taskEndTime})),total:filtered.length,hasMore:start+20<filtered.length,nextCursor:start+20<filtered.length?'1':undefined}
}
api.salesOrderApprovalInbox = load
api.salesOrderApprovalCursor = load
api.salesOrder = async id => {
  const row = rows.find(row=>row.id===id)!
  return {...row,items:[],paymentVouchers:[],canRevise:false,registrationApproval:{taskDefinitionKey:'registrationReview',status:row.taskEndTime?'approved':'pending'},financeApproval:{taskDefinitionKey:'financeReview',status:row.status==='effective'?'approved':'pending'}} as SalesOrder
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><SalesOrderApprovalPage permissions={state==='denied'?[]:['zsjos:sales-order:review']}/></BrowserRouter></App></ThemeProvider>)
