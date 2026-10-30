import React from 'react'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import TodayTasksPage from '../src/pages/TodayTasksPage'
import { api, type BusinessTask } from '../src/services/api'
import '../src/styles/pages/today-tasks.css'
const state = { cancelled: [] as {id: number; reason: string}[], fail: false }
Object.assign(window, { cancellationFixture: state })
const rows: BusinessTask[] = [1,2].map(id => ({id, taskType: id === 1 ? 'sales_order_revision' : 'lead_submitter_assist', bizType:'lead', bizId:id, title:id === 1 ? '订单补正测试' : '新媒体协助测试', status:'pending', createTime:0, overdue:false, actionable:true, cancellable:true, actionCode:'OPEN_SALES_ORDER_REVISION'}))
api.businessTaskList = async params => { const list=params.bucket === 'unscheduled' ? rows.filter(t => !state.cancelled.some(c => c.id === t.id)) : []; return {list,total:list.length} }
api.businessTaskSummary = async () => ({today:0,overdue:0,future:0,unscheduled:2-state.cancelled.length})
api.cancelBusinessTask = async (id,reason) => { if(state.fail) throw new Error('测试提交失败'); state.cancelled.push({id,reason}); return true }
const permissions = new URLSearchParams(location.search).has('denied') ? ['zsjos:business-task:query'] : ['zsjos:business-task:query','zsjos:business-task:cancel']
createRoot(document.getElementById('root')!).render(<ConfigProvider><MemoryRouter><RealtimeProvider platform="PC"><AnnouncementProvider enabled={false}><TodayTasksPage permissions={permissions} onOpenAssignment={() => {}} /></AnnouncementProvider></RealtimeProvider></MemoryRouter></ConfigProvider>)
