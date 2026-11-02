import {App} from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import {OverlayCoordinatorProvider} from '../src/components/OverlayCoordinator'
import {WorkbenchPageNavigation} from '../src/components/WorkbenchPageNavigation'
import {STORAGE_KEYS} from '../src/constants'
import {RealtimeProvider} from '../src/components/RealtimeProvider'
import {AnnouncementProvider} from '../src/components/AnnouncementProvider'
import React from 'react'
import {createRoot} from 'react-dom/client'
import {BrowserRouter} from 'react-router-dom'
import {ConfigProvider} from 'antd'
import TodayTasksPage from '../src/pages/TodayTasksPage'
import {api,http} from '../src/services/api'
import '../src/styles/index.css'
import {metricApi} from '../src/services/homeMetric'
localStorage.setItem(STORAGE_KEYS.THEME,JSON.stringify({inboxLayoutMode:new URLSearchParams(location.search).get('layout')||'table'}))
const sourceKey=new URLSearchParams(location.search).get('source')||'lead'
const spec={operands:[{calculation:{name:'A',source:sourceKey}},{calculation:{name:'B',source:sourceKey}}],name:'本月客资成交比率',description:'隔离测试数据：按本人授权范围统计',unit:'',action:'ROUTE',formula:'A / B',percentage:true,decimals:2}
const evaluation={evaluatedAt:Date.parse('2026-10-29T08:00:00Z'),result:{status:'OK',value:'0.125'},inputs:[{name:'A',sourceLabel:'订单',range:{start:Date.parse('2026-10-01T00:00:00+08:00'),end:Date.parse('2026-11-01T00:00:00+08:00')},result:{status:'OK',value:'1'}},{name:'B',result:{status:'OK',value:'8'}}]}
metricApi.visible=async()=>new URLSearchParams(location.search).has('empty')?[]:Array.from({length:Number(new URLSearchParams(location.search).get('count')||12)},(_,i)=>({id:i+1,version:1,enabled:true,spec:{...spec,name:'旧版名称'}}))
metricApi.evaluate=async(id)=>{const status=new URLSearchParams(location.search).get('status');return status?{id,publishedVersion:2,spec,contextId:null,evaluation:{...evaluation,result:{status,value:null},inputs:[]}}:{id,publishedVersion:2,spec:{...spec,name:id===12?'长名称'.repeat(26):spec.name+id,description:id===12?'完整口径说明'.repeat(150):spec.description},contextId:'fixture-context',evaluation}}
metricApi.context=async()=>({id:1,spec,evaluation,expiresAt:'2026-10-29T09:00:00Z'})
metricApi.details=async()=>({list:[{id:1,'客资编号':'KZ-TEST-001','说明':'隔离测试明细'}],total:1})
api.businessTaskSummary=async()=>{if(new URLSearchParams(location.search).has('summaryError'))throw new Error('汇总加载失败');return {overdue:0,today:0,future:0,unscheduled:0}}
api.businessTaskList=async()=>({list:[],total:0})
const lead={id:1,leadNo:'KZ-TEST-001',submittedName:'测试客资',status:'submitted',assignmentStatus:'owned',sourceType:'education_self_sourced',sourceLabel:'教务自拓录',submittedAt:1793491200000,visibleTabs:['overview'],overviewVisible:true,availableActions:[],attachments:[],intendedProducts:[]}
const requests:unknown[]=[]
Object.assign(window,{metricRequests:requests})
http.defaults.adapter=async config=>{
 const url=config.url||'';const query=typeof config.data==='string'?JSON.parse(config.data):config.data||config.params||{};requests.push({url,query})
 let data:unknown=[]
 if(url.endsWith('/source-page')) { if(query.query?.keyword==='fail')throw new Error('模拟明细加载失败');data={list:sourceKey==='lead'&&query.query?.keyword!=='empty'?[lead]:[],total:sourceKey==='lead'&&query.query?.keyword!=='empty'?1:0} }
 else if(url.endsWith('/filter-profile'))data={groups:[]}
 else if(url.endsWith('/catalog'))data={fields:[],relativeDateOptions:[]}
 else if(url.endsWith('/lead/get'))data=lead
 else if(url.endsWith('/page')||url.endsWith('-page')||url.endsWith('/search-page'))data={list:[],total:0}
 return {data:{code:0,data},config,status:200,statusText:'OK',headers:{}}
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><WorkbenchPageNavigation canOpen={()=>true}><main style={{padding:24}}><p>隔离测试 · 非生产数据</p><RealtimeProvider platform="PC"><AnnouncementProvider enabled={false}><TodayTasksPage permissions={['zsjos:lead:query','zsjos:lead:query-owned','zsjos:sales-order:query-management','zsjos:student:query-my','zsjos:registration:query-pool','zsjos:lead:appeal:query','zsjos:lead-duplicate-review:query','zsjos:media-student:query-my','zsjos:subordinate-sales:query','zsjos:education-team:query','zsjos:cashback:my-query','zsjos:withdrawal:my-query','bpm:task:query','zsjos:business-task:query']} onOpenAssignment={()=>{}}/></AnnouncementProvider></RealtimeProvider></main></WorkbenchPageNavigation></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
