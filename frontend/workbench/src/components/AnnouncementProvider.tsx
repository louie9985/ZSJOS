import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, type Announcement, type AnnouncementUnreadSummary } from '../services/api'
import { useRealtimeEvent } from './RealtimeProvider'

type AnnouncementContextValue = AnnouncementUnreadSummary & {
  loading: boolean
  error: string
  refresh: () => Promise<void>
  markRead: (announcement: Announcement) => Promise<void>
}

const AnnouncementContext = createContext<AnnouncementContextValue | null>(null)

export function AnnouncementProvider({ enabled, children }: PropsWithChildren<{ enabled: boolean }>) {
  const [summary, setSummary] = useState<AnnouncementUnreadSummary>({ unreadCount: 0 })
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')

  const refresh = useCallback(async () => {
    if (!enabled) { setSummary({ unreadCount: 0 }); setLoading(false); return }
    setLoading(true)
    try {
      setSummary(await api.announcementUnreadSummary())
      setError('')
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '公告加载失败')
    } finally {
      setLoading(false)
    }
  }, [enabled])

  const markRead = useCallback(async (announcement: Announcement) => {
    if (!announcement.read) await api.markAnnouncementRead(announcement.id)
    await refresh()
  }, [refresh])

  useEffect(() => { void refresh() }, [refresh])
  useRealtimeEvent('notice-published', () => { void refresh() })

  const value = useMemo(() => ({ ...summary, loading, error, refresh, markRead }),
    [error, loading, markRead, refresh, summary])
  return <AnnouncementContext.Provider value={value}>{children}</AnnouncementContext.Provider>
}

export function useAnnouncements() {
  const context = useContext(AnnouncementContext)
  if (!context) throw new Error('useAnnouncements must be used inside AnnouncementProvider')
  return context
}
