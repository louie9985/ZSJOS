// UTF-8. Real action/detail components with isolated synthetic transport.
import { createRoot } from 'react-dom/client'
import { BrowserRouter, useLocation, useNavigate } from 'react-router-dom'
import { App, Button } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import FeedbackPage from '../src/pages/FeedbackPage'
import MessageDetail from '../src/components/MessageDetail'
import { http, type NotifyMessage } from '../src/services/api'
import { executeNotifyMessageAction, isNotifyBusinessActionCandidate } from '../src/services/notifyMessageAction'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
const state = { failure: params.get('failure') || '', requests: [] as string[] }
Object.assign(window, { feedbackNotificationFixture: state })
const permissions = ['zsjos:feedback:query', ...(params.has('no-read') ? [] : ['zsjos:feedback:read'])]
const item: NotifyMessage = { id: 11, templateNickname: '系统', templateTitle: '反馈进度更新', templateContent: '反馈已处理完成',
  templateType: 1, readStatus: true, createTime: Date.now(), actionType: 'business_detail',
  bizType: 'feedback', bizId: params.has('invalid') ? 0 : 73, sceneCode: 'zsjos.feedback.completed' }
http.defaults.adapter = async config => {
  const url = config.url || ''
  state.requests.push(url)
  let data: unknown
  let code = 0, msg = ''
  if (url.endsWith('/portal')) data = { entries: [], recent: [] }
  else if (url === '/zsjos/feedback/73' || url === '/zsjos/feedback/73/approver-view') {
    if (state.failure) { code = state.failure === 'forbidden' ? 403 : 404; msg = state.failure === 'forbidden' ? '无权查看该反馈' : '反馈不存在' }
    else data = { id: 73, feedbackNo: 'BUG-TEST-073', feedbackType: 'BUG', title: '指定反馈详情验证', status: 'COMPLETED',
      createTime: Date.now(), version: 1, fields: [], values: {}, replies: [], completedResult: '已修复目标问题' }
  } else throw new Error('Unexpected fixture request: ' + url)
  return { data: { code, msg, data }, status: 200, statusText: 'OK', headers: {}, config }
}
function Fixture() {
  const navigate = useNavigate(), current = useLocation()
  const { message } = App.useApp()
  const open = () => { void executeNotifyMessageAction(item, { navigate, warn: message.warning, refreshUnreadCount: async () => {} }) }
  return current.pathname === '/zsjos/feedback'
    ? <FeedbackPage permissions={permissions}/>
    : <><Button onClick={open}>点击反馈消息</Button><MessageDetail message={item} categories={[]}
      businessAction={isNotifyBusinessActionCandidate(item)} onOpenLead={open}/></>
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><Fixture/></App></ThemeProvider></BrowserRouter>)
