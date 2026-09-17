import { DeleteOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons'
import { Alert, Button, Space, Typography, Upload } from 'antd'
import { useState } from 'react'
import { api } from '../services/api'
import { isPendingPositioningFile, type PositioningAttachmentValue } from '../services/positioningManualSave'

type FileInfo = { id: number; name: string; type?: string; size: number; url?: string }
export default function PositioningCardAttachments({ value = [], onChange, cardId, disabled = false, snapshots = [] }: {
  value?: PositioningAttachmentValue[]; onChange?: (ids: PositioningAttachmentValue[]) => void
  cardId?: number; disabled?: boolean; snapshots?: FileInfo[]
}) {
  const [files, setFiles] = useState<Record<number, FileInfo>>({})
  const [error, setError] = useState('')
  const download = async (item: PositioningAttachmentValue) => {
    setError('')
    try {
      let url: string | undefined
      if (isPendingPositioningFile(item)) url = URL.createObjectURL(item.file)
      else {
        if (!cardId) throw new Error('草稿尚未保存')
        const file = await api.positioningCard.attachment(cardId, item)
        setFiles(current => ({ ...current, [item]: file })); url = file.url
      }
      if (!url) throw new Error('附件下载地址不可用')
      const link = document.createElement('a'); link.href = url; link.target = '_blank'; link.rel = 'noopener noreferrer'
      if (isPendingPositioningFile(item)) link.download = item.file.name
      link.click()
      if (isPendingPositioningFile(item)) setTimeout(() => URL.revokeObjectURL(url!), 1000)
    } catch (cause) { setError(cause instanceof Error ? cause.message : '附件下载失败') }
  }
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {value.map((item, index) => <Space key={isPendingPositioningFile(item) ? item.uid : item} wrap>
      <Typography.Text>{isPendingPositioningFile(item) ? `${item.file.name}（待上传）` : files[item]?.name || snapshots.find(file => file.id === item)?.name || '已上传附件'}</Typography.Text>
      <Button disabled={disabled} aria-label="下载附件" icon={<DownloadOutlined />} onClick={() => void download(item)} />
      <Button aria-label="移除附件" disabled={disabled} icon={<DeleteOutlined />} onClick={() => onChange?.(value.filter((_, i) => i !== index))} />
    </Space>)}
    <Upload showUploadList={false} disabled={disabled || value.length >= 20} beforeUpload={file => {
      setError('')
      if (file.size > 20 * 1024 * 1024 || !file.size) setError('附件大小须为 1 字节至 20 MB')
      else if (value.length >= 20) setError('最多选择 20 个附件')
      else onChange?.([...value, { uid: crypto.randomUUID(), file }])
      return Upload.LIST_IGNORE
    }}><Button disabled={disabled || value.length >= 20} icon={<UploadOutlined />}>选择附件</Button></Upload>
    {error && <Alert type="error" showIcon message={error} />}
  </Space>
}
