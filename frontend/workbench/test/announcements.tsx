// UTF-8. All requests are intercepted with synthetic data; never writes business services.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import AnnouncementCenterPage from '../src/pages/AnnouncementCenterPage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { AnnouncementProvider } from '../src/components/AnnouncementProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { http } from '../src/services/api'
import type { ManagedNotice } from '../src/services/noticeManagement'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
const role = params.get('role') || 'both'
const permissions = (role === 'none' ? [] : role === 'read' ? ['read'] : role === 'query' ? ['query'] : role === 'manage' ? ['query', 'create', 'update', 'publish', 'offline', 'delete'] : ['read', 'query', 'create', 'update', 'publish', 'offline', 'delete']).map(action => `system:notice:${action}`)
const rows: ManagedNotice[] = ['DRAFT', 'PUBLISHED', 'OFFLINE'].map((publishStatus, i) => ({
  id: i + 1, title: ['测试草稿', '已发布测试公告', '已下线测试公告'][i], type: 2,
  content: '<p><strong>公告测试正文</strong></p><p>格式、链接与附件验证。</p>', audienceType: 'ALL', targetDeptIds: [], targetUserIds: [],
  publishStatus: publishStatus as ManagedNotice['publishStatus'], publishTime: 1790000000000,
  attachments: [{ infraFileId: 9, fileName: '不可用测试附件.pdf', fileSize: 1024, sort: 0 }]
}))
let sequence = 4
let failures = 0
let read = false
let uploadFailure = false
const calls: string[] = []
const output = () => { const node = document.getElementById('requests'); if (node) node.textContent = calls.join(' | ') }
http.defaults.adapter = async config => {
  const url = config.url || ''
  calls.push(`${config.method} ${url}`); output()
  const id = Number(config.params?.id)
  const row = rows.find(item => item.id === id)
  let data: unknown = true
  let code = 0
  let msg = ''
  if (url.endsWith('/page')) {
    if (params.has('error') && failures++ < 2) throw new Error('测试加载失败')
    if (params.has('forbidden')) { code = 403; msg = '测试无权访问' }
    const list = params.has('empty') ? [] : rows.filter(item => (!config.params?.title || item.title.includes(config.params.title)) && (!config.params?.publishStatus || item.publishStatus === config.params.publishStatus))
    data = { list, total: list.length }
  } else if (url.endsWith('/attachment/upload')) {
    await new Promise(resolve => setTimeout(resolve, 1200))
    if (uploadFailure) throw new Error('测试附件上传失败')
    data = { infraFileId: sequence++, fileName: '合成测试附件.pdf', fileSize: 10, sort: 0 }
  } else if (url.endsWith('/get')) data = row
  else if (url.endsWith('/my-cursor') || url.endsWith('/my-page')) data = { list: rows.filter(item => item.publishStatus === 'PUBLISHED').map(item => ({ ...item, read })), total: 1, hasMore: false }
  else if (url.endsWith('/my-get')) data = { ...row, read }
  else if (url.endsWith('/mark-read')) read = true
  else if (url.endsWith('/unread-summary')) data = { unreadCount: read ? 0 : 1 }
  else if (url.endsWith('/simple-list')) data = [{ value: '2', label: '公告', dictType: 'system_notice_type' }, { value: '1', label: '通知', dictType: 'system_notice_type' }]
  else if (url.endsWith('/recipient-options')) data = { departments: [{ id: 1, parentId: 0, name: '测试部门' }], users: [{ id: 1, deptId: 1, nickname: '测试接收人', selectable: true }, { id: 2, nickname: '不可接收用户', selectable: false, disabledReason: '无阅读权限' }] }
  else if (url.endsWith('/create')) { const input = JSON.parse(config.data); data = sequence++; rows.push({ ...input, id: data, publishStatus: 'DRAFT' }) }
  else if (url.endsWith('/update')) { const input = JSON.parse(config.data); Object.assign(rows.find(item => item.id === input.id)!, input) }
  else if (url.endsWith('/publish')) {
    if (params.has('publishError')) { code = 100200; msg = '测试发布失败，草稿保留' }
    else if (row) row.publishStatus = 'PUBLISHED'
  } else if (url.endsWith('/offline') && row) row.publishStatus = 'OFFLINE'
  else if (url.endsWith('/copy') && row) { data = sequence++; rows.push({ ...row, id: data as number, title: `${row.title}（副本）`, publishStatus: 'DRAFT' }) }
  else if (url.endsWith('/delete')) { const index = rows.findIndex(item => item.id === id); if (index >= 0) rows.splice(index, 1) }
  else throw new Error(`Fixture has no handler: ${url}`)
  return { data: { code, msg, data }, status: 200, statusText: 'OK', headers: {}, config }
}
function syntheticUpload(fail: boolean) {
  uploadFailure = fail
  const input = document.querySelector<HTMLInputElement>('input[type="file"]')
  if (!input) return
  const transfer = new DataTransfer()
  transfer.items.add(new File(['synthetic test attachment'], '合成测试附件.pdf', { type: 'application/pdf' }))
  input.files = transfer.files
  input.dispatchEvent(new Event('change', { bubbles: true }))
}
createRoot(document.getElementById('root')!).render(<StrictMode><ThemeProvider><BrowserRouter><App><RealtimeProvider platform="PC"><AnnouncementProvider enabled={permissions.includes('system:notice:read')}>
  <main style={{ padding: 12, ...(params.has('narrow') ? { width: 390, maxWidth: '100%' } : {}) }}><p>隔离测试数据；权限：{role}</p><details><summary>请求记录</summary><div id="requests" /></details><AnnouncementCenterPage permissions={permissions} /></main>
  {params.has('uploadTest') && <div style={{ position: 'fixed', zIndex: 10000, top: 0, right: 0 }}><button onClick={() => syntheticUpload(false)}>注入成功附件</button><button onClick={() => syntheticUpload(true)}>注入失败附件</button></div>}
</AnnouncementProvider></RealtimeProvider></App></BrowserRouter></ThemeProvider></StrictMode>)
