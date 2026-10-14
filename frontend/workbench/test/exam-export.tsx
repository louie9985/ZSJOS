// Synthetic transport only; mounts the production calendar and export task page.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExamCalendarPage from '../src/pages/ExamCalendarPage'
import ExportTaskPage from '../src/pages/ExportTaskPage'
import { api, http } from '../src/services/api'
import { examCalendarNote } from '../src/services/examCalendarNote'

const query = new URLSearchParams(location.search)
const permissions = ['zsjos:exam-calendar:query', 'zsjos:exam-calendar:export', 'zsjos:export:query']
if (query.has('denied')) permissions.splice(permissions.indexOf(query.get('denied')!), 1)
const requests: unknown[] = []
http.defaults.adapter = async config => {
  if (config.url !== '/zsjos/export-task' || config.method !== 'post') throw new Error('禁止真实接口请求')
  requests.push(JSON.parse(config.data))
  await new Promise(resolve => setTimeout(resolve, 300))
  return { config, status: 200, statusText: 'OK', headers: {}, data:
    query.has('fail') && requests.length === 1 ? { code: 403, msg: '没有导出权限' } : { code: 0, data: 123 } }
}
api.examCalendar.exactPage = api.examCalendar.multiDayPage = async () => ({ list: [], total: 0 })
examCalendarNote.get = async () => ({ content: '', version: 0, images: [] })
api.exportTaskPage = async () => ({ list: [{ id: 123, taskNo: 'EXP-TEST', exportType: 'exam_calendar', status: 'ready', attemptCount: 0, resultFileName: '考期.xlsx' }], total: 1 })
api.exportDownloadUrl = async () => 'data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64,UEs='
Object.assign(window, { examExportRequests: requests })
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>
  {query.has('tasks') ? <ExportTaskPage /> : <ExamCalendarPage permissions={permissions} />}
</App></ThemeProvider></BrowserRouter>)
