import { useEffect, useRef } from 'react'
import { DatePicker, Form, Image, Input, Rate, Select, Upload, Button, Space, Typography, message } from 'antd'
import { DeleteOutlined, PaperClipOutlined, PictureOutlined, UploadOutlined } from '@ant-design/icons'
import type { FormInstance, UploadProps } from 'antd'
import dayjs from 'dayjs'
import type { FeedbackAttachment, FeedbackField } from '../services/feedbackApi'
import { feedbackApi } from '../services/feedbackApi'
import { useClipboardPasteTarget } from './ClipboardPasteTarget'

export function normalizeFeedbackInitialValues(
  fields: FeedbackField[],
  values: Record<string, unknown> = {}
) {
  return Object.fromEntries(
    fields.map(field => {
      const value = values[field.key]
      if (field.type === 'dictionary' && value && typeof value === 'object' && 'value' in value) {
        return [field.key, (value as { value: unknown }).value]
      }
      return [field.key, value]
    })
  )
}

export function serializeFeedbackFormValues(
  fields: FeedbackField[],
  values: Record<string, unknown>
) {
  return Object.fromEntries(fields.map(field => {
    const value = values[field.key]
    if (field.type === 'upload' || field.type === 'image') {
      const attachmentIds = Array.isArray(value)
        ? value.map(item => typeof item === 'object' && item !== null && 'id' in item
          ? (item as FeedbackAttachment).id
          : item)
        : value
      return [field.key, attachmentIds]
    }
    return [field.key, value]
  }))
}

export function FeedbackAttachmentInput({
  value = [],
  onChange,
  imageOnly = false
}: {
  value?: FeedbackAttachment[]
  onChange?: (value: FeedbackAttachment[]) => void
  imageOnly?: boolean
}) {
  const localPreviewUrls = useRef(new Set<string>())
  const attachmentsRef = useRef(value)
  attachmentsRef.current = value

  useEffect(() => () => {
    localPreviewUrls.current.forEach(url => URL.revokeObjectURL(url))
    localPreviewUrls.current.clear()
  }, [])

  const customRequest: UploadProps['customRequest'] = async options => {
    const file = options.file as File
    const localPreviewUrl = imageOnly ? URL.createObjectURL(file) : undefined
    if (localPreviewUrl) localPreviewUrls.current.add(localPreviewUrl)
    try {
      const uploaded = await feedbackApi.upload(file)
      const next = [...attachmentsRef.current, { ...uploaded, previewUrl: localPreviewUrl }]
      attachmentsRef.current = next
      onChange?.(next)
      options.onSuccess?.(uploaded)
    } catch (cause) {
      if (localPreviewUrl) {
        URL.revokeObjectURL(localPreviewUrl)
        localPreviewUrls.current.delete(localPreviewUrl)
      }
      options.onError?.(cause as Error)
    }
  }

  const remove = (file: FeedbackAttachment) => {
    if (file.previewUrl && localPreviewUrls.current.has(file.previewUrl)) {
      URL.revokeObjectURL(file.previewUrl)
      localPreviewUrls.current.delete(file.previewUrl)
    }
    onChange?.(value.filter(item => item.id !== file.id))
  }

  const uploadFile = async (file: File) => {
    const localPreviewUrl = imageOnly ? URL.createObjectURL(file) : undefined
    if (localPreviewUrl) localPreviewUrls.current.add(localPreviewUrl)
    try {
      const uploaded = await feedbackApi.upload(file)
      const next = [...attachmentsRef.current, { ...uploaded, previewUrl: localPreviewUrl }]
      attachmentsRef.current = next
      onChange?.(next)
    } catch (cause) {
      if (localPreviewUrl) {
        URL.revokeObjectURL(localPreviewUrl)
        localPreviewUrls.current.delete(localPreviewUrl)
      }
      message.error(cause instanceof Error ? cause.message : '附件上传失败')
    }
  }

  const { targetRef, targetProps, pasteButtonProps } = useClipboardPasteTarget({
    canPaste: () => value.length < 20,
    onFiles: files => files.slice(0, 20 - value.length).forEach(file => { void uploadFile(file) }),
  })
  return <div ref={targetRef} className="feedback-attachment-input" {...targetProps}>
    <div className="attachment-upload-actions">
      <Upload
        accept={imageOnly ? 'image/*' : undefined}
        customRequest={customRequest}
        fileList={[]}
        showUploadList={false}
        disabled={value.length >= 20}
      >
        <Button icon={<UploadOutlined/>}>上传附件</Button>
      </Upload>
      <Button {...pasteButtonProps} icon={<PictureOutlined />}>上传剪贴板截图</Button>
    </div>
    {value.length > 0 && <>
      {value.some(file => isImageAttachment(file)) && <Image.PreviewGroup>
        <div className="feedback-image-grid">
          {value.filter(file => isImageAttachment(file)).map(file => <div className="feedback-image-item" key={file.id}>
            {file.previewUrl || file.url
              ? <Image src={file.previewUrl || file.url} alt={file.name || `图片 ${file.id}`} preview={{ mask: '预览' }}/>
              : <div className="feedback-image-missing"><PictureOutlined/><span>{file.name || `图片 ${file.id}`}</span></div>}
            <Button className="feedback-image-remove" type="text" danger icon={<DeleteOutlined/>} aria-label={`移除${file.name || '图片'}`} title="移除图片" onClick={() => remove(file)}/>
            <Typography.Text ellipsis={{ tooltip: file.name }} className="feedback-image-name">{file.name || `图片 ${file.id}`}</Typography.Text>
          </div>)}
        </div>
      </Image.PreviewGroup>}
      {!imageOnly && value.filter(file => !isImageAttachment(file)).map(file => <div className="feedback-attachment-row" key={file.id}>
        <Space size={6}><PaperClipOutlined/>{file.url ? <Typography.Link href={file.url} target="_blank">{file.name || `附件 ${file.id}`}</Typography.Link> : <span>{file.name || `附件 ${file.id}`}</span>}</Space>
        <Button type="text" danger icon={<DeleteOutlined/>} aria-label="移除附件" onClick={() => remove(file)}/>
      </div>)}
    </>}
  </div>
}

function isImageAttachment(file: FeedbackAttachment) {
  const type = file.type?.toLowerCase() || ''
  const name = file.name?.toLowerCase() || ''
  return type.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(name)
}

export default function FeedbackDynamicForm({
  form,
  fields
}: {
  form: FormInstance<Record<string, unknown>>
  fields: FeedbackField[]
}) {
  return <Form form={form} layout="vertical" requiredMark="optional">
    {fields.map(field => {
      const rules = [{ required: field.required, message: `请填写${field.label}` }]
      if (field.type === 'textarea') {
        return <Form.Item key={field.key} name={field.key} label={field.label} rules={rules}>
          <Input.TextArea rows={5} maxLength={field.maxLength} showCount={Boolean(field.maxLength)}/>
        </Form.Item>
      }
      if (field.type === 'date') {
        return <Form.Item
          key={field.key}
          name={field.key}
          label={field.label}
          rules={rules}
          getValueProps={value => ({ value: value ? dayjs(String(value)) : undefined })}
          normalize={value => value ? value.format('YYYY-MM-DD') : undefined}
        >
          <DatePicker style={{ width: '100%' }}/>
        </Form.Item>
      }
      if (field.type === 'dictionary') {
        return <Form.Item key={field.key} name={field.key} label={field.label} rules={rules}>
          <Select options={field.options || []} placeholder={`请选择${field.label}`}/>
        </Form.Item>
      }
      if (field.type === 'upload' || field.type === 'image') {
        return <Form.Item key={field.key} name={field.key} label={field.label} rules={rules}>
          <FeedbackAttachmentInput imageOnly={field.type === 'image'}/>
        </Form.Item>
      }
      if (field.type === 'rating') {
        return <Form.Item key={field.key} name={field.key} label={field.label} rules={rules}>
          <Rate count={field.maxRating || 5}/>
        </Form.Item>
      }
      return <Form.Item key={field.key} name={field.key} label={field.label} rules={rules}>
        <Input maxLength={field.maxLength} showCount={Boolean(field.maxLength)}/>
      </Form.Item>
    })}
  </Form>
}
