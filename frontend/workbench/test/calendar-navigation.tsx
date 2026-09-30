// UTF-8. Isolated real-component fixture: synthetic data, no shared API requests or writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useEffect, useState } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import PersonalCalendarPage from '../src/pages/PersonalCalendarPage'
import LeadFollowUpCalendarPage from '../src/pages/LeadFollowUpCalendarPage'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import CourseCalendarPage from '../src/pages/CourseCalendarPage'
import MediaCalendarPage from '../src/pages/MediaCalendarPage'
import { api } from '../src/services/api'
import { leadCalendarApi } from '../src/services/leadCalendar'

const params = new URLSearchParams(location.search)
const scenario = params.get('state') || 'success'
const calls: string[] = []
let sequence = 0
async function request(start: string, end: string) {
  calls.push(start + ' → ' + end)
  const call = ++sequence
  await new Promise(resolve => setTimeout(resolve, scenario === 'slow' && call % 2 === 0 ? 1600 : 50))
  if (scenario === 'error') throw new Error('验证用加载失败')
}
api.dictDataByType = async () => []
api.mediaAccount.calendarCandidates = async () => ({ directors: [], operators: [] })
api.personalCalendar.list = async q => {
  await request(q.rangeStart, q.rangeEnd)
  return scenario === 'empty' ? [] : [{ id: 1, title: '验证日程', startTime: dayjs(q.rangeStart).add(10, 'day').hour(9).format('YYYY-MM-DDTHH:mm:ss'), endTime: dayjs(q.rangeStart).add(10, 'day').hour(10).format('YYYY-MM-DDTHH:mm:ss'), allDay: false }] as Awaited<ReturnType<typeof api.personalCalendar.list>>
}
api.courseCalendar.page = async q => {
  await request(q.rangeStart!, q.rangeEnd!)
  return scenario === 'empty' ? [] : [{ id: 1, courseName: '验证课程 ' + q.rangeStart?.slice(0, 10), startTime: dayjs(q.rangeStart).add(10, 'day').hour(9).format('YYYY-MM-DDTHH:mm:ss'), endTime: dayjs(q.rangeStart).add(10, 'day').hour(10).format('YYYY-MM-DDTHH:mm:ss'), courseFormLabelSnapshot: '验证课程形式' }] as Awaited<ReturnType<typeof api.courseCalendar.page>>
}
api.examCalendar.exactPage = async q => {
  await request(q.rangeStart!, q.rangeEnd!)
  return { list: scenario === 'empty' ? [] : [{ id: 1, scheduleName: '验证考期', scheduleType: 'EXACT', exactDate: dayjs(q.rangeStart).add(10, 'day').format('YYYY-MM-DD'), recordStatus: 'PUBLISHED', displayStatus: 'UPCOMING' }], total: scenario === 'empty' ? 0 : 1 } as Awaited<ReturnType<typeof api.examCalendar.exactPage>>
}
api.examCalendar.multiDayPage = async () => ({ list: [], total: 0 })
api.mediaAccount.calendar = async q => {
  await request(q.rangeStart, q.rangeEnd)
  return { list: scenario === 'empty' ? [] : [{ id: 1, nickname: '验证账号', accountNo: 'TEST-001', studentName: '示例学员', platformLabelSnapshot: '示例平台', startDate: q.rangeStart, endDate: q.rangeEnd }], total: scenario === 'empty' ? 0 : 1, unscheduledCount: 0 } as Awaited<ReturnType<typeof api.mediaAccount.calendar>>
}
leadCalendarApi.days = async (start, end) => {
  await request(start, end)
  return scenario === 'empty' ? [] : [{ date: dayjs(start).add(10, 'day').format('YYYY-MM-DD'), count: 2 }]
}
leadCalendarApi.cards = async () => ({ list: [], total: 0 })
const pages = { personal: PersonalCalendarPage, lead: LeadFollowUpCalendarPage, exam: ExamCalendarPage, course: CourseCalendarPage, media: MediaCalendarPage }

function Diagnostics() {
  const [text, setText] = useState('等待页面渲染')
  useEffect(() => {
    const timer = setInterval(() => {
      const buttons = [...document.querySelectorAll<HTMLElement>('.calendar-side-navigation-arrow')]
      const content = document.querySelector<HTMLElement>('.calendar-side-navigation-content')
      if (buttons.length !== 2 || !content) { setText('导航未渲染'); return }
      const [left, right] = buttons.map(button => button.getBoundingClientRect()), body = content.getBoundingClientRect()
      const outside = left.right <= body.left && right.left >= body.right
      const centered = Math.abs(left.top + left.height / 2 - (body.top + body.height / 2)) < 2
      const fits = right.right <= innerWidth && left.left >= 0 && document.documentElement.scrollWidth <= innerWidth
      setText((outside && centered && fits ? '布局通过' : '布局待检查') + ' · 两侧独立 ' + outside + ' · 垂直居中 ' + centered + ' · 页面无横溢出 ' + fits + ' · 视口 ' + innerWidth + 'px · 最近请求 ' + (calls.at(-1) || '无'))
    }, 250)
    return () => clearInterval(timer)
  }, [])
  return <output aria-label="校验结果" style={{ display: 'block', padding: 8, fontSize: 12 }}>{text}</output>
}
function Inner() {
  const Page = pages[(params.get('scene') || 'exam') as keyof typeof pages] || ExamCalendarPage
  const permissions = scenario === 'denied' ? [] : ['zsjos:personal-calendar:query', 'zsjos:lead-follow-up-calendar:query', 'zsjos:lead:query']
  return <BrowserRouter><ThemeProvider><App><div style={{ height: 750 }}><Page permissions={permissions} /></div><Diagnostics /></App></ThemeProvider></BrowserRouter>
}
function Host() {
  const [scene, setScene] = useState(params.get('scene') || 'exam')
  const [width, setWidth] = useState(params.has('mobile') ? '390' : '1200')
  const [state, setState] = useState(params.get('state') || 'success')
  return <div style={{ padding: 12 }}><div style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
    <label>验证页面 <select value={scene} onChange={event => setScene(event.target.value)}>{Object.entries({ personal: '我的日历', lead: '销售客资跟进日历', exam: '考期日历', course: '课程日历', media: '账号日历' }).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</select></label>
    <label>视口 <select value={width} onChange={event => setWidth(event.target.value)}><option value="1200">桌面</option><option value="390">手机</option><option value="320">窄屏</option></select></label>
    <label>数据状态 <select value={state} onChange={event => setState(event.target.value)}>{['success', 'empty', 'error', 'slow', 'denied'].map(value => <option key={value}>{value}</option>)}</select></label>
  </div><iframe title="实际日历页面" src={'?inner=1&scene=' + scene + '&state=' + state} style={{ width: Number(width), maxWidth: '100%', height: 930, border: '1px solid #ddd' }} /></div>
}
createRoot(document.getElementById('root')!).render(params.has('inner') ? <Inner /> : <Host />)
