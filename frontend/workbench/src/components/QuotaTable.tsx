import HrmProTable from './HrmProTable'
import { Input, InputNumber, Space } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { HrmPerformanceQuota, HrmPerformanceQuotaSave } from '../services/api'

/** 可编辑的绩效指标表格。fill 模式员工可填实际值+自评分；score 模式评分人可填评分+评语。 */
export function QuotaEditableTable({ quotas, mode, onChange }: {
  quotas: HrmPerformanceQuota[]
  mode: 'fill' | 'score'
  onChange: (updated: HrmPerformanceQuotaSave[]) => void
}) {
  /** 元素 index 作为 key 不可靠，这里以内存态辅助 stable key */
  const keyedQuotas = quotas.map((quota, index) => ({ ...quota, __key: `${quota.id ?? 'new'}-${index}` }))

  const update = (targetKey: string, patch: Partial<HrmPerformanceQuotaSave>) => {
    onChange(keyedQuotas.map(quota => {
      if (quota.__key !== targetKey) return quota
      return { ...quota, ...patch }
    }).map(({ __key, ...rest }) => rest))
  }

  const valueInput = (quota: HrmPerformanceQuota & { __key: string }, field: 'actualValue' | 'selfScore' | 'finalScore') => {
    // 员工填写模式下只给 actualValue 和 selfScore 输入框
    if (mode === 'fill' && field === 'finalScore') return quota.finalScore ?? '-'
    if (mode === 'score' && (field === 'actualValue' || field === 'selfScore')) return quota[field] != null ? String(quota[field]) : '-'
    return <InputNumber
      value={quota[field] as number | undefined}
      min={0} max={100} precision={1}
      onChange={value => update(quota.__key, { [field]: value })}
      placeholder={field === 'actualValue' ? '实际完成情况' : '评分'}
      style={{ width: '100%' }}
    />
  }

  const columns: ColumnsType<HrmPerformanceQuota & { __key: string }> = [
    { title: '维度', dataIndex: 'dimensionName', width: 110, render: (value?: string) => value || '-' },
    { title: '指标', dataIndex: 'name', minWidth: 145, ellipsis: true },
    { title: '目标值', dataIndex: 'targetValue', width: 120, render: (value?: string) => value || '-' },
    { title: '实际值', dataIndex: 'actualValue', width: 140, render: (value, quota) => valueInput(quota, 'actualValue') },
    { title: '权重', dataIndex: 'weight', width: 80, align: 'right', render: (value?: number) => value != null ? `${value}%` : '-' },
    { title: '自评分', dataIndex: 'selfScore', width: 100, align: 'right', render: (value, quota) => valueInput(quota, 'selfScore') },
    { title: '最终得分', dataIndex: 'finalScore', width: 100, align: 'right', render: (value, quota) => valueInput(quota, 'finalScore') },
    { title: '评语', dataIndex: 'comment', width: 180, render: (value: string, quota) => mode === 'fill'
      ? <span className="hrm-muted">{value || '-'}</span>
      : <Input.TextArea
        value={value}
        autoSize={{ minRows: 1, maxRows: 3 }}
        onChange={event => update(quota.__key, { comment: event.target.value })}
        placeholder="指标评语"
      />}
  ]

  return <HrmProTable<HrmPerformanceQuota & { __key: string }> rowKey="__key" size="small" columns={columns}
    dataSource={keyedQuotas} pagination={false} scroll={{ x: 1000 }}/>
}

/** 展示用的只读指标表，避免在详情抽屉里重复定义列。 */
export function QuotaReadonlyTable({ quotas }: { quotas: HrmPerformanceQuota[] }) {
  const columns: ColumnsType<HrmPerformanceQuota> = [
    { title: '维度', dataIndex: 'dimensionName', width: 110, render: (value?: string) => value || '-' },
    { title: '指标', dataIndex: 'name', minWidth: 160, ellipsis: true },
    { title: '考核标准', dataIndex: 'standard', minWidth: 200, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '目标值', dataIndex: 'targetValue', width: 110, render: (value?: string) => value || '-' },
    { title: '权重', dataIndex: 'weight', width: 80, align: 'right', render: (value?: number) => value != null ? `${value}%` : '-' },
    { title: '最终得分', dataIndex: 'finalScore', width: 90, align: 'right', render: (value?: number) => value != null ? String(value) : '-' }
  ]
  return <HrmProTable<HrmPerformanceQuota> rowKey={quota => quota.id ?? quota.dimensionId ?? 0} size="small" columns={columns}
    dataSource={quotas} pagination={false} scroll={{ x: 900 }}/>
}

/** 评分阶段的中缀中间态渲染（供详情页复用）。 */
export function StageTable({ stages }: { stages: HrmPerformanceStageLike[] }) {
  const columns: ColumnsType<HrmPerformanceStageLike> = [
    { title: '评分阶段', dataIndex: 'name', minWidth: 130, render: (value?: string) => value || '-' },
    { title: '评分人', dataIndex: 'handlerName', width: 120, render: (value?: string) => value || '-' },
    { title: '权重', dataIndex: 'weight', width: 80, align: 'right', render: (value?: number) => value != null ? `${value}%` : '-' },
    { title: '阶段得分', dataIndex: 'score', width: 90, align: 'right', render: (value?: number) => value != null ? String(value) : '-' },
    { title: '评语', dataIndex: 'comment', minWidth: 160, render: (value?: string) => value || '-' }
  ]
  return <HrmProTable<HrmPerformanceStageLike> rowKey="id" size="small" columns={columns} dataSource={stages}
    pagination={false} scroll={{ x: 700 }}/>
}

type HrmPerformanceStageLike = {
  id?: number; name?: string; handlerName?: string; weight?: number; score?: number; comment?: string
}
