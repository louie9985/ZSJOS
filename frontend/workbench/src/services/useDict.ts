import { useCallback, useEffect, useState } from 'react'
import { api, type DictData } from '../services/api'

/**
 * 按字典类型加载选项。字典必须来自后端，禁止在组件内硬编码回退值，
 * 因此加载失败时返回空数组并暴露 error 供调用方提示与重试。
 */
export function useDict(dictType: string) {
  const [items, setItems] = useState<DictData[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    if (!dictType) { setItems([]); setLoading(false); setError(''); return }
    setLoading(true); setError('')
    try { setItems(await api.dictDataByType(dictType)) }
    catch (e) { setError(e instanceof Error ? e.message : '字典加载失败') }
    finally { setLoading(false) }
  }, [dictType])

  useEffect(() => { void load() }, [load])

  const labels: Record<string, string> = {}
  for (const item of items) labels[item.value] = item.label
  const options = items.map(item => ({ value: Number(item.value), label: item.label }))

  return { items, options, labels, loading, error, reload: load }
}
