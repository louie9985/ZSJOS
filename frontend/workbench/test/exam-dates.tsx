// Synthetic transport only; no real business requests or writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import { api, http, type ExamSchedule } from '../src/services/api'
import { examCalendarNote } from '../src/services/examCalendarNote'

const base = { recordStatus: 'DRAFT', displayStatus: 'DRAFT', categoryPathSnapshot: [] } as const
let rows: ExamSchedule[] = [
  { ...base, categoryPathSnapshot: [], id: 1, scheduleName: '过期草稿', scheduleType: 'EXACT', exactDate: '2026-10-07' },
  { ...base, categoryPathSnapshot: [], id: 2, scheduleName: '今天草稿', scheduleType: 'EXACT', exactDate: '2026-10-08' },
  { ...base, categoryPathSnapshot: [], id: 3, scheduleName: '进行中多日', scheduleType: 'MULTI_DAY', startDate: '2026-10-01', endDate: '2026-10-09' }
]
let creates = 0, updates = 0, publishes = 0
http.defaults.adapter = async () => { throw new Error('禁止真实接口请求') }
examCalendarNote.get = async () => ({ content: '', version: 0, images: [] })
api.examCalendar.exactPage = async () => ({ list: rows.filter(row => row.scheduleType === 'EXACT'), total: 2 })
api.examCalendar.multiDayPage = async () => ({ list: rows.filter(row => row.scheduleType === 'MULTI_DAY'), total: 1 })
api.examCalendar.create = async input => {
  creates++; const id = 100 + creates
  rows.push({ ...base, ...input, id, categoryPathSnapshot: [] }); return id
}
api.examCalendar.update = async (id, input) => {
  updates++; Object.assign(rows.find(row => row.id === id)!, input); return true
}
api.examCalendar.publish = async id => {
  publishes++
  if (new URLSearchParams(location.search).has('fail') && publishes === 1) throw new Error('模拟发布失败')
  Object.assign(rows.find(row => row.id === id)!, { recordStatus: 'PUBLISHED' }); return true
}
Object.assign(window, { examDatesFixture: { counters: () => ({ creates, updates, publishes }), rows: () => rows } })
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><ExamCalendarPage permissions={['zsjos:exam-calendar:query', 'zsjos:exam-calendar:manage']} /></App></ThemeProvider></BrowserRouter>)
