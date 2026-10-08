import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Skeleton, Typography } from 'antd'
import { NotificationOutlined, PushpinOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { api, ApiError, type Announcement } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'
import { useAnnouncements } from './AnnouncementProvider'
import { useRealtimeEvent } from './RealtimeProvider'

import { noticeAudienceText } from '../services/noticeManagement'

const PAGE_SIZE = 10

export interface AnnouncementPanelViewProps {
  enabled: boolean
  items: Announcement[]
  loading: boolean
  error: string
  hasMore?: boolean
  loadingMore?: boolean
  moreError?: string
  onLoadMore?: () => void
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
        <div className="home-announcement-actions">
          <Button className="home-announcement-all" type="link" size="small" onClick={props.onAll}>查看所有公告 <RightOutlined /></Button>
          <Button type="text" icon={<ReloadOutlined />} aria-label="刷新公告" loading={props.loading} onClick={props.onRefresh} />
        </div>
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
          <span className="notice-origin-meta">
            <span>文章来源：{item.sourceDeptName || '未记录'}</span><span>发布人：{item.publisherName || '未记录'}</span>
            <span>接收部门/人员：{noticeAudienceText(item)}</span>
          </span>
          <span className="home-announcement-meta">
            <span className="home-announcement-tags">
              {item.highlighted && <span className="home-announcement-pin"><PushpinOutlined />置顶</span>}
              {item.read ? <span className="home-announcement-read">已读</span> : <span className="home-announcement-unread"><i aria-hidden="true" />未读</span>}
            </span>
            <time>{formatTimestamp(item.publishTime)}</time>
          </span>
        </button>) : !props.error && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公告" />}
      {props.enabled && !props.loading && !props.error && props.items.length > 0 && <div className="home-announcement-pagination" aria-live="polite">
        {props.moreError && <Alert type="error" showIcon title={props.moreError} />}
        {props.hasMore ? <Button block loading={props.loadingMore} onClick={props.onLoadMore}>
          {props.moreError ? '重试加载' : '加载更多'}
        </Button> : <Typography.Text type="secondary">已加载全部公告</Typography.Text>}
      </div>}
    </div>
  </section>
}

export default function HomeAnnouncementPanel({ enabled }: { enabled: boolean }) {
  const navigate = useNavigate()
  const summary = useAnnouncements()
  const [items, setItems] = useState<Announcement[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState('')
  const [incoming, setIncoming] = useState(false)
  const generation = useRef(0)
  const publication = useRef(0)
  const pageNo = useRef(0)
  const busy = useRef(false)
  const load = useCallback(async () => {
    const request = ++generation.current
    const revision = publication.current
    pageNo.current = 0
    setHasMore(false)
    setLoadingMore(false)
    setMoreError('')
    busy.current = enabled
    if (!enabled) { setItems([]); setLoading(false); setError(''); setIncoming(false); return }
    setLoading(true)
    setError('')
    try {
      const [page, summaryOk] = await Promise.all([api.announcementPage({ pageNo: 1, pageSize: PAGE_SIZE }), summary.refresh()])
      if (request !== generation.current) return
      setItems(page.list)
      pageNo.current = 1
      setHasMore(page.list.length > 0 && PAGE_SIZE < page.total)
      // A publication received during refresh still needs another explicit refresh.
      if (summaryOk && revision === publication.current) setIncoming(false)
    } catch (failure) {
      if (request === generation.current) setError(failure instanceof ApiError && failure.code === 403 ? '暂无权限，请联系管理员配置对应功能权限' : failure instanceof Error ? failure.message : '公告加载失败')
    } finally { if (request === generation.current) { setLoading(false); busy.current = false } }
  }, [enabled, summary.refresh])
  const loadMore = async () => {
    if (!enabled || busy.current || !hasMore) return
    busy.current = true
    const request = generation.current
    const nextPage = pageNo.current + 1
    setLoadingMore(true)
    setMoreError('')
    try {
      const page = await api.announcementPage({ pageNo: nextPage, pageSize: PAGE_SIZE })
      if (request !== generation.current) return
      // A newly published/pinned notice can shift page boundaries; do not render duplicate rows.
      setItems(previous => {
        const ids = new Set(previous.map(item => item.id))
        return [...previous, ...page.list.filter(item => !ids.has(item.id))]
      })
      pageNo.current = nextPage
      setHasMore(page.list.length > 0 && nextPage * PAGE_SIZE < page.total)
    } catch (failure) {
      if (request === generation.current) setMoreError(failure instanceof ApiError && failure.code === 403 ? '暂无权限，请联系管理员配置对应功能权限' : failure instanceof Error ? failure.message : '更多公告加载失败')
    } finally {
      if (request === generation.current) { setLoadingMore(false); busy.current = false }
    }
  }
  useEffect(() => { void load(); return () => { generation.current++ } }, [load])
  useRealtimeEvent('notice-published', () => { if (enabled) { publication.current++; setIncoming(true) } })
  return <AnnouncementPanelView enabled={enabled} items={items} loading={loading} error={error}
    hasMore={hasMore} loadingMore={loadingMore} moreError={moreError} onLoadMore={() => void loadMore()}
    unreadCount={summary.unreadCount} summaryLoading={summary.loading} summaryError={summary.error} hasSummary={summary.hasLoaded}
    incoming={incoming} onRefresh={() => void load()} onRefreshSummary={() => void summary.refresh()}
    onOpen={id => navigate(`${APP_ROUTES.ANNOUNCEMENTS}?announcementId=${id}`)} onAll={() => navigate(APP_ROUTES.ANNOUNCEMENTS)} />
}
