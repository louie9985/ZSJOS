import {metricApi} from '../src/services/homeMetric'
import React from 'react'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import TodayTasksPage from '../src/pages/TodayTasksPage'
import { api, type BusinessTask } from '../src/services/api'
import '../src/styles/pages/today-tasks.css'
const state = { cancelled: [] as {id: number; reason: string}[], fail: false, metricEvaluations: 0 }
Object.assign(window, { cancellationFixture: state })
const rows: BusinessTask[] = [1,2].map(id => ({id, taskType: id === 1 ? 'sales_order_revision' : 'lead_submitter_assist', bizType:'lead', bizId:id, title:id === 1 ? '订单补正测试' : '新媒体协助测试', status:'pending', createTime:0, overdue:false, actionable:true, cancellable:true, actionCode:'OPEN_SALES_ORDER_REVISION'}))
api.businessTaskList = async params => { const list=params.bucket === 'unscheduled' ? rows.filter(t => !state.cancelled.some(c => c.id === t.id)) : []; return {list,total:list.length} }
api.businessTaskSummary = async () => ({today:0,overdue:0,future:0,unscheduled:2-state.cancelled.length})
api.cancelBusinessTask = async (id,reason) => { if(state.fail) throw new Error('测试提交失败'); state.cancelled.push({id,reason}); return true }
const metricSpec={name:'动态业务待办',description:'隔离测试：取消后刷新',unit:'项',action:'MODAL',formula:'A',percentage:false,decimals:0,operands:[{calculation:{source:'business_todo'}}]}
metricApi.visible=async()=>[{id:1,version:1,publishedVersion:1,enabled:true,spec:metricSpec}]
metricApi.evaluate=async(id)=>{state.metricEvaluations++;return {id,publishedVersion:1,spec:metricSpec,contextId:null,evaluation:{evaluatedAt:Date.now(),result:{status:'OK',value:String(2-state.cancelled.length)},inputs:[]}}}
const permissions = new URLSearchParams(location.search).has('denied') ? ['zsjos:business-task:query'] : ['zsjos:business-task:query','zsjos:business-task:cancel']
createRoot(document.getElementById('root')!).render(<ConfigProvider><MemoryRouter><RealtimeProvider platform="PC"><AnnouncementProvider enabled={false}><TodayTasksPage permissions={permissions} onOpenAssignment={() => {}} /></AnnouncementProvider></RealtimeProvider></MemoryRouter></ConfigProvider>)
