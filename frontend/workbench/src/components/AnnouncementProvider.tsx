import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { api, type Announcement, type AnnouncementUnreadSummary } from '../services/api'
import { useRealtimeEvent } from './RealtimeProvider'

type AnnouncementContextValue = AnnouncementUnreadSummary & {
  loading: boolean
  error: string
  hasLoaded: boolean
  refresh: () => Promise<boolean>
  markRead: (announcement: Announcement) => Promise<void>
}

const AnnouncementContext = createContext<AnnouncementContextValue | null>(null)

export function AnnouncementProvider({ enabled, children }: PropsWithChildren<{ enabled: boolean }>) {
  const [summary, setSummary] = useState<AnnouncementUnreadSummary>({ unreadCount: 0 })
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')
  const [hasLoaded, setHasLoaded] = useState(false)
  const requestVersion = useRef(0)

  const refresh = useCallback(async () => {
    const request = ++requestVersion.current
    if (!enabled) { setSummary({ unreadCount: 0 }); setHasLoaded(false); setError(''); setLoading(false); return false }
    setLoading(true)
    try {
      const next = await api.announcementUnreadSummary()
      // Reading and publication can refresh concurrently; an older response must not restore a stale count.
      if (request !== requestVersion.current) return false
      setSummary(next)
      setHasLoaded(true)
      setError('')
      return true
    } catch (loadError) {
      if (request === requestVersion.current) setError(loadError instanceof Error ? loadError.message : '公告加载失败')
      return false
    } finally {
      if (request === requestVersion.current) setLoading(false)
    }
  }, [enabled])

  const markRead = useCallback(async (announcement: Announcement) => {
    if (!announcement.read) await api.markAnnouncementRead(announcement.id)
    await refresh()
  }, [refresh])

  useEffect(() => { void refresh(); return () => { requestVersion.current++ } }, [refresh])
  useRealtimeEvent('notice-published', () => { void refresh() })

  const value = useMemo(() => ({ ...summary, loading, error, hasLoaded, refresh, markRead }),
    [error, hasLoaded, loading, markRead, refresh, summary])
  return <AnnouncementContext.Provider value={value}>{children}</AnnouncementContext.Provider>
}

export function useAnnouncements() {
  const context = useContext(AnnouncementContext)
  if (!context) throw new Error('useAnnouncements must be used inside AnnouncementProvider')
  return context
}
