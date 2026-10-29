import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App, Layout } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'
import TodayTasksPage from '../src/pages/TodayTasksPage'
import { api, http } from '../src/services/api'
http.defaults.adapter = async () => { throw new Error('Fixture blocks real requests') }
api.businessTaskSummary = async () => ({ overdue: 2, today: 8, future: 1, unscheduled: 9 })
api.businessTaskList = async (q: any) => {
 if(q.status !== 'pending') throw new Error('Only pending allowed')
 return { total: q.bucket === 'unscheduled' ? 9 : 8, list: Array.from({length: q.pageNo === 2 ? 2 : 6}, (_,i) => ({id: i+q.pageNo*10, title: (q.bucket === 'unscheduled' ? '提交人协助：' : '今日跟进：') + q.pageNo + '-' + i, summary: '需要联系确认的任务内容', status: 'pending', actionable: false, dueAt: q.bucket === 'unscheduled' ? undefined : '2026-10-29 18:00:00'})) } as any
}
const tabs = new URLSearchParams(location.search).has('tabs')
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter><RealtimeProvider platform="PC"><AnnouncementProvider enabled={false}><Layout className={`crm-shell ${tabs ? "tabs-enabled" : ""}`}><Layout.Header className="crm-header">测试页眉</Layout.Header>{tabs && <div style={{height: 34, flexShrink: 0}}>首页页签</div>}<Layout className="content-layout"><Layout.Content><TodayTasksPage permissions={[]} onOpenAssignment={()=>{}} /></Layout.Content></Layout></Layout></AnnouncementProvider></RealtimeProvider></MemoryRouter></App></ThemeProvider>)
