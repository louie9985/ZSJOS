import type { WorkOrderField, WorkOrderFile } from './workOrderApi'

export function serializeWorkOrderDynamicValues(
  fields: WorkOrderField[],
  rawValues: Record<string, unknown>
) {
  const attachmentFiles: WorkOrderFile[] = []
  const values = Object.fromEntries(fields.map(field => {
    const value = rawValues[field.key]
    if (field.type === 'attachment') {
      const files = Array.isArray(value) ? value as WorkOrderFile[] : []
      attachmentFiles.push(...files)
      return [field.key, files.map(file => file.id)]
    }
    if (field.type === 'date') return [field.key, formatValue(value, 'YYYY-MM-DD')]
    if (field.type === 'datetime') return [field.key, formatValue(value, 'YYYY-MM-DDTHH:mm:ss')]
    return [field.key, value]
  }))
  return {
    values,
    attachmentFiles: [...new Map(attachmentFiles.map(file => [file.id, file])).values()]
  }
}

function formatValue(value: unknown, pattern: string) {
  if (value && typeof value === 'object' && 'format' in value
      && typeof (value as { format?: unknown }).format === 'function') {
    return (value as { format: (value: string) => string }).format(pattern)
  }
  return value
}
