import React from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import CalendarNotificationPanel from '../src/components/CalendarNotificationPanel'
import { api, ApiError, type CalendarNotifyInput } from '../src/services/api'

const params = new URLSearchParams(location.search)
const fixture = { requests: [] as CalendarNotifyInput[], fail: 0, previews: 0, loadFail: params.has('loadError'), pages: [] as number[] }
Object.assign(window, { calendarFixture: fixture })
const employees = [
  { id: 1, nickname: '员工甲', deptId: 2 }, { id: 2, nickname: '员工乙', deptId: 3 },
  { id: 3, nickname: '员工丙', deptId: 4 },
  ...Array.from({ length: 100 }, (_, i) => ({ id: i + 4, nickname: '分页员工' + (i + 4), deptId: 5 })),
]
api.simpleDepartments = async () => [
  { id: 1, name: '测试学院', parentId: 0 }, { id: 2, name: '教务部', parentId: 1 },
  { id: 3, name: '教务一组', parentId: 2 }, { id: 4, name: '教研部', parentId: 1 },
  { id: 5, name: '分页部门', parentId: 1 },
]
for (const client of [api.examCalendar, api.courseCalendar]) {
  client.notifyUsers = async (_keyword, page = 1, size = 20) => {
    fixture.pages.push(page)
    await new Promise(resolve => setTimeout(resolve, params.has('slow') ? 700 : 20))
    if (fixture.loadFail) throw new ApiError(1900092002, '测试员工加载失败')
    const list = params.has('empty') ? [] : employees
    return { list: list.slice((page - 1) * size, page * size), total: list.length }
  }
  client.previewNotify = async data => {
    fixture.previews++
    return { calendarVersion: 3, title: '测试课程（线上）', time: '2026-10-01 09:00', remark: '测试快照',
      recipientCount: data.scope === 'ALL' ? 103 : data.userIds!.length, notifiedCount: 0,
      newRecipientCount: data.scope === 'ALL' ? 103 : data.userIds!.length,
      previewToken: 'preview-' + fixture.previews, contentHash: 'same-content' }
  }
  client.notify = async data => {
    fixture.requests.push(data)
    await new Promise(resolve => setTimeout(resolve, 200))
    if (fixture.fail) throw new ApiError(fixture.fail, '测试发送失败，请重新预览或重试')
    return { batchId: 12, acceptedCount: 1, skippedCount: 0, status: 'SUBMITTED', resend: Boolean(data.resend), sourceEventKey: 'fixture' }
  }
}
const type = params.get('type') === 'EXAM' ? 'EXAM' : 'COURSE'
const prefix = type === 'EXAM' ? 'zsjos:exam-calendar' : 'zsjos:course-calendar'
const permissions = params.has('denied') ? [] : [prefix + ':notify', ...(params.has('specified') ? [] : [prefix + ':notify-all'])]
createRoot(document.getElementById('root')!).render(<ConfigProvider locale={zhCN}>
  <CalendarNotificationPanel calendarType={type} calendarId={9} permissions={permissions} onClose={() => {}} />
</ConfigProvider>)
