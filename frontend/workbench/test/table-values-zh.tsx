// UTF-8. Isolated API fixtures for real page components; no business-service requests.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App, Button, Space } from 'antd'
import ExportTaskPage from '../src/pages/ExportTaskPage'
import { BusinessAuditPage, NotifyRulePage } from '../src/pages/ManagementPages'
import { WorkPlanConfigPage } from '../src/pages/ConfigurationPages'
import { http } from '../src/services/api'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'

let sceneAttempts = 0
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url === '/zsjos/export-task/page') {
    const list = ['lead', 'order', 'finance_order', 'cashback', 'withdrawal', 'future_type', undefined].map((exportType, index) => ({
      id: index + 1, taskNo: `TEST-${index + 1}`, exportType, status: index < 5 ? 'queued' : index === 5 ? 'future_status' : undefined,
      createTime: '2026-09-27T10:00:00', resultFileName: 'Original-English-Filename.xlsx',
    }))
    data = { list, total: list.length }
  } else if (url === '/zsjos/business-audit/page') {
    document.getElementById('request-state')!.textContent = JSON.stringify(config.params)
    data = { list: [{ id: 1, operatorNameSnapshot: '测试操作人', sourceType: config.params?.sourceType || 'ADMIN', categoryCode: config.params?.categoryCode || 'business', resultStatus: config.params?.resultStatus || 'SUCCESS', actionCode: 'test_action', targetType: 'test-target', targetId: 'TEST-01', occurredAt: '2026-09-27T10:00:00', requestMethod: 'GET', requestPath: '/test/path', traceId: 'TEST-TRACE' }], total: 1 }
  } else if (url === '/system/notify-scene/list') {
    if (++sceneAttempts === 1) throw new Error('测试场景服务暂不可用')
    data = [{ code: 'test_scene', name: '测试通知场景', recipientRoles: [{ code: 'owner', name: '服务负责人' }], allowedActions: ['message_detail'] }]
  } else if (url === '/system/notify-rule/page') {
    data = { list: [{ id: 1, name: '中文渠道与角色', sceneCode: 'test_scene', channelCode: 'in_app', recipientRoles: ['owner'], specifiedUserIds: [], status: 0 }], total: 1 }
  } else if (url.includes('work-plan') && url.includes('template')) {
    data = [{ id: 1, name: '季度计划模板', typeId: 1, periodMode: 'quarter', status: 'published', versionStatus: 'draft', currentVersionNo: 2 }]
  } else if (url.includes('work-plan') && url.includes('type')) {
    data = [{ id: 1, name: '测试计划类型' }]
  } else if (url.endsWith('/page')) data = { list: [], total: 0 }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
function Fixture() {
  const [view, setView] = useState('export')
  return <main style={{ padding: 16, minWidth: 0 }}><Space wrap>
    <Button onClick={() => setView('export')}>导出验收</Button><Button onClick={() => setView('audit')}>审计验收</Button>
    <Button onClick={() => setView('notify')}>通知验收</Button><Button onClick={() => setView('plan')}>计划验收</Button>
  </Space><pre id="request-state" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }} />
    {view === 'export' ? <ExportTaskPage /> : view === 'audit' ? <BusinessAuditPage permissions={['zsjos:audit:query']} />
      : view === 'notify' ? <NotifyRulePage permissions={[]} /> : <WorkPlanConfigPage permissions={[]} />}
  </main>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><App><Fixture /></App></ThemeProvider></MemoryRouter>)
