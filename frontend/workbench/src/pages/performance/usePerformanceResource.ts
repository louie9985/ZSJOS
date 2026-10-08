import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { PerformanceCache, performanceQueryKey } from '../../services/performanceCache'
import { isPerformanceAccessDenied } from '../../services/performanceSession'

export function usePerformanceResource<T>(cache: PerformanceCache, resource: string, query: object,
  enabled: boolean, loader: (signal: AbortSignal) => Promise<T>) {
  const key = performanceQueryKey(resource, query)
  useSyncExternalStore(cache.subscribe, cache.snapshot)
  const [attempt, setAttempt] = useState(0)
  const [failure, setFailure] = useState<{ key: string; generation: number; attempt: number; message: string }>()
  const latestLoader = useRef(loader); latestLoader.current = loader
  const generation = cache.generation
  const expired = !cache.peek(key)
  useEffect(() => {
    if (!enabled || cache.blockedError) return
    let active = true
    setFailure(undefined)
    cache.load(key, signal => latestLoader.current(signal)).catch((error: unknown) => {
      if (isPerformanceAccessDenied(error)) {
        cache.clear(error instanceof Error ? error.message : '无权查看该业绩范围')
        return
      }
      if (active && !(error instanceof Error && error.name === 'AbortError')) {
        setFailure({ key, generation, attempt, message: error instanceof Error ? error.message : '加载失败，请重试' })
      }
    })
    // Tab changes detach the view, while the page owns and deduplicates its in-flight request.
    return () => { active = false }
  }, [cache, key, enabled, generation, attempt, expired])
  const entry = cache.peek(key)
  const error = cache.blockedError || (!entry && failure?.key === key && failure.generation === generation && failure.attempt === attempt ? failure.message : '')
  return { data: entry?.data as T | undefined, updatedAt: entry?.updatedAt, error, loading: enabled && !entry && !error,
    reload: () => { if (cache.blockedError) cache.clear(); else cache.invalidate(key); setAttempt(value => value + 1) } }
}
