import type { Material, MaterialFieldDefinition } from './materialApi'

export type MaterialSearchEntry = { key: string; label: string; text: string; terms: string[]; searchable: boolean }

function labels(value: unknown): string[] {
  if (Array.isArray(value)) return value.flatMap(labels)
  if (value == null) return []
  if (typeof value === 'object') return labels((value as Record<string, unknown>).label)
  return [String(value).trim()].filter(Boolean)
}

export function materialSearchEntries(material: Material): MaterialSearchEntry[] {
  const version = material.currentVersion
  const entries: MaterialSearchEntry[] = []
  const add = (key: string, label: string, value: unknown) => {
    const text = labels(value).join('、')
    if (text) entries.push({ key, label, text, terms: [], searchable: false })
  }
  add('materialNo', '素材编号', material.materialNo)
  add('title', '标题', version?.title || material.title)
  add('summary', '摘要', version?.summary ?? material.summary)
  const walk = (fields: MaterialFieldDefinition[], values: Record<string, unknown>, snapshots: Record<string, unknown>, prefix = '', labelPrefix = '') => {
    fields.forEach(field => {
      const key = `${prefix}${field.key}`
      const label = `${labelPrefix}${field.label}`
      const raw = values[field.key]
      const snapshot = snapshots[field.key]
      if (field.type === 'repeat-group') {
        if (Array.isArray(raw)) raw.forEach((row, index) => {
          if (!row || typeof row !== 'object' || Array.isArray(row)) return
          walk(field.children || [], row, Array.isArray(snapshot) ? snapshot[index] || {} : {}, `${key}.${index}.`, `${label} / 第${index + 1}项 / `)
        })
        return
      }
      if (!field.searchable) return
      // Entity/dictionary codes are not historical display values. Missing snapshots stay missing.
      const snapshotType = ['dict-single', 'dict-multi', 'employee', 'department'].includes(field.type)
      let parts = labels(snapshotType ? snapshot : raw)
      if (['image', 'video', 'attachment'].includes(field.type)) {
        const path = key.replace(/\.\d+\./g, '.')
        const groupIndex = Number(prefix.match(/\.(\d+)\.$/)?.[1] ?? -1)
        parts = (version?.files || []).filter(file => file.fieldKey === path && file.groupIndex === groupIndex).map(file => file.name)
      }
      if (field.type === 'rich-text') {
        parts = parts.map(html => new DOMParser().parseFromString(html, 'text/html'))
          .map(doc => {
            doc.querySelectorAll('script, style').forEach(node => node.remove())
            return doc.body.textContent?.trim() || ''
          })
      }
      const text = parts.filter(Boolean).join('、')
      if (!text) return
      const longText = ['textarea', 'rich-text', 'https-link', 'image', 'video', 'attachment'].includes(field.type)
      entries.push({ key, label, text, searchable: true, terms: !longText && parts.every(part => part.length <= 40 && !part.includes('\n')) ? parts : [] })
    })
  }
  if (version) walk(version.fields, version.values, version.dictSnapshot)
  return entries
}

export function materialSearchHits(entries: MaterialSearchEntry[], keyword: string) {
  const term = keyword.trim().toLocaleLowerCase()
  return term ? entries.filter(entry => entry.text.toLocaleLowerCase().includes(term)) : []
}

export function materialSearchSnippet(text: string, keyword: string, size = 100) {
  const index = keyword ? text.toLocaleLowerCase().indexOf(keyword.toLocaleLowerCase()) : -1
  const start = Math.max(0, index - 24)
  const end = Math.min(text.length, Math.max(start + size, index + keyword.length))
  return `${start ? '…' : ''}${text.slice(start, end)}${end < text.length ? '…' : ''}`
}
