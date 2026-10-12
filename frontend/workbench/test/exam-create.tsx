// Synthetic transport only; no real business requests or writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import { api, http, ApiError, type ExamSchedule } from '../src/services/api'
import { examCalendarNote } from '../src/services/examCalendarNote'

const base = { recordStatus: 'DRAFT', displayStatus: 'DRAFT', categoryPathSnapshot: [] } as const
let rows: ExamSchedule[] = [
  { ...base, categoryPathSnapshot: [], id: 1, scheduleName: '过期草稿', scheduleType: 'EXACT', exactDate: '2026-10-07' },
  { ...base, categoryPathSnapshot: [], id: 2, scheduleName: '今天草稿', scheduleType: 'EXACT', exactDate: '2026-10-08' },
  { ...base, categoryPathSnapshot: [], id: 3, scheduleName: '进行中多日', scheduleType: 'MULTI_DAY', startDate: '2026-10-01', endDate: '2026-10-09' }
]
let creates = 0, updates = 0, publishes = 0
const createRequests: unknown[] = []
const ids = new Map<string, number>()
http.defaults.adapter = async () => { throw new Error('禁止真实接口请求') }
examCalendarNote.get = async () => ({ content: '', version: 0, images: [] })
api.examCalendar.exactPage = async () => ({ list: rows.filter(row => row.scheduleType === 'EXACT'), total: 2 })
api.examCalendar.multiDayPage = async () => ({ list: rows.filter(row => row.scheduleType === 'MULTI_DAY'), total: 1 })
api.examCalendar.create = async input => {
  createRequests.push(structuredClone(input))
  if (new URLSearchParams(location.search).has('reject') && createRequests.length === 1) {
    throw new ApiError(1900018031, '模拟附件校验拒绝')
  }
  const existing = ids.get(input.idempotencyKey)
  if (existing != null) return existing
  creates++; const id = 100 + creates
  ids.set(input.idempotencyKey, id)
  rows.push({ ...base, ...input, id, categoryPathSnapshot: [] })
  if (!new URLSearchParams(location.search).has('reject') && creates === 1) throw new Error('模拟已落库但响应丢失')
  return id
}
api.examCalendar.update = async (id, input) => {
  updates++; Object.assign(rows.find(row => row.id === id)!, input); return true
}
api.examCalendar.publish = async id => {
  publishes++
  if (new URLSearchParams(location.search).has('fail') && publishes === 1) throw new Error('模拟发布失败')
  Object.assign(rows.find(row => row.id === id)!, { recordStatus: 'PUBLISHED' }); return true
}
Object.assign(window, { examDatesFixture: { counters: () => ({ creates, updates, publishes }), rows: () => rows, requests: () => createRequests } })
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><ExamCalendarPage permissions={['zsjos:exam-calendar:query', 'zsjos:exam-calendar:manage']} /></App></ThemeProvider></BrowserRouter>)
