import { PlusOutlined } from '@ant-design/icons'
import { Image, Upload, message, type UploadFile, type UploadProps } from 'antd'
import { api, type LeadAppealEvidence } from '../services/api'

const ACCEPTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])

export default function LeadAppealEvidenceUpload({ value, onChange, disabled = false, uploadImage }: {
  value: LeadAppealEvidence[]
  onChange: (value: LeadAppealEvidence[]) => void
  disabled?: boolean
  uploadImage?: (file: File) => Promise<LeadAppealEvidence>
}) {
  const files: UploadFile[] = value.map(item => ({
    uid: String(item.infraFileId), name: item.originalName, status: 'done', url: item.fileUrl, type: item.contentType
  }))

  const upload: UploadProps['customRequest'] = async options => {
    const file = options.file as File
    if (!ACCEPTED_TYPES.has(file.type)) {
      const error = new Error('仅支持 JPG、PNG、WebP 图片')
      message.error(error.message); options.onError?.(error); return
    }
    if (value.length >= 9) {
      const error = new Error('每个环节最多上传 9 张图片')
      message.warning(error.message); options.onError?.(error); return
    }
    try {
      const result = await (uploadImage || api.uploadLeadAppealImage)(file)
      onChange([...value, result])
      options.onSuccess?.(result)
    } catch (error) {
      const uploadError = error instanceof Error ? error : new Error('图片上传失败')
      message.error(uploadError.message); options.onError?.(uploadError)
    }
  }

  return <Upload
    accept="image/jpeg,image/png,image/webp"
    listType="picture-card"
    fileList={files}
    customRequest={upload}
    disabled={disabled}
    onRemove={file => { onChange(value.filter(item => String(item.infraFileId) !== file.uid)); return true }}
    itemRender={(origin, file) => file.url ? <Image src={file.url} alt={file.name} preview/> : origin}
  >
    {value.length < 9 && !disabled ? <button type="button" className="appeal-upload-button"><PlusOutlined/><span>上传图片</span></button> : null}
  </Upload>
}
