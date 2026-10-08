// Isolated transport fixture. Never sends business writes to a live backend.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { StrictMode, useState } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { App, Button } from 'antd'
import dayjs from 'dayjs'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import NoticeRichTextEditor from '../src/components/NoticeRichTextEditor'
import { api, ApiError, http } from '../src/services/api'
import { examCalendarNote, type ExamCalendarNote } from '../src/services/examCalendarNote'
import { noticeManagement } from '../src/services/noticeManagement'

const params = new URLSearchParams(location.search)
const state = params.get('state') || 'success'
const key = 'exam-note-isolated-fixture'
const defaultNote: ExamCalendarNote = { content: '<h2>项目说明</h2><p><strong>无需考试</strong>，按项目要求提交材料。</p><ul><li>示例项目甲</li><li>示例项目乙</li></ul><table><tbody><tr><th>项目</th><th>办理说明</th></tr><tr><td>示例项目甲</td><td>提交审核材料</td></tr></tbody></table>', version: 1, images: [] }
let saved: ExamCalendarNote = JSON.parse(sessionStorage.getItem(key) || 'null') || defaultNote
let loadCount = 0, saveCount = 0, uploadCount = 0
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
http.defaults.adapter = async () => { throw new Error('隔离验收禁止真实接口调用') }
examCalendarNote.get = async () => {
  loadCount++; await delay(state === 'loading' ? 1500 : 30)
  if (state === 'load-error' && loadCount <= 2) throw new Error('说明加载失败（模拟）')
  return state === 'empty' ? { content: '', version: 0, images: [] } : saved
}
examCalendarNote.save = async (content, version) => {
  saveCount++; await delay(100)
  if (state === 'save-error' && saveCount === 1) throw new Error('保存失败（模拟）')
  if (state === 'conflict' && saveCount === 1) { saved = { ...saved, content: '<p>其他人刚保存的内容</p>', version: saved.version + 1 }; throw new ApiError(1900018021, '说明已被其他人更新') }
  if (version !== saved.version && state !== 'empty') throw new ApiError(1900018021, '说明版本冲突')
  saved = { content: content === '<p><br></p>' ? '' : content, version: version + 1, images: saved.images }
  sessionStorage.setItem(key, JSON.stringify(saved)); return saved
}
examCalendarNote.upload = async file => {
  uploadCount++; await delay(800)
  if (state === 'upload-error' && uploadCount === 1) throw new Error('图片上传失败（模拟）')
  const image = { fileId: 100 + uploadCount, url: '' }
  // Keep the fixture URL same-origin HTTP, as production signed image URLs are.
  const data = await new Promise<string>(resolve => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.readAsDataURL(file) })
  image.url = `/test/exam-note-fixture-image/${image.fileId}.png`
  ;(window as unknown as { examNoteImage: string }).examNoteImage = data
  saved.images.push(image); return image
}
api.examCalendar.exactPage = async q => ({ list: [{ id: 1, scheduleName: '示例考试安排', scheduleType: 'EXACT', exactDate: dayjs(q.rangeStart).add(10, 'day').format('YYYY-MM-DD'), recordStatus: 'PUBLISHED', displayStatus: 'UPCOMING' }], total: 1 }) as Awaited<ReturnType<typeof api.examCalendar.exactPage>>
api.examCalendar.multiDayPage = async () => ({ list: [], total: 0 })
noticeManagement.uploadContent = async () => '/test/exam-note-fixture-image/notice.png'
function Fixture() {
  const [value, setValue] = useState('<p>公告原内容</p>')
  const [uploads, setUploads] = useState(0)
  const permissions = state === 'denied' ? [] : ['zsjos:exam-calendar:query', ...(state === 'read-only' ? [] : ['zsjos:exam-calendar:manage'])]
  return <BrowserRouter><ThemeProvider><App><div style={{ padding: 16 }}>
    {params.has('notice') ? <><NoticeRichTextEditor value={value} onChange={setValue} onUploadChange={delta => setUploads(n => n + delta)} onError={() => undefined}/><output>{uploads} uploading</output><Button onClick={() => setValue('<p>新公告内容</p>')}>替换公告</Button></> : <ExamCalendarPage permissions={permissions} />}
  </div></App></ThemeProvider></BrowserRouter>
}
createRoot(document.getElementById('root')!).render(<StrictMode><Fixture /></StrictMode>)
