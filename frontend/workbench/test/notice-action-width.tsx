// UTF-8. Actual page with isolated transport; no real account or business writes.
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { useTheme } from '../src/components/Theme/ThemeContext'
import NoticeManagementPage from '../src/pages/NoticeManagementPage'
import { http } from '../src/services/api'
import type { ManagedNotice } from '../src/services/noticeManagement'
import '../src/styles/index.css'

const notices: ManagedNotice[] = ['PUBLISHED', 'DRAFT', 'OFFLINE'].map((status, index) => ({
  id: index + 1, title: `公告布局示例 ${index + 1}`, type: 2, content: '<p>公告内容</p>',
  audienceType: 'ALL', targetDeptIds: [], targetUserIds: [], attachments: [],
  publishStatus: status as ManagedNotice['publishStatus'], sourceDeptName: '示例部门', publisherName: '示例发布人',
  publishTime: status === 'DRAFT' ? undefined : '2026-10-13 11:42:00', highlightUntil: '2026-10-16 18:00:00',
}))
http.defaults.adapter = async config => {
  let data: unknown
  if (config.url?.endsWith('/system/notice/page')) {
    const rows = notices.filter(row => !config.params.publishStatus || row.publishStatus === config.params.publishStatus)
    data = { list: rows, total: rows.length }
  } else if (config.url?.endsWith('/system/notice/get')) data = notices.find(row => row.id === config.params.id)
  else if (config.url?.endsWith('/dict-data/simple-list')) data = []
  else throw new Error(`Unexpected fixture request: ${config.method} ${config.url}`)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
function Fixture() {
  const [mode, setMode] = useState('view')
  const { setFontScale, setDensity } = useTheme()
  const permissions = mode === 'manage' ? ['*:*:*'] : mode === 'share' ? ['system:notice:query', 'system:notice:share'] : mode === 'denied' ? [] : ['system:notice:query']
  return <><Space wrap>
    <Button onClick={() => setMode('view')}>仅查看</Button><Button onClick={() => setMode('share')}>查看与分享</Button>
    <Button onClick={() => setMode('manage')}>管理权限</Button><Button onClick={() => setMode('denied')}>无查询权限</Button>
    <Button onClick={() => { setFontScale('large'); setDensity('loose') }}>大字号宽松</Button>
    <Button onClick={() => { setFontScale('default'); setDensity('default') }}>默认显示</Button>
  </Space><NoticeManagementPage permissions={permissions} /></>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture /></App></ThemeProvider>)
