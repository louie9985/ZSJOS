// Synthetic HTTP transport; executes the production page and typed delete request.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import { api, http, type ExamSchedule } from '../src/services/api'
import { examCalendarNote } from '../src/services/examCalendarNote'

const query = new URLSearchParams(location.search)
const manager = !query.has('readonly')
const base = { recordStatus: 'DRAFT', displayStatus: 'DRAFT', categoryPathSnapshot: [] } as const
let rows: ExamSchedule[] = [
  { ...base, categoryPathSnapshot: [], id: 1, scheduleName: '过期草稿', scheduleType: 'EXACT', exactDate: '2026-10-07' },
  { ...base, categoryPathSnapshot: [], id: 2, scheduleName: '今天草稿', scheduleType: 'EXACT', exactDate: '2026-10-08' },
  { ...base, categoryPathSnapshot: [], id: 3, scheduleName: '多日草稿', scheduleType: 'MULTI_DAY', startDate: '2026-10-01', endDate: '2026-10-09' },
  { ...base, categoryPathSnapshot: [], id: 4, scheduleName: '已发布考期', scheduleType: 'EXACT', exactDate: '2026-10-08', recordStatus: 'PUBLISHED', displayStatus: 'PUBLISHED' }
]
const requests: { method?: string; url?: string }[] = []
http.defaults.adapter = async config => {
  if (config.method !== 'delete' || !config.url?.startsWith('/zsjos/exam-calendar/delete/')) throw new Error('禁止真实接口请求')
  requests.push({ method: config.method, url: config.url })
  await new Promise(resolve => setTimeout(resolve, 300))
  const id = Number(config.url.split('/').pop())
  let data: { code: number; msg: string; data?: boolean } = { code: 0, msg: '', data: true }
  if (query.has('fail') && requests.length === 1) data = { code: 1900018005, msg: '当前考期状态不允许该操作' }
  else if (!manager) data = { code: 403, msg: '没有管理考期权限' }
  else rows = rows.filter(row => row.id !== id)
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}
examCalendarNote.get = async () => ({ content: '', version: 0, images: [] })
const pageRows = (type: string) => {
  const list = rows.filter(row => row.scheduleType === type && (manager || row.recordStatus === 'PUBLISHED'))
  return { list: structuredClone(list), total: list.length }
}
api.examCalendar.exactPage = async () => pageRows('EXACT')
api.examCalendar.multiDayPage = async () => pageRows('MULTI_DAY')
Object.assign(window, { examDeleteFixture: { requests: () => requests, rows: () => rows } })
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><ExamCalendarPage
  permissions={manager ? ['zsjos:exam-calendar:query', 'zsjos:exam-calendar:manage'] : ['zsjos:exam-calendar:query']} />
</App></ThemeProvider></BrowserRouter>)
