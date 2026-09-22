// UTF-8. Isolated visual fixture; data is supplied only by the browser test.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SalesPerformancePage from '../src/pages/SalesPerformancePage'
import SalesPerformanceTargetPage from '../src/pages/SalesPerformanceTargetPage'
// Opt-in transport fixture matching the server's numeric LocalDateTime JSON shape.
import { http } from '../src/services/api'
if (new URLSearchParams(location.search).has('timestamps')) {
 const start = Date.parse('2026-08-31T16:00:00Z'), end = Date.parse('2026-09-22T16:00:00Z')
 const metric = {key:'month',label:'本月',start,end,amount:1000,orders:1,converted:1,denominator:2,rate:0.5,average:1000}
 const target = {scopeType:'SELF',name:'测试销售',periodType:'month',periodStart:'2026-09-01',floorAmount:2000,sprintAmount:4000,automaticFloor:null,automaticSprint:null,manual:false,complete:true,missing:0}
 http.defaults.adapter = async config => {
  const path = config.url ?? ''
  let data: unknown
  if(path.endsWith('/tree')) data=[{key:'self',title:'测试销售',scopeType:'SELF',selectable:true}]
  else if(path.endsWith('/overview')) data={asOf:end,attributionAvailableSince:start,targets:[{key:'month',label:'本月',actual:metric,target}],performance:[metric],conversion:[metric],pending:{},missingAttributionOrders:0,missingAttributionAmount:0,canDetail:true}
  else if(path.endsWith('/details')) {
   if(config.params.start!=='2026-09-01'||config.params.end!=='2026-09-22') throw new Error('周期边界错误')
   data={list:[{id:1,number:'TEST-001',kind:'order',label:'测试订单',occurredAt:start,amount:1000,state:'审批通过'}],total:1}
  } else data=[]
  return {data:{code:0,data},status:200,statusText:'OK',headers:{},config}
 }
}
const permissions=['zsjos:sales-performance:query','zsjos:sales-performance:self','zsjos:sales-performance:department','zsjos:sales-performance:center','zsjos:sales-performance:detail','zsjos:sales-performance-target:query','zsjos:sales-performance-target:update','zsjos:sales-performance-target:configure']
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>{location.search.includes('target')?<SalesPerformanceTargetPage permissions={permissions}/>:<SalesPerformancePage permissions={permissions}/>}</App></ThemeProvider></BrowserRouter>)
