// UTF-8. Production page and message action with isolated transport; no real requests or writes.
import { createRoot } from 'react-dom/client'
import { BrowserRouter, useNavigate } from 'react-router-dom'
import { App, Button, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ContentReviewBatchPage from '../src/pages/ContentReviewBatchPage'
import { http, type NotifyMessage } from '../src/services/api'
import { executeNotifyMessageAction } from '../src/services/notifyMessageAction'
import type { ContentReviewBatch } from '../src/services/materialApi'
import '../src/styles/index.css'

const controls = { listError: false, denied: false, missing: false, temporary: false, delay: 0, gets: [] as number[] }
Object.assign(window, { nodeMessageFixture: controls })
const batch = (id: number): ContentReviewBatch => ({
  id, batchNo: `CR-NODE-${id}`, accountId: 1, operatorUserId: 7,
  relationSnapshot: {}, contextSnapshot: {}, version: 1, availableActions: [], accounts: [],
  studentName: id === 41 ? '历史轮次示例' : '终审通过示例',
  status: id === 41 ? 'RESUBMITTED' : 'COMPLETED', currentStage: 'DONE',
  items: [{ id: id * 10, contentId: id * 10, contentVersionId: id * 100, contentRecordVersion: 1,
    sortNo: 1, version: 1, collectMaterial: false, files: [], directorDecision: 'APPROVED',
    finalDecision: id === 41 ? 'RETURNED' : 'APPROVED', finalComment: id === 41 ? '历史冻结意见' : '终审已通过',
    contentSnapshot: { titleSnapshot: id === 41 ? '历史冻结作品' : '当前通过作品', scriptText: '隔离示例正文', contentNo: `CT-${id}` } }]
})
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = [], code = 0, msg = ''
  if (url.endsWith('/content-review/batch/page')) {
    if (controls.listError) { code = 503; msg = '列表暂不可用' }
    data = { list: [batch(1)], total: 100 }
  } else if (url.endsWith('/content-review/batch/get')) {
    const id = Number(config.params.id); controls.gets.push(id)
    const failure = controls.denied ? 1900020030 : controls.missing ? 1900020021 : controls.temporary ? 503 : 0
    await new Promise(resolve => setTimeout(resolve, controls.delay))
    code = failure; msg = '隔离失败示例'; data = batch(id)
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code, msg, data } }
}
function Fixture() {
  const navigate = useNavigate()
  const { message, notification } = App.useApp()
  const open = (id: number) => {
    const detail: NotifyMessage = { id, templateNickname: '中世健消息中心', templateContent: '审批结果',
      templateType: 2, createTime: Date.now(), readStatus: true, actionType: 'business_detail',
      bizType: 'content_review', bizId: id, sceneCode: 'zsjos.content_review.final_completed' }
    return executeNotifyMessageAction(detail, { navigate, warn: message.warning, refreshUnreadCount: async () => {} })
  }
  return <>
    <Space wrap style={{ padding: 8 }}>
      <Button onClick={() => void open(42)}>打开终审通过消息</Button>
      <Button onClick={() => void open(41)}>打开历史未通过消息</Button>
      <Button onClick={() => notification.info({ key: 'fixture-node', title: '内容审批 · 终审通过',
        description: '批次 CR-NODE-42，已全部通过，待发布。', duration: 0, onClick: () => { notification.destroy('fixture-node'); void open(42) } })}>模拟结果弹窗</Button>
    </Space>
    <div style={{ height: 'calc(100dvh - 64px)' }}><ContentReviewBatchPage permissions={['zsjos:content-review:query']} /></div>
  </>
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><Fixture /></App></ThemeProvider></BrowserRouter>)
