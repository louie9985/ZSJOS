import {RealtimeProvider} from '../src/components/RealtimeProvider'
import {AnnouncementProvider} from '../src/components/AnnouncementProvider'
import React from 'react'
import {createRoot} from 'react-dom/client'
import {BrowserRouter} from 'react-router-dom'
import {ConfigProvider} from 'antd'
import TodayTasksPage from '../src/pages/TodayTasksPage'
import {api} from '../src/services/api'
import '../src/styles/index.css'
import {metricApi} from '../src/services/homeMetric'
const spec={name:'本月客资成交比率',description:'隔离测试数据：按本人授权范围统计',unit:'',action:'ROUTE',formula:'A / B',percentage:true,decimals:2}
const evaluation={evaluatedAt:Date.parse('2026-10-29T08:00:00Z'),result:{status:'OK',value:'0.125'},inputs:[{name:'A',sourceLabel:'订单',range:{start:Date.parse('2026-10-01T00:00:00+08:00'),end:Date.parse('2026-11-01T00:00:00+08:00')},result:{status:'OK',value:'1'}},{name:'B',result:{status:'OK',value:'8'}}]}
metricApi.visible=async()=>new URLSearchParams(location.search).has('empty')?[]:Array.from({length:Number(new URLSearchParams(location.search).get('count')||12)},(_,i)=>({id:i+1,version:1,enabled:true,spec:{...spec,name:'旧版名称'}}))
metricApi.evaluate=async(id)=>{const status=new URLSearchParams(location.search).get('status');return status?{id,publishedVersion:2,spec,contextId:null,evaluation:{...evaluation,result:{status,value:null},inputs:[]}}:{id,publishedVersion:2,spec:{...spec,name:id===12?'长名称'.repeat(26):spec.name+id,description:id===12?'完整口径说明'.repeat(150):spec.description},contextId:'fixture-context',evaluation}}
metricApi.context=async()=>({id:1,spec,evaluation,expiresAt:'2026-10-29T09:00:00Z'})
metricApi.details=async()=>({list:[{id:1,'客资编号':'KZ-TEST-001','说明':'隔离测试明细'}],total:1})
api.businessTaskSummary=async()=>{if(new URLSearchParams(location.search).has('summaryError'))throw new Error('汇总加载失败');return {overdue:0,today:0,future:0,unscheduled:0}}
api.businessTaskList=async()=>({list:[],total:0})
createRoot(document.getElementById('root')!).render(<ConfigProvider><BrowserRouter><main style={{padding:24}}><p>隔离测试 · 非生产数据</p><RealtimeProvider platform="PC"><AnnouncementProvider enabled={false}><TodayTasksPage permissions={[]} onOpenAssignment={()=>{}}/></AnnouncementProvider></RealtimeProvider></main></BrowserRouter></ConfigProvider>)
