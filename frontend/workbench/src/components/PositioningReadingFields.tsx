import type { ReactNode } from 'react'
import type { StudentContactFormField } from '../services/api'
import PositioningPlanGrid, { positioningPlanKind, type PositioningPlanKind, type PositioningPlanRow } from './PositioningPlanGrid'

// Reference fields remain attached to their owner. Orphaned references stay visible
// so a retired template field cannot silently hide a persisted value.
export function positioningReadingRows(fields: StudentContactFormField[]) {
  const enabled = fields.filter(field => field.enabled).sort((a, b) => a.sort - b.sort)
  const roots = enabled.filter(field => !field.referenceFor || !enabled.some(owner => !owner.referenceFor && owner.key === field.referenceFor))
  return roots.map(field => ({ field, references: enabled.filter(ref => ref.referenceFor === field.key && ref.key !== field.key) }))
}

export function positioningReadingGroups(fields: StudentContactFormField[]) {
  const groups: Array<{ key: string; title?: string; rows: ReturnType<typeof positioningReadingRows> }> = []
  let group: typeof groups[number] | undefined
  for (const row of positioningReadingRows(fields)) {
    // Some templates mark a section only on its first field. Keep subsequent
    // unlabelled rows in that section without inventing a business category.
    if (!group || row.field.group && row.field.group !== group.title) {
      group = { key: row.field.key, title: row.field.group, rows: [] }
      groups.push(group)
    }
    group.rows.push(row)
  }
  return groups
}

export default function PositioningReadingFields({ fields, render, wideKeys = [] }: {
  fields: StudentContactFormField[]; render: (field: StudentContactFormField) => ReactNode; wideKeys?: string[]
}) {
  return <div className="positioning-reading-grid-sections">
    {positioningReadingGroups(fields).map(group => <section className="positioning-reading-group" key={group.key}>
      {group.title && <h4>{group.title}</h4>}
      <div className="positioning-reading-grid">{positioningReadingBlocks(group.rows).map(block => block.kind ? <PositioningPlanGrid key={block.rows[0].field.key} kind={block.kind} rows={block.rows} render={render} /> : block.rows.map(({ field, references }) => <section className="positioning-reading-field" key={field.key} data-wide={wideKeys.includes(field.key) || ['attachment', 'material_picker'].includes(field.type) || references.length > 0}>
        <strong className="positioning-reading-field-name">{field.title}{field.required && ' *'}：</strong>
        <div className="positioning-reading-field-value">{render(field)}</div>
        {references.length > 0 && <div className="positioning-reading-references">{references.map(ref => <section key={ref.key}><strong>{ref.title}：</strong>{render(ref)}</section>)}</div>}
      </section>))}</div>
    </section>)}
  </div>
}

export function positioningReadingBlocks(rows: PositioningPlanRow[]) {
  const blocks: Array<{ kind?: PositioningPlanKind; rows: PositioningPlanRow[] }> = []
  for (const row of rows) {
    const kind = positioningPlanKind(row.field.key)
    const previous = blocks.at(-1)
    // Never move a configured field across an intervening field or group boundary.
    if (previous && previous.kind === kind) previous.rows.push(row)
    else blocks.push({ kind, rows: [row] })
  }
  return blocks
}
