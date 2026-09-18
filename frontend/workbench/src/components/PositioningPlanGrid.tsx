import type { ReactNode } from 'react'
import type { StudentContactFormField } from '../services/api'

export type PositioningPlanRow = { field: StudentContactFormField; references: StudentContactFormField[] }
export type PositioningPlanKind = 'platform' | 'stage'

// These are existing template field identities, not new fields or material choices.
// Unknown or renamed fields retain the generic reading presentation.
export function positioningPlanKind(key: string): PositioningPlanKind | undefined {
  if (['pc_homepage_douyin', 'pc_homepage_xiaohongshu', 'pc_homepage_channels', 'pc_homepage_other'].includes(key)) return 'platform'
  if (/^pc_delivery_s[0-6]$/.test(key)) return 'stage'
}

export default function PositioningPlanGrid({ kind, rows, render }: {
  kind: PositioningPlanKind; rows: PositioningPlanRow[]; render: (field: StudentContactFormField) => ReactNode
}) {
  const title = kind === 'platform' ? '平台主页搭建' : '阶段交付约定'
  return <section className={`positioning-plan positioning-plan-${kind}`} aria-label={title}>
    <h5>{title}</h5>
    <div className="positioning-plan-grid" data-count={rows.length}>
      {rows.map(({ field, references }) => <article className="positioning-plan-item" key={field.key} data-field-key={field.key}>
        <h6>{field.title}{field.required && ' *'}</h6>
        <div className="positioning-plan-value">{render(field)}</div>
        {references.map(ref => <section className="positioning-plan-references" key={ref.key} aria-label={ref.title}>
          <strong>{ref.title}：</strong>{render(ref)}
        </section>)}
      </article>)}
    </div>
  </section>
}
