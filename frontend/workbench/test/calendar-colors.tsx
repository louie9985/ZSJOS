import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import PersonalCalendarPage from '../src/pages/PersonalCalendarPage'
import CourseCalendarPage from '../src/pages/CourseCalendarPage'
import { api, type PersonalCalendarEvent, type CourseCalendarEvent } from '../src/services/api'
import { calendarColors, type CalendarPalette } from '../src/services/calendarColors'
const params = new URLSearchParams(location.search)
const course = params.get('scene') === 'course'
const presets = [{ id: 'a', name: '公共营养师', color: '#6ABF6D' }, { id: 'b', name: '营养指导员', color: '#00D0FF' }, { id: 'c', name: '注册营养师', color: '#B695E8' }, { id: 'd', name: '执业药师', color: '#FFBE73' }]
const read = <T,>(key: string, fallback: T): T => JSON.parse(sessionStorage.getItem(key) || JSON.stringify(fallback))
const write = (key: string, value: unknown) => sessionStorage.setItem(key, JSON.stringify(value))
if (params.has('grid') && !sessionStorage.getItem('grid-seeded')) {
  const day = dayjs().format('YYYY-MM-DD')
  const timed = [
    {id:10,title:'重叠长安排',startTime:day+'T09:00:00',endTime:day+'T12:00:00',backgroundColor:'#6ABF6D'},
    {id:11,title:'重叠短安排',startTime:day+'T09:30:00',endTime:day+'T10:30:00',backgroundColor:'#B695E8'},
    {id:12,title:'跨午夜安排',startTime:dayjs(day).subtract(1,'day').format('YYYY-MM-DD')+'T23:00:00',endTime:day+'T01:00:00',backgroundColor:'#FFBE73'}
  ]
  write('events-personal',[...timed.map(e=>({...e,allDay:false})),{id:13,title:'全天安排',allDay:true,startTime:day+'T00:00:00',endTime:dayjs(day).add(1,'day').format('YYYY-MM-DD')+'T00:00:00'}])
  write('events-course',timed.map(e=>({...e,courseName:e.title,courseFormValue:'LIVE',courseFormLabelSnapshot:'直播',attachmentIds:[]})))
  write('grid-seeded',true)
}
let colorLoads = 0
let colorSaves = 0
calendarColors.get = async type => { if (params.has('error') && colorLoads++ < 2) throw new Error('颜色加载失败'); return read<CalendarPalette>('palette-' + type, { presets: [], colors: type === 'COURSE' ? presets : [] }) }
calendarColors.save = async (type, data, id) => { if (params.has('save-error') && colorSaves++ === 0) throw new Error('快捷色保存失败，请重试'); const p = await calendarColors.get(type); if (p.colors.some(c => c.name === data.name && c.id !== id)) throw new Error('快捷色名称不能重名'); if (id) p.colors = p.colors.map(c => c.id === id ? { ...c, ...data } : c); else p.colors.push({ id: String(Date.now()), ...data }); write('palette-' + type, p); return p }
calendarColors.remove = async (type, id) => { const p = await calendarColors.get(type); p.colors = p.colors.filter(c => c.id !== id); write('palette-' + type, p); return p }
api.dictDataByType = async () => [{ id: 1, label: '直播', value: 'LIVE' }] as Awaited<ReturnType<typeof api.dictDataByType>>
api.personalCalendar.list = async q => { write('last-range',q); return read<PersonalCalendarEvent[]>('events-personal', []) }
api.personalCalendar.create = async data => { const rows = await api.personalCalendar.list({ rangeStart: '', rangeEnd: '' }); rows.push({ ...data, id: 1, status: 'ACTIVE', sourceType: 'MANUAL', createTime: '', updateTime: '' }); write('events-personal', rows); return 1 }
api.personalCalendar.update = async (id, data) => { write('events-personal', (await api.personalCalendar.list({ rangeStart: '', rangeEnd: '' })).map(r => r.id === id ? { ...r, ...data } : r)); return true }
api.courseCalendar.page = async q => { write('last-range',q); return read<CourseCalendarEvent[]>('events-course', []) }
api.courseCalendar.create = async data => { write('events-course', [{ ...data, id: 1, courseFormLabelSnapshot: '直播', attachmentIds: [] }]); return 1 }
api.courseCalendar.update = async (id, data) => { write('events-course', [{ ...data, id, courseFormLabelSnapshot: '直播', attachmentIds: [] }]); return true }
const permissions = params.has('readonly') ? ['zsjos:course-calendar:query','zsjos:personal-calendar:query'] : params.has('denied') ? ['zsjos:personal-calendar:create', 'zsjos:personal-calendar:update', 'zsjos:course-calendar:manage'] : ['*:*:*']
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>{course ? <CourseCalendarPage permissions={permissions} /> : <PersonalCalendarPage permissions={permissions} />}</App></ThemeProvider></BrowserRouter>)
