import React from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import { api, ApiError, type ExamSchedule, type ExamScheduleInput } from '../src/services/api'
import { withExamClock } from '../src/services/examReedit'
import '../src/styles/index.css'

const state = { rows: [] as ExamSchedule[], creates: 0, updates: [] as number[], claims: [] as string[],
  lostResponse: false, failPublish: false, claimed: new Map<number, { key: string; content: ExamScheduleInput }>() }
function reset(type = 'EXACT', own = true) {
  state.rows = [{ id: 1, scheduleType: type as 'EXACT' | 'MULTI_DAY', scheduleName: '待修正考试',
    exactDate: type === 'EXACT' ? dayjs().format('YYYY-MM-DD') : undefined,
    startDate: type === 'MULTI_DAY' ? dayjs().startOf('month').format('YYYY-MM-DD') : undefined,
    endDate: type === 'MULTI_DAY' ? dayjs().add(1, 'month').date(5).format('YYYY-MM-DD') : undefined,
    remark: '原始完整备注', recordStatus: 'REVOKED', displayStatus: 'REVOKED', categoryPathSnapshot: [],
    reeditDeadline: Date.now() + 300000, canReedit: own }]
  state.creates = 0; state.updates = []; state.claims = []; state.claimed.clear(); state.lostResponse = false; state.failPublish = false
}
reset()
Object.assign(window, { examReeditFixture: state, resetExamReedit: reset })
const list = (type: string) => withExamClock(async () => {
  const list = state.rows.filter(row => row.scheduleType === type && (row.recordStatus !== 'REVOKED' || Number(row.reeditDeadline) > Date.now()))
    .map(row => ({ ...row, serverTime: Date.now() }))
  return { list, total: list.length }
})
api.examCalendar.exactPage = () => list('EXACT')
api.examCalendar.multiDayPage = () => list('MULTI_DAY')
api.examCalendar.revoke = async id => {
  const row = state.rows.find(row => row.id === id)!
  Object.assign(row, { recordStatus: 'REVOKED', displayStatus: 'REVOKED', reeditDeadline: Date.now() + 300000, canReedit: true })
  return true
}
api.examCalendar.reedit = async (id, key) => {
  state.claims.push(key)
  const previous = state.claimed.get(id)
  if (previous) { if (previous.key !== key) throw new Error('重复领取'); return previous.content }
  const row = state.rows.find(row => row.id === id)!
  if (!row.canReedit) throw new Error('无权重新编辑')
  if (Date.now() >= Number(row.reeditDeadline)) throw new Error('已超过五分钟')
  const content = { scheduleType: row.scheduleType, scheduleName: row.scheduleName!, exactDate: row.exactDate,
    startDate: row.startDate, endDate: row.endDate, remark: row.remark }
  state.claimed.set(id, { key, content }); state.rows = state.rows.filter(row => row.id !== id)
  if (state.lostResponse) { state.lostResponse = false; throw new Error('网络响应丢失') }
  return content
}
api.examCalendar.create = async input => {
  state.creates++
  const id = 100 + state.creates
  state.rows.push({ ...input, id, categoryPathSnapshot: [], recordStatus: 'DRAFT', displayStatus: 'DRAFT' })
  return id
}
api.examCalendar.update = async (id, input) => { state.updates.push(id); Object.assign(state.rows.find(row => row.id === id)!, input); return true }
api.examCalendar.publish = async id => {
  if (state.failPublish) { state.failPublish = false; throw new Error('测试发布失败，草稿已保存') }
  Object.assign(state.rows.find(row => row.id === id)!, { recordStatus: 'PUBLISHED', displayStatus: 'PUBLISHED' })
  return true
}
createRoot(document.getElementById('root')!).render(<ConfigProvider locale={zhCN}><BrowserRouter>
  <ExamCalendarPage permissions={['zsjos:exam-calendar:query', 'zsjos:exam-calendar:manage']} />
</BrowserRouter></ConfigProvider>)
