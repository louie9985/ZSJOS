import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, type DictData, type ManagedLead } from './api'
import { DICT_TYPE } from '../constants'

export function useLeadSalesStages(lead: ManagedLead) {
  const requestVersion = useRef(0)
  const [items, setItems] = useState<DictData[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const reload = useCallback(async () => {
    const version = ++requestVersion.current
    setLoading(true); setError('')
    try {
      const data = await api.dictDataByType(DICT_TYPE.LEAD_SALES_STAGE)
      if (version === requestVersion.current) setItems(data)
    } catch (cause) {
      if (version === requestVersion.current) setError(cause instanceof Error ? cause.message : '销售阶段加载失败')
    } finally { if (version === requestVersion.current) setLoading(false) }
  }, [])
  useEffect(() => { void reload(); return () => { requestVersion.current++ } }, [reload])
  const options = useMemo(() => {
    const values = items.map(item => ({ value: item.value,
      label: item.value === lead.salesStage ? lead.salesStageLabelSnapshot || '未记录' : item.label,
      disabled: false }))
    if (lead.salesStage && !values.some(item => item.value === lead.salesStage)) {
      values.unshift({ value: lead.salesStage, label: lead.salesStageLabelSnapshot || '未记录', disabled: true })
    }
    return values
  }, [items, lead.salesStage, lead.salesStageLabelSnapshot])
  return { options, loading, error, reload }
}
