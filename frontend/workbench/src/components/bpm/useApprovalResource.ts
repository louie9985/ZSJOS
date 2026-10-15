import { useCallback, useEffect, useRef, useState } from 'react'

/** Each resource owns its sequence: retry/refresh cannot publish an older response. */
export function useApprovalResource<T>(identity: string, request: () => Promise<T>) {
  const loader = useRef(request)
  loader.current = request
  const seq = useRef(0)
  const [data, setData] = useState<T>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const reload = useCallback(async () => {
    const current = ++seq.current
    setLoading(true); setError('')
    try {
      const result = await loader.current()
      if (current === seq.current) setData(result)
    } catch (cause) {
      if (current === seq.current) setError(cause instanceof Error ? cause.message : '加载失败，请重试')
    } finally { if (current === seq.current) setLoading(false) }
  }, [identity])
  useEffect(() => { setData(undefined); void reload(); return () => { seq.current++ } }, [reload])
  return { data, loading, error, reload }
}
