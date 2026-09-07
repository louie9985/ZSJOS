import type { DatabaseAdminColumnVO } from '../../../api/infra/databaseAdmin/index'

export type EditorValue = string | boolean | null | undefined
export type EditorRow = Record<string, EditorValue>
export type FieldMode = 'default' | 'null' | 'value'

export function editorValue(column: DatabaseAdminColumnVO, value: unknown): EditorValue {
  // Some response serializers omit null map entries; an absent queried column is still SQL NULL.
  if (value === null || value === undefined) return null
  if (column.valueKind === 'boolean') {
    if (value === true || value === 'true' || value === 1 || value === '1') return true
    if (value === false || value === 'false' || value === 0 || value === '0') return false
    throw new Error(`字段 ${column.name} 的布尔值无效`)
  }
  if (typeof value === 'number' && !Number.isSafeInteger(value)) {
    throw new Error(`字段 ${column.name} 的数值精度无法确认，请刷新页面`)
  }
  if (typeof value !== 'string' && typeof value !== 'number') {
    throw new Error(`字段 ${column.name} 的数据类型无效`)
  }
  return String(value)
}

function comparable(column: DatabaseAdminColumnVO, value: EditorValue): EditorValue {
  if (typeof value !== 'string') return value
  if (column.valueKind === 'integer' && /^[+-]?\d+$/.test(value)) return BigInt(value).toString()
  if (column.valueKind === 'decimal' && /^[+-]?(\d+(\.\d*)?|\.\d+)$/.test(value)) {
    const negative = value.startsWith('-')
    const [whole, fraction = ''] = value.replace(/^[+-]/, '').split('.')
    const normalizedWhole = whole.replace(/^0+/, '') || '0'
    const normalizedFraction = fraction.replace(/0+$/, '')
    const sign = negative && (normalizedWhole !== '0' || normalizedFraction) ? '-' : ''
    return `${sign}${normalizedWhole}${normalizedFraction ? `.${normalizedFraction}` : ''}`
  }
  if (column.valueKind === 'datetime' || column.valueKind === 'time') {
    return value
      .replace('T', ' ')
      .replace(/(\.\d*?)0+$/, '$1')
      .replace(/\.$/, '')
  }
  return value
}

export function changedValues(
  columns: DatabaseAdminColumnVO[],
  original: EditorRow,
  current: EditorRow,
  modes: Record<string, FieldMode>,
  create: boolean
): EditorRow {
  const result: EditorRow = {}
  for (const column of columns) {
    if (!column.editable || modes[column.name] === 'default') continue
    const value = modes[column.name] === 'null' ? null : current[column.name]
    if (create || comparable(column, value) !== comparable(column, original[column.name])) {
      result[column.name] = value
    }
  }
  return result
}

export function fieldError(column: DatabaseAdminColumnVO, value: EditorValue): string | undefined {
  if (value === null) return column.nullable ? undefined : '不能为空'
  if (value === undefined) return '请输入字段值'
  const text = String(value)
  switch (column.valueKind) {
    case 'boolean':
      return typeof value === 'boolean' ? undefined : '请选择布尔值'
    case 'integer':
      return /^[+-]?\d+$/.test(text) ? undefined : '请输入整数'
    case 'decimal':
      return /^[+-]?(\d+(\.\d*)?|\.\d+)$/.test(text) ? undefined : '请输入十进制数值'
    case 'float':
      return text.trim() !== '' && Number.isFinite(Number(text)) ? undefined : '请输入有限数值'
    case 'date':
      return /^\d{4}-\d{2}-\d{2}$/.test(text) ? undefined : '格式为 YYYY-MM-DD'
    case 'time':
      return /^-?\d{2,3}:[0-5]\d:[0-5]\d(\.\d{1,9})?$/.test(text)
        ? undefined
        : '格式为 HH:mm:ss[.小数秒]'
    case 'datetime':
      return /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(\.\d{1,9})?$/.test(text)
        ? undefined
        : '格式为 YYYY-MM-DD HH:mm:ss[.小数秒]'
    case 'json':
      try {
        JSON.parse(text)
      } catch {
        return 'JSON 格式无效'
      }
      return undefined
    case 'readonly':
      return '该字段只读'
    default:
      return undefined
  }
}
