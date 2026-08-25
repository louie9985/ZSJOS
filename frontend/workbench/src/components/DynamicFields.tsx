import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Empty, Form, Input, InputNumber, Select, Skeleton, Upload, message } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { api, type EamCategoryField } from '../services/api'
import { FIELD_TYPE } from '../services/eam'
import { useDict } from '../services/useDict'
import dayjs from 'dayjs'

/**
 * 下拉字段的取值控件。optionSource 为 SYSTEM_DICT 时按 dictType 用字典加载选项，
 * 静态源则直接取字段定义的 options。抽成子组件是因为 useDict 是 hook，不能放进循环。
 */
function SelectFieldValue({ field }: { field: EamCategoryField }) {
  const dict = useDict(field.dictType ?? '')
  if (field.optionSource === 'SYSTEM_DICT') {
    return <Select allowClear placeholder={`请选择${field.fieldName}`} options={dict.options}
      loading={dict.loading}/>
  }
  return <Select allowClear placeholder={`请选择${field.fieldName}`}
    options={(field.options ?? []).map(opt => ({ value: opt, label: opt }))}/>
}

function FileFieldValue({ value, onChange, field }: { value?: unknown; onChange?: (value: string | undefined) => void; field: EamCategoryField }) {
  const url = typeof value === 'string' ? value : undefined
  return <Upload fileList={url ? [{ uid: url, name: url.split('/').pop() || field.fieldName, status: 'done' as const, url }] : []}
    maxCount={1} beforeUpload={async file => {
      try { onChange?.(await api.eam.uploadFile(file)); message.success('文件上传成功') }
      catch (e) { message.error(e instanceof Error ? e.message : '文件上传失败') }
      return false
    }} onRemove={() => { onChange?.(undefined); return true }}>
    <Button icon={<UploadOutlined/>}>上传文件</Button>
  </Upload>
}

/**
 * 按分类的自定义字段定义动态渲染表单项；字段定义随分类切换重新加载。
 * 值统一存放在表单的 extFields 命名空间下。
 */
export default function DynamicFields({ categoryId, onFieldsChange, onStateChange }: {
  categoryId?: number
  onFieldsChange?: (fields: EamCategoryField[]) => void
  onStateChange?: (state: { loading: boolean; error: string }) => void
}) {
  const [fields, setFields] = useState<EamCategoryField[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const load = useCallback(async (id?: number) => {
    const current = ++version.current
    if (!id) { setFields([]); setError(''); setLoading(false); onFieldsChange?.([]); onStateChange?.({ loading: false, error: '' }); return }
    setLoading(true); setError(''); onStateChange?.({ loading: true, error: '' })
    try {
      const definitions = await api.eam.categoryField.effectiveList(id)
      if (current !== version.current) return
      const visible = definitions.filter(field => field.adminVisible !== false)
      setFields(visible); onFieldsChange?.(visible); onStateChange?.({ loading: false, error: '' })
    } catch (e) {
      if (current === version.current) {
        const messageText = e instanceof Error ? e.message : '自定义字段加载失败'
        setError(messageText); onStateChange?.({ loading: false, error: messageText })
      }
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [onFieldsChange])

  useEffect(() => { void load(categoryId) }, [categoryId, load])

  if (loading) return <Skeleton active paragraph={{ rows: 3 }}/>
  if (error) return <Alert type="error" showIcon message={error}/>
  if (!categoryId) return null
  if (!fields.length) return <Empty description="该分类未配置自定义字段" image={Empty.PRESENTED_IMAGE_SIMPLE}/>

  return <>{fields.map(field => <Form.Item
    key={field.fieldKey}
    name={['extFields', field.fieldKey]}
    label={field.fieldName}
    rules={field.required ? [{ required: true, message: `请填写${field.fieldName}` }] : undefined}
    getValueProps={field.fieldType === FIELD_TYPE.DATE
      ? (value: unknown) => ({ value: value ? dayjs(value as string) : undefined })
      : undefined}
    normalize={field.fieldType === FIELD_TYPE.DATE
      ? (value: unknown) => value ? dayjs(value as dayjs.Dayjs).format('YYYY-MM-DD') : undefined
      : undefined}
  >
    {field.fieldType === FIELD_TYPE.TEXTAREA ? <Input.TextArea rows={3} placeholder={`请输入${field.fieldName}`}/>
      : field.fieldType === FIELD_TYPE.NUMBER ? <InputNumber style={{ width: '100%' }} placeholder={`请输入${field.fieldName}`}/>
        : field.fieldType === FIELD_TYPE.DATE ? <DatePicker style={{ width: '100%' }} placeholder={`请选择${field.fieldName}`}/>
          : field.fieldType === FIELD_TYPE.SELECT ? <SelectFieldValue field={field}/>
            : field.fieldType === FIELD_TYPE.FILE ? <FileFieldValue field={field}/>
            : <Input allowClear placeholder={`请输入${field.fieldName}`}/>}
  </Form.Item>)}</>
}
