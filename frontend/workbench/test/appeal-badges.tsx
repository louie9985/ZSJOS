import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Badge } from 'antd'
import { api } from '../src/services/api'
import { APP_ROUTES } from '../src/constants'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import MenuTaskBadgeProvider, { useMenuTaskBadges } from '../src/components/MenuTaskBadgeProvider'
let pending = 2
let fail = false
let calls = 0
api.menuTaskSummary = async () => ({ generatedAt: 1, total: 0, items: [] })
api.leadAppealInboxPage = async () => { calls++; if (fail) throw new Error('network'); return { total: pending, list: [] } }
api.bpmTaskPage = async (_view, params) => ({ total: params.excludeProcessDefinitionKeys?.includes('zsjos_lead_appeal_review') ? 0 : pending, list: [] })
function Probe() {
  const { resolve, refresh } = useMenuTaskBadges()
  return <><div id="appeal"><Badge count={resolve(APP_ROUTES.LEAD_APPEALS)?.count}>申诉处理</Badge></div>
    <div id="workflow"><Badge count={resolve(APP_ROUTES.BPM_TODO)?.count}>工作流程</Badge></div>
    <button onClick={() => { pending = 0; void refresh() }}>处理完成</button>
    <button onClick={() => { fail = true; void refresh() }}>查询失败</button>
    <button onClick={() => { fail = false; pending = 3; void refresh() }}>重试</button>
    <button onClick={() => document.body.dataset.calls = String(calls)}>读取请求数</button></>
}
function App() { const [enabled, setEnabled] = useState(true); return <RealtimeProvider platform="PC"><button onClick={() => setEnabled(false)}>撤销权限</button><MenuTaskBadgeProvider bpmEnabled appealEnabled={enabled}><Probe/></MenuTaskBadgeProvider></RealtimeProvider> }
createRoot(document.getElementById('root')!).render(<App/> )
