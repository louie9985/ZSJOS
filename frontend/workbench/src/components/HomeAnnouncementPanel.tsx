import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Skeleton, Typography } from 'antd'
import { NotificationOutlined, PushpinOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { api, ApiError, type Announcement } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'
import { useAnnouncements } from './AnnouncementProvider'
import { useRealtimeEvent } from './RealtimeProvider'

export interface AnnouncementPanelViewProps {
  enabled: boolean
  items: Announcement[]
  loading: boolean
  error: string
  unreadCount: number
  summaryLoading: boolean
  summaryError: string
  hasSummary: boolean
  incoming: boolean
  onRefresh: () => void
  onRefreshSummary: () => void
  onOpen: (id: number) => void
  onAll: () => void
}

export function AnnouncementPanelView(props: AnnouncementPanelViewProps) {
  const root = useRef<HTMLElement>(null)
  useEffect(() => {
    const element = root.current
    if (!element) return
    const visibility = () => { element.dataset.paused = String(document.hidden) }
    visibility()
    document.addEventListener('visibilitychange', visibility)
    // Observe each row: the dashboard panel has its own scroll viewport.
    const observer = new IntersectionObserver(entries => entries.forEach(entry => {
      ;(entry.target as HTMLElement).dataset.offscreen = String(!entry.isIntersecting)
    }))
    element.querySelectorAll('.home-announcement-item, .home-announcement-count, .home-announcement-incoming').forEach(node => observer.observe(node))
    return () => { observer.disconnect(); document.removeEventListener('visibilitychange', visibility) }
  }, [props.items, props.incoming, props.unreadCount, props.loading])

  return <section ref={root} className="home-panel home-announcement-panel" aria-label="公告栏">
    <header className="home-panel-header compact">
      <Typography.Title level={4}><NotificationOutlined /> 公告栏</Typography.Title>
      {props.enabled && <>
        <span className="home-announcement-summary" aria-live="polite">
          {props.hasSummary && props.unreadCount > 0 && <span className="home-announcement-count">未读 {props.unreadCount}</span>}
          {!props.hasSummary && props.summaryLoading && <span>未读数加载中</span>}
        </span>
        <Button type="text" icon={<ReloadOutlined />} aria-label="刷新公告" loading={props.loading} onClick={props.onRefresh} />
      </>}
    </header>
    {props.enabled && props.summaryError && <Alert type="warning" showIcon title={props.hasSummary ? '未读数更新失败，当前显示上次结果' : '未读数暂不可用'} description={props.summaryError} action={<Button size="small" onClick={props.onRefreshSummary}>重试未读数</Button>} />}
    {props.enabled && props.incoming && <button type="button" className="home-announcement-incoming" disabled={props.loading} onClick={props.onRefresh}><NotificationOutlined /> 有新公告 · 点击刷新</button>}
    {props.error && <Alert type="error" showIcon title={props.error} action={<Button size="small" onClick={props.onRefresh}>重试</Button>} />}
    <div className="home-announcement-list">
      {!props.enabled ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公告查看权限" />
        : props.loading ? <Skeleton active paragraph={{ rows: 5 }} />
        : props.items.length ? props.items.map(item => <button type="button" key={item.id} data-announcement-id={item.id}
          className={`home-announcement-item${item.read ? '' : ' unread'}${item.highlighted ? ' highlighted' : ''}`}
          onClick={() => props.onOpen(item.id)}>
          <span className="home-announcement-title-text" title={item.title}>{item.title}</span>
          <span className="home-announcement-meta">
            <span className="home-announcement-tags">
              {item.highlighted && <span className="home-announcement-pin"><PushpinOutlined />置顶</span>}
              {item.read ? <span className="home-announcement-read">已读</span> : <span className="home-announcement-unread"><i aria-hidden="true" />未读</span>}
            </span>
            <time>{formatTimestamp(item.publishTime)}</time>
          </span>
        </button>) : !props.error && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公告" />}
    </div>
    {props.enabled && <Button className="home-announcement-all" type="link" onClick={props.onAll}>查看所有公告 <RightOutlined /></Button>}
  </section>
}

export default function HomeAnnouncementPanel({ enabled }: { enabled: boolean }) {
  const navigate = useNavigate()
  const summary = useAnnouncements()
  const [items, setItems] = useState<Announcement[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')
  const [incoming, setIncoming] = useState(false)
  const generation = useRef(0)
  const publication = useRef(0)
  const load = useCallback(async () => {
    const request = ++generation.current
    const revision = publication.current
    if (!enabled) { setItems([]); setLoading(false); setError(''); setIncoming(false); return }
    setLoading(true)
    setError('')
    try {
      const [page, summaryOk] = await Promise.all([api.announcementPage({ pageNo: 1, pageSize: 5 }), summary.refresh()])
      if (request !== generation.current) return
      setItems(page.list)
      // A publication received during refresh still needs another explicit refresh.
      if (summaryOk && revision === publication.current) setIncoming(false)
    } catch (failure) {
      if (request === generation.current) setError(failure instanceof ApiError && failure.code === 403 ? '暂无权限，请联系管理员配置对应功能权限' : failure instanceof Error ? failure.message : '公告加载失败')
    } finally { if (request === generation.current) setLoading(false) }
  }, [enabled, summary.refresh])
  useEffect(() => { void load(); return () => { generation.current++ } }, [load])
  useRealtimeEvent('notice-published', () => { if (enabled) { publication.current++; setIncoming(true) } })
  return <AnnouncementPanelView enabled={enabled} items={items} loading={loading} error={error}
    unreadCount={summary.unreadCount} summaryLoading={summary.loading} summaryError={summary.error} hasSummary={summary.hasLoaded}
    incoming={incoming} onRefresh={() => void load()} onRefreshSummary={() => void summary.refresh()}
    onOpen={id => navigate(`${APP_ROUTES.ANNOUNCEMENTS}?announcementId=${id}`)} onAll={() => navigate(APP_ROUTES.ANNOUNCEMENTS)} />
}
