import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

type OverlayCoordinatorValue = {
  businessOverlayCount: number
  registerBusinessOverlay: () => () => void
}

const OverlayCoordinatorContext = createContext<OverlayCoordinatorValue | undefined>(undefined)

export function OverlayCoordinatorProvider({ children }: React.PropsWithChildren) {
  const [businessOverlayCount, setBusinessOverlayCount] = useState(0)
  const registerBusinessOverlay = useCallback(() => {
    setBusinessOverlayCount(value => value + 1)
    let active = true
    return () => {
      if (!active) return
      active = false
      setBusinessOverlayCount(value => Math.max(0, value - 1))
    }
  }, [])
  const value = useMemo(() => ({ businessOverlayCount, registerBusinessOverlay }),
    [businessOverlayCount, registerBusinessOverlay])
  return <OverlayCoordinatorContext.Provider value={value}>{children}</OverlayCoordinatorContext.Provider>
}

export function useBusinessOverlay(open: boolean) {
  const context = useContext(OverlayCoordinatorContext)
  if (!context) throw new Error('useBusinessOverlay must be used inside OverlayCoordinatorProvider')
  useEffect(() => open ? context.registerBusinessOverlay() : undefined,
    [context.registerBusinessOverlay, open])
}

export function useOverlayCoordinator() {
  const context = useContext(OverlayCoordinatorContext)
  if (!context) throw new Error('useOverlayCoordinator must be used inside OverlayCoordinatorProvider')
  return context
}
