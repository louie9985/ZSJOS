import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, App, Avatar, Button, Empty, Form, Input, Modal, Pagination, Space, Spin, Tag, Typography } from 'antd'
import { ReloadOutlined, UserOutlined } from '@ant-design/icons'
import { api, type ManagedLead, type LeadAttachment } from '../services/api'
import { leadAssistApi, type AssistHistory, type AssistAttachment } from '../services/leadSubmitterAssist'
import { formatTimestamp } from '../services/time'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import AttachmentCard from './AttachmentCard'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import '../styles/components/lead-assist-conversation.css'

export default function LeadSubmitterAssistHistoryPanel({ lead, canReply, onChanged }: {
  lead: ManagedLead; canReply: boolean; onChanged: () => void
}) {
  const { message } = App.useApp()
  const [rows, setRows] = useState<AssistHistory[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [current, setCurrent] = useState<AssistHistory>()
  const [remark, setRemark] = useState('')
  const [files, setFiles] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const { submitting: saving, run, resetIntent } = useSubmissionGuard()
  const request = useRef(0)
  const load = useCallback(async () => {
    const version = ++request.current
    setLoading(true); setError('')
    try {
      const data = await leadAssistApi.page(lead.id, page)
      if (version === request.current) { setRows(data.list); setTotal(data.total) }
    } catch (cause) {
      if (version === request.current) setError(cause instanceof Error ? cause.message : '协助历史加载失败')
    } finally { if (version === request.current) setLoading(false) }
  }, [lead.id, page])
  useEffect(() => { void load(); return () => { request.current++ } }, [load])
  const reply = async () => {
    if (!current || !remark.trim()) { message.warning('请填写协助备注'); return }
    try {
      await run(async ({ idempotencyKey, complete }) => {
        const uploaded = await uploadDeferredFiles(files, api.uploadLeadAttachment, setFiles)
        if (uploaded.failed) { message.error('附件上传失败，请重试'); return }
        await leadAssistApi.reply(lead.id, current.id, {
          remark: remark.trim(), attachments: uploaded.items.map(item => ({ infraFileId: item.uploaded!.infraFileId })),
          version: current.version ?? 0, idempotencyKey,
        })
        complete(); message.success('协助已回复'); setCurrent(undefined); setRemark(''); setFiles([])
        await load(); onChanged()
      })
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '回复失败') }
  }
  // Coalesce card URL refreshes through the authorized history endpoint.
  const attachmentRefresh = useRef<Promise<{ list: AssistHistory[]; total: number }> | undefined>(undefined)
  const attachments = (row: AssistHistory, field: 'requestAttachments' | 'responseAttachments') => (
    <div className="lead-assist-attachments">
      {(row[field] ?? []).map((file: AssistAttachment) => <AttachmentCard key={file.infraFileId} name={file.name} load={async () => {
        if (!attachmentRefresh.current) {
          attachmentRefresh.current = leadAssistApi.page(lead.id, page).finally(() => { attachmentRefresh.current = undefined })
        }
        const data = await attachmentRefresh.current
        const fresh = data.list.find(item => item.id === row.id)?.[field]?.find(item => item.infraFileId === file.infraFileId)
        if (!fresh) throw new Error('附件记录已变化，请刷新协助历史')
        return fresh
      }} />)}
    </div>
  )
  return <section className="lead-detail-tab-content lead-assist-panel">
    <div className="lead-assist-toolbar"><Typography.Text type="secondary">申请与回复 · 共 {total} 次协助</Typography.Text><Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>刷新</Button></div>
    {loading ? <div className="lead-assist-loading"><Spin /></div> : error ? <Alert type="error" title={error} showIcon action={<Button onClick={() => void load()}>重试</Button>} /> : rows.length === 0 ? <Empty description="暂无协助历史" /> : <div className="lead-assist-conversations" aria-label="协助对话">
      {rows.map(row => <article key={row.id} className="lead-assist-conversation">
        <div className="lead-assist-round"><Tag color={row.status === 'completed' ? 'green' : 'gold'}>{row.status === 'completed' ? '已回复' : '待回复'}</Tag><Typography.Text type="secondary">提交人：{row.submitterName || '未记录'}{row.assigneeName && ` · 协助处理人：${row.assigneeName}`}</Typography.Text></div>
        <div className="lead-assist-message is-request">
          <Avatar icon={<UserOutlined />} />
          <div className="lead-assist-message-body">
            <Space wrap className="lead-assist-message-meta"><Typography.Text strong>{row.requesterName || '发起人（姓名未记录）'}</Typography.Text><Typography.Text type="secondary">发起申请 · {formatTimestamp(row.requestedAt)}</Typography.Text></Space>
            <div className="lead-assist-bubble">
              <dl><dt>遇到的问题</dt><dd>{row.problem || '未填写'}</dd><dt>希望协助方式</dt><dd>{row.expectedAssistance || '未填写'}</dd><dt>备注</dt><dd>{row.remark || '未填写'}</dd></dl>
              <div className="lead-assist-attachment-label">申请附件{row.requestAttachments?.length ? `（${row.requestAttachments.length}）` : '：无'}</div>
              {attachments(row, 'requestAttachments')}
            </div>
          </div>
        </div>
        {row.status === 'completed' ? <div className="lead-assist-message is-response">
          <Avatar icon={<UserOutlined />} />
          <div className="lead-assist-message-body">
            <Space wrap className="lead-assist-message-meta"><Typography.Text strong>{row.responderName || '回复人（姓名未记录）'}</Typography.Text><Typography.Text type="secondary">协助回复 · {formatTimestamp(row.respondedAt)}</Typography.Text></Space>
            <div className="lead-assist-bubble"><dl><dt>协助备注</dt><dd>{row.responseRemark || '未填写'}</dd></dl><div className="lead-assist-attachment-label">回复附件{row.responseAttachments?.length ? `（${row.responseAttachments.length}）` : '：无'}</div>{attachments(row, 'responseAttachments')}</div>
          </div>
        </div> : <div className="lead-assist-pending"><Typography.Text type="secondary">等待协助回复</Typography.Text>{canReply && <Button type="primary" onClick={() => { resetIntent(); setCurrent(row); setRemark(''); setFiles([]) }}>填写回复</Button>}</div>}
      </article>)}
    </div>}
    {!loading && !error && total > 10 && <Pagination current={page} pageSize={10} total={total} showSizeChanger={false} onChange={setPage} />}
    <Modal title="回复协助申请" open={!!current} confirmLoading={saving} closable={!saving} maskClosable={!saving} keyboard={!saving} cancelButtonProps={{ disabled: saving }} onCancel={() => { if (!saving) setCurrent(undefined) }} onOk={() => void reply()} okText="发送回复">
      <Form layout="vertical"><Form.Item label="协助备注" required><Input.TextArea rows={6} maxLength={2000} showCount disabled={saving} value={remark} onChange={event => setRemark(event.target.value)} placeholder="填写协助备注" /></Form.Item><Form.Item label="附件" extra="支持 JPG、PNG、WebP 图片"><DeferredAttachmentPicker value={files} accept="image/jpeg,image/png,image/webp" maxCount={20} disabled={saving} onChange={setFiles} /></Form.Item></Form>
    </Modal>
  </section>
}
