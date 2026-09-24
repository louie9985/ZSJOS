import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Segmented, Spin } from 'antd'
import { api, type AdvancedFilterField } from '../services/api'

export default function SubordinateSalesStatusFilter({ value, onChange }: {
  value: number | undefined
  onChange: (value: number | undefined) => void
}) {
  const [options, setOptions] = useState<AdvancedFilterField['options']>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const retry = useCallback(() => setAttempt(current => current + 1), [])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    void api.advancedFilterCatalog('subordinate_sales').then(catalog => {
      if (!active) return
      const statuses = catalog.fields.find(field => field.fieldKey === 'subordinate.accountStatus')?.options || []
      const enabled = statuses.find(option => String(option.value) === '0')
      const disabled = statuses.find(option => String(option.value) === '1')
      if (!enabled || !disabled) throw new Error('账号状态筛选配置不完整')
      setOptions([
        { value: 0, label: enabled.label },
        { value: 'all', label: '全部' },
        { value: 1, label: disabled.label },
      ])
    }).catch(cause => {
      if (active) setError(cause instanceof Error ? cause.message : '账号状态筛选加载失败')
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [attempt])

  if (loading) return <Spin size="small" aria-label="加载账号状态筛选" />
  if (error) return <Alert type="error" showIcon title={error}
    action={<Button size="small" onClick={retry}>重试</Button>} />
  return <Segmented aria-label="账号状态" value={value ?? 'all'} options={options}
    onChange={next => onChange(next === 'all' ? undefined : Number(next))} />
}
