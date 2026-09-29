import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MaterialApprovalPage from '../src/pages/MaterialApprovalPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const fields = [
  { key: 'homepage', label: '主页链接', type: 'https-link' as const, section: 'ACCOUNT_DETAIL' as const },
  { key: 'hot', label: '最火作品链接', type: 'https-link' as const, section: 'DIRECTOR_ANALYSIS' as const },
  { key: 'matrix', label: '内容矩阵', type: 'textarea' as const, section: 'BUILD_SUGGESTION' as const }
]
const types = [{ code: 'viral_account', name: '爆款账号拆解' }, { code: 'viral_content', name: '爆款内容拆解' }]
const rows = types.flatMap((type, typeIndex) => Array.from({ length: 2 }, (_, index) => ({
  typeCode: type.code,
  task: { id: `${typeIndex}-${index}`, createTime: '2026-09-18T08:00:00Z', processInstanceId: `pi-${typeIndex}-${index}` },
  versionId: typeIndex * 10 + index + 1,
  materialNo: `${typeIndex ? 'CONTENT' : 'ACCOUNT'}-${index + 1}`, title: `${type.name}验收 ${index + 1}`, snapshotAvailable: true
})))
const snapshots: Record<number, any> = Object.fromEntries(rows.map(row => [row.versionId, {
  id: row.versionId, versionNo: 2, title: row.title, summary: '审批快照摘要', coverPreviewUrl: '/fixture-cover.png',
  fields, values: { homepage: 'https://example.com/home', hot: 'https://example.com/hot', matrix: '平台A：教程；平台B：案例' }, dictSnapshot: {}, files: []
}]))
// 与后端 activityNodes 同形：节点名与状态由服务端给出，前端只渲染。
const approvalDetail = {
  status: 1,
  activityNodes: [
    { id: 'StartUserNode', name: '发起人', nodeType: 10, status: 2, tasks: [
      { id: 't-start', assigneeUser: { id: 31, nickname: '栗伊琳' }, endTime: '2026-09-18T08:00:00Z' }] },
    { id: 'Activity_fb10be5a', name: '爆款审核', nodeType: 11, status: 0, tasks: [
      { id: 't-review', assigneeUser: { id: 12, nickname: '程伟' }, createTime: '2026-09-18T08:05:00Z' }] },
    { id: 'EndEvent', name: '结束', nodeType: 99, status: -1, tasks: [] }
  ],
  todoTask: { id: 't-review', name: '爆款审核', status: 1, processInstanceId: 'pi-0-0' },
  processInstance: { id: 'pi-0-0', name: '爆款内容拆解审核', status: 1, businessKey: 'material-version:1' }
}
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (url.endsWith('/material-approval/types')) data = types
  else if (url.endsWith('/material-approval/page')) {
    const typeCode = String(config.params?.typeCode)
    const list = rows.filter(row => row.typeCode === typeCode)
    data = { list, total: list.length }
  } else if (url.endsWith('/material-approval/get')) {
    const id = Number(config.params?.versionId); const row = rows.find(item => item.versionId === id)
    data = { ...row, snapshot: snapshots[id], task: { ...row?.task, id: config.params?.taskId } }
  } else if (url.includes('/bpm/process-instance/get-approval-detail')) data = approvalDetail
  else if (url.includes('/bpm/comment/list-by-process-instance-id')) data = []
  else throw new Error(`Unexpected request: ${config.method} ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const granted = new URLSearchParams(location.search).get('bpm') === '1'
createRoot(document.getElementById('root')!).render(<MemoryRouter><App><MaterialApprovalPage permissions={[
  'zsjos:material-approval:query', 'zsjos:material-approval:approve', 'zsjos:material-approval:reject',
  ...(granted ? ['bpm:process-instance:query'] : [])
]} /></App></MemoryRouter>)
