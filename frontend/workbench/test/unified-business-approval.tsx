// UTF-8. Isolated HTTP adapter: every request ends here; no real business mutations.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import BpmApprovalCenterPage from '../src/pages/BpmApprovalCenterPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
localStorage.setItem('crm-theme', JSON.stringify({ inboxLayoutMode: params.has('table') ? 'table' : 'inbox', preset: 'default-light' }))
const state = { requests: [] as Array<{ url: string; body: unknown; params: unknown }>, completed: false, saved: false,
  failLocate: params.has('failLocate'), failWrite: params.has('failWrite') }
Object.assign(window, { businessApprovalFixture: state })
const tasks = [
  { id: 'content-1', name: '编导审核', processDefinitionKey: 'zsjos_production_content_review', processInstance: { name: '生产内容批次' } },
  { id: 'viral-content', name: '爆款内容拆解', processDefinitionKey: 'zsjos_viral_content_review', processInstance: { name: '爆款内容拆解任务' } },
  { id: 'viral-account', name: '爆款账号拆解', processDefinitionKey: 'zsjos_viral_account_review', processInstance: { name: '爆款账号拆解任务' } }
].map(t => ({ ...t, status: 1, createTime: Date.now(), processInstanceId: 'p-' + t.id }))
const batch = () => ({ id: 12, batchNo: 'CR-DEMO', version: 3, studentName: '示例学员', directorName: '示例编导',
  status: params.has('final') ? 'FINAL_REVIEW' : 'DIRECTOR_REVIEW', currentStage: params.has('final') ? 'FINAL' : 'DIRECTOR', currentTaskId: params.has('stale') ? 'another-task' : 'content-1',
  processInstanceId: 'p-content-1', availableActions: params.has('final') ? ['FINAL_DECIDE', 'FINAL_APPROVE', 'FINAL_RETURN'] : ['DIRECTOR_DECIDE', 'DIRECTOR_APPROVE', 'DIRECTOR_RETURN'],
  contextSnapshot: {}, accountSnapshots: [], items: [{ id: 21, version: 2, contentVersionId: 31, contentId: 41,
    contentSnapshot: { titleSnapshot: '生产作品标题', scriptText: '本轮冻结正文', contentNo: 'CT-DEMO', contentVersionNo: 1 },
    directorDecision: params.has('final') ? 'APPROVED' : state.saved ? (params.has('returned') ? 'RETURNED' : 'APPROVED') : undefined,
    finalDecision: params.has('final') && state.saved ? 'APPROVED' : undefined, files: [] }] })
http.defaults.adapter = async config => {
  const url = config.url || ''
  const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
  state.requests.push({ url, body, params: config.params })
  await new Promise(resolve => setTimeout(resolve, 20))
  let data: unknown = []
  if (url.endsWith('/todo-page')) data = { list: state.completed ? tasks.slice(1) : tasks, total: state.completed ? 2 : 3 }
  else if (url.endsWith('/done-page')) data = { list: tasks.map(t => ({ ...t, status: 2, endTime: Date.now() })), total: 3 }
  else if (url.endsWith('/business-summary-batch')) data = Object.fromEntries(tasks.map(t => [t.id, { title: t.processInstance.name, fields: [] }]))
  else if (url.endsWith('/business-detail')) {
    if (state.failLocate) throw new Error('业务读取被拒绝')
    data = { bizType: 'content_review', title: '生产内容批次', query: { batchId: 12 }, groups: [] }
  } else if (url.endsWith('/business-task-target')) {
    if (state.failLocate) throw new Error('业务读取被拒绝')
    data = { supported: true, bizType: 'material', route: '/zsjos/material-library/approvals',
      query: { taskId: config.params.taskId, versionId: config.params.taskId === 'viral-content' ? 51 : 52 } }
  } else if (url === '/zsjos/content-review/batch/get') data = batch()
  else if (url.includes('director-decision') || url.includes('final-decision')) {
    if (state.failWrite) throw new Error('保存失败，请重试')
    state.saved = true; data = true
  } else if (url.includes('complete-director') || url.includes('complete-final')) {
    if (state.failWrite) throw new Error('提交失败，请重试')
    state.completed = true; data = true
  } else if (url === '/zsjos/material-approval/get') data = { task: { id: config.params.taskId, status: config.params.done ? 2 : 1 },
    versionId: config.params.versionId, materialNo: 'MT-DEMO', title: '拆解详情', snapshotAvailable: true,
    snapshot: { versionNo: 1, summary: '复用原业务拆解快照', dictSnapshot: {}, files: [], values: { analysis: '冻结的拆解正文' },
      fields: [{ key: 'analysis', label: '拆解分析', type: 'text' }] } }
  else if (url === '/zsjos/material-approval/approve' || url === '/zsjos/material-approval/reject') {
    if (state.failWrite) throw new Error('提交失败，请重试')
    state.completed = true; data = true
  } else if (url.endsWith('/get-approval-detail')) {
    const task = tasks[0]
    data = { processDefinition: {}, processInstance: { status: 1 }, todoTask: task, activityNodes: [] }
  } else if (url.endsWith('/batch/history')) data = []
  else if (url.endsWith('/simple-list') || url.endsWith('/list-by-process-instance-id')) data = []
  else throw new Error('Unexpected fixture request: ' + url)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const permissions = ['bpm:task:query', 'bpm:task:update', 'bpm:process-instance:query', 'zsjos:content-review:query',
  'zsjos:material-approval:query', ...(params.has('readonly') ? [] : ['zsjos:material-approval:approve', 'zsjos:material-approval:reject'])]
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>
  <div style={{ height: '100dvh', display: 'flex', flexDirection: 'column' }}><BpmApprovalCenterPage permissions={permissions} /></div>
</App></ThemeProvider></BrowserRouter>)
