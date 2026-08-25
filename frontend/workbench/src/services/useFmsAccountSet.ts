import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { FmsAccountSetVO } from './fms/types'

// ────────────────────────────────────────────────────────────────
// Types
// ────────────────────────────────────────────────────────────────

/** 当前选中的账套上下文信息 */
export interface FmsAccountSetContext {
  id: number
  companyName: string
  level: number
}

/** Provider 向下传递的完整 value */
export interface FmsAccountSetContextValue {
  accountSet?: FmsAccountSetContext
  accountSetList: FmsAccountSetVO[]
  listLoaded: boolean
  currentMonth?: string
  writable: boolean
  loading: boolean
  error: string
  selectAccountSet: (id: number) => Promise<void>
  reloadList: () => Promise<void>
  reloadCurrentMonth: () => Promise<string | undefined>
}

// ────────────────────────────────────────────────────────────────
// Context
// ────────────────────────────────────────────────────────────────

export const FmsAccountSetReactContext = createContext<FmsAccountSetContextValue | undefined>(undefined)

// ────────────────────────────────────────────────────────────────
// useFmsAccountSet — simple Context consumer
// ────────────────────────────────────────────────────────────────

export function useFmsAccountSet(): FmsAccountSetContextValue {
  const context = useContext(FmsAccountSetReactContext)
  if (!context) throw new Error('useFmsAccountSet must be used within FmsAccountSetProvider')
  return context
}

// ────────────────────────────────────────────────────────────────
// useFmsResource — load data scoped to the current account set
// ────────────────────────────────────────────────────────────────

/**
 * 当账套就绪后执行 `load(accountSetId)`，账套切换/清空时自动重跑。
 * 内部用递增序号丢弃过期响应（竞态守卫）。
 *
 * @param load  加载函数，接收当前 accountSetId
 * @param deps  额外依赖项（可选）
 */
export function useFmsResource<T>(
  load: (accountSetId: number) => Promise<T>,
  deps: unknown[] = []
): { data: T | undefined; loading: boolean; error: string; reload: () => void } {
  const { accountSet } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const [data, setData] = useState<T | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const versionRef = useRef(0)

  const execute = useCallback(async () => {
    if (!accountSetId) {
      setData(undefined)
      setLoading(false)
      setError('')
      return
    }
    const version = ++versionRef.current
    setLoading(true)
    setError('')
    try {
      const result = await load(accountSetId)
      if (version !== versionRef.current) return
      setData(result)
    } catch (e) {
      if (version !== versionRef.current) return
      setError(e instanceof Error ? e.message : '加载失败')
      setData(undefined)
    } finally {
      if (version === versionRef.current) setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountSetId, ...deps])

  useEffect(() => { void execute() }, [execute])

  const reload = useCallback(() => { void execute() }, [execute])

  return { data, loading, error, reload }
}
