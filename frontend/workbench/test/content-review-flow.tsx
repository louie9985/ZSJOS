// UTF-8. Isolated fixture: all HTTP requests are intercepted; no business writes.
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { App, Button, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import BpmApprovalActions from '../src/components/bpm/BpmApprovalActions'
import ContentReviewBatchPage, { DraftEditDialog } from '../src/pages/ContentReviewBatchPage'
import { api, http } from '../src/services/api'
import type { ContentReviewBatch } from '../src/services/materialApi'
import '../src/styles/index.css'
let phase = 'FINAL'
let returned = false
let incomplete = false
let result = ''
let refreshResult = () => {}
function batch(): ContentReviewBatch {
  const prefix = phase === 'FINAL' ? 'FINAL' : 'DIRECTOR'
  return { id: 1, batchNo: '验收批次-1', accountId: 1, operatorUserId: 1, operatorName: '测试运营', directorName: '测试编导',
    relationSnapshot: {}, contextSnapshot: {}, status: `${prefix}_REVIEW`, currentStage: prefix, version: 1,
    processInstanceId: 'fixture', currentTaskId: 'task-fixture',
    availableActions: [`${prefix}_DECIDE`, ...(incomplete ? [] : [`${prefix}_COMPLETE`, `${prefix}_${returned ? 'RETURN' : 'APPROVE'}`])],
    items: [1, 2].map(id => ({ id, contentId: id, contentVersionId: id, contentRecordVersion: 1, sortNo: id, collectMaterial: false, version: 1,
      contentSnapshot: { title: `测试作品 ${id}`, scriptText: '保留原内容和附件，修改后再次提交。' }, files: [],
      directorDecision: phase === 'DIRECTOR' && incomplete && id === 2 ? undefined : phase === 'DIRECTOR' && returned && id === 2 ? 'RETURNED' : 'APPROVED',
      finalDecision: phase !== 'FINAL' || incomplete && id === 2 ? undefined : returned && id === 2 ? 'RETURNED' : 'APPROVED'
    })) }
}
api.bpmApprovalDetail = async () => ({ activityNodes: [] })
api.bpmCommentList = async () => []
api.dictDataByType = async () => [{ value: 'test', label: '历史选项' }]
function revisionBatch(): ContentReviewBatch {
  const source = batch()
  return { ...source, contextSnapshot: { accountSnapshots: [{ id: 1, accountNo: 'ACC-1', nickname: '原账号名称', platformLabel: '抖音', platformValue: 'dy', sStageLabel: '原期段', currentStatusLabel: '原状态' }] }, studentPersonId: 3, status: 'NEED_MODIFY', availableActions: ['RESUBMIT'], items: source.items.map(item => ({
    ...item, directorComment: '请补充说明后重新提交', contentSnapshot: { ...item.contentSnapshot, commentHook: '原评论区钩子', referenceWorkUrl: 'https://example.com/reference', plannedPublishAt: '2030-10-01T10:00:00', purposeValue: 'test', formatValue: 'test' },
    files: [{ fieldKey: 'cover', infraFileId: 11, originalName: '原封面.png', contentType: 'image/png', fileSize: 12 },
      { fieldKey: 'deliverable', infraFileId: 12, originalName: '原附件.pdf', contentType: 'application/pdf', fileSize: 12 }]
  })) }
}
let savedRevision: ContentReviewBatch | undefined
let failSubmitOnce = false
const revisionCommands: string[] = []
http.defaults.adapter = async config => {
  let data: unknown = true
  const url = config.url || ''
  if (config.method === 'get') data = url.endsWith('/page') ? { list: [batch()], total: 1 } : url.endsWith('/get') && Number(config.params?.id) === 9 && savedRevision ? savedRevision : batch()
  else {
    revisionCommands.push(url)
    result = revisionCommands.join('\n'); refreshResult()
    const payload = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    if (url.endsWith('resubmit-from-student') || url.endsWith('save-student-draft')) {
      savedRevision = { ...revisionBatch(), id: 9, batchNo: '修订草稿-9', status: 'DRAFT', currentStage: 'DRAFT',
        availableActions: ['SUBMIT'], version: (savedRevision?.version || 0) + 1,
        items: payload.works.map((work: Record<string, unknown>, index: number) => ({ ...revisionBatch().items[index],
          id: index + 1, contentId: index + 1, contentVersionId: 100 + index + (savedRevision?.version || 0), contentSnapshot: work })) }
      data = 9
    } else if (url.endsWith('/9/submit')) {
      if (failSubmitOnce) { failSubmitOnce = false; return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 400, msg: '模拟提交失败，修改已保存，请重试' } } }
      savedRevision = { ...savedRevision!, status: 'DIRECTOR_REVIEW', currentStage: 'DIRECTOR' }
    }
  }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
function Fixture() {
  const [key, setKey] = useState(0)
  const [editing, setEditing] = useState(false)
  const [editorBatch, setEditorBatch] = useState<ContentReviewBatch>(() => revisionBatch())
  const [, update] = useState(0)
  refreshResult = () => update(v => v + 1)
  const change = (p: string, r: boolean, i: boolean) => { phase = p; returned = r; incomplete = i; setKey(v => v + 1) }
  return <><section aria-label="内容审核动作"><BpmApprovalActions task={{ id: 'fixture', status: 1, name: '终审', createTime: 0, processInstanceId: 'fixture' }} canUpdate users={[]} decisionOnly onSuccess={() => {}} /></section><section aria-label="其他审批动作"><BpmApprovalActions task={{ id: 'other', status: 1, name: '其他审批', createTime: 0, processInstanceId: 'other' }} canUpdate users={[]} onSuccess={() => {}} /></section><Space wrap>
    <Button onClick={() => change('FINAL', false, false)}>终审全通过场景</Button>
    <Button onClick={() => change('FINAL', true, false)}>终审混合场景</Button>
    <Button onClick={() => change('FINAL', false, true)}>终审未审完场景</Button>
    <Button onClick={() => change('DIRECTOR', true, false)}>编导退回场景</Button>
    <Button onClick={() => { setEditorBatch(savedRevision?.status === 'DRAFT' ? savedRevision : revisionBatch()); setEditing(true) }}>运营修改场景</Button>
    <Button onClick={() => { savedRevision = undefined; revisionCommands.length = 0; failSubmitOnce = true; setEditorBatch(revisionBatch()); setEditing(true) }}>重提失败重试场景</Button>
    </Space><pre aria-label="最后请求" style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{result}</pre><ContentReviewBatchPage key={key} />{editing && <DraftEditDialog batch={editorBatch} open onClose={() => setEditing(false)} onSaved={() => setEditing(false)} />}</>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter><Fixture /></MemoryRouter></App></ThemeProvider>)
