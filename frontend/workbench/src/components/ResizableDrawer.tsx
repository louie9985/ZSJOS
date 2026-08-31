import { useEffect, useRef, useState } from 'react'
import { Drawer, type DrawerProps } from 'antd'

const DEFAULT_MAX_VIEWPORT_RATIO = 0.92

export function normalizeDrawerWidth(width: number, viewportWidth: number, minimum: number, maximumRatio = DEFAULT_MAX_VIEWPORT_RATIO) {
  const maximum = Math.floor(viewportWidth * maximumRatio)
  return Math.min(Math.max(width, Math.min(minimum, maximum)), maximum)
}

export function readDrawerWidth(storage: Pick<Storage, 'getItem'>, storageKey: string, viewportWidth: number, minimum: number, maximumRatio = DEFAULT_MAX_VIEWPORT_RATIO) {
  try {
    const stored = Number(storage.getItem(storageKey))
    return Number.isFinite(stored) && stored > 0
      ? normalizeDrawerWidth(stored, viewportWidth, minimum, maximumRatio)
      : undefined
  } catch {
    return undefined
  }
}

type ResizableDrawerProps = Omit<DrawerProps, 'defaultSize' | 'maxSize' | 'panelRef' | 'resizable' | 'size'> & {
  desktopResizable?: boolean
  storageKey: string
  defaultSize: number | string
  minSize: number
  maxViewportRatio?: number
}

export default function ResizableDrawer({
  desktopResizable = true,
  storageKey,
  defaultSize,
  minSize,
  maxViewportRatio = DEFAULT_MAX_VIEWPORT_RATIO,
  open,
  rootClassName,
  width,
  ...props
}: ResizableDrawerProps) {
  const rootRef = useRef<HTMLDivElement>(null)
  const sizeRef = useRef<number | string>(defaultSize)
  const [isDesktop, setIsDesktop] = useState(() => typeof window === 'undefined' || !window.matchMedia('(max-width: 768px)').matches)
  const [drawerSize, setDrawerSize] = useState<number | string>(() => {
    if (typeof window === 'undefined') return defaultSize
    return readDrawerWidth(window.localStorage, storageKey, window.innerWidth, minSize, maxViewportRatio) ?? defaultSize
  })
  const [maximumSize, setMaximumSize] = useState(() => typeof window === 'undefined' ? 1920 : Math.floor(window.innerWidth * maxViewportRatio))
  const canResize = desktopResizable && isDesktop

  useEffect(() => {
    const updateViewport = () => {
      const desktop = !window.matchMedia('(max-width: 768px)').matches
      setIsDesktop(desktop)
      if (!desktop) return
      const nextMaximum = Math.floor(window.innerWidth * maxViewportRatio)
      setMaximumSize(nextMaximum)
      setDrawerSize(current => {
        const next = typeof current === 'number' ? normalizeDrawerWidth(current, window.innerWidth, minSize, maxViewportRatio) : current
        sizeRef.current = next
        return next
      })
    }
    window.addEventListener('resize', updateViewport)
    return () => window.removeEventListener('resize', updateViewport)
  }, [maxViewportRatio, minSize])

  useEffect(() => {
    if (!canResize || !open) return
    const frame = window.requestAnimationFrame(() => {
      const dragger = rootRef.current?.querySelector<HTMLElement>('.ant-drawer-resizable-dragger')
      if (!dragger) return
      dragger.title = '拖动调整抽屉宽度，双击恢复默认宽度'
      dragger.ondblclick = () => {
        try { window.localStorage.removeItem(storageKey) } catch { /* Storage may be unavailable. */ }
        sizeRef.current = defaultSize
        setDrawerSize(defaultSize)
      }
    })
    return () => window.cancelAnimationFrame(frame)
  }, [canResize, defaultSize, open, storageKey])

  return <Drawer
    {...props}
    open={open}
    panelRef={rootRef}
    rootClassName={[rootClassName, canResize && 'crm-resizable-drawer'].filter(Boolean).join(' ')}
    width={canResize ? undefined : width}
    size={canResize ? drawerSize : undefined}
    maxSize={canResize ? maximumSize : undefined}
    resizable={canResize ? {
      onResize: nextSize => {
        const next = normalizeDrawerWidth(nextSize, window.innerWidth, minSize, maxViewportRatio)
        sizeRef.current = next
        setDrawerSize(next)
      },
      onResizeEnd: () => {
        if (typeof sizeRef.current === 'number') {
          try { window.localStorage.setItem(storageKey, String(sizeRef.current)) } catch { /* Persistence is optional. */ }
        }
      },
    } : false}
  />
}
