import { useEffect } from 'react'

export function useCalendarHighlight(id: number | undefined, records: unknown) {
  useEffect(() => {
    if (id === undefined) return
    let observer: MutationObserver | undefined
    const reveal = () => {
      const element = Array.from(document.querySelectorAll<HTMLElement>('[data-calendar-located="true"]')).find(node => node.getClientRects().length > 0)
      if (!element) return false
      element.scrollIntoView({ block: 'center', inline: 'nearest' })
      observer?.disconnect()
      return true
    }
    // Modals mount their contents after the parent state update.
    if (!reveal()) { observer = new MutationObserver(reveal); observer.observe(document.body, { childList: true, subtree: true }) }
    return () => observer?.disconnect()
  }, [id, records])
}
