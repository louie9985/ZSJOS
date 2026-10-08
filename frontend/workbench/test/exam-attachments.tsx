// Isolated synthetic transport. Never reads or writes real business data.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import { api, http, type ExamSchedule } from '../src/services/api'
import { examCalendarNote } from '../src/services/examCalendarNote'
import { examScheduleAttachments, type ExamAttachment } from '../src/services/examScheduleAttachments'
import { withExamClock } from '../src/services/examReedit'

const params = new URLSearchParams(location.search)
const state = params.get('state') || 'success'
const key = 'exam-attachments-fixture-' + state
const files: ExamAttachment[] = [
  { fileId: 11, name: '官方通知.png', type: 'image/png', size: 60, url: location.origin + '/test/fixture-exam.png' },
  { fileId: 12, name: '考试安排.pdf', type: 'application/pdf', size: 100, url: location.origin + '/test/fixture-exam.pdf' }
]
const today = dayjs().format('YYYY-MM-DD')
const base = { categoryPathSnapshot: [], recordStatus: 'DRAFT', displayStatus: 'DRAFT' } as const
let rows: ExamSchedule[] = JSON.parse(sessionStorage.getItem(key) || 'null') || [
  { ...base, categoryPathSnapshot: [], id: 1, scheduleName: '单日附件考试', scheduleType: 'EXACT', exactDate: today, remark: '文字备注 https://example.test/notice', attachments: files },
  { ...base, categoryPathSnapshot: [], id: 2, scheduleName: '多日附件考试', scheduleType: 'MULTI_DAY', startDate: today, endDate: dayjs().add(2,'day').format('YYYY-MM-DD'), attachments: files }
]
if (state === 'readonly' || state === 'reedit') rows = rows.map(row => ({ ...row, recordStatus: 'PUBLISHED', displayStatus: 'PUBLISHED' }))
let uploadCount = 0, saveCount = 0, readCount = 0, publishCount = 0
const delay = () => new Promise(resolve => setTimeout(resolve, 120))
http.defaults.adapter = async () => { throw new Error('隔离验收禁止真实接口请求') }
examCalendarNote.get = async () => ({ content: '', version: 0, images: [] })
const persist = () => { sessionStorage.setItem(key, JSON.stringify(rows)); return true }
const list = (type: string) => withExamClock(async () => ({ list: rows.filter(row => row.scheduleType === type)
  .map(row => ({ ...row, serverTime: Date.now() })), total: rows.filter(row => row.scheduleType === type).length }))
api.examCalendar.exactPage = () => list('EXACT')
api.examCalendar.multiDayPage = () => list('MULTI_DAY')
examScheduleAttachments.upload = async file => {
  uploadCount++; await delay()
  if (state === 'upload-error' && uploadCount === 1) throw new Error('模拟上传失败')
  const result = { fileId: 100 + uploadCount, name: file.name, type: file.type, size: file.size,
    url: location.origin + (file.type.startsWith('image/') ? '/test/fixture-exam.png' : '/test/fixture-exam.pdf') }
  files.push(result); return result
}
examScheduleAttachments.read = async (id, fileId) => {
  const attempt = ++readCount; await delay()
  if (state === 'read-error' && attempt === 1) throw new Error('模拟读取失败')
  const result = rows.find(row => row.id === id)?.attachments?.find(file => file.fileId === fileId)
  if (!result) throw new Error('附件不存在')
  return result
}
const attachments = (ids: number[] = []) => ids.map(id => files.find(file => file.fileId === id)!)
api.examCalendar.create = async input => {
  saveCount++; await delay()
  const id = Math.max(0, ...rows.map(row => row.id)) + 1
  rows.push({ ...base, ...input, id, categoryPathSnapshot: [], attachments: attachments(input.attachmentIds) }); persist(); return id
}
api.examCalendar.update = async (id, input) => {
  saveCount++; await delay()
  if (state === 'save-error' && saveCount === 1) throw new Error('模拟保存失败')
  Object.assign(rows.find(row => row.id === id)!, input, { attachments: attachments(input.attachmentIds) }); return persist()
}
api.examCalendar.publish = async id => {
  publishCount++; await delay()
  if (state === 'publish-error' && publishCount === 1) throw new Error('模拟发布失败')
  Object.assign(rows.find(row => row.id === id)!, { recordStatus: 'PUBLISHED', displayStatus: 'PUBLISHED' }); return persist()
}
api.examCalendar.revoke = async id => {
  Object.assign(rows.find(row => row.id === id)!, { recordStatus: 'REVOKED', displayStatus: 'REVOKED', canReedit: true, reeditDeadline: Date.now() + 300000 }); return persist()
}
api.examCalendar.reedit = async id => {
  const row = rows.find(row => row.id === id)!
  rows = rows.filter(row => row.id !== id); persist()
  return { scheduleName: row.scheduleName!, scheduleType: row.scheduleType, exactDate: row.exactDate,
    startDate: row.startDate, endDate: row.endDate, remark: row.remark, attachments: row.attachments }
}
Object.assign(window, { attachmentFixture: { counters: () => ({ uploadCount, saveCount, readCount, publishCount }), rows: () => rows } })
const permissions = state === 'denied' ? [] : ['zsjos:exam-calendar:query', ...(state === 'readonly' ? [] : ['zsjos:exam-calendar:manage'])]
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><ExamCalendarPage permissions={permissions} /></App></ThemeProvider></BrowserRouter>)
