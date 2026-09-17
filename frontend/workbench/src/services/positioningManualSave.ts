export type PendingPositioningFile = { uid: string; file: File }
export type PositioningAttachmentValue = number | PendingPositioningFile
export type DraftIdentity = { id: number; version: number }
export const isPendingPositioningFile = (value: unknown): value is PendingPositioningFile =>
  typeof value === 'object' && value !== null && 'file' in value && 'uid' in value

/** Keeps one explicit save in flight and preserves uploaded IDs across retry. */
export class PositioningManualSave {
  busy = false
  draft?: DraftIdentity
  async run(options: {
    values: Record<string, unknown>
    create: (values: Record<string, unknown>) => Promise<DraftIdentity>
    update: (draft: DraftIdentity, values: Record<string, unknown>) => Promise<DraftIdentity>
    upload: (id: number, key: string, file: File) => Promise<{ id: number }>
    onUploaded: (key: string, values: PositioningAttachmentValue[], file: { id: number; name: string; size: number }) => void
  }) {
    if (this.busy) return false
    this.busy = true
    try {
      const values = { ...options.values }
      const pendingFields = Object.entries(values).filter(([, value]) => Array.isArray(value) && value.some(isPendingPositioningFile))
      const creating = !this.draft
      if (!this.draft) {
        const initial = { ...values }
        for (const [key, value] of pendingFields) initial[key] = (value as PositioningAttachmentValue[]).filter(item => !isPendingPositioningFile(item))
        this.draft = await options.create(initial)
      }
      for (const [key, value] of pendingFields) {
        const items = [...value as PositioningAttachmentValue[]]
        for (let index = 0; index < items.length; index++) {
          const item = items[index]
          if (!isPendingPositioningFile(item)) continue
          const uploaded = await options.upload(this.draft.id, key, item.file)
          items[index] = uploaded.id
          options.onUploaded(key, [...items], { id: uploaded.id, name: item.file.name, size: item.file.size })
        }
        values[key] = items
      }
      if (!creating || pendingFields.length) this.draft = await options.update(this.draft, values)
      return true
    } finally { this.busy = false }
  }
}
