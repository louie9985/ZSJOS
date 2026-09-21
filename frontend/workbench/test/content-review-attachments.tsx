import { DraftEditDialog } from '../src/pages/ContentReviewBatchPage'
import type { ContentReviewBatch } from '../src/services/materialApi'
// UTF-8. Isolated UI fixture; uploads and business commands never leave the browser.
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button, Form, Modal } from 'antd'
import dayjs from 'dayjs'
import ContentApprovalDraft from '../src/components/ContentApprovalDraft'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { api, http } from '../src/services/api'
import { prepareContentReviewWorks } from '../src/services/contentReviewAttachments'
import '../src/styles/index.css'
const fixture = { uploads: 0, failNext: false, requests: [] as Array<Record<string, unknown>[]>, values: {}, saves: [] as Array<{ url: string; body: Record<string, unknown> }> }
Object.assign(window, { attachmentFixture: fixture })
http.defaults.adapter = async config => {
  if (config.method === 'post') fixture.saves.push({ url: config.url || '', body: JSON.parse(config.data) })
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data: config.method === 'post' ? 88 : [{ value: 'test', label: '测试选项' }] } }
}
api.mediaContent.uploadVersionFile = async file => {
  fixture.uploads++
  if (fixture.failNext) { fixture.failNext = false; throw new Error('模拟文件传输失败') }
  return { fileId: fixture.uploads, name: file.name, contentType: file.type, size: file.size, previewUrl: URL.createObjectURL(file) }
}
function Fixture() {
  const [form] = Form.useForm()
  const [busy, setBusy] = useState(false)
  const [editor, setEditor] = useState<ContentReviewBatch>()
  Object.assign(fixture, { edit: (status: string) => setEditor({
    id: 50, batchNo: 'TEST', accountId: 1, studentPersonId: 1, operatorUserId: 1, relationSnapshot: {}, contextSnapshot: {},
    status, currentStage: 'draft', version: 0, availableActions: [status === 'DRAFT' ? 'SUBMIT' : 'RESUBMIT'],
    items: [{ id: 1, contentId: 10, contentVersionId: 20, contentRecordVersion: 0, sortNo: 1, collectMaterial: false, version: 0,
      contentSnapshot: { title: '历史作品', scriptText: '历史正文', plannedPublishAt: dayjs().add(1, 'day').format('YYYY-MM-DDTHH:mm:ss'), purposeValue: 'test', formatValue: 'test' },
      files: [{ id: 1, fieldKey: 'cover', sortNo: 1, infraFileId: 11, originalName: 'cover.png', contentType: 'image/png', fileSize: 10 },
        { id: 2, fieldKey: 'deliverable', sortNo: 1, infraFileId: 12, originalName: '历史审核.pdf', contentType: 'application/pdf', fileSize: 10 }],
    }],
  }) })
  const { message } = App.useApp()
  const save = async () => {
    try {
      const values = await form.validateFields()
      setBusy(true)
      fixture.requests.push(await prepareContentReviewWorks(values.works, (index, field, items) => form.setFieldValue(['works', index, field], items)))
      message.success('草稿已保存')
    } catch (error) { message.error(error instanceof Error ? error.message : '请填写必填字段') }
    finally { setBusy(false) }
  }
  if (editor) return <DraftEditDialog batch={editor} open onClose={() => setEditor(undefined)} onSaved={() => setEditor(undefined)} />
  return <Modal open title="发起内容审批" width="min(1100px, calc(100vw - 32px))" styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} footer={<Button type="primary" loading={busy} onClick={() => void save()}>保存草稿</Button>}>
    <Form form={form} layout="vertical" disabled={busy} onValuesChange={() => { fixture.values = form.getFieldsValue(true) }} initialValues={{ accountIds: [1], works: [1, 2].map(index => ({ title: `测试作品 ${index}`, purposeValue: 'test', formatValue: 'test', plannedPublishAt: dayjs().add(1, 'day'), scriptText: '审核正文' })) }}>
      <ContentApprovalDraft disabled={busy} accounts={[{ id: 1, accountNo: 'TEST', nickname: '测试账号' }]} purposeOptions={[{ value: 'test', label: '测试目的' }]} formatOptions={[{ value: 'test', label: '测试形式' }]} />
    </Form>
  </Modal>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture /></App></ThemeProvider>)
