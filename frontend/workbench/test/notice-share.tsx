// UTF-8. Synthetic transport fixture only.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import NoticeManagementDetail from '../src/components/NoticeManagementDetail'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const state = { fail: false, denied: false, active: false, version: 0, selected: [] as number[], calls: [] as string[] }
Object.assign(window, { shareFixture: state })
const token = () => String(state.version).padStart(43, 'x')
const current = () => ({ active: state.active, version: state.version, attachmentIds: state.selected, url: state.active ? 'http://127.0.0.1:5199/notice/share#token=' + token() : undefined })
const notice = { id: 1, title: '中世健公开课程说明', content: '<p>面向学员、合作伙伴的课程介绍。</p>', type: 2, status: 0, remark: '', creator: 'fixture', createTime: new Date(),
  publishStatus: 'PUBLISHED' as const, audienceType: 'TARGET' as const, targetDeptIds: [1], targetUserIds: [], attachments: [
    { infraFileId: 101, fileName: '课程介绍.pdf', fileSize: 1024, mimeType: 'application/pdf', sort: 0 },
    { infraFileId: 102, fileName: '内部材料.zip', fileSize: 2048, mimeType: 'application/zip', sort: 1 }
  ] }
const adapter = async (config: any) => {
  const path = (config.url || '').split('?')[0]
  state.calls.push(path)
  if (state.fail) throw new Error('分享配置加载失败')
  let data: unknown; let code = state.denied ? 403 : 0
  if (path.includes('notice-share')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    if (!code && path.endsWith('/open')) { state.active = true; state.version++; state.selected = body.attachmentIds }
    if (!code && path.endsWith('/close')) { state.active = false; state.version++; state.selected = [] }
    data = current()
  } else if (path.endsWith('/dict-data/simple-list')) data = []
  else if (path.endsWith('/system/notice/get')) data = notice
  else throw new Error('Unexpected request ' + path)
  return { config, status: 200, statusText: 'OK', headers: {}, request: { responseType: 'json' }, data: { code, msg: code ? '无权管理分享' : '', data } }
}

http.defaults.adapter = adapter
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><NoticeManagementDetail notice={notice} permissions={params.has('denied') ? ['system:notice:query'] : ['system:notice:query', 'system:notice:share']} /></App></ThemeProvider>)
