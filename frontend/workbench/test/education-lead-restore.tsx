// UTF-8. Real pages and HTTP adapter with synthetic data; no live business requests.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import { api, http, type ManagedLead } from '../src/services/api'
import '../src/styles/index.css'
const params=new URLSearchParams(location.search)
const state={ valid:false, denied:params.has('denied'), fail:params.has('error')?'负责人状态已变化，请刷新后重试':'', posts:[] as Record<string, unknown>[], reads:0 }
Object.assign(window,{restoreFixture:state})
const lead=():ManagedLead => ({id:1,leadNo:'KZ-RESTORE-TEST',submittedName:'恢复测试客资',status:state.valid?'submitted':'suspended',
  assignmentStatus:'owned',qualificationStatus:'pending',ownerUserId:20,ownerUserName:'测试学习规划师',ownerIdentity:'education',sourceType:'education_self_sourced',operationalStatus:state.valid?'active':'suspended',
  sourceLabel:'教务自拓录',visibleTabs:['overview'],overviewVisible:true,
  availableActions:state.valid||state.denied?[]:[{code:'SUPERVISOR_RESTORE',enabled:true}],
  attachments:[],intendedProducts:[]} as ManagedLead)
api.dictDataByType=async()=>[]
api.leadInboxFilterProfile=async()=>({groups:[{key:'all',label:'全部',sections:[]}]})
api.allLeadPage=async()=>({list:[lead()],total:1})
api.managedLead=async()=>{state.reads++;return lead()}
api.leadFollowUpPage=async()=>({list:[],total:0})
http.defaults.adapter=async config=>{
  let data:unknown=[]
  if(config.url?.endsWith('/restore')){
    state.posts.push(JSON.parse(config.data));await new Promise(resolve=>setTimeout(resolve,100))
    if(state.fail){const failure=state.fail;state.fail='';throw new Error(failure)}
    state.valid=true;data=true
  }
  return {data:{code:0,data},status:200,statusText:'OK',headers:{},config}
}
const permissions=['zsjos:lead:query-owned','zsjos:subordinate-sales:lead-restore']
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC">
  <LeadManagementPage permissions={permissions}/>
</RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
