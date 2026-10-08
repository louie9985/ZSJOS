// UTF-8. Actual FeedbackPage, synthetic transport only; no live requests.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import FeedbackPage from '../src/pages/FeedbackPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
const state = { fail: false, urgeFail: false, posts: [] as Record<string, unknown>[], urged: false }
Object.assign(window, { feedbackFixture: state })
const now = Date.now()
const currentTasks = [{ id: 'task-2', name: '终审', nodeId: 'review', assigneeUserId: 22, assigneeName: '测试审批人乙', createTime: now }]
const record = { id: 1, feedbackType: 'REQUIREMENT', feedbackNo: 'XQ-TEST-001', title: '需求审批展示验证', status: 'APPROVING', submitterUserId: 11, submitterName: '测试提交人', version: 3, approvalRoundNo: 2, processInstanceId: 'process', createTime: now, lastActivityAt: now, canReply: false, canResubmit: false, canSubmitSurvey: false, approvalSummary: { availability: 'AVAILABLE', currentTasks } }
http.defaults.adapter = async config => {
  let data: unknown = {}
  if (config.url?.endsWith('/portal')) data = { entries: [], recent: [record] }
  else if (config.url?.endsWith('/my-page')) data = { list: [record], total: 1 }
  else if (config.url?.endsWith('/approval')) {
    if (state.fail) throw new Error('流程读取失败，请重试')
    const round = config.params?.roundNo || 2
    const historical = round === 1
    data = { roundNo: round, latestRoundNo: 2, version: 3, rounds: [{ roundNo: 1 }, { roundNo: 2 }],
      availability: params.has('missing') ? 'UNAVAILABLE' : params.has('disabled') ? 'NOT_REQUIRED' : 'AVAILABLE',
      unavailableReason: params.has('disabled') ? '本轮无需审批' : '本轮审批流程记录缺失，请联系管理员',
      canUrge: !historical && !params.has('readonly') && !params.has('disabled') && !params.has('missing'),
      lastUrgedAt: state.urged ? now : undefined, nextUrgeAt: state.urged ? now + 1800000 : undefined,
      fields: [{ key: 'title', label: '需求内容', type: 'text' }], values: { title: historical ? '第一轮原始内容' : '第二轮调整后的内容' },
      progress: params.has('disabled') || params.has('missing') ? undefined : { status: historical ? 3 : 1,
        currentTasks: historical ? [] : currentTasks, nodes: [
          { id: 'leader', name: '部门负责人审核', status: historical ? 3 : 2, candidates: [], tasks: [{ id: 'task-1', status: historical ? 3 : 2, assigneeName: '测试审批人甲', createTime: now - 3600000, endTime: now - 1800000, reason: historical ? '请补充使用场景' : '同意提交终审' }] },
          ...(historical ? [] : [{ id: 'review', name: '终审', status: 1, candidates: [], tasks: [{ id: 'task-2', status: 1, assigneeName: '测试审批人乙', createTime: now }] }])
        ] }
    }
  } else if (config.url?.endsWith('/urge')) {
    state.posts.push(JSON.parse(config.data)); if (state.urgeFail) throw new Error('网络中断，请重试')
    state.urged = true; data = true
  } else if (config.url?.endsWith('/1')) data = record
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const permissions = ['zsjos:feedback:query', 'zsjos:feedback:read', ...(params.has('readonly') ? [] : ['zsjos:feedback:requirement:urge'])]
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><FeedbackPage permissions={permissions}/></App></ThemeProvider></BrowserRouter>)
