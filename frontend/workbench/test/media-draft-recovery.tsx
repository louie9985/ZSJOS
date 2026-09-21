import ContentApprovalDraft from '../src/components/ContentApprovalDraft'
import { restoreDraftWorks } from '../src/services/contentReviewDraft'
// Synthetic transport only. No business service receives these fixture writes.
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button, Form } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ViralDecomposeWorkspace from '../src/components/ViralDecomposeWorkspace'
import StudentContentDraftPicker from '../src/components/StudentContentDraftPicker'
import { DraftEditDialog } from '../src/pages/ContentReviewBatchPage'
import { http } from '../src/services/api'
import type { ContentReviewBatch } from '../src/services/materialApi'
import '../src/styles/index.css'

const review = new URLSearchParams(location.search).has('review')
const content = new URLSearchParams(location.search).has('content')
const code = content ? 'viral_content' : 'viral_account'
const titleKey = content ? 'work_title' : 'account_name'
const field = { key: titleKey, label: '验收标题', type: 'text', section: 'ACCOUNT_DETAIL' }
const oldField = { key: 'old_notes', label: '原模板备注', type: 'textarea', section: 'DIRECTOR_ANALYSIS' }
const fixtureKey = `media-draft-recovery-${code}`
let material = JSON.parse(sessionStorage.getItem(fixtureKey) || 'null') || { id: 42, materialTypeId: 2, materialNo: 'TEST-42', title: '验收草稿', status: 'DRAFT', version: 1,
  availableActions: ['UPDATE'], currentVersion: { status: 'DRAFT', fields: [field, oldField], values: { [titleKey]: '验收草稿', old_notes: '旧模板已保存备注' }, dictSnapshot: {} } }
const types = [{ id: 2, code, name: '验收类型', currentSchema: { fields: [field, { ...oldField, key: 'new_notes', label: '新模板备注' }] } }]
let batch: ContentReviewBatch = { id: 7, batchNo: 'TEST-BATCH-7', studentPersonId: 3, accountId: 4, accountIds: [4], status: 'DRAFT', currentStage: 'DRAFT', version: 0,
  operatorUserId: 1, relationSnapshot: {}, availableActions: ['SUBMIT'], contextSnapshot: { accountSnapshots: [{ id: 4, nickname: '验收账号' }] },
  items: [{ id: 8, contentId: 9, contentVersionId: 10, contentRecordVersion: 0, sortNo: 1, version: 0, files: [{ id: 1, fieldKey: 'cover', sortNo: 1, infraFileId: 99, originalName: '验收封面.png', contentType: 'image/png', fileSize: 10 }],
    contentSnapshot: { title: '原草稿标题', scriptText: '已保存的正文', purposeValue: 'test', formatValue: 'test', plannedPublishAt: '2099-01-01T12:00:00',
      materialRefs: [{ materialId: 12, materialVersionId: 34, materialNo: 'TEST-M12', title: '原参考素材', materialTypeName: '验收素材' }] } }] }
http.defaults.adapter = async config => {
  const url = config.url || ''; let data: unknown
  if (url.endsWith('/material-type/list')) data = types
  else if (url.includes('dict-data')) data = [{ value: 'test', label: '验收选项' }]
  else if (url.endsWith('/material/page')) data = { list: material.status === 'DRAFT' ? [material] : [], total: material.status === 'DRAFT' ? 1 : 0 }
  else if ((url.endsWith('/material/42') && config.method === 'put') || (url.endsWith('/material') && config.method === 'post')) {
    const body = JSON.parse(config.data)
    material = { ...material, title: body.title, version: material.version + 1, currentVersion: { ...material.currentVersion, values: body.values } }
    sessionStorage.setItem(fixtureKey, JSON.stringify(material)); data = 42
  } else if (url.endsWith('/material/42')) data = structuredClone(material)
  else if (url.endsWith('/material/42/submit')) { material.status = 'IN_APPROVAL'; data = true }
  else if (url.endsWith('/batch/page')) data = { list: [batch], total: 1 }
  else if (url.endsWith('/batch/get')) data = structuredClone(batch)
  else if (url.endsWith('/save-student-draft')) {
    const body = JSON.parse(config.data), work = body.works[0]
    batch = { ...batch, items: [{ ...batch.items[0], contentSnapshot: { ...work, materialRefs: work.referenceMaterials } }] }
    document.title = `引用版本 ${work.referenceMaterials?.[0]?.materialVersionId ?? '丢失'}`; data = 7
  } else throw new Error(`Unexpected fixture request: ${config.method} ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
function StudentFormFixture() {
  const [form] = Form.useForm()
  const [mounted, setMounted] = useState(false)
  const open = () => { form.resetFields(); form.setFieldsValue({ accountIds: batch.accountIds, works: restoreDraftWorks(batch) }); setMounted(true) }
  const save = async () => {
    await form.validateFields()
    const works = form.getFieldsValue(true).works
    document.title = `原表单引用版本 ${works[0].referenceMaterials?.[0]?.materialVersionId ?? '丢失'}`
  }
  return <><Button onClick={open}>恢复到学员原表单</Button>{mounted && <Form form={form} layout="vertical"><ContentApprovalDraft accounts={[{ id: 4, accountNo: 'TEST-A4', nickname: '验收账号' }]} purposeOptions={[{ value: 'test', label: '验收目的' }]} formatOptions={[{ value: 'test', label: '验收形式' }]} /><Button onClick={() => void save()}>验收原表单保存</Button></Form>}</>
}
function ReviewFixture() {
  const [picker, setPicker] = useState(true), [editing, setEditing] = useState<ContentReviewBatch>()
  return <><Button onClick={() => setPicker(true)}>重开草稿入口</Button>
    {picker && <StudentContentDraftPicker studentPersonId={3} onClose={() => setPicker(false)} onCreate={() => setPicker(false)} onResume={value => { setPicker(false); setEditing(value) }} />}
    {editing && <DraftEditDialog batch={editing} open onClose={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); setPicker(true) }} />}</>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter><App>{new URLSearchParams(location.search).has('student-form') ? <StudentFormFixture /> : review ? <ReviewFixture /> : <ViralDecomposeWorkspace code={code} />}</App></MemoryRouter>)
