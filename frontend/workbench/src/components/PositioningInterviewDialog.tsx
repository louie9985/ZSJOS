import BusinessTable from './BusinessTable'
import PositioningAttachmentPicker, { POSITIONING_ATTACHMENT_ACCEPT, type AttachmentItem } from './PositioningAttachmentPicker'
import { Alert, App, Button, DatePicker, Empty, Input, Modal, Radio, Space, Spin, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import dayjs from 'dayjs'
import { positioningInterviewApi, interviewMissingFields, type InterviewContext, type InterviewItem, type InterviewCommand } from '../services/positioningInterviewApi'
import { createIdempotencyKey } from '../services/idempotency'

export default function PositioningInterviewDialog({ relationId, onClose, onChanged, forceReadOnly = false }: {
  relationId: number; onClose: () => void; onChanged: () => void; forceReadOnly?: boolean
}) {
  const { message } = App.useApp()
  const [context, setContext] = useState<InterviewContext>()
  const [items, setItems] = useState<InterviewItem[]>([])
  const [date, setDate] = useState<string>()
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [failedFiles, setFailedFiles] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const pending = useRef<{ fingerprint: string; key: string } | undefined>(undefined)
  const removalKeys = useRef(new Map<string,string>())
  const generation = useRef(0)
  const apply = (value: InterviewContext) => { setContext(value); setItems(value.items); setDate(value.collectedAt) }
  const load = async () => {
    const run = ++generation.current; setLoading(true); setError('')
    try { const value = await positioningInterviewApi.context(relationId); if (run === generation.current) apply(value) }
    catch (cause) { if (run === generation.current) setError(cause instanceof Error ? cause.message : '加载失败，请重试') }
    finally { if (run === generation.current) setLoading(false) }
  }
  useEffect(() => { void load(); return () => { generation.current++ } }, [relationId])
  const readOnly = forceReadOnly || context?.status === 'completed'
    || !context?.availableActions.some(action => ['START_POSITIONING_INTERVIEW', 'CONTINUE_POSITIONING_INTERVIEW', 'COMPLETE_POSITIONING_INTERVIEW'].includes(action))
  const canSave = !readOnly && context?.availableActions.some(action => ['START_POSITIONING_INTERVIEW', 'CONTINUE_POSITIONING_INTERVIEW'].includes(action))
  const canComplete = !readOnly && context?.availableActions.includes('COMPLETE_POSITIONING_INTERVIEW')
  const change = (key: string, update: Partial<InterviewItem>) => setItems(previous => {
    const value = previous.find(item => item.fieldKey === key)
    return [...previous.filter(item => item.fieldKey !== key), { ...value, fieldKey: key, ...update }]
  })
  const save = async (complete: boolean) => {
    if (!context) return
    if (complete) {
      const missing = interviewMissingFields(context, items)
      if (missing.length) { setError(`还有 ${missing.length} 项未确认：${missing.slice(0, 3).map(field => field.title).join('、')}`); return }
      if (!context.attachments.length) { setError('请至少上传一份本次访谈稿'); return }
      if (!date) { setError('请选择采集日期'); return }
    }
    const body = { studentPersonId: context.studentPersonId, serviceRelationId: relationId, version: context.version, templateVersionId: context.templateVersionId, items, collectedAt: date, attachmentIds: context.attachments.map(file => file.fileId) }
    const fingerprint = JSON.stringify({ body, complete })
    if (pending.current?.fingerprint !== fingerprint) pending.current = { fingerprint, key: createIdempotencyKey() }
    const command: InterviewCommand = { ...body, idempotencyKey: pending.current.key }
    setBusy(true); setError('')
    try { apply(await positioningInterviewApi.save(relationId, command, complete)); pending.current = undefined; message.success(complete ? '定位访谈已完成' : '草稿已保存'); onChanged() }
    catch (cause) { setError(cause instanceof Error ? cause.message : '保存失败，请重试') }
    finally { setBusy(false) }
  }
  // The interview endpoint persists each transcript on selection, so the picker keeps only
  // the uploaded records; failures are re-offered as a retry instead of a phantom item.
  const upload = async (files: File[]) => {
    setBusy(true); setError(''); setUploading(true)
    const failed: File[] = []
    try {
      for (const file of files) {
        try {
          const attachment = await positioningInterviewApi.upload(relationId, file)
          setContext(value => value && ({ ...value, attachments: [...value.attachments, attachment] }))
        } catch (cause) {
          failed.push(file)
          setError(cause instanceof Error ? cause.message : `${file.name} 上传失败`)
        }
      }
    } finally { setFailedFiles(failed); setUploading(false); setBusy(false) }
  }
  const remove = async (fileId: number) => {
    if (!context) return
    setBusy(true); setError('')
    try {
      const operation = `${fileId}:${context.version}`
      if (!removalKeys.current.has(operation)) removalKeys.current.set(operation, createIdempotencyKey())
      await positioningInterviewApi.remove(relationId, fileId, context.version, removalKeys.current.get(operation)!)
      // Reload only the concurrency token. Unsaved answers and other uploaded files must survive removal.
      const latest = await positioningInterviewApi.context(relationId)
      setContext(value => value && ({ ...value, version: latest.version, attachments: value.attachments.filter(file => file.fileId !== fileId) }))
    } catch (cause) { setError(cause instanceof Error ? cause.message : '移除失败，请重试') }
    finally { setBusy(false) }
  }
  const openFile = async (fileId: number) => {
    setError('')
    const file = await positioningInterviewApi.download(relationId, fileId)
    if (!file.url || !/^https?:\/\//i.test(file.url)) throw new Error('文件地址不可用，请重试')
    return file
  }
  const attachmentItems: AttachmentItem[] = context?.attachments.map(file => ({
    key: `file-${file.fileId}`, name: file.fileName, type: file.mimeType, size: file.fileSize,
    uploaded: { id: file.fileId, name: file.fileName, type: file.mimeType, size: file.fileSize },
  })) || []
  return <Modal open width="min(1180px, calc(100vw - 32px))" title={readOnly ? '查看定位访谈' : '定位访谈大纲'} onCancel={busy ? undefined : onClose}
    styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} footer={<Space>
      <Button disabled={busy} onClick={onClose}>{readOnly ? '关闭' : '取消'}</Button>
      {canSave && <Button loading={busy} onClick={() => void save(false)}>保存草稿</Button>}
      {canComplete && <Button type="primary" loading={busy} onClick={() => void save(true)}>完成定位访谈</Button>}
    </Space>}>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" disabled={busy} onClick={() => void load()}>重新加载</Button>} />}
    {loading ? <Spin /> : !context ? <Empty description="暂时无法加载定位访谈" /> : <>
      {readOnly && <Alert type="success" message={context?.status === 'completed' ? '已完成定位访谈，记录只读' : '定位访谈记录只读'} />}
      {context.status === 'empty' && <Typography.Paragraph type="secondary">尚无草稿，可随时保存已填写的部分。</Typography.Paragraph>}
      <BusinessTable tableKey="positioning-interview" mode="compact" className="positioning-interview-table"
        rowKey="key" pagination={false} dataSource={context.fields.filter(field => field.enabled)}
        rowClassName="positioning-interview-row" columns={[
          { key: 'field', title: '字段', width: 200, ellipsis: false, render: (_, field) => <strong>{field.title}{field.required && !field.systemField ? ' *' : ''}</strong> },
          { key: 'note', title: '访谈注意', width: 320, ellipsis: false, render: (_, field) => <span className="positioning-interview-note">{field.interviewNote || '—'}</span> },
          { key: 'answer', title: '访谈内容确认', width: 320, ellipsis: false, render: (_, field) => {
            const answer = items.find(item => item.fieldKey === field.key)
            return <>
            {field.key === 'studentIdentity' ? <Typography.Text>{answer?.value || `${context.studentName} / ${context.studentNo}`}</Typography.Text>
              : field.key === 'collectedAt' ? <DatePicker disabled={readOnly || busy} value={date ? dayjs(date) : null} onChange={value => setDate(value?.format('YYYY-MM-DD'))} />
              : <Radio.Group aria-label={field.title} disabled={!canSave || busy} value={answer?.status} onChange={event => change(field.key, { status: event.target.value })}>
                <Space direction="vertical">{Object.entries(context.statusOptions).map(([value, label]) => <Radio key={value} value={value}>{label}</Radio>)}</Space>
              </Radio.Group>}
            {field.allowRemark && <Input.TextArea aria-label={`${field.title}备注`} disabled={readOnly || busy || !canSave} maxLength={4000} placeholder="可选备注，例如文稿页码或未沟通原因" value={answer?.remark} onChange={event => change(field.key, { remark: event.target.value })} />}
            </>
          } },
        ]} />
      <section className="positioning-interview-files"><Typography.Title level={5}>本次访谈稿</Typography.Title>
        <Typography.Paragraph type="secondary">支持文档、图片、音频、视频，每份不超过 20MB。完成前至少上传一份。</Typography.Paragraph>
        <PositioningAttachmentPicker items={attachmentItems} accept={POSITIONING_ATTACHMENT_ACCEPT} disabled={!canSave} busy={busy}
          hint="选择后立即上传" onChange={() => undefined} onUpload={files => void upload(files)}
          onRemove={item => { if (item.uploaded) void remove(item.uploaded.id) }}
          onDownload={async item => {
            try { return await openFile(item.uploaded!.id) }
            catch (cause) { setError(cause instanceof Error ? cause.message : '文件读取失败'); throw cause }
          }} />
        {uploading && <Typography.Text type="secondary">访谈稿上传中…</Typography.Text>}
        {failedFiles.length > 0 && <Button disabled={busy} onClick={() => void upload(failedFiles)}>重试上传：{failedFiles.map(file => file.name).join('、')}</Button>}
      </section>
      {context.legacyInterviewSnapshotJson && <details><summary>历史采访记录</summary><pre className="positioning-interview-note">{context.legacyInterviewSnapshotJson}</pre></details>}
    </>}
  </Modal>
}
