import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError, AuthenticationError, type AdvancedFilterField } from '../services/api'

export function useFinanceFilterOptions(scene: 'cashback' | 'withdrawal', enabled: boolean) {
  const [fields, setFields] = useState<AdvancedFilterField[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const sequence = useRef(0)
  const reload = useCallback(async () => {
    const request = ++sequence.current
    setError('')
    if (!enabled) { setFields([]); setLoading(false); return }
    setLoading(true)
    try {
      const catalog = await api.advancedFilterCatalog(scene)
      if (request === sequence.current) setFields(catalog.fields)
    } catch (cause) {
      if (request === sequence.current) setError(cause instanceof AuthenticationError ? '登录已失效，请重新登录'
        : cause instanceof ApiError && cause.code === 403 ? '暂无该场景的筛选查询权限'
        : '筛选选项加载失败，请重试')
    } finally {
      if (request === sequence.current) setLoading(false)
    }
  }, [scene, enabled])
  useEffect(() => { setFields([]); void reload(); return () => { sequence.current++ } }, [reload])
  const options = (key: string) => fields.find(field => field.fieldKey === `${scene}.${key}`)?.options || []
  return { options, loading, error, reload }
}
