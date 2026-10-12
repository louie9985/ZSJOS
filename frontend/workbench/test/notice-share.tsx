// UTF-8. Synthetic transport fixture only.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import NoticeManagementPage from '../src/pages/NoticeManagementPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const state = { fail: false, detailFail: false, stale: false, denied: false, active: false, version: 0, selected: [] as number[], calls: [] as string[] }
Object.assign(window, { shareFixture: state })
const token = () => String(state.version).padStart(43, 'x')
const current = () => ({ active: state.active, version: state.version, attachmentIds: state.selected, url: state.active ? 'http://127.0.0.1:5199/public-api/system/notice-share/page?token=' + token() : undefined })
const notice = { id: 1, title: '中世健公开课程说明', content: '<p>面向学员、合作伙伴的课程介绍。</p>', type: 2, status: 0, remark: '', creator: 'fixture', createTime: new Date(),
  publishStatus: params.has('draft') ? 'DRAFT' : params.has('offline') ? 'OFFLINE' : 'PUBLISHED', audienceType: 'TARGET' as const, targetDeptIds: [1], targetUserIds: [], attachments: [
    { infraFileId: 101, fileName: '课程介绍.pdf', fileSize: 1024, mimeType: 'application/pdf', sort: 0 },
    { infraFileId: 102, fileName: '内部材料.zip', fileSize: 2048, mimeType: 'application/zip', sort: 1 }
  ] }
const adapter = async (config: any) => {
  const path = (config.url || '').split('?')[0]
  state.calls.push(path)
  if (state.fail && path.includes('notice-share')) throw new Error('分享配置加载失败')
  if (state.detailFail && path.endsWith('/system/notice/get')) throw new Error('公告加载失败，请重试')
  let data: unknown; let code = state.denied ? 403 : 0
  if (path.includes('notice-share')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    if (!code && path.endsWith('/open')) { state.active = true; state.version++; state.selected = body.attachmentIds }
    if (!code && path.endsWith('/close')) { state.active = false; state.version++; state.selected = [] }
    data = current()
  } else if (path.endsWith('/dict-data/simple-list')) data = []
  else if (path.endsWith('/system/notice/get')) data = { ...notice, publishStatus: state.stale ? 'OFFLINE' : notice.publishStatus }
  else if (path.endsWith('/system/notice/page')) data = { list: [{ ...notice, content: undefined, publishStatus: state.stale ? 'OFFLINE' : notice.publishStatus }], total: 1 }
  else throw new Error('Unexpected request ' + path)
  return { config, status: 200, statusText: 'OK', headers: {}, request: { responseType: 'json' }, data: { code, msg: code ? '无权管理分享' : '', data } }
}

http.defaults.adapter = adapter
const permissions = params.has('no-query') ? ['system:notice:share'] : params.has('denied') ? ['system:notice:query'] : ['system:notice:query', 'system:notice:share', 'system:notice:offline', 'system:notice:create']
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><NoticeManagementPage permissions={permissions} /></App></ThemeProvider>)
