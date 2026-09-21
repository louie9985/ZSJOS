import { useEffect, useRef, useState } from 'react'
import type { HTMLAttributes, PointerEvent as ReactPointerEvent } from 'react'
import { clampColumnWidth, parseColumnWidths } from './columns'

function readWidths(key: string) {
  try { return parseColumnWidths(window.localStorage.getItem(key)) } catch { return {} }
}

export function useColumnWidths(storageKey: string) {
  const [state, setState] = useState(() => ({ key: storageKey, widths: readWidths(storageKey) }))
  const finish = useRef<(() => void) | undefined>(undefined)
  const suppressClickUntil = useRef(0)
  const widths = state.key === storageKey ? state.widths : readWidths(storageKey)
  useEffect(() => {
    if (state.key !== storageKey) return
    try { window.localStorage.setItem(storageKey, JSON.stringify(state.widths)) } catch { /* Storage denial must not disable resizing. */ }
  }, [state, storageKey])
  useEffect(() => () => finish.current?.(), [storageKey])

  function setWidth(key: string, width: number) {
    setState(current => ({ key: storageKey, widths: {
      ...(current.key === storageKey ? current.widths : readWidths(storageKey)), [key]: clampColumnWidth(width),
    } }))
  }
  function resize(event: ReactPointerEvent<HTMLElement>, key: string, width: number) {
    event.preventDefault()
    event.stopPropagation()
    finish.current?.()
    const startX = event.clientX
    const move = (next: PointerEvent) => setWidth(key, width + next.clientX - startX)
    const end = () => {
      suppressClickUntil.current = Date.now() + 300
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', end)
      window.removeEventListener('pointercancel', end)
      finish.current = undefined
    }
    finish.current = end
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', end)
    window.addEventListener('pointercancel', end)
  }
  function headerProps(key: string, width: number): HTMLAttributes<HTMLElement> {
    const edge = (element: HTMLElement, x: number) => element.getBoundingClientRect().right - x <= 12
    return {
      className: 'business-table-resizable-header',
      onPointerMove: event => { event.currentTarget.style.cursor = edge(event.currentTarget, event.clientX) ? 'col-resize' : '' },
      onPointerLeave: event => { event.currentTarget.style.cursor = '' },
      onPointerDownCapture: event => { if (edge(event.currentTarget, event.clientX)) resize(event, key, width) },
      onClickCapture: event => {
        if (Date.now() < suppressClickUntil.current || edge(event.currentTarget, event.clientX)) { event.preventDefault(); event.stopPropagation() }
      },
    }
  }
  return { widths, headerProps }
}
