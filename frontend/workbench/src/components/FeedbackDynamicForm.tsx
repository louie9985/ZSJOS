import { DatePicker, Form, Input, Rate, Select, Upload, Button, Space, Typography } from 'antd'
import { DeleteOutlined, PaperClipOutlined, UploadOutlined } from '@ant-design/icons'
import type { FormInstance, UploadProps } from 'antd'
import dayjs from 'dayjs'
import type { FeedbackAttachment, FeedbackField } from '../services/feedbackApi'
import { feedbackApi } from '../services/feedbackApi'

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
  const customRequest: UploadProps['customRequest'] = async options => {
    try {
      const uploaded = await feedbackApi.upload(options.file as File)
      onChange?.([...value, uploaded])
      options.onSuccess?.(uploaded)
    } catch (cause) {
      options.onError?.(cause as Error)
    }
  }
  return <div className="feedback-attachment-input">
    <Upload
      accept={imageOnly ? 'image/*' : undefined}
      customRequest={customRequest}
      fileList={[]}
      showUploadList={false}
      disabled={value.length >= 20}
    >
      <Button icon={<UploadOutlined/>}>上传{imageOnly ? '图片' : '附件'}</Button>
    </Upload>
    {value.map(file => <div className="feedback-attachment-row" key={file.id}>
      <Space size={6}>
        <PaperClipOutlined/>
        {file.url ? <Typography.Link href={file.url} target="_blank">{file.name || `附件 ${file.id}`}</Typography.Link> : <span>{file.name || `附件 ${file.id}`}</span>}
      </Space>
      <Button type="text" danger icon={<DeleteOutlined/>} aria-label="移除附件" onClick={() => onChange?.(value.filter(item => item.id !== file.id))}/>
    </div>)}
  </div>
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
