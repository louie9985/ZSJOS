import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, type ReactNode } from 'react'
import { message } from 'antd'

type EditorState = { dirty: boolean; busy: boolean }
type Guard = { register: (id: symbol, read: () => EditorState) => () => void; confirmLeave: () => boolean }
const Context = createContext<Guard | undefined>(undefined)

export function StudentArchiveEditGuard({ children }: { children: ReactNode }) {
  const editors = useRef(new Map<symbol, () => EditorState>())
  const register = useCallback<Guard['register']>((id, read) => {
    editors.current.set(id, read)
    return () => { editors.current.delete(id) }
  }, [])
  const confirmLeave = useCallback(() => {
    const states = [...editors.current.values()].map(read => read())
    if (states.some(state => state.busy)) { message.warning('档案正在保存或上传，请完成后再切换'); return false }
    return !states.some(state => state.dirty) || window.confirm('档案有未保存修改，切换将丢失这些内容。确定继续吗？')
  }, [])
  const guard = useMemo(() => ({ register, confirmLeave }), [register, confirmLeave])
  return <Context.Provider value={guard}>{children}</Context.Provider>
}

export function useStudentArchiveNavigationGuard() { return useContext(Context) }

export function useStudentArchiveEditor(dirty: boolean, busy: boolean) {
  const guard = useContext(Context)
  const id = useRef(Symbol('archive-editor'))
  const current = useRef({ dirty, busy }); current.current = { dirty, busy }
  useLayoutEffect(() => guard?.register(id.current, () => current.current), [guard])
  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (current.current.dirty || current.current.busy) { event.preventDefault(); event.returnValue = '' }
    }
    window.addEventListener('beforeunload', beforeUnload)
    return () => window.removeEventListener('beforeunload', beforeUnload)
  }, [])
}
