import { filterSelectDisplayProps } from "../components/filterSelectDisplay"
import { Button, DatePicker, Input, Select, Space, Typography } from 'antd'
import dayjs from 'dayjs'
import type { DeliveryClassFilterContext } from '../services/api'
import { classDateRange, type ClassDatePreset } from './classDatePresets'

export type ClassFilters = { projectCategoryId?: number; homeroomUserId?: number; dateRange?: [string, string]; preset: ClassDatePreset }
export const defaultClassFilters: ClassFilters = { preset: 'all' }
export default function DeliveryClassFilters({ value, onChange, context, loading, keyword, onKeyword, onSearch, onReset }: {
  value: ClassFilters; onChange: (next: ClassFilters) => void; context?: DeliveryClassFilterContext; loading: boolean;
  keyword: string; onKeyword: (value: string) => void; onSearch: () => void; onReset: () => void;
}) {
  return <div className="delivery-class-filters">
    <div className="delivery-class-filter-fields">
      <label>项目<Select aria-label="项目" showSearch optionFilterProp="label" allowClear placeholder="全部项目" loading={loading} disabled={!context}
        value={value.projectCategoryId} onChange={projectCategoryId => onChange({ ...value, projectCategoryId })}
        options={context?.projects.map(row => ({ value: row.id, label: row.path || row.name }))}  {...filterSelectDisplayProps}/></label>
      <label>班主任<Select aria-label="班主任筛选" showSearch optionFilterProp="label" allowClear placeholder="全部班主任" loading={loading} disabled={!context}
        value={value.homeroomUserId} onChange={homeroomUserId => onChange({ ...value, homeroomUserId })}
        options={context?.homerooms.map(row => ({ value: row.id, label: row.name + (row.deptName ? ' · ' + row.deptName : '') }))}  {...filterSelectDisplayProps}/></label>
      <label>搜索班级<Input.Search aria-label="搜索班级" allowClear value={keyword} placeholder="班级名称 / 编号" onChange={event => onKeyword(event.target.value)} onSearch={onSearch} /></label>
      <Button onClick={onReset}>重置</Button>
    </div>
    <Space wrap className="delivery-class-quick-dates"><Typography.Text type="secondary">考期范围</Typography.Text>
      {([{ value: 'all', label: '不限' }, { value: 'month', label: '本月' }, { value: 'next', label: '下月' }, { value: '30', label: '未来 30 天' }, { value: '90', label: '未来 90 天' }, { value: 'custom', label: '自定义' }] as const).map(item =>
        <Button key={item.value} size="small" type={value.preset === item.value ? 'primary' : 'text'} aria-pressed={value.preset === item.value}
          onClick={() => onChange({ ...value, preset: item.value, dateRange: item.value === 'custom' ? value.dateRange : classDateRange(item.value) })}>{item.label}</Button>)}
      {value.preset === 'custom' && <DatePicker.RangePicker aria-label="自定义考期范围" value={value.dateRange ? [dayjs(value.dateRange[0]), dayjs(value.dateRange[1])] : null}
        onChange={dates => onChange({ ...value, dateRange: dates?.[0] && dates[1] ? [dates[0].format('YYYY-MM-DD'), dates[1].format('YYYY-MM-DD')] : undefined })} />}
    </Space>
  </div>
}
