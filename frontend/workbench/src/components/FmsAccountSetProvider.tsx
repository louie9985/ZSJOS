import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { STORAGE_KEYS } from '../constants'
import { fmsConfig, fmsClosing, FmsAccountUserLevel } from '../services/fms'
import type { FmsAccountSetVO } from '../services/fms/types'
import { FmsAccountSetReactContext, type FmsAccountSetContext, type FmsAccountSetContextValue } from '../services/useFmsAccountSet'

// ────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────

function readStoredAccountSet(): FmsAccountSetContext | undefined {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.FMS_ACCOUNT_SET)
    if (!raw) return undefined
    const parsed = JSON.parse(raw) as Partial<FmsAccountSetContext>
    if (parsed && typeof parsed.id === 'number' && typeof parsed.companyName === 'string' && typeof parsed.level === 'number') {
      return parsed as FmsAccountSetContext
    }
  } catch { /* ignore */ }
  return undefined
}

function persistAccountSet(ctx: FmsAccountSetContext | undefined) {
  if (ctx) {
    localStorage.setItem(STORAGE_KEYS.FMS_ACCOUNT_SET, JSON.stringify(ctx))
  } else {
    localStorage.removeItem(STORAGE_KEYS.FMS_ACCOUNT_SET)
  }
}

// ────────────────────────────────────────────────────────────────
// Empty value (used when enabled = false)
// ────────────────────────────────────────────────────────────────

const EMPTY_LIST: FmsAccountSetVO[] = []
const NOOP_ASYNC = async () => {}
const NOOP_ASYNC_ID = async (_id: number) => {}
const NOOP_ASYNC_MONTH = async (): Promise<string | undefined> => undefined
const DISABLED_VALUE: FmsAccountSetContextValue = {
  accountSet: undefined,
  accountSetList: EMPTY_LIST,
  listLoaded: false,
  currentMonth: undefined,
  writable: false,
  loading: false,
  error: '',
  selectAccountSet: NOOP_ASYNC_ID,
  reloadList: NOOP_ASYNC,
  reloadCurrentMonth: NOOP_ASYNC_MONTH
}

// ────────────────────────────────────────────────────────────────
// Provider
// ────────────────────────────────────────────────────────────────

export function FmsAccountSetProvider({ enabled, children }: { enabled: boolean; children: ReactNode }) {
  // When disabled, short-circuit with empty value
  if (!enabled) {
    return <FmsAccountSetReactContext.Provider value={DISABLED_VALUE}>{children}</FmsAccountSetReactContext.Provider>
  }
  return <FmsAccountSetProviderInner>{children}</FmsAccountSetProviderInner>
}

function FmsAccountSetProviderInner({ children }: { children: ReactNode }) {
  const [accountSet, setAccountSet] = useState<FmsAccountSetContext | undefined>(readStoredAccountSet)
  const [accountSetList, setAccountSetList] = useState<FmsAccountSetVO[]>([])
  const [listLoaded, setListLoaded] = useState(false)
  const [currentMonth, setCurrentMonth] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Race guard for currentMonth loading
  const monthVersionRef = useRef(0)

  // ─── Load account set list ─────────────────────────────────────

  const loadList = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const list = await fmsConfig.accountSet.list()
      setAccountSetList(list)
      setListLoaded(true)

      // Restore logic: cached account set → default → first initialized
      setAccountSet(prev => {
        const restored =
          list.find(item => item.id === prev?.id && item.initialized) ||
          list.find(item => item.defaultStatus && item.initialized) ||
          list.find(item => item.initialized)

        if (restored) {
          const ctx: FmsAccountSetContext = { id: restored.id!, companyName: restored.companyName, level: restored.level! }
          persistAccountSet(ctx)
          return ctx
        }
        persistAccountSet(undefined)
        return undefined
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : '账套列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void loadList() }, [loadList])

  // ─── Select account set ────────────────────────────────────────

  const selectAccountSet = useCallback(async (id: number) => {
    const found = accountSetList.find(item => item.id === id && item.initialized)
    if (!found || found.id === accountSet?.id) return

    await fmsConfig.accountUser.updateDefaultStatus(id)
    const ctx: FmsAccountSetContext = { id: found.id!, companyName: found.companyName, level: found.level! }
    setAccountSetList(currentList => currentList.map(item => ({ ...item, defaultStatus: item.id === id })))
    setCurrentMonth(undefined)
    persistAccountSet(ctx)
    setAccountSet(ctx)
  }, [accountSet?.id, accountSetList])

  // ─── Load current month (with race guard) ──────────────────────

  const reloadCurrentMonth = useCallback(async (): Promise<string | undefined> => {
    const accountSetId = accountSet?.id
    if (!accountSetId) {
      setCurrentMonth(undefined)
      return undefined
    }
    const version = ++monthVersionRef.current
    try {
      const month = await fmsClosing.period.getCurrentMonth(accountSetId)
      if (version !== monthVersionRef.current) return undefined
      setCurrentMonth(month)
      return month
    } catch {
      if (version !== monthVersionRef.current) return undefined
      setCurrentMonth(undefined)
      return undefined
    }
  }, [accountSet?.id])

  // Auto-load currentMonth when accountSet changes
  useEffect(() => { void reloadCurrentMonth() }, [reloadCurrentMonth])

  // ─── writable ──────────────────────────────────────────────────

  const writable = useMemo(
    () => listLoaded && (accountSet?.level === FmsAccountUserLevel.OWNER || accountSet?.level === FmsAccountUserLevel.WRITE),
    [listLoaded, accountSet?.level]
  )

  // ─── Context value ─────────────────────────────────────────────

  const value = useMemo<FmsAccountSetContextValue>(() => ({
    accountSet,
    accountSetList,
    listLoaded,
    currentMonth,
    writable,
    loading,
    error,
    selectAccountSet,
    reloadList: loadList,
    reloadCurrentMonth
  }), [accountSet, accountSetList, listLoaded, currentMonth, writable, loading, error, selectAccountSet, loadList, reloadCurrentMonth])

  return <FmsAccountSetReactContext.Provider value={value}>{children}</FmsAccountSetReactContext.Provider>
}
