// UTF-8. Real pages and HTTP adapter with synthetic data; no live business requests.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import SubordinateSalesPage from '../src/pages/SubordinateSalesPage'
import { api, http, type ManagedLead, type SubordinateSales } from '../src/services/api'
import '../src/styles/index.css'
const params=new URLSearchParams(location.search)
const state={ valid:false, denied:params.has('denied'), fail:'', uploadFail:false, posts:[] as Record<string, unknown>[], reads:0, salesReads:0 }
Object.assign(window,{overturnFixture:state})
const lead=():ManagedLead => ({id:1,leadNo:'KZ-OV-TEST',submittedName:'改判测试客资',status:state.valid?'valid':'invalid',
  assignmentStatus:'owned',qualificationStatus:state.valid?'valid':'invalid',ownerUserId:20,ownerUserName:'测试销售',
  invalidReasonLabelSnapshot:state.valid?undefined:'原判联系不上',invalidDescription:state.valid?undefined:'原始判定说明',
  validDescription:state.valid?'已核实客户意向':undefined,visibleTabs:['overview','flow-history'],overviewVisible:true,
  availableActions:state.valid||state.denied?[]:[{code:'SUPERVISOR_OVERTURN_VALID',enabled:true,qualificationToken:'test-token'}],
  attachments:[],intendedProducts:[]} as ManagedLead)
const sales=()=>({userId:20,name:'测试销售',username:'fixture',accountStatus:0,accepting:true,presence:'online',
  validLeadCount:state.valid?1:0,convertedLeadCount:0,effectiveOrderAmount:0,deptName:'测试部门',categoryCounts:[]} as SubordinateSales)
api.dictDataByType=async()=>[]
api.leadInboxFilterProfile=async()=>({groups:[{key:'all',label:'全部',sections:[]}]})
api.allLeadPage=async()=>({list:[lead()],total:1})
api.managedLead=async()=>{state.reads++;return lead()}
api.leadFollowUpPage=async()=>({list:[],total:0})
api.leadFlowHistory=async()=>state.valid?[{id:'event:1',businessObject:'客资',flowNode:'主管直接改判有效',source:'员工工作台',operator:'测试主管',leadStatusBefore:'无效',leadStatusAfter:'有效',reason:'已核实客户意向',occurredAt:Date.now(),attachments:[]}]:[]
api.subordinateSalesPage=async()=>{state.salesReads++;return {list:[sales()],total:1}}
api.subordinateSalesOverview=async()=>sales()
api.subordinateSalesLeads=async()=>({list:[lead()],total:1})
api.subordinateSalesTasks=async()=>({list:[],total:0})
http.defaults.adapter=async config=>{
  let data:unknown=[]
  if(config.url?.endsWith('/overturn-attachment/upload')){
    if(state.uploadFail)throw new Error('测试图片上传失败')
    data={infraFileId:100,originalName:'evidence.png',contentType:'image/png',fileSize:68}
  }else if(config.url?.endsWith('/overturn-valid')){
    state.posts.push(JSON.parse(config.data));await new Promise(resolve=>setTimeout(resolve,100))
    if(state.fail)throw new Error(state.fail)
    state.valid=true;data=true
  }
  return {data:{code:0,data},status:200,statusText:'OK',headers:{},config}
}
const permissions=['zsjos:lead:query-all','zsjos:subordinate-sales:query','zsjos:subordinate-sales:lead-overturn-valid']
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC">
  {params.get('entry')==='subordinate'?<SubordinateSalesPage permissions={permissions}/>:<LeadManagementPage permissions={permissions}/>}
</RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
