import { useEffect, useRef, useState } from 'react'
import { Button, Form, Image, Spin, Typography, Upload, App } from 'antd'
import { DeleteOutlined, InboxOutlined, PictureOutlined } from '@ant-design/icons'
import { AttachmentTypeIcon, attachmentKind } from './AttachmentCard'
import { clipboardImageFiles, readClipboardImageFiles } from '../services/clipboardImage'
import { createDeferredUploadItem } from '../services/deferredUpload'
import { CONTENT_REVIEW_ATTACHMENT_ACCEPT, contentReviewFileError, type ContentReviewAttachment } from '../services/contentReviewAttachments'
import '../styles/components/content-review-attachments.css'

export function ContentReviewAttachmentPicker({ value = [], onChange, cover = false, disabled = false }: {
  value?: ContentReviewAttachment[]; onChange?: (items: ContentReviewAttachment[]) => void; cover?: boolean; disabled?: boolean
}) {
  const { message } = App.useApp()
  const [reading, setReading] = useState(false)
  const current = useRef(value)
  current.current = value
  const localUrls = useRef(new Set<string>())
  useEffect(() => () => { localUrls.current.forEach(url => URL.revokeObjectURL(url)) }, [])
  const max = cover ? 1 : 20
  const add = (files: File[]) => {
    if (disabled) return
    const next = [...current.current]
    for (const file of files) {
      if (next.length >= max) { message.warning(`最多选择 ${max} 个文件`); break }
      const error = contentReviewFileError(file, cover)
      if (error) { message.error(error); continue }
      const item = createDeferredUploadItem<NonNullable<ContentReviewAttachment['uploaded']>>(file)
      if (item.previewUrl) localUrls.current.add(item.previewUrl)
      next.push(item)
    }
    current.current = next
    onChange?.(next)
  }
  const paste = async () => {
    setReading(true)
    try { const files = await readClipboardImageFiles(); if (files.length) add(files); else message.warning('剪贴板中没有图片') }
    catch (cause) { message.error(cause instanceof Error ? cause.message : '无法读取剪贴板，请选择文件') }
    finally { setReading(false) }
  }
  return <div className="content-review-attachments" tabIndex={disabled ? -1 : 0} aria-label={cover ? '作品封面粘贴区域' : '审核附件粘贴区域'}
    onPaste={event => {
      if (disabled) return
      const files = clipboardImageFiles(event.nativeEvent)
      if (files.length) { event.preventDefault(); event.stopPropagation(); add(files) }
    }}>
    <div className="content-review-attachments-toolbar">
      <Typography.Text type="secondary">{cover ? '选择封面，或在此处粘贴截图' : '图片、视频、PDF、Word、Excel、PPT'}</Typography.Text>
      <Button icon={<PictureOutlined />} loading={reading} disabled={disabled || value.length >= max} onClick={() => void paste()}>粘贴截图</Button>
    </div>
    {value.length < max && <Upload.Dragger accept={cover ? 'image/*' : CONTENT_REVIEW_ATTACHMENT_ACCEPT} multiple={!cover} showUploadList={false} disabled={disabled}
      beforeUpload={(file, files) => { if (file === files[0]) add(files); return Upload.LIST_IGNORE }}>
      <InboxOutlined className="content-review-attachments-upload-icon" />
      <div>点击选择或拖拽{cover ? '封面图片' : '审核附件'}</div>
      <Typography.Text type="secondary">Ctrl / Cmd + V 粘贴图片 · {cover ? '1 张封面' : '最多 20 个附件'} · 单个不超过 1GB</Typography.Text>
    </Upload.Dragger>}
    <div className="content-review-attachments-grid">
      {value.map(item => {
        const url = item.url || item.previewUrl
        return <article className={`content-review-attachment status-${item.status}`} key={item.uid}>
          <div className="content-review-attachment-preview">
            {item.type?.startsWith('image/') && url ? <Image src={url} alt={item.name} />
              : item.type?.startsWith('video/') && url ? <video src={url} controls preload="metadata" aria-label={item.name} />
              : <AttachmentTypeIcon kind={attachmentKind(item.name, item.type)} extension={item.name.split('.').pop() || ''} />}
          </div>
          <div className="content-review-attachment-meta"><span title={item.name}>{item.name}</span>
            <Typography.Text type="secondary">{((item.size || 0) / 1024 / 1024).toFixed(1)} MB · {({ pending: '保存时上传', uploading: '正在上传', done: '已上传', error: '上传失败，保存时重试' })[item.status]}</Typography.Text>
            {item.error && <Typography.Text type="danger">{item.error}</Typography.Text>}
            {url && !item.type?.startsWith('image/') && <a href={url} target="_blank" rel="noopener noreferrer" download={item.name}>下载文件</a>}
          </div>
          {item.status === 'uploading' ? <Spin size="small" /> : <Button type="text" danger icon={<DeleteOutlined />} disabled={disabled} aria-label={`移除 ${item.name}`} onClick={() => {
            if (item.previewUrl && localUrls.current.delete(item.previewUrl)) URL.revokeObjectURL(item.previewUrl)
            const next = current.current.filter(file => file.uid !== item.uid); current.current = next; onChange?.(next)
          }} />}
        </article>
      })}
    </div>
  </div>
}

export default function ContentReviewAttachments({ index, cover = false, disabled = false }: { index: number; cover?: boolean; disabled?: boolean }) {
  return <Form.Item name={[index, cover ? 'coverItems' : 'attachmentItems']} label={cover ? '作品封面图' : '审核附件（选填）'} initialValue={[]}
    rules={cover ? [{ required: true, type: 'array', min: 1, message: '请选择作品封面图' }] : []}>
    <ContentReviewAttachmentPicker cover={cover} disabled={disabled} />
  </Form.Item>
}
