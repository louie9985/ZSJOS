import { api } from '../services/api'
import { isPendingPositioningFile, type PositioningAttachmentValue } from '../services/positioningManualSave'
import PositioningAttachmentPicker, { POSITIONING_ATTACHMENT_ACCEPT, type AttachmentItem, type UploadedAttachment } from './PositioningAttachmentPicker'

type FileInfo = UploadedAttachment
export default function PositioningCardAttachments({ value = [], onChange, cardId, disabled = false, snapshots = [] }: {
  value?: PositioningAttachmentValue[]; onChange?: (ids: PositioningAttachmentValue[]) => void
  cardId?: number; disabled?: boolean; snapshots?: FileInfo[]
}) {
  // Pending files stay local until the draft save uploads them, so the stored value is a mixed list.
  const items: AttachmentItem[] = value.map(item => {
    if (isPendingPositioningFile(item)) return { key: `pending-${item.uid}`, name: item.file.name, type: item.file.type, size: item.file.size, pending: item }
    const known = snapshots.find(file => file.id === item)
    return { key: `file-${item}`, name: known?.name || '已上传附件', type: known?.type, size: known?.size, uploaded: known || { id: item, name: '已上传附件' } }
  })
  const write = (next: AttachmentItem[]) => onChange?.(next.map(item =>
    item.pending ? item.pending : (item.uploaded as UploadedAttachment).id))
  return <PositioningAttachmentPicker items={items} disabled={disabled} accept={POSITIONING_ATTACHMENT_ACCEPT}
    hint="支持文档、图片、音频、视频，每份不超过 20 MB" onChange={write}
    onDownload={async item => {
      if (!cardId) throw new Error('草稿尚未保存，请先保存草稿再读取附件')
      return await api.positioningCard.attachment(cardId, (item.uploaded as UploadedAttachment).id)
    }} />
}
