import { Select, Typography } from 'antd'
import type { BusinessSort } from '../services/businessListSort'
import './LeadInboxSortControls.css'

export default function BusinessSortMenu({ label, value, fields, onChange, total, loaded, loading, error }: {
  label: string
  value: BusinessSort
  fields: { field: string; label: string }[]
  onChange: (sort: BusinessSort) => void
  total?: number
  loaded: number
  loading?: boolean
  error?: boolean
}) {
  return <div className="lead-inbox-sort-controls">
    <div className="lead-inbox-sort-fields">
      <label><Typography.Text type="secondary">排序字段</Typography.Text>
        <Select aria-label={`${label}排序字段`} value={value.sortField || 'default'}
          options={[{ value: 'default', label: '默认排序' }, ...fields.map(item => ({ value: item.field, label: item.label }))]}
          onChange={field => onChange(field === 'default' ? {} : { sortField: field, sortOrder: value.sortOrder || 'descend' })}/>
      </label>
      <label><Typography.Text type="secondary">排序方向</Typography.Text>
        <Select aria-label={`${label}排序方向`} disabled={!value.sortField} placeholder="默认" value={value.sortField ? value.sortOrder : undefined}
          options={[{ value: 'ascend', label: '升序 ↑' }, { value: 'descend', label: '降序 ↓' }]}
          onChange={sortOrder => onChange({ sortField: value.sortField, sortOrder })}/>
      </label>
    </div>
    <Typography.Text type="secondary" className="lead-inbox-sort-hint">{value.sortField ? `按${fields.find(field => field.field === value.sortField)?.label || '当前字段'}${value.sortOrder === 'ascend' ? '升序' : '降序'}排列` : '按系统默认顺序排列'}</Typography.Text>
    <div className="business-inbox-result-count" role="status" aria-live="polite">
      {loading ? <Typography.Text type="secondary">正在统计…</Typography.Text> : error || total === undefined ? <Typography.Text type="secondary">数量暂不可用</Typography.Text> : <><span>当前结果 <strong>{total}</strong> 条</span><Typography.Text type="secondary">已加载 {loaded} 条</Typography.Text></>}
    </div>
  </div>
}
