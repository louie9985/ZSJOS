import type { ReactNode } from 'react'
import type { StudentContactFormField } from '../services/api'

export default function PositioningCardFields({ fields, render }: { fields: StudentContactFormField[]; render: (field: StudentContactFormField) => ReactNode }) {
  const enabled = fields.filter(field => field.enabled).sort((a, b) => a.sort - b.sort)
  const grouped = enabled.filter(field => !field.referenceFor).reduce<Map<string, StudentContactFormField[]>>((result, field) => {
    const key = field.group || ''
    const rows = result.get(key) || []
    rows.push(field)
    result.set(key, rows)
    return result
  }, new Map())
  return <div className="positioning-card-fields">
    <div className="positioning-card-field-head"><span>分组</span><span>定位卡项目</span><span>填写提示</span><span>计划交付内容确定</span><span>参考账号与爆款</span></div>
    {[...grouped.entries()].map(([group, groupFields]) => groupFields.map((field, index) => <section className="positioning-card-field-row" key={field.key}>
      {index === 0 && <strong className="positioning-card-field-group" style={{ gridRow: `span ${groupFields.length}` }}>{group || '账号选择'}</strong>}
      <strong className="positioning-card-field-title">{field.title}{field.required ? ' *' : ''}</strong>
      <div className="positioning-card-field-hint" data-label="填写提示">{field.description || '—'}</div>
      <div className="positioning-card-field-control" data-label="计划交付内容确定">{render({ ...field, description: undefined })}</div>
      <div className="positioning-card-field-control positioning-card-field-references" data-label="参考账号与爆款">{enabled.some(ref => ref.referenceFor === field.key)
        ? enabled.filter(ref => ref.referenceFor === field.key).map(ref => <div key={ref.key}>{render({ ...ref, description: undefined })}</div>)
        : <span className="positioning-card-no-reference">—</span>}</div>
    </section>))}
  </div>
}
