import { createContext, useCallback, useContext, useEffect, useRef, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'

type Guard = (destination?: string) => Promise<boolean>
type Navigation = { canOpen: (path: string) => boolean; open: (path: string) => Promise<void>; canClose: (path: string) => Promise<boolean>; validate: (path: string, destination?: string) => Promise<boolean>; register: (path: string, guard: Guard) => () => void }
const Context = createContext<Navigation | undefined>(undefined)

export function WorkbenchPageNavigation({ canOpen, children }: { canOpen: (path: string) => boolean; children: ReactNode }) {
  const navigate = useNavigate()
  const guards = useRef(new Map<string, Set<Guard>>())
  const register = useCallback((path: string, guard: Guard) => {
    const group = guards.current.get(path) || new Set<Guard>()
    group.add(guard); guards.current.set(path, group)
    return () => { group.delete(guard); if (!group.size) guards.current.delete(path) }
  }, [])
  const check = useCallback(async (path: string, destination?: string) => {
    for (const guard of guards.current.get(path) || []) if (!await guard(destination)) return false
    return true
  }, [])
  const canClose = useCallback((path: string) => check(path), [check])
  const open = useCallback(async (path: string) => {
    const pathname = path.split(/[?#]/, 1)[0]
    if (!canOpen(pathname)) return
    if (!await check(pathname, path)) return
    navigate(path)
  }, [canOpen, navigate, check])
  return <Context.Provider value={{ canOpen, open, register, canClose, validate: check }}>{children}</Context.Provider>
}

export function useWorkbenchPageNavigation() { return useContext(Context) }
export function useWorkbenchPageGuard(path: string, guard: Guard) {
  const navigation = useContext(Context)
  const latest = useRef(guard)
  latest.current = guard
  useEffect(() => navigation?.register(path, destination => latest.current(destination)), [navigation?.register, path])
}
