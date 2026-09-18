import { Alert, App, Button, Space, Typography, Upload } from 'antd'
import { useRef, useState } from 'react'
import { api, type PositioningCard } from '../services/api'
import PositioningDialog from './PositioningDialog'
import { PositioningFileView } from './PositioningSnapshot'

export default function PositioningEvidence({ card, onChanged, mode = 'all' }: { card: PositioningCard; onChanged: () => void; mode?: 'all' | 'files' | 'actions' }) {
  const { message } = App.useApp()
  const [open, setOpen] = useState(false), [busy, setBusy] = useState(false)
  const [files, setFiles] = useState<Array<{ uid: string; name: string; file: File; id?: number }>>([])
  const lock = useRef(false)
  const submit = async () => {
    if (lock.current || !card.submissionId || card.submissionVersion == null || !files.length) return
    lock.current = true; setBusy(true)
    try {
      const ids: number[] = []
      for (const file of files) {
        const id = file.id || (await api.positioningCard.uploadEvidence(card.id, card.submissionId, file.file)).id
        setFiles(current => current.map(row => row.uid === file.uid ? { ...row, id } : row)); ids.push(id)
      }
      await api.positioningCard.submitEvidence(card.id, card.submissionId, card.submissionVersion, ids)
      setFiles([]); setOpen(false); message.success('确认凭证已提交'); onChanged()
    } catch (cause) { message.error(cause instanceof Error ? cause.message : '凭证提交失败，请重试') }
    finally { lock.current = false; setBusy(false) }
  }
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {mode !== 'actions' && <section className="positioning-evidence" aria-label="学员确认凭证（运营上传）"><strong>学员确认凭证（运营上传）：</strong>
    {card.evidence?.length ? <div className="positioning-file-grid">{card.evidence.map(file => <div key={file.id}>
      <Typography.Text type="secondary">上传于 {file.uploadedAt ? new Date(file.uploadedAt).toLocaleString() : '历史未记录'}</Typography.Text>
      <PositioningFileView name={file.name} load={() => api.positioningCard.evidenceFile(card.id, card.submissionId!, file.id)} />
    </div>)}</div> : <Typography.Text type="secondary">暂无确认凭证</Typography.Text>}
    </section>}
    {mode !== 'files' && card.canUploadEvidence && <Button onClick={() => setOpen(true)}>{card.evidence?.length ? '追加确认凭证' : '上传确认凭证'}</Button>}
    <PositioningDialog title="上传学员确认凭证" open={open} mask={{ closable: false }} keyboard={false} onCancel={() => { if (!busy) setOpen(false) }}
      onOk={() => void submit()} okText="提交确认凭证" confirmLoading={busy} okButtonProps={{ disabled: !files.length }}>
      <Alert type="info" message="请上传与学员确认本版定位卡的聊天记录、音频等凭证。至少一份，每份不超过 20 MB；提交成功后该版本才可应用。" />
      <Upload fileList={files} disabled={busy} multiple beforeUpload={file => {
        if (!file.size || file.size > 20 * 1024 * 1024) { message.error('文件须为 1 字节至 20 MB'); return Upload.LIST_IGNORE }
        setFiles(current => current.length < 20 ? [...current, { uid: file.uid, name: file.name, file }] : current); return false
      }} onRemove={file => { setFiles(current => current.filter(row => row.uid !== file.uid)); return true }}><Button disabled={busy}>选择凭证附件</Button></Upload>
    </PositioningDialog>
  </Space>
}
