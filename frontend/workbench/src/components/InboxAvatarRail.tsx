import { useLayoutEffect, useRef, useState } from 'react'
import { Button, Tooltip } from 'antd'
import { FilterOutlined, LeftOutlined, MenuFoldOutlined, MenuUnfoldOutlined, ReloadOutlined, RightOutlined, WarningOutlined } from '@ant-design/icons'

/** Keeps layout preference separate from data loading and preserves each presentation's scroll position. */
export function useInboxAvatarRail(storageKey: string) {
  const [collapsed, setCollapsed] = useState(() => {
    try { return localStorage.getItem(storageKey) === 'true' } catch { return false }
  })
  const scrollRef = useRef<HTMLDivElement>(null)
  const filterRef = useRef<HTMLDivElement>(null)
  const focusFilter = useRef(false)
  const positions = useRef({ expanded: { top: 0, left: 0 }, collapsed: { top: 0, left: 0 } })
  const change = (next: boolean, focus = false) => {
    const node = scrollRef.current
    if (node) positions.current[collapsed ? 'collapsed' : 'expanded'] = { top: node.scrollTop, left: node.scrollLeft }
    focusFilter.current = focus
    setCollapsed(next)
    try { localStorage.setItem(storageKey, String(next)) } catch { /* Browser storage is optional. */ }
  }
  useLayoutEffect(() => {
    scrollRef.current?.scrollTo(positions.current[collapsed ? 'collapsed' : 'expanded'])
    if (!collapsed && focusFilter.current) {
      filterRef.current?.querySelector<HTMLInputElement>('.advanced-filter-toolbar input')?.focus()
      focusFilter.current = false
    }
  }, [collapsed])
  useLayoutEffect(() => {
    const scroll = scrollRef.current
    if (!scroll) return
    // Classic scrollbars consume width while overlay scrollbars do not; align to the actual cards.
    const alignToolbar = () => scroll.parentElement?.style.setProperty('--inbox-scrollbar-width', `${scroll.offsetWidth - scroll.clientWidth}px`)
    alignToolbar()
    const observer = new ResizeObserver(alignToolbar)
    observer.observe(scroll)
    return () => observer.disconnect()
  })
  return { collapsed, change, scrollRef, filterRef }
}

export function InboxAvatarControls({ label, listId, collapsed, filtered, onChange }: {
  label: string; listId: string; collapsed: boolean; filtered: boolean; onChange: (collapsed: boolean, focus?: boolean) => void
}) {
  const title = `${collapsed ? '展开' : '收起'}${label}列表`
  return <div className="inbox-avatar-controls">
    <Tooltip title={title}><Button size={collapsed ? 'small' : 'middle'} type={collapsed ? 'text' : 'default'} aria-label={title} aria-expanded={!collapsed} aria-controls={listId} icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={() => onChange(!collapsed)} /></Tooltip>
    {collapsed && <Tooltip title={filtered ? '搜索与筛选（已筛选）' : '搜索与筛选'}><Button aria-label={`搜索与筛选${label}`} size="small" type={filtered ? 'primary' : 'text'} icon={<FilterOutlined />} onClick={() => onChange(false, true)} /></Tooltip>}
  </div>
}

export function InboxAvatarError({ message, retry, expand }: { message: string; retry?: () => void; expand: () => void }) {
  return <div className="inbox-avatar-error"><Tooltip title={message}><Button aria-label="查看列表错误" danger icon={<WarningOutlined />} onClick={expand} /></Tooltip>
    {retry && <Tooltip title="重试加载"><Button aria-label="重试加载列表" icon={<ReloadOutlined />} onClick={retry} /></Tooltip>}
  </div>
}

export function InboxAvatarPagination({ page, total, pageSize, loading, onChange }: { page: number; total: number; pageSize: number; loading: boolean; onChange: (page: number) => void }) {
  const pages = Math.ceil(total / pageSize)
  return <nav className="inbox-avatar-pagination" aria-label="学员分页">
    <Tooltip title="上一页"><Button aria-label="上一页学员" icon={<LeftOutlined />} disabled={loading || page <= 1} onClick={() => onChange(page - 1)} /></Tooltip>
    <span aria-label={`第 ${page} 页，共 ${pages} 页`}>{page}/{pages}</span>
    <Tooltip title="下一页"><Button aria-label="下一页学员" icon={<RightOutlined />} disabled={loading || page >= pages} onClick={() => onChange(page + 1)} /></Tooltip>
  </nav>
}
