// UTF-8. Production form/editor with isolated transport; never calls a live backend.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App, Button, ConfigProvider, Form, Input, Modal } from 'antd'
import ContentApprovalDraft from '../src/components/ContentApprovalDraft'
import { DraftEditDialog } from '../src/pages/ContentReviewBatchPage'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { ApiError, api, http } from '../src/services/api'
import { contentReviewApi, type ContentReviewBatch } from '../src/services/materialApi'
import { requireContentReviewReviewer, submitWithFormReviewer } from '../src/services/contentReviewReviewer'
import { restoreDraftWorks } from '../src/services/contentReviewDraft'
import '../src/styles/index.css'

const mode = new URLSearchParams(location.search).get('mode') || 'edit'
const requests: Array<{ url: string; params: unknown; payload: any }> = []
let failedContext = mode === 'load-error', failedSubmit = ['version', 'unavailable', 'reject', 'lost'].includes(mode)
let definition = 'v2'
const profile = { id: 4, accountNo: 'TEST-A4', nickname: '验收账号', platformLabel: '测试平台' }
function original(): ContentReviewBatch {
  return { id: 7, batchNo: 'TEST-INLINE-7', studentPersonId: 3, accountId: 4, accountIds: [4],
    status: mode === 'revision' ? 'NEED_MODIFY' : 'DRAFT', currentStage: 'DRAFT', version: 2,
    operatorUserId: 1, directorUserId: mode === 'revision' ? 20 : undefined, relationSnapshot: {},
    availableActions: [mode === 'revision' ? 'RESUBMIT' : 'SUBMIT'], contextSnapshot: { accountSnapshots: [profile] },
    draftReviewerSelection: ['edit', 'version', 'unavailable', 'reject', 'lost'].includes(mode) ? { directorUserId: 20, processDefinitionId: 'v2' }
      : mode === 'stale' ? { directorUserId: 99, processDefinitionId: 'v2' } : mode === 'old-version' ? { directorUserId: 20, processDefinitionId: 'v1' } : undefined,
    items: [{ id: 8, contentId: 9, contentVersionId: 10, sortNo: 1, version: 0, collectMaterial: false,
      files: [{ fieldKey: 'cover', infraFileId: 99, originalName: '验收封面.png', contentType: 'image/png', fileSize: 10 }],
      contentSnapshot: { title: '保留输入作品', scriptText: '提交失败仍保留正文', purposeValue: 'test', purposeLabelSnapshot: '作品目的',
        formatValue: 'test', formatLabelSnapshot: '作品形式', plannedPublishAt: '2099-01-01T12:00:00' } }] }
}
let current = original()
Object.assign(window, { inlineFixture: { requests, get current() { return current } } })
api.dictDataByType = async () => [{ value: 'test', label: '测试选项' }]
http.defaults.adapter = async config => {
  const url = config.url || '', payload = config.data ? JSON.parse(config.data) : undefined
  requests.push({ url, params: config.params, payload })
  const respond = (data: unknown, code = 0, msg = '') => ({ config, status: 200, statusText: 'OK', headers: {}, data: { code, msg, data } })
  if (mode === 'student') {
    const student = { personId: 3, personNo: 'ST-TEST', name: '验收学员', services: [{ serviceRelationId: 5, status: 'active' }], accounts: [] }
    if (url.endsWith('/media-students/page')) return respond({ list: [student], total: 1 })
    if (url.endsWith('/media-students/3')) return respond({ student, accounts: [{ ...profile, availableActions: [] }], contents: [], positioningCards: [], positioningDrafts: [], tickets: [], talks: [] })
    if (url.endsWith('/batch/page')) return respond({ list: [current], total: 1 })
    if (url.endsWith('/student/service/5/contact-context')) return respond({ studentPersonId: 3, serviceRelationId: 5, availableActions: [], visibleTabs: [], currentStage: 'active', version: 1 })
    if (url.endsWith('/media-students/3/partner-context')) return respond({ opened: false, canInvite: false, availableActions: [] })
    if (url.endsWith('/simple-list') || url.endsWith('/list-all-simple')) return respond([])
  }
  if (url.endsWith('/submit-context')) {
    await new Promise(resolve => setTimeout(resolve, 60))
    if (failedContext) { failedContext = false; return respond(null, 403, '候选加载失败，请重试') }
    return respond({ processDefinitionId: definition, processDefinitionVersion: definition === 'v2' ? 2 : 3,
      directorTaskKey: 'opaque', roleIds: [7], previousDirectorUserId: mode === 'revision' ? 20 : undefined,
      candidates: mode === 'empty' ? [] : [{ id: 20, nickname: '审核编导甲' }, { id: 21, nickname: '审核编导乙' }] })
  }
  if (url.endsWith('/account-profile-preview')) return respond([profile])
  if (url.endsWith('/batch/get')) return respond(structuredClone(current))
  if (url.endsWith('/save-student-draft') || url.endsWith('/resubmit-from-student') || url.endsWith('/create-from-student')) {
    await new Promise(resolve => setTimeout(resolve, 80))
    current = { ...current, status: 'DRAFT', currentStage: 'DRAFT', version: current.version + 1,
      draftReviewerSelection: payload.draftReviewerSelection, items: payload.works.map((work: any, index: number) => ({
        ...current.items[index], contentVersionId: current.items[index].contentVersionId + 1, contentSnapshot: work })) }
    return respond(current.id)
  }
  if (url.endsWith('/submit')) {
    if (failedSubmit) {
      failedSubmit = false
      if (mode === 'version') { definition = 'v3'; return respond(null, 1900012141, '审核流程版本已变化，请重新选择') }
      if (mode === 'unavailable') return respond(null, 1900012143, '所选编导不可用，请重新选择')
      if (mode === 'reject') return respond(null, 1900012124, '流程暂不可用，请重试')
      current = { ...current, status: 'DIRECTOR_REVIEW', processInstanceId: 'fixture-process' }
      throw new ApiError(0, '响应丢失')
    }
    current = { ...current, status: 'DIRECTOR_REVIEW', processInstanceId: 'fixture-process' }
    return respond(true)
  }
  throw new Error(`Unexpected isolated request: ${url}`)
}
function NewForm() {
  const [form] = Form.useForm(), [done, setDone] = useState('')
  const save = async (submit: boolean) => {
    try {
      await form.validateFields()
      const reviewer = submit ? requireContentReviewReviewer(form) : undefined
      const values = form.getFieldsValue(true)
      const id = await contentReviewApi.createFromStudent({ studentPersonId: 3, accountIds: values.accountIds,
        works: values.works, draftReviewerSelection: values.draftReviewerSelection || null })
      if (reviewer) await submitWithFormReviewer(form, id, current.version, reviewer)
      setDone(submit ? '已提交审批' : '草稿已保存')
    } catch (cause) { if (!(cause as { errorFields?: unknown }).errorFields) setDone(String(cause)) }
  }
  return <Modal open title="发起内容审批" width="min(1100px, calc(100vw - 32px))" onCancel={() => {}}
    styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} footer={<>
      <Button onClick={() => form.setFieldsValue({ accountIds: [4], works: restoreDraftWorks(original()) })}>填入测试作品</Button>
      <Button onClick={() => void save(false)}>保存草稿</Button><Button type="primary" onClick={() => void save(true)}>保存并提交审批</Button></>}>
    <Form form={form} layout="vertical"><ContentApprovalDraft studentPersonId={3} accounts={[profile]}
      purposeOptions={[{ value: 'test', label: '测试选项' }]} formatOptions={[{ value: 'test', label: '测试选项' }]} />
      <Form.Item name="unused" hidden><Input /></Form.Item></Form><p>{done}</p>
  </Modal>
}
function Fixture() {
  const [open, setOpen] = useState(true)
  if (mode === 'student') return <ThemeProvider><MediaStudentsPage permissions={['zsjos:media-student:query-my', 'zsjos:content-review:query', 'zsjos:content-review:create', 'zsjos:content-review:submit']} /></ThemeProvider>
  return mode === 'new' ? <NewForm /> : <><Button onClick={() => setOpen(true)}>继续草稿</Button>
    <DraftEditDialog batch={current} open={open} onClose={() => setOpen(false)} onSaved={() => {}} /></>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={['/zsjos/media-students?personId=3']}><ConfigProvider><App><Fixture /></App></ConfigProvider></MemoryRouter>)
