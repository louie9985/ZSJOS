import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, App, Button, Empty, Form, Image, Input, Modal, Pagination, Space, Spin, Tag, Typography } from 'antd'
import { MessageOutlined, ReloadOutlined } from '@ant-design/icons'
import type { ManagedLead } from '../services/api'
import { leadFeedbackApi, type LeadFeedbackAttachment, type LeadSubmitterFeedback } from '../services/leadSubmitterFeedback'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import { formatTimestamp } from '../services/time'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'

export default function LeadSubmitterFeedbackPanel({ lead, canCreate, onChanged, onDirtyChange }: {
  lead: ManagedLead; canCreate: boolean; onChanged: () => void; onDirtyChange: (dirty: boolean) => void
}) {
  const { message } = App.useApp()
  const [rows, setRows] = useState<LeadSubmitterFeedback[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [feedback, setFeedback] = useState('')
  const [files, setFiles] = useState<DeferredUploadItem<LeadFeedbackAttachment>[]>([])
  const [sendVersion, setSendVersion] = useState<number>()
  const { run, submitting, resetIntent } = useSubmissionGuard()
  useEffect(() => { onDirtyChange(open && (Boolean(feedback.trim()) || files.length > 0 || submitting)) },
    [feedback, files.length, onDirtyChange, open, submitting])
  useEffect(() => () => onDirtyChange(false), [onDirtyChange])
  const sequence = useRef(0)
  const filesRef = useRef(files)
  filesRef.current = files
  const load = useCallback(async () => {
    const current = ++sequence.current
    setLoading(true); setError('')
    try {
      const data = await leadFeedbackApi.page(lead.id, page)
      if (current === sequence.current) { setRows(data.list); setTotal(data.total) }
    } catch (cause) {
      if (current === sequence.current) setError(cause instanceof Error ? cause.message : '反馈加载失败')
    } finally { if (current === sequence.current) setLoading(false) }
  }, [lead.id, page])
  useEffect(() => { void load(); return () => { sequence.current++ } }, [load])
  useEffect(() => () => {
    filesRef.current.forEach(file => { if (file.previewUrl) URL.revokeObjectURL(file.previewUrl) })
  }, [])
  const clear = () => {
    files.forEach(file => { if (file.previewUrl) URL.revokeObjectURL(file.previewUrl) })
    setFeedback(''); setFiles([])
  }
  const send = async () => {
    if (!feedback.trim()) { message.warning('请填写反馈'); return }
    if (sendVersion === undefined) { message.warning('请刷新客资后重试'); return }
    try {
      await run(async ({ idempotencyKey, complete }) => {
        const uploaded = await uploadDeferredFiles(files, file => leadFeedbackApi.upload(lead.id, file), setFiles)
        if (uploaded.failed) return
        await leadFeedbackApi.create(lead.id, { feedback: feedback.trim(), version: sendVersion,
          attachmentIds: uploaded.items.map(item => item.uploaded!.fileId), idempotencyKey })
        complete(); setOpen(false); clear()
        message.success('反馈已提交'); onChanged()
        if (page === 1) await load(); else setPage(1)
      })
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '发送失败') }
  }
  return <section className="lead-detail-tab-content">
    <Space wrap>
      <Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>刷新</Button>
      {canCreate && <Button type="primary" icon={<MessageOutlined/>} onClick={() => {
        resetIntent(); setSendVersion(lead.version); setOpen(true)
      }}>回复提交人</Button>}
    </Space>
    {loading ? <Spin/> : error ? <Alert type="error" showIcon title={error}
      action={<Button onClick={() => void load()}>重试</Button>}/> : rows.length === 0
      ? <Empty description="暂无销售反馈"/> : rows.map(row => <article key={row.id} style={{ paddingBlock: 'var(--crm-card-pad)', borderBottom: '1px solid var(--crm-border)' }}>
        <Space wrap><Typography.Text strong>{row.salesName || '销售'}</Typography.Text>
          <Typography.Text type="secondary">{formatTimestamp(row.createTime)}</Typography.Text><Tag>已提交</Tag></Space>
        <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{row.feedback}</Typography.Paragraph>
        <Image.PreviewGroup><Space wrap>{row.attachments.map(file => file.url
          ? <div key={file.fileId} style={{ maxWidth: '100%', overflowWrap: 'anywhere' }}>
            {file.contentType.startsWith('image/') && <Image width={88} height={88} style={{ objectFit: 'cover' }} src={file.url} alt={file.originalName}/>}
            <Typography.Link href={file.url} target="_blank" rel="noopener noreferrer">{file.originalName}</Typography.Link>
          </div> : <Typography.Text key={file.fileId} type="secondary">{file.originalName}（文件不可用）</Typography.Text>)}</Space></Image.PreviewGroup>
      </article>)}
    {total > 10 && <Pagination current={page} pageSize={10} total={total} showSizeChanger={false} onChange={setPage}/>}
    <Modal title="回复提交人" open={open} confirmLoading={submitting} closable={!submitting} maskClosable={false}
      cancelButtonProps={{ disabled: submitting }} okText="发送反馈" onOk={() => void send()} onCancel={() => {
        if (feedback.trim() || files.length) {
          Modal.confirm({ title: '放弃未发送的反馈？', onOk: () => { setOpen(false); clear() } })
        } else setOpen(false)
      }}>
      <Form layout="vertical" disabled={submitting}>
        <Form.Item label="反馈" required><Input.TextArea rows={5} maxLength={5000} showCount value={feedback}
          onChange={event => { resetIntent(); setFeedback(event.target.value) }}/></Form.Item>
        <Form.Item label="附件"><DeferredAttachmentPicker value={files} maxCount={20} imageOnly={false} disabled={submitting}
          accept=".jpg,.jpeg,.png,.webp,.pdf,.txt,.doc,.docx,.xls,.xlsx" onChange={items => { resetIntent(); setFiles(items) }}/></Form.Item>
      </Form>
    </Modal>
  </section>
}
