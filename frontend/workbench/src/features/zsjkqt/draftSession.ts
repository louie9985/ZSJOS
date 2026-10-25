import type { ZsjkqtDraft, ZsjkqtSave, ZsjkqtSnapshot } from '../../services/zsjkqt'
import { createIdempotencyKey } from '../../services/idempotency'

type Writer = (id: number | undefined, data: ZsjkqtSave) => Promise<ZsjkqtDraft>

export function copyDraftName(name: string): string {
  const suffix = '（副本）'
  let base = ''
  for (const character of name) {
    if (base.length + character.length > 120 - suffix.length) break
    base += character
  }
  return base + suffix
}

/** One serialized writer owns the draft version; edits arriving during a save remain dirty. */
export class ZsjkqtDraftSession {
  row?: ZsjkqtDraft
  latest?: ZsjkqtSnapshot
  name: string
  readonly clientId: string
  private saved = ''
  private writing?: Promise<ZsjkqtDraft>
  constructor(private writer: Writer, row?: ZsjkqtDraft, clientId: string = createIdempotencyKey()) {
    this.row = row; this.clientId = row?.clientId || clientId; this.name = row?.name || ''
    if (row?.snapshot) { this.latest = row.snapshot; this.saved = JSON.stringify(row.snapshot) }
  }
  get dirty() { return !!this.latest && JSON.stringify(this.latest) !== this.saved }
  update(snapshot: ZsjkqtSnapshot) {
    this.latest = snapshot
    if (!this.name) { const data = snapshot.project || snapshot.month; this.name = `${data?.year}年${data?.month}月考勤` }
  }
  async save(snapshot?: ZsjkqtSnapshot): Promise<ZsjkqtDraft> {
    if (snapshot) this.update(snapshot)
    if (this.writing) return this.writing
    if (!this.latest) throw new Error('请先导入月报')
    if (!this.dirty && this.row) return this.row
    this.writing = this.persist()
    try { return await this.writing } finally { this.writing = undefined }
  }
  private async persist(): Promise<ZsjkqtDraft> {
    do {
      const value = this.latest!, serialized = JSON.stringify(value)
      this.row = await this.writer(this.row?.id, { clientId: this.clientId, name: this.name, version: this.row?.version || 0, snapshot: value })
      this.saved = serialized
    } while (this.dirty)
    return this.row!
  }
}
