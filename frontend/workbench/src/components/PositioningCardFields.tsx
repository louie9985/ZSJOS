import { InfoCircleOutlined } from '@ant-design/icons'
import { Tooltip, Typography } from 'antd'
import type { ReactNode } from 'react'
import type { StudentContactFormField } from '../services/api'

export default function PositioningCardFields({ fields, render }: { fields: StudentContactFormField[]; render: (field: StudentContactFormField) => ReactNode }) {
  const enabled = fields.filter(field => field.enabled).sort((a, b) => a.sort - b.sort)
  return <div className="positioning-card-fields">
    <div className="positioning-card-field-head"><span>定位卡项目</span><span>填写提示</span><span>计划交付内容确定</span><span>参考账号与爆款</span></div>
    {enabled.filter(field => !field.referenceFor).map(field => <section className="positioning-card-field-row" key={field.key}>
      <strong>{field.title}{field.required ? ' *' : ''}</strong>
      <div className="positioning-card-field-hint">{field.description ? <Tooltip title={field.description}><Typography.Link><InfoCircleOutlined /> 查看填写提示</Typography.Link></Tooltip> : '—'}</div>
      <div className="positioning-card-field-control">{render({ ...field, description: undefined })}</div>
      <div className="positioning-card-field-control">{enabled.filter(ref => ref.referenceFor === field.key).map(ref => <div key={ref.key}>{render({ ...ref, description: undefined })}</div>)}</div>
    </section>)}
  </div>
}
