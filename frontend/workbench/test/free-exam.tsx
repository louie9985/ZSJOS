import React from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import DeliveryClassPage from '../src/pages/DeliveryClassPage'
import { api, type ExamSchedule, type ExamScheduleInput } from '../src/services/api'
import '../src/styles/index.css'

const state = { exams: [] as ExamSchedule[], multiDay: [{ id: 41, scheduleType: 'MULTI_DAY', scheduleName: '秋季多日考试', startDate: dayjs().startOf('month').format('YYYY-MM-DD'), endDate: dayjs().add(1, 'month').date(5).format('YYYY-MM-DD'), categoryPathSnapshot: [], recordStatus: 'PUBLISHED', displayStatus: 'PUBLISHED' }] as ExamSchedule[], requests: [] as unknown[], fail: false }
Object.assign(window, { freeExamFixture: state })
api.examCalendar.exactPage = async () => ({ list: state.exams, total: state.exams.length })
api.examCalendar.multiDayPage = async () => ({ list: state.multiDay, total: state.multiDay.length })
api.examCalendar.create = async (data: ExamScheduleInput) => {
  state.requests.push(data)
  if (state.fail) throw new Error('测试保存失败')
  const id = 7 + state.requests.length - 1
  const row: ExamSchedule = { ...data, id, categoryPathSnapshot: [], recordStatus: 'DRAFT', displayStatus: 'DRAFT' }
  if (data.scheduleType === 'EXACT') state.exams.push(row)
  else state.multiDay.push(row)
  return id
}
api.examCalendar.update = async (_id, data) => {
  state.requests.push(data)
  state.exams[0] = { ...state.exams[0], ...data }
  return true
}
api.examCalendar.publish = async id => { const row = [...state.exams, ...state.multiDay].find(row => row.id === id)!; row.recordStatus = 'PUBLISHED'; row.displayStatus = 'PUBLISHED'; return true }
api.deliveryClasses.page = async () => ({ list: [], total: 0 })
api.deliveryClasses.exams = async () => [{ id: 7, scheduleType: 'MULTI_DAY', displayName: `自由考试 · ${dayjs().add(1, 'month').format('YYYY-MM-DD')}` }]
api.deliveryClasses.candidates = async () => [{ id: 8, name: '测试班主任' }]
api.deliveryClasses.create = async data => { state.requests.push(data); return 10 }
const permissions = ['zsjos:exam-calendar:manage', 'zsjos:delivery-class:query-managed', 'zsjos:delivery-class:create']
createRoot(document.getElementById('root')!).render(<ConfigProvider locale={zhCN}><BrowserRouter>
  {location.search.includes('class') ? <DeliveryClassPage permissions={permissions} /> : <ExamCalendarPage permissions={permissions} />}
</BrowserRouter></ConfigProvider>)
