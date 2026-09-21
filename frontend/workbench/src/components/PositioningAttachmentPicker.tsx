import { DeleteOutlined, DownloadOutlined, FileOutlined, PlusOutlined } from '@ant-design/icons'
import { Alert, Button, Image, Modal, Spin, Typography, Upload, type UploadProps } from 'antd'
import { useEffect, useState } from 'react'
import { attachmentKind, AttachmentTypeIcon } from './AttachmentCard'

/** One uploaded file, without the signed URL that only the owning service can issue. */
export type UploadedAttachment = { id: number; name: string; type?: string; size?: number }
/** One file the operator picked but has not persisted yet. */
export type PendingAttachment = { uid: string; file: File }

export type AttachmentItem = { key: string; name: string; type?: string; size?: number; uploaded?: UploadedAttachment; pending?: PendingAttachment }

export const MAX_ATTACHMENT_COUNT = 20
export const MAX_ATTACHMENT_SIZE = 20 * 1024 * 1024

/** Positioning attachments accept documents, images, audio and video in one field. */
export const POSITIONING_ATTACHMENT_ACCEPT = [
  '.pdf', '.doc', '.docx', '.xls', '.xlsx', '.csv', '.ppt', '.pptx', '.txt', '.md', '.rtf',
  '.jpg', '.jpeg', '.png', '.webp', '.gif', '.bmp', '.heic', '.heif',
  '.mp3', '.wav', '.m4a', '.aac', '.flac', '.ogg', '.amr',
  '.mp4', '.mov', '.webm', '.mkv', '.avi',
].join(',')

const caption = (item: AttachmentItem) => attachmentKind(item.name, item.type || '')

function isAllowed(file: File) {
  const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  return POSITIONING_ATTACHMENT_ACCEPT.split(',').includes(`.${extension}`)
}

function formatSize(size?: number) {
  if (!size) return ''
  return size < 1024 ** 2 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1024 ** 2).toFixed(1)} MB`
}

/**
 * Shared attachment control for the positioning interview outline and the positioning card.
 *
 * The owner owns persistence. Files picked here stay visible as `待保存` items until the owner's
 * `items` grows to include them, so an owner that uploads on selection never shows a gap.
 */
export default function PositioningAttachmentPicker({ items, onChange, accept = POSITIONING_ATTACHMENT_ACCEPT,
  maxCount = MAX_ATTACHMENT_COUNT, disabled = false, busy = false, hint, onUpload, onRemove, onDownload }: {
  items: AttachmentItem[]
  /** Commit the next value. Owners that persist on their own may ignore it for additions. */
  onChange: (items: AttachmentItem[]) => void
  /** Persist the picked files. Omit it to keep them pending until the owning form saves. */
  onUpload?: (files: File[]) => void
  /** Persist a removal of an already stored file. Pending files never reach it. */
  onRemove?: (item: AttachmentItem) => void
  /** Resolve a stored file to a signed URL; pending files preview from the local blob. */
  onDownload: (item: AttachmentItem) => Promise<{ url?: string; name?: string; type?: string; size?: number }>
  disabled?: boolean; busy?: boolean; accept?: string; maxCount?: number; hint?: string
}) {
  const [error, setError] = useState('')
  const [queued, setQueued] = useState<AttachmentItem[]>([])
  const [preview, setPreview] = useState<{ item: AttachmentItem; url?: string }>()
  const [previewBusy, setPreviewBusy] = useState(false)
  // Drop queued files once the owner reports them back as stored, or once a removal clears them.
  useEffect(() => {
    if (!queued.length) return
    const stored = new Set(items.flatMap(item => (item.uploaded ? [item.name] : [])))
    const remaining = queued.filter(item => !stored.has(item.name))
    if (remaining.length !== queued.length) setQueued(remaining)
  }, [items, queued])
  const shown = [...items, ...queued]

  const open = async (item: AttachmentItem) => {
    setError('')
    if (item.pending) { setPreview({ item, url: URL.createObjectURL(item.pending.file) }); return }
    setPreviewBusy(true)
    try {
      const file = await onDownload(item)
      if (!file.url || !/^https?:\/\//i.test(file.url)) throw new Error('附件地址不可用')
      setPreview({ item, url: file.url })
    } catch (cause) { setError(cause instanceof Error ? cause.message : '附件读取失败') }
    finally { setPreviewBusy(false) }
  }
  const close = () => {
    if (preview?.item.pending && preview.url) URL.revokeObjectURL(preview.url)
    setPreview(undefined)
  }
  const download = async (item: AttachmentItem) => {
    setError('')
    try {
      if (item.pending) {
        const url = URL.createObjectURL(item.pending.file)
        const anchor = document.createElement('a'); anchor.href = url; anchor.download = item.name
        anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); return
      }
      const file = await onDownload(item)
      if (!file.url) throw new Error('附件地址不可用')
      const anchor = document.createElement('a')
      anchor.href = file.url; anchor.download = file.name || item.name
      anchor.target = '_blank'; anchor.rel = 'noopener noreferrer'; anchor.click()
    } catch (cause) { setError(cause instanceof Error ? cause.message : '附件下载失败') }
  }
  const remove = (item: AttachmentItem) => {
    if (!item.pending) { onRemove?.(item); onChange(items.filter(current => current.key !== item.key)); return }
    setQueued(current => current.filter(entry => entry.key !== item.key))
  }
  const beforeUpload: UploadProps['beforeUpload'] = file => {
    setError('')
    if (!file.size || file.size > MAX_ATTACHMENT_SIZE) { setError(`${file.name} 大小须为 1 字节至 20 MB`); return Upload.LIST_IGNORE }
    if (!isAllowed(file)) { setError(`${file.name} 不是支持的格式，请上传文档、图片、音频或视频`); return Upload.LIST_IGNORE }
    if (shown.length >= maxCount) { setError(`最多上传 ${maxCount} 个附件`); return Upload.LIST_IGNORE }
    const next: AttachmentItem = { key: `pending-${file.uid}`, name: file.name, type: file.type, size: file.size, pending: { uid: file.uid, file } }
    if (onUpload) { setQueued(current => [...current, next]); onUpload([file]) }
    else onChange([...items, next])
    return false
  }
  const kind = preview ? caption(preview.item) : 'file'
  return <div className="positioning-attachment-picker">
    <div className="attachment-upload-actions">
      <Upload accept={accept} multiple showUploadList={false} disabled={disabled || busy || shown.length >= maxCount} beforeUpload={beforeUpload}>
        <Button icon={<PlusOutlined />} disabled={disabled || busy || shown.length >= maxCount}>{shown.length ? '继续添加附件' : '上传附件'}</Button>
      </Upload>
      {hint && <Typography.Text type="secondary" className="attachment-paste-hint">{hint}</Typography.Text>}
    </div>
    {shown.length ? <div className="positioning-attachment-grid">
      {shown.map(item => <div className="positioning-attachment-item" key={item.key} data-kind={caption(item)}>
        <button type="button" className="positioning-attachment-thumb" disabled={disabled || busy || previewBusy}
          aria-label={`预览 ${item.name}`} title="点击预览" onClick={() => void open(item)}>
          {caption(item) === 'image' ? <ImageThumbnail item={item} onDownload={onDownload} />
            : <AttachmentTypeIcon kind={caption(item)} extension={item.name.includes('.') ? item.name.split('.').pop()! : ''} />}
        </button>
        <div className="positioning-attachment-meta">
          <span title={item.name}>{item.name}</span>
          <small>{item.pending ? '待保存' : '已上传'}{formatSize(item.size) && ` · ${formatSize(item.size)}`}</small>
        </div>
        <div className="positioning-attachment-actions">
          <Button type="text" size="small" icon={<DownloadOutlined />} aria-label={`下载 ${item.name}`} disabled={disabled || busy || Boolean(item.pending)} onClick={() => void download(item)} />
          <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`移除 ${item.name}`} disabled={disabled || busy}
            onClick={() => remove(item)} />
        </div>
      </div>)}
    </div> : <Typography.Text type="secondary"><FileOutlined /> 暂无附件</Typography.Text>}
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => setError('')}>知道了</Button>} />}
    <Modal open={Boolean(preview)} title={preview?.item.name} onCancel={close} footer={preview && <Button icon={<DownloadOutlined />} disabled={Boolean(preview.item.pending)} onClick={() => void download(preview.item)}>下载原文件</Button>}>
      {kind === 'image' ? <Image preview={false} src={preview?.url} alt={preview?.item.name} style={{ width: '100%', maxHeight: '60vh', objectFit: 'contain' }} />
        : kind === 'video' ? <video controls src={preview?.url} style={{ width: '100%', maxHeight: '60vh' }} />
        : kind === 'audio' ? <audio controls src={preview?.url} style={{ width: '100%' }} />
        : kind === 'pdf' ? <iframe title={preview?.item.name} src={preview?.url} className="positioning-attachment-preview" />
        : <Typography.Paragraph>该格式无法内联预览，请下载后查看。</Typography.Paragraph>}
    </Modal>
  </div>
}

/** Image thumbnails reuse the signed-URL loader so the picker never guesses an endpoint. */
function ImageThumbnail({ item, onDownload }: { item: AttachmentItem; onDownload: (item: AttachmentItem) => Promise<{ url?: string }> }) {
  const [url, setUrl] = useState('')
  const [loading, setLoading] = useState(!item.pending)
  useEffect(() => {
    if (item.pending) {
      const local = URL.createObjectURL(item.pending.file)
      setUrl(local); setLoading(false)
      return () => URL.revokeObjectURL(local)
    }
    let active = true
    setLoading(true)
    onDownload(item).then(file => { if (active) setUrl(file.url || '') })
      .catch(() => undefined)
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [item.key])
  if (loading) return <Spin size="small" />
  return url ? <img src={url} alt={item.name} /> : <AttachmentTypeIcon kind="image" extension={item.name.split('.').pop() || ''} />
}
