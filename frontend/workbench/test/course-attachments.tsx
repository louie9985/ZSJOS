import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import CourseCalendarPage from '../src/pages/CourseCalendarPage'
import { api, type CourseCalendarEvent } from '../src/services/api'
import { calendarColors } from '../src/services/calendarColors'
import { courseAttachments, type CourseAttachment } from '../src/services/courseAttachments'
const params = new URLSearchParams(location.search)
const read = <T,>(key: string, fallback: T): T => JSON.parse(sessionStorage.getItem(key) || JSON.stringify(fallback))
const write = (key: string, value: unknown) => sessionStorage.setItem(key, JSON.stringify(value))
const files = () => read<CourseAttachment[]>('files', [])
api.dictDataByType = async () => [{ id: 1, label: '直播', value: 'LIVE' }] as Awaited<ReturnType<typeof api.dictDataByType>>
calendarColors.get = async () => ({ presets: [], colors: [] })
api.courseCalendar.page = async () => read<CourseCalendarEvent[]>('events', [])
api.courseCalendar.create = async data => { write('creates', read('creates', 0) + 1); write('events', [{ ...data, id: 1, courseFormLabelSnapshot: '直播', attachmentIds: data.attachmentIds || [], attachments: files().filter(f => data.attachmentIds?.includes(f.fileId)) }]); return 1 }
api.courseCalendar.update = async (id, data) => { write('events', [{ ...data, id, courseFormLabelSnapshot: '直播', attachmentIds: data.attachmentIds || [], attachments: files().filter(f => data.attachmentIds?.includes(f.fileId)) }]); return true }
courseAttachments.upload = async file => {
 const counts = read<Record<string, number>>('uploads', {}); counts[file.name] = (counts[file.name] || 0) + 1; write('uploads', counts)
 if (file.name === 'retry.png' && counts[file.name] === 1) throw new Error('模拟上传失败')
 const all = files(); const uploaded = { fileId: all.length + 1, name: file.name, type: file.type, size: file.size, url: location.origin + '/test/course-file.png' }
 all.push(uploaded); write('files', all); return uploaded
}
courseAttachments.read = async (courseId, fileId) => {
 write('reads', read('reads', 0) + 1)
 if (!read<CourseCalendarEvent[]>('events', []).some(r => r.id === courseId && r.attachmentIds.includes(fileId))) throw new Error('无权读取附件')
 return files().find(f => f.fileId === fileId)!
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><CourseCalendarPage permissions={params.has('denied') ? ['zsjos:course-calendar:query'] : ['*:*:*']} /></App></ThemeProvider></BrowserRouter>)
