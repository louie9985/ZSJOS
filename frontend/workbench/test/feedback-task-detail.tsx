// UTF-8. Real approval-center page with isolated synthetic transport.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import BpmApprovalCenterPage from '../src/pages/BpmApprovalCenterPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
if (params.has('done')) history.replaceState(null, '', '/bpm/task/done')
localStorage.setItem('crm-theme', JSON.stringify({ inboxLayoutMode: 'table' }))
const state = { decision: '', fail: params.has('fail'), stale: params.has('stale'), actions: [] as Record<string, unknown>[] }
Object.assign(window, { taskFixture: state })
const task = { id: 'feedback-task', name: '部门负责人审批', status: 1, processInstanceId: 'feedback-process',
  processDefinitionKey: 'zsjos_feedback_requirement_approval', createTime: Date.now(), reasonRequire: true,
  processInstance: { name: '需求审批验证', startUser: { id: 11, nickname: '测试提交人' } } }
const brief = { bizType: 'feedback', title: 'REQ-TEST-001 · 需求详情内审批', fields: [{ label: '轮次', value: '第 2 轮' }] }
http.defaults.adapter = async config => {
  await new Promise(resolve => setTimeout(resolve, 20))
  let data: unknown = []
  if (config.url?.endsWith('/todo-page')) data = { list: state.decision ? [] : [task], total: state.decision ? 0 : 1 }
  else if (config.url?.endsWith('/done-page')) data = { list: [task], total: 1 }
  else if (config.url?.endsWith('/business-summary-batch')) data = { [task.id]: brief }
  else if (config.url?.endsWith('/business-detail')) {
    if (state.fail) throw new Error('需求快照暂不可用')
    data = { ...brief, groups: [{ title: '本轮提交内容', fields: [
      { label: '需求说明', value: '测试需求的完整业务说明，审批人直接在右侧处理。', span: 2 },
      { label: '历史选项', value: '提交时冻结的标签' },
      { label: '图片附件', attachments: [{ name: '需求示意图', contentType: 'image/svg+xml', url: location.origin + '/fixture-attachment.svg' }] }
    ] }] }
  } else if (config.url?.endsWith('/get-approval-detail')) data = {
    status: 1, processDefinition: {}, processInstance: { name: '需求审批验证', status: state.decision ? 2 : 1, startUser: { id: 11, nickname: '测试提交人' } },
    todoTask: state.stale ? { ...task, id: 'different-task' } : task,
    activityNodes: [{ id: 'start', name: '发起人', status: 2, tasks: [{ id: 'start-task', status: 2, assigneeUser: { nickname: '测试提交人' } }] },
      { id: 'leader', name: '部门负责人审批', status: 1, tasks: [{ ...task, assigneeUser: { nickname: '测试主管' } }] },
      { id: 'chairman', name: '董事长终审', status: -1, candidateUsers: [{ nickname: '测试终审人' }] }]
  }
  else if (config.url === '/bpm/task/approve' || config.url === '/bpm/task/reject') {
    state.actions.push({ ...JSON.parse(config.data), url: config.url }); state.decision = config.url.split('/').at(-1)!; data = true
  } else if (config.url?.endsWith('/business-task-target')) data = { supported: false }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const permissions = ['bpm:task:query', 'bpm:process-instance:query', ...(params.has('readonly') ? [] : ['bpm:task:update'])]
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><BpmApprovalCenterPage permissions={permissions}/></App></ThemeProvider></BrowserRouter>)
