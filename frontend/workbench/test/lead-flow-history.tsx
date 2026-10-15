// UTF-8. Isolated synthetic transport; no live business API requests.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import LeadFlowHistoryPanel from '../src/components/LeadFlowHistoryPanel'
import { http, type LeadFlowHistory } from '../src/services/api'
const params = new URLSearchParams(location.search)
let failed = false
const rows: LeadFlowHistory[] = [{ id: 'event:2', occurredAt: Date.UTC(2026,9,15,2), businessObject: '客资', flowNode: '主管恢复客资', source: '员工工作台', operator: '测试主管', operationResult: '已恢复', snapshotVersion: 1,
  leadStatusBefore: '挂起', leadStatusAfter: '待判定', reason: '客户重新联系，恢复后重新判定。', attachments: [],
  changes: [{ field: 'suspendedAt', label: '挂起时间', before: { state: 'VALUE', text: '2026-10-01 10:00:00' }, after: { state: 'EMPTY' } },
    { field: 'category', label: '客资类别', before: { state: 'UNRECORDED' }, after: { state: 'VALUE', text: '选择时类别标签' } },
    { field: 'remark', label: '备注', before: { state: 'EMPTY' }, after: { state: 'VALUE', text: '这是验证长文本换行的业务说明。'.repeat(30) } }] },
  { id: 'event:1', occurredAt: Date.UTC(2026,9,1,2), businessObject: '客资', flowNode: '客资挂起', source: '系统任务', operator: '系统', reason: '有效性判定超时', attachments: [] }]
http.defaults.adapter = async config => {
  if (params.has('error') && !failed) { failed = true; throw new Error('测试加载失败') }
  await new Promise(resolve => setTimeout(resolve, 120))
  return { data: { code: 0, data: params.has('empty') ? [] : rows }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><div style={{ maxWidth: 1100, margin: '16px auto', padding: 12 }}><LeadFlowHistoryPanel leadId={7}/></div></App></ThemeProvider>)
