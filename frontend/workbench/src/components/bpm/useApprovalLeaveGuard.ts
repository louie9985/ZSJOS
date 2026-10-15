import { useEffect, useLayoutEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useWorkbenchPageGuard } from '../WorkbenchPageNavigation'

/** BrowserRouter has no data-router blocker. Restore a POP before asking, then replay only on consent. */
export function useApprovalLeaveGuard(check: () => Promise<boolean>, blocked: () => boolean) {
  const location = useLocation()
  const navigate = useNavigate()
  const navigateRef = useRef(navigate)
  navigateRef.current = navigate
  const latest = useRef({ check, blocked })
  latest.current = { check, blocked }
  const index = useRef<number>(window.history.state?.idx ?? 0)
  useEffect(() => { index.current = window.history.state?.idx ?? index.current }, [location])
  useWorkbenchPageGuard(location.pathname, check)
  useLayoutEffect(() => {
    let restoring = false
    let replaying = false
    let disposed = false
    let pendingDelta = 0
    const pop = (event: PopStateEvent) => {
      if (replaying) { replaying = false; return }
      if (restoring) {
        event.stopImmediatePropagation(); restoring = false
        void latest.current.check().then(allowed => {
          if (allowed && !disposed) { replaying = true; window.history.go(-pendingDelta) }
        })
        return
      }
      if (!latest.current.blocked()) return
      const target = event.state?.idx
      if (typeof target !== 'number' || target === index.current) return
      event.stopImmediatePropagation()
      pendingDelta = index.current - target
      restoring = true
      window.history.go(pendingDelta)
    }
    const click = (event: MouseEvent) => {
      if (event.button || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || !latest.current.blocked()) return
      const anchor = (event.target as Element).closest?.('a[href]') as HTMLAnchorElement | null
      if (!anchor || anchor.target || anchor.hasAttribute('download') || anchor.origin !== window.location.origin || anchor.href === window.location.href) return
      event.preventDefault(); event.stopImmediatePropagation()
      void latest.current.check().then(allowed => { if (allowed && !disposed) navigateRef.current(anchor.pathname + anchor.search + anchor.hash) })
    }
    window.addEventListener('popstate', pop, true)
    document.addEventListener('click', click, true)
    return () => { disposed = true; window.removeEventListener('popstate', pop, true); document.removeEventListener('click', click, true) }
  }, [])
}
