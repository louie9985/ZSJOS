// UTF-8. Production detail component with an isolated adapter; no live requests.
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import BpmApprovalDetail from '../src/components/bpm/BpmApprovalDetail'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const query = new URLSearchParams(location.search)
const task = { id: 'delete-task', name: '所属编导审批', status: 1, processInstanceId: 'delete-instance',
  processInstance: { name: '新媒体账号删除审批', startUser: { id: 525, nickname: '申请运营' } } }
const card = { bizType: 'media_account_delete', title: '删除账号 · 测试账号', statusText: '审批中',
  groups: [{ title: '删除申请快照', fields: [
    { label: '账号名称', value: '测试账号' }, { label: '账号编号', value: 'MA-TEST-210' },
    { label: '平台', value: '测试平台' }, { label: '申请原因', value: '误新增账号' },
    { label: '申请人', value: '申请运营' }, { label: '所属编导（审批人）', value: '所属编导' },
  ] }] }
http.defaults.adapter = async config => {
  let data: unknown = []
  if (config.url?.endsWith('/get-approval-detail')) data = { processDefinition: { formType: 20 },
    processInstance: { ...task.processInstance, status: 1, summary: query.has('summary') ? [{ key: 'assignee', label: '审批人', value: '47' }] : [], formVariables: { assignee: 47, coll_userList: [47], applicantUserId: 525 } }, todoTask: task, activityNodes: [] }
  else if (config.url?.endsWith('/business-detail')) data = query.has('denied') ? { bizType: 'media_account_delete', message: '暂无查看本次删除申请的权限', groups: [] } : card
  else if (!config.url?.includes('/comment/') && !config.url?.includes('/task/list-by-process')) throw new Error('Unexpected fixture request: ' + config.url)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><App>
  <div style={{ height: '100vh' }}><BpmApprovalDetail task={task} view="todo" canUpdate={false} users={[]} dictDataByType={async () => []} /></div>
</App></ThemeProvider></MemoryRouter>)
