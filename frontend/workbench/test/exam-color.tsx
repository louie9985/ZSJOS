// Synthetic transport fixture; all writes stay inside this browser tab.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import { api, http, type ExamSchedule } from '../src/services/api'
import { examCalendarNote } from '../src/services/examCalendarNote'
import { withExamClock } from '../src/services/examReedit'

const params = new URLSearchParams(location.search)
const key = 'exam-color-fixture-' + (params.get('case') || 'main')
const base = { categoryPathSnapshot: [], recordStatus: 'DRAFT', displayStatus: 'DRAFT' } as const
let rows: ExamSchedule[] = JSON.parse(sessionStorage.getItem(key) || 'null') || [
  { ...base, categoryPathSnapshot: [], id: 1, scheduleName: '浅色单日考试', scheduleType: 'EXACT', exactDate: dayjs().format('YYYY-MM-DD'), backgroundColor: '#FFCC00' },
  { ...base, categoryPathSnapshot: [], id: 2, scheduleName: '深色跨周多日考试', scheduleType: 'MULTI_DAY', startDate: dayjs().startOf('month').add(4, 'day').format('YYYY-MM-DD'), endDate: dayjs().startOf('month').add(20, 'day').format('YYYY-MM-DD'), backgroundColor: '#001133' },
  { ...base, categoryPathSnapshot: [], id: 3, scheduleName: '默认底色考试', scheduleType: 'EXACT', exactDate: dayjs().format('YYYY-MM-DD') }
]
let failures = 0
http.defaults.adapter = async () => { throw new Error('隔离验收禁止真实接口调用') }
examCalendarNote.get = async () => ({ content: '', version: 0, images: [] })
const persist = () => { sessionStorage.setItem(key, JSON.stringify(rows)); return true }
const list = (type: string) => withExamClock(async () => ({ list: rows.filter(row => row.scheduleType === type)
  .map(row => ({ ...row, serverTime: Date.now() })), total: rows.filter(row => row.scheduleType === type).length }))
api.examCalendar.exactPage = () => list('EXACT')
api.examCalendar.multiDayPage = () => list('MULTI_DAY')
api.examCalendar.create = async input => {
  const id = Math.max(0, ...rows.map(row => row.id)) + 1
  rows.push({ ...base, ...input, id, categoryPathSnapshot: [] }); persist(); return id
}
api.examCalendar.update = async (id, input) => {
  if (params.has('fail') && failures++ === 0) throw new Error('模拟保存失败，请重试')
  Object.assign(rows.find(row => row.id === id)!, input); return persist()
}
api.examCalendar.publish = async id => {
  Object.assign(rows.find(row => row.id === id)!, { recordStatus: 'PUBLISHED', displayStatus: 'PUBLISHED' }); return persist()
}
api.examCalendar.revoke = async id => {
  Object.assign(rows.find(row => row.id === id)!, { recordStatus: 'REVOKED', displayStatus: 'REVOKED', canReedit: true, reeditDeadline: Date.now() + 300000 }); return persist()
}
api.examCalendar.reedit = async id => {
  const row = rows.find(row => row.id === id)!
  rows = rows.filter(row => row.id !== id); persist()
  return { scheduleName: row.scheduleName!, scheduleType: row.scheduleType, exactDate: row.exactDate,
    startDate: row.startDate, endDate: row.endDate, backgroundColor: row.backgroundColor, remark: row.remark }
}
const permissions = params.has('denied') ? [] : ['zsjos:exam-calendar:query', ...(params.has('readonly') ? [] : ['zsjos:exam-calendar:manage'])]
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><ExamCalendarPage permissions={permissions} /></App></ThemeProvider></BrowserRouter>)
