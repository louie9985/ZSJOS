// UTF-8. Synthetic transport for real components; never calls shared business APIs.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import { api, ApiError, type ExamSchedule } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
const date = dayjs().startOf('month').date(10)
const states = ['DRAFT', 'PUBLISHED', 'UPCOMING', 'IN_PROGRESS', 'ENDED', 'REVOKED'] as const
const state = { mode: params.get('state') || 'success', date: date.format('YYYY-MM-DD'), calls: [] as string[] }
Object.assign(window, { examFullNamesFixture: state })
const rows: ExamSchedule[] = Array.from({ length: Number(params.get('count') || 10) }, (_, i) => {
  const status = states[i % states.length]
  return { id: i + 1, scheduleType: 'EXACT', scheduleName: i === 0 ? '中文完整考试名称'.repeat(12) : i === 1 ? 'LongExamName'.repeat(8) : '验证考试' + (i + 1),
    exactDate: state.date, remark: '此备注仅应出现在当天详情', categoryPathSnapshot: [],
    recordStatus: status === 'DRAFT' || status === 'REVOKED' ? status : 'PUBLISHED', displayStatus: status, reeditExpiresAt: performance.now() + 3600000 }
})
const multi: ExamSchedule[] = params.has('noMulti') ? [] : Array.from({ length: 6 }, (_, i) => ({
  ...rows[i % rows.length], id: 1001 + i, scheduleType: 'MULTI_DAY',
  scheduleName: i === 0 ? '跨周跨月完整名称'.repeat(12) : i === 5 ? '多日单格完整名称'.repeat(12) : '重叠考试' + (i + 1),
  startDate: (i === 0 ? date.startOf('month').subtract(2, 'day') : date).format('YYYY-MM-DD'),
  endDate: (i === 0 ? date.add(1, 'month').date(4) : i === 5 ? date : date.add(2, 'day')).format('YYYY-MM-DD')
}))
async function response(source: ExamSchedule[], pageNo: number, pageSize: number, status?: string, isMulti = false) {
  state.calls.push((isMulti ? 'multi:' : 'exact:') + pageNo)
  await new Promise(resolve => setTimeout(resolve, state.mode === 'loading' ? 1500 : 20))
  if (state.mode === 'denied') throw new ApiError(403, '验证无权访问')
  if (state.mode === 'error' || state.mode === 'multi-error' && isMulti) throw new Error('验证加载失败')
  const filtered = state.mode === 'empty' ? [] : source.filter(row => !status || row.displayStatus === status)
  return { list: filtered.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: filtered.length }
}
api.examCalendar.exactPage = q => response(rows, q.pageNo, q.pageSize, q.displayStatus)
api.examCalendar.multiDayPage = q => response(multi, q.pageNo, q.pageSize, undefined, true)
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>
  <div style={{ height: '100vh' }}><ExamCalendarPage permissions={params.has('viewer') ? [] : ['zsjos:exam-calendar:manage']} /></div>
</App></ThemeProvider></BrowserRouter>)
