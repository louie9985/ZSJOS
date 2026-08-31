import type { StudentContactFormField } from './api'


export type PositioningJsonImportItem = {
  key: string
  title: string
  value?: unknown
  reason?: string
}

export type PositioningJsonImportPreview = {
  importable: PositioningJsonImportItem[]
  cleared: PositioningJsonImportItem[]
  skipped: PositioningJsonImportItem[]
  values: Record<string, unknown>
  clearKeys: string[]
}

export type PositioningJsonImportContext = {
  fields: StudentContactFormField[]
  dictionaryValues: Record<string, string[]>
  areaCodes?: number[]
}

const validDate = (value: string) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])))
  return date.getUTCFullYear() === Number(match[1])
    && date.getUTCMonth() === Number(match[2]) - 1
    && date.getUTCDate() === Number(match[3])
}

const validDateTime = (value: string) => {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})?$/.exec(value)
  return Boolean(match && validDate(match[1]) && Number(match[2]) <= 23
    && Number(match[3]) <= 59 && (match[4] == null || Number(match[4]) <= 59)
    && Number.isFinite(Date.parse(value)))
}

const dictionaryReason = (
  field: StudentContactFormField,
  values: string[],
  dictionaryValues: Record<string, string[]>,
) => {
  if (!field.dictType) return undefined
  const allowed = new Set(dictionaryValues[field.dictType] || [])
  const invalid = values.filter(value => !allowed.has(value))
  return invalid.length ? `字典值无效：${invalid.join('、')}` : undefined
}

const validateValue = (
  field: StudentContactFormField,
  raw: unknown,
  context: PositioningJsonImportContext,
): { value?: unknown; reason?: string } => {
  if (field.type === 'attachment') return { reason: '附件字段不能通过 JSON 导入，请使用正式附件入口' }
  if (raw === null) return { value: null }
  if (field.type === 'text' || field.type === 'textarea') {
    if (typeof raw !== 'string') return { reason: '应为字符串' }
    if (!raw.trim()) return { reason: '空内容不会导入；需要清空时请使用 null' }
    if (field.maxLength != null && raw.length > field.maxLength) return { reason: `最多 ${field.maxLength} 个字符` }
    return { value: raw }
  }
  if (field.type === 'number') {
    if (typeof raw !== 'number' || !Number.isFinite(raw)) return { reason: '应为有限数字' }
    if (field.minValue != null && raw < field.minValue) return { reason: `不能小于 ${field.minValue}` }
    if (field.maxValue != null && raw > field.maxValue) return { reason: `不能大于 ${field.maxValue}` }
    return { value: raw }
  }
  if (field.type === 'checkbox') return typeof raw === 'boolean' ? { value: raw } : { reason: '应为 true 或 false' }
  if (field.type === 'date') return typeof raw === 'string' && validDate(raw)
    ? { value: raw } : { reason: '应为 YYYY-MM-DD 格式的有效日期' }
  if (field.type === 'datetime') return typeof raw === 'string' && validDateTime(raw)
    ? { value: raw } : { reason: '应为 ISO 8601 日期时间字符串' }
  if (field.type === 'region') {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return { reason: '应为 {"code": 地区编码}' }
    const codeValue = (raw as { code?: unknown }).code
    const code = typeof codeValue === 'number' ? codeValue
      : typeof codeValue === 'string' && /^\d+$/.test(codeValue) ? Number(codeValue) : NaN
    if (!Number.isInteger(code) || code <= 0) return { reason: '地区 code 必须为正整数' }
    if (!new Set(context.areaCodes || []).has(code)) return { reason: `地区 code 不存在或已停用：${code}` }
    return { value: { code } }
  }
  if (field.type === 'multi_select' || field.type === 'checkbox_group') {
    if (!Array.isArray(raw) || raw.some(value => typeof value !== 'string')) return { reason: '应为字符串数组' }
    if (!raw.length) return { reason: '空数组不会导入；需要清空时请使用 null' }
    if (field.minSelections != null && raw.length < field.minSelections) return { reason: `至少选择 ${field.minSelections} 项` }
    if (field.maxSelections != null && raw.length > field.maxSelections) return { reason: `最多选择 ${field.maxSelections} 项` }
    const reason = dictionaryReason(field, raw, context.dictionaryValues)
    return reason ? { reason } : { value: raw }
  }
  if (field.type === 'select' || field.type === 'radio' || field.type === 'dict') {
    if (typeof raw !== 'string') return { reason: '应为字符串字典值' }
    const reason = dictionaryReason(field, [raw], context.dictionaryValues)
    return reason ? { reason } : { value: raw }
  }
  return { reason: `暂不支持字段类型：${field.type}` }
}

export const parsePositioningJson = (
  text: string,
  context: PositioningJsonImportContext,
): PositioningJsonImportPreview => {
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('JSON 格式不正确，请检查括号、引号和逗号')
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('JSON 顶层必须是字段 key 组成的对象')

  const fields = new Map(context.fields.filter(field => field.enabled).map(field => [field.key, field]))
  const preview: PositioningJsonImportPreview = { importable: [], cleared: [], skipped: [], values: {}, clearKeys: [] }
  Object.entries(parsed as Record<string, unknown>).forEach(([key, raw]) => {
    const field = fields.get(key)
    if (!field) {
      preview.skipped.push({ key, title: key, reason: '当前定位卡模板不存在该字段' })
      return
    }
    const result = validateValue(field, raw, context)
    if (result.reason) {
      preview.skipped.push({ key, title: field.title, value: raw, reason: result.reason })
    } else if (result.value === null) {
      preview.cleared.push({ key, title: field.title })
      preview.clearKeys.push(key)
    } else {
      preview.importable.push({ key, title: field.title, value: result.value })
      preview.values[key] = result.value
    }
  })
  return preview
}

export const mergePositioningJsonValues = (
  current: Record<string, unknown>,
  preview: PositioningJsonImportPreview,
) => {
  const merged = { ...current, ...preview.values }
  preview.clearKeys.forEach(key => delete merged[key])
  return merged
}

export const serializePositioningFormValues = (
  values: Record<string, unknown>,
  fields: StudentContactFormField[],
) => {
  const serialized = { ...values }
  fields.forEach(field => {
    const value = serialized[field.key]
    if ((field.type === 'date' || field.type === 'datetime') && value
      && typeof (value as { format?: unknown }).format === 'function') {
      serialized[field.key] = (value as { format: (pattern: string) => string })
        .format(field.type === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss')
    }
    if (field.type === 'region' && Array.isArray(value) && value.length) {
      serialized[field.key] = { code: Number(value[value.length - 1]) }
    }
  })
  return serialized
}
