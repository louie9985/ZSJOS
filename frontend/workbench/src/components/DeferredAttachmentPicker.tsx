import { DeleteOutlined, FileOutlined, PlusOutlined } from '@ant-design/icons'
import { Button, Image, Spin, Upload, message, type UploadProps } from 'antd'
import { createDeferredUploadItem, type DeferredUploadItem } from '../services/deferredUpload'

export default function DeferredAttachmentPicker<T>({ value, onChange, accept, maxCount = 9,
  maxSize = 10 * 1024 * 1024, imageOnly = true, disabled = false }: {
  value: DeferredUploadItem<T>[]
  onChange: (value: DeferredUploadItem<T>[]) => void
  accept: string
  maxCount?: number
  maxSize?: number
  imageOnly?: boolean
  disabled?: boolean
}) {
  const beforeUpload: UploadProps['beforeUpload'] = file => {
    if (value.length >= maxCount) { message.warning(`最多选择 ${maxCount} 个文件`); return Upload.LIST_IGNORE }
    if (file.size > maxSize) { message.error(`单个文件不能超过 ${Math.round(maxSize / 1024 / 1024)}MB`); return Upload.LIST_IGNORE }
    if (imageOnly && !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      message.error('仅支持 JPG、PNG、WebP 图片'); return Upload.LIST_IGNORE
    }
    onChange([...value, createDeferredUploadItem<T>(file)])
    return false
  }
  return <div className="deferred-attachment-picker">
    <Upload accept={accept} multiple beforeUpload={beforeUpload} showUploadList={false} disabled={disabled || value.length >= maxCount}>
      {value.length < maxCount && <Button icon={<PlusOutlined/>} disabled={disabled}>选择文件</Button>}
    </Upload>
    <div className="deferred-attachment-grid">
      {value.map(item => <div key={item.uid} className={`deferred-attachment-item status-${item.status}`}>
        {(item.type || '').startsWith('image/') && (item.url || item.previewUrl) ? <Image src={item.url || item.previewUrl} preview={Boolean(item.url || item.previewUrl)} alt={item.name}/> : <FileOutlined className="deferred-attachment-file-icon"/>}
        {item.status === 'uploading' && <div className="deferred-attachment-loading"><Spin size="small"/></div>}
        <div className="deferred-attachment-meta"><span title={item.name}>{item.name}</span>{item.status === 'error' && <small>{item.error || '上传失败'}</small>}</div>
        <Button danger type="text" icon={<DeleteOutlined/>} aria-label={`删除${item.name}`} title="删除文件" disabled={disabled || item.status === 'uploading'} onClick={() => onChange(value.filter(current => current.uid !== item.uid))}/>
      </div>)}
    </div>
  </div>
}
