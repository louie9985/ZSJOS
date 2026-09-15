import { useMemo } from 'react'
import { DatePicker, Form, Input, InputNumber, Rate, Select, Typography } from 'antd'
import dayjs from 'dayjs'
import type { DictData } from '../../services/api'

/**
 * BPM 动态表单（流程表单 formType=10）的字段定义。
 * 结构由 BPM 表单设计器产出，后端以 JSON 字符串数组下发到 formFields。
 */
export type BpmFormField = {
  type: string
  field: string
  title?: string
  dictType?: string
  hidden?: boolean
  display?: boolean
  props?: {
    disabled?: boolean
    readonly?: boolean
    maxlength?: number
    max?: number
    limit?: number
    placeholder?: string
    type?: string
    autosize?: { minRows?: number; maxRows?: number }
  }
  validate?: Array<{ required?: boolean; message?: string }>
}

export type BpmFormConf = {
  form?: { labelPosition?: 'top' | 'left' | 'right'; labelWidth?: string; size?: string }
}

/** 字段权限，取值来自后端 formFieldsPermission。 */
export const BPM_FIELD_PERMISSION = { READ: '1', WRITE: '2', NONE: '3' } as const

/**
 * 解析后端下发的 formFields。每个元素是一段 JSON 字符串；
 * 单个字段解析失败时跳过该字段而不是让整张表单失败。
 */
export function parseBpmFormFields(formFields?: string[]): BpmFormField[] {
  if (!formFields?.length) return []
  return formFields.flatMap(raw => {
    try {
      const parsed = JSON.parse(raw) as BpmFormField
      return parsed?.field ? [parsed] : []
    } catch {
      return []
    }
  })
}

export function parseBpmFormConf(formConf?: string): BpmFormConf {
  if (!formConf) return {}
  try {
    return JSON.parse(formConf) as BpmFormConf
  } catch {
    return {}
  }
}

const isBlank = (value: unknown) =>
  value === undefined || value === null || value === ''

/** 只读展示：把任意变量值渲染成可读文本，不猜测业务语义。 */
export function formatBpmVariable(value: unknown): string {
  if (isBlank(value)) return '-'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) {
    return value.length === 0 ? '-' : value.map(item => formatBpmVariable(item)).join('、')
  }
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function fieldRequired(field: BpmFormField) {
  return field.validate?.some(rule => rule.required) ?? false
}

/**
 * 渲染单个字段的输入控件。仓库内 BPM 表单实际使用 input/textarea/text/date/select/rate/upload
 * 七类，未知类型降级为文本输入，避免新增控件时整张表单不可用。
 */
function FieldControl({
  field,
  disabled,
  dictData
}: {
  field: BpmFormField
  disabled: boolean
  dictData: DictData[]
}) {
  const placeholder = field.props?.placeholder
  switch (field.type) {
    case 'textarea':
      return <Input.TextArea
        disabled={disabled}
        maxLength={field.props?.maxlength}
        placeholder={placeholder}
        autoSize={{
          minRows: field.props?.autosize?.minRows ?? 2,
          maxRows: field.props?.autosize?.maxRows ?? 8
        }}
      />
    case 'select':
      return <Select
        disabled={disabled}
        placeholder={placeholder}
        allowClear
        options={dictData.map(item => ({ value: item.value, label: item.label }))}
      />
    case 'rate':
      return <Rate disabled={disabled} count={field.props?.max ?? 5} />
    case 'date':
      return <DatePicker disabled={disabled} placeholder={placeholder} style={{ width: '100%' }} />
    case 'number':
      return <InputNumber disabled={disabled} placeholder={placeholder} style={{ width: '100%' }} />
    default:
      return <Input
        disabled={disabled}
        maxLength={field.props?.maxlength}
        placeholder={placeholder}
      />
  }
}

export type BpmDynamicFormProps = {
  fields: BpmFormField[]
  conf?: BpmFormConf
  values?: Record<string, unknown>
  /** 字段权限；缺省视为只读，避免误开放编辑。 */
  fieldsPermission?: Record<string, string>
  /** 整表只读：审批详情展示、已办任务。 */
  readOnly?: boolean
  dictDataByType?: Record<string, DictData[]>
}

/**
 * 流程表单渲染器。表单本身不提交，取值由承载它的审批动作读取，
 * 因此这里只负责结构与只读语义。
 */
export default function BpmDynamicForm({
  fields,
  conf,
  values,
  fieldsPermission,
  readOnly = true,
  dictDataByType
}: BpmDynamicFormProps) {
  const visibleFields = useMemo(
    () => fields.filter(field => {
      if (field.hidden || field.display === false) return false
      return fieldsPermission?.[field.field] !== BPM_FIELD_PERMISSION.NONE
    }),
    [fields, fieldsPermission]
  )

  if (visibleFields.length === 0) return null

  const labelPosition = conf?.form?.labelPosition
  const layout = labelPosition === 'top' ? 'vertical' : 'horizontal'

  return <Form
    layout={layout}
    labelWrap
    labelCol={layout === 'horizontal' ? { flex: '0 0 120px' } : undefined}
    initialValues={values}
    className="bpm-dynamic-form"
  >
    {visibleFields.map(field => {
      // 只读优先：整表只读、字段设计为只读、或权限非“可编辑”，都不可输入。
      const designedReadOnly = field.props?.disabled === true || field.props?.readonly === true
      const permission = fieldsPermission?.[field.field]
      const disabled = readOnly
        || designedReadOnly
        || (permission !== undefined && permission !== BPM_FIELD_PERMISSION.WRITE)

      if (field.type === 'text') {
        return <Form.Item key={field.field} label={field.title}>
          <Typography.Text>{formatBpmVariable(values?.[field.field])}</Typography.Text>
        </Form.Item>
      }

      // 附件与只读值统一走文本展示：Workbench 不在审批表单内提供 BPM 附件上传入口。
      if (field.type === 'upload' || (disabled && field.type !== 'rate')) {
        return <Form.Item key={field.field} label={field.title}>
          <Typography.Text type={isBlank(values?.[field.field]) ? 'secondary' : undefined}>
            {formatBpmVariable(values?.[field.field])}
          </Typography.Text>
        </Form.Item>
      }

      return <Form.Item
        key={field.field}
        name={field.field}
        label={field.title}
        required={fieldRequired(field)}
        valuePropName={field.type === 'rate' ? 'value' : undefined}
        getValueProps={field.type === 'date'
          ? value => ({ value: value ? dayjs(value as string) : undefined })
          : undefined}
      >
        <FieldControl
          field={field}
          disabled={disabled}
          dictData={field.dictType ? dictDataByType?.[field.dictType] ?? [] : []}
        />
      </Form.Item>
    })}
  </Form>
}
