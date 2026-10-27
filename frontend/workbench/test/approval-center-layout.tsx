// UTF-8. All requests terminate in this isolated adapter; no live business writes.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import { Link } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import BpmApprovalCenterPage from '../src/pages/BpmApprovalCenterPage'
import BpmApprovalActions from '../src/components/bpm/BpmApprovalActions'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
localStorage.setItem('crm-theme', JSON.stringify({ inboxLayoutMode: params.has('table') ? 'table' : 'inbox', preset: params.has('dark') ? 'default-dark' : 'default-light' }))
const state = { actions: [] as unknown[], businessRequests: [] as { taskId: string; view: string; completed: boolean }[], decision: false, fail: params.has('fail'), recordFail: params.has('recordFail'), commentFail: params.has('commentFail'), rejectWrite: params.has('rejectWrite'), requests: [] as string[] }
Object.assign(window, { approvalFixture: state })
const task = { id: 't1', name: '上级审批', status: 1, processInstanceId: 'p1', reasonRequire: true, signEnable: params.has('sign'), createTime: Date.now(), processDefinitionKey: params.has('feedback') ? 'zsjos_feedback_requirement_approval' : 'points', processInstance: { name: '500积分兑换申请', startUser: { id: 11, nickname: '申请人甲' } }, buttonsSetting: params.has('hidden') ? { 1: { enable: false }, 2: { enable: false } } : undefined }
const tasks = [task, { ...task, id: 't2', processInstanceId: 'p2', name: '第二条审批', processInstance: { ...task.processInstance, name: '第二条申请' } }]
const brief = { bizType: params.has('feedback') ? 'feedback' : 'points', title: '500积分兑换申请', fields: [{ label: '积分', value: '500' }] }
http.defaults.adapter = async config => {
  state.requests.push(config.url || '')
  await new Promise(resolve => setTimeout(resolve, config.url?.includes('get-approval-detail') && params.has('slow') ? 500 : 20))
  const selected = tasks.find(t => t.id === config.params?.taskId || t.processInstanceId === config.params?.processInstanceId) || task
  let data: unknown = []
  if (config.url?.endsWith('/todo-page')) data = { list: state.decision ? tasks.slice(1) : tasks, total: state.decision ? 1 : 2 }
  else if (config.url?.endsWith('/done-page')) data = { list: [{ ...task, status: 2, reason: '已同意', endTime: Date.now() }], total: 1 }
  else if (config.url?.endsWith('/business-summary-batch')) data = { t1: brief, t2: { ...brief, title: '第二条申请' } }
  else if (config.url?.endsWith('/business-detail')) {
    state.businessRequests.push({ taskId: config.params.taskId, view: config.params.view, completed: state.decision })
    if (state.decision && selected.id === 't1' && config.params.view === 'todo') throw new Error('任务不存在')
    if (state.fail) throw new Error('业务快照暂不可用')
    data = { ...brief, groups: [{ title: '申请内容', fields: [{ label: '申请理由', value: '申请兑换500积分，用于本次活动。'.repeat(params.has('long') ? 60 : 1), span: 2 }, { label: '历史标签', value: '提交时冻结的标签' }] }] }
  } else if (config.url?.endsWith('/get-approval-detail')) data = { processDefinition: {}, processInstance: { ...selected.processInstance, status: state.decision ? 2 : 1 }, todoTask: params.has('stale') ? { ...selected, id: 'other' } : state.decision && selected.id === 't1' ? undefined : selected, activityNodes: [{ id: 'start', name: '发起申请', status: 2, tasks: [{ id: 'start', status: 2, assigneeUser: { nickname: '申请人甲' } }] }, { id: 'current', name: '上级审批', status: 1, tasks: [{ ...selected, reason: state.decision ? '同意申请' : undefined, assigneeUser: { nickname: '审批人乙' } }] }, { id: 'future', name: '结束', status: -1, candidateUsers: [{ nickname: '候选人丙' }] }] }
  else if (config.url === '/bpm/task/list-by-process-instance-id') {
    if (state.recordFail) throw new Error('记录读取失败')
    data = [{ ...selected, children: [{ id: 'child', name: '加签', parentTaskId: selected.id, reason: '加签意见', status: 2 }] }, { id: 'old', name: '退回前审批', reason: '请补充材料', status: 3 }]
  } else if (config.url === '/bpm/comment/list-by-process-instance-id') {
    if (state.commentFail) throw new Error('评论读取失败')
    data = [{ id: 'c1', user: { nickname: '申请人甲' }, message: '申请材料已补充。', createTime: Date.now() }]
  } else if (config.url === '/bpm/task/approve' || config.url === '/bpm/task/reject') {
    if (state.rejectWrite) throw new Error('提交失败，请重试')
    state.actions.push(JSON.parse(config.data)); state.decision = true; data = true
  } else if (config.url === '/bpm/comment/create') { state.actions.push(JSON.parse(config.data)); data = true }
  else if (config.url?.endsWith('/business-task-target')) data = { supported: false }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>{params.has('legacy') ? <BpmApprovalActions task={task} canUpdate users={[]} onSuccess={() => {}}/> : <div style={{ height: '100dvh', display: 'flex', flexDirection: 'column' }}><Link to="/test/approval-center-layout.html?destination">测试离开页面</Link><BpmApprovalCenterPage permissions={['bpm:task:query', 'bpm:process-instance:query', ...(params.has('readonly') ? [] : ['bpm:task:update'])]}/></div>}</App></ThemeProvider></BrowserRouter>)
