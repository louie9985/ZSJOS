import { useMemo } from 'react'
import { message } from 'antd'
import AttachmentCard from './AttachmentCard'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import type { DeferredUploadItem } from '../services/deferredUpload'
import { pointsApi, type PointsAttachment } from '../services/pointsRedemptionApi'

export type PointsAttachmentItem = DeferredUploadItem<PointsAttachment>
const ACCEPT = '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.png,.jpg,.jpeg,.webp,.zip'

export function PointsAttachmentPicker({ value, onChange, disabled }: {
  value: PointsAttachmentItem[]; onChange: (items: PointsAttachmentItem[]) => void; disabled: boolean
}) {
  const change = (items: PointsAttachmentItem[]) => {
    const valid = items.filter(item => {
      if (!item.file || value.some(current => current.uid === item.uid)) return true
      const extension = `.${item.name.split('.').pop()?.toLowerCase()}`
      const error = !ACCEPT.split(',').includes(extension) ? '不支持该文件类型'
        : !item.file.size ? '不能上传空文件' : item.file.size > 20 * 1024 * 1024 ? '每份附件不能超过20MB' : ''
      if (error) {
        message.error(`${item.name}：${error}`)
        if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
      }
      return !error
    })
    onChange(valid)
  }
  return <DeferredAttachmentPicker value={value} onChange={change} disabled={disabled}
    imageOnly={false} maxCount={10} accept={ACCEPT} />
}

export function PointsAttachments({ requestId, files }: { requestId: number; files: PointsAttachment[] }) {
  const loaders = useMemo(() => {
    let pending: ReturnType<typeof pointsApi.get> | undefined
    const refresh = () => {
      if (!pending) pending = pointsApi.get(requestId).finally(() => { pending = undefined })
      return pending
    }
    return files.map(file => {
      let initial = true
      return { file, load: async () => {
        if (initial) { initial = false; return file }
        const current = (await refresh()).attachments?.find(item => item.id === file.id)
        if (!current) throw new Error('附件不存在或无权访问')
        return current
      } }
    })
  }, [requestId, files])
  return <div className="points-attachment-list">{loaders.map(({file, load}) =>
    <AttachmentCard key={`${requestId}:${file.id}:${file.url}`} name={file.name} load={load} />)}</div>
}
