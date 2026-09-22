import type { ReactNode } from 'react'
import type { StudentContactFormField } from '../services/api'

export default function PositioningCardFields({ fields, render }: { fields: StudentContactFormField[]; render: (field: StudentContactFormField) => ReactNode }) {
  const enabled = fields.filter(field => field.enabled).sort((a, b) => a.sort - b.sort)
  return <div className="positioning-card-fields">
    <div className="positioning-card-field-head"><span>定位卡项目</span><span>填写提示</span><span>计划交付内容确定</span><span>参考账号与爆款</span></div>
    {enabled.filter(field => !field.referenceFor).map(field => <section className="positioning-card-field-row" key={field.key}>
      <strong className="positioning-card-field-title">{field.title}{field.required ? ' *' : ''}</strong>
      <div className="positioning-card-field-hint" data-label="填写提示">{field.description || '—'}</div>
      <div className="positioning-card-field-control" data-label="计划交付内容确定">{render({ ...field, description: undefined })}</div>
      <div className="positioning-card-field-control positioning-card-field-references" data-label="参考账号与爆款">{enabled.some(ref => ref.referenceFor === field.key)
        ? enabled.filter(ref => ref.referenceFor === field.key).map(ref => <div key={ref.key}>{render({ ...ref, description: undefined })}</div>)
        : <span className="positioning-card-no-reference">—</span>}</div>
    </section>)}
  </div>
}
