import { DeleteOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons'
import { Alert, Button, Space, Typography, Upload } from 'antd'
import { useState } from 'react'
import { api } from '../services/api'

type FileInfo = { id: number; name: string; type?: string; size: number; url?: string }
export default function PositioningCardAttachments({ value = [], onChange, fieldKey, ensureDraft, snapshots = [] }: {
  value?: number[]; onChange?: (ids: number[]) => void; fieldKey: string; ensureDraft: () => Promise<number>; snapshots?: FileInfo[]
}) {
  const [files, setFiles] = useState<Record<number, FileInfo>>({})
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const upload = async (file: File) => {
    setBusy(true); setError('')
    try {
      if (file.size > 20 * 1024 * 1024 || !file.size) throw new Error('附件大小须为 1 字节至 20 MB')
      const id = await ensureDraft()
      const result = await api.positioningCard.uploadAttachment(id, fieldKey, file)
      setFiles(current => ({ ...current, [result.id]: result })); onChange?.([...value, result.id])
    } catch (cause) { setError(cause instanceof Error ? cause.message : '附件上传失败') }
    finally { setBusy(false) }
    return false
  }
  const download = async (fileId: number) => {
    setError('')
    try {
      const file = await api.positioningCard.attachment(await ensureDraft(), fileId)
      setFiles(current => ({ ...current, [fileId]: file }))
      if (!file.url) throw new Error('附件下载地址不可用')
      const link = document.createElement('a'); link.href = file.url; link.target = '_blank'; link.rel = 'noopener noreferrer'; link.click()
    } catch (cause) { setError(cause instanceof Error ? cause.message : '附件下载失败') }
  }
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {value.map(id => <Space key={id} wrap><Typography.Text>{files[id]?.name || snapshots.find(file => file.id === id)?.name || '已保存附件'}</Typography.Text>
      <Button aria-label="下载附件" icon={<DownloadOutlined />} onClick={() => void download(id)} />
      <Button aria-label="移除附件" disabled={busy} icon={<DeleteOutlined />} onClick={() => onChange?.(value.filter(item => item !== id))} />
    </Space>)}
    <Upload showUploadList={false} disabled={busy || value.length >= 20} beforeUpload={upload}><Button icon={<UploadOutlined />} loading={busy}>上传附件</Button></Upload>
    {error && <Alert type="error" showIcon message={error} />}
  </Space>
}
