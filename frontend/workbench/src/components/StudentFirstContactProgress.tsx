import { useState } from 'react'
import { Alert, App, Button, Checkbox, Empty, Modal, Space, Tag, Typography, Upload } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { api, type StudentContactContext } from '../services/api'
import { formatTimestamp } from '../services/time'

export default function StudentFirstContactProgress({ context, onRefresh }: {
  context: StudentContactContext; onRefresh?: () => Promise<void>
}) {
  const { message } = App.useApp()
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<string[]>([])
  const [files, setFiles] = useState<Array<{ uid: string; name: string; status: 'uploading' | 'done' | 'error'; fileId?: number }>>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [command, setCommand] = useState('')
  const [editVersion, setEditVersion] = useState(context.version)
  const progress = context.firstContactProgress
  const canEdit = !!onRefresh && context.availableActions.includes('UPDATE_FIRST_CONTACT_CHECKLIST') && !progress?.historyIncomplete
  const begin = () => {
    setSelected(progress?.items.filter(item => item.completed).map(item => item.key) || [])
    setFiles([]); setError(''); setEditVersion(context.version); setCommand(crypto.randomUUID()); setOpen(true)
  }
  const save = async () => {
    if (files.some(file => file.status !== 'done')) { setError('请等待附件上传完成，或移除失败附件'); return }
    if (progress?.items.some(item => selected.includes(item.key) && item.attachmentRequired && !item.attachmentFileIds?.length && !files.length)) {
      setError('已勾选的附件项需要上传附件'); return
    }
    setSaving(true); setError('')
    try {
      await api.studentFirstContactChecklist(context.serviceRelationId, { version: editVersion,
        completedChecklistKeys: selected, attachmentFileIds: files.map(file => file.fileId!), idempotencyKey: command })
      setOpen(false); message.success('首联清单已更新'); await onRefresh?.()
    } catch (e) { setError(e instanceof Error ? e.message : '更新失败，请重试') }
    finally { setSaving(false) }
  }
  const planLabel = progress?.studyPlanStatus === 'first_contact' ? '首联时已完成' : progress?.studyPlanStatus === 'later' ? '后续已完成' : progress?.studyPlanStatus === 'pending' ? '待制定' : '历史信息不完整'
  return <section className="lead-card" data-testid="first-contact-progress">
    <Space wrap style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }}><Typography.Text strong>首联完成情况</Typography.Text>{progress && canEdit && <Button size="small" onClick={begin}>更新清单</Button>}</Space>
    {!progress ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未成功首联" /> : <Space orientation="vertical" style={{ width: '100%' }}>
      <Typography.Text type="secondary">{formatTimestamp(progress.submittedAt)} · {progress.operatorUserName || '历史操作人未记录'}</Typography.Text>
      {progress.historyIncomplete && <Alert type="warning" showIcon title="历史信息不完整，暂不能更新清单" />}
      {progress.source === 'historical_config' && <Typography.Text type="secondary">清单来自首联任务绑定的历史配置</Typography.Text>}
      <Typography.Text>已完成 {progress.items.filter(item => item.completed).length} / {progress.items.length}</Typography.Text>
      {progress.items.map(item => <div key={item.key}><Tag color={item.completed ? 'success' : 'default'}>{item.completed ? '已完成' : '未完成'}</Tag>{item.title}</div>)}
      <Typography.Text>学习计划：{planLabel}{progress.studyPlanCompletedAt ? ' · ' + formatTimestamp(progress.studyPlanCompletedAt) : ''}</Typography.Text>
    </Space>}
    <Modal title="更新首联清单" open={open} onCancel={() => { if (!saving && !files.some(file => file.status === 'uploading')) setOpen(false) }} onOk={() => void save()} confirmLoading={saving} destroyOnHidden>
      <Space orientation="vertical" style={{ width: '100%' }}>
        <Typography.Text type="secondary">按实际完成情况更新，不改变当前交付阶段和下次联系时间。</Typography.Text>
        {error && <Alert type="error" showIcon title={error} />}
        <Checkbox.Group value={selected} disabled={saving} onChange={value => { setSelected(value as string[]); setCommand(crypto.randomUUID()) }}>
          <Space orientation="vertical">{progress?.items.map(item => <Checkbox key={item.key} value={item.key}>{item.title}{item.attachmentRequired ? '（完成需附件）' : ''}</Checkbox>)}</Space>
        </Checkbox.Group>
        <Upload disabled={saving || files.length >= 9} fileList={files} beforeUpload={async file => {
          setCommand(crypto.randomUUID()); setFiles(old => [...old, { uid: file.uid, name: file.name, status: 'uploading' }])
          try { const result = await api.studentContactUpload(context.serviceRelationId, file); setFiles(old => old.map(row => row.uid === file.uid ? { ...row, status: 'done', fileId: result.fileId } : row)) }
          catch { setFiles(old => old.map(row => row.uid === file.uid ? { ...row, status: 'error' } : row)) }
          return false
        }} onRemove={file => { setCommand(crypto.randomUUID()); setFiles(old => old.filter(row => row.uid !== file.uid)); return true }}><Button icon={<UploadOutlined />}>上传完成凭证</Button></Upload>
      </Space>
    </Modal>
  </section>
}
