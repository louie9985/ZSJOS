import { Tabs } from 'antd'
import { useCallback, useEffect, useRef, type Dispatch, type SetStateAction } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import type { WorkbenchMenu } from '../services/api'
import type { TabStyle } from '../constants'

export interface TabItem {
  key: string
  label: string
  closable: boolean
}

export const MAX_TABS = 15

export function appendMenuTab(tabs: TabItem[], currentMenu?: WorkbenchMenu): TabItem[] {
  if (!currentMenu || tabs.some(tab => tab.key === currentMenu.path)) return tabs
  const next = [
    ...tabs,
    { key: currentMenu.path, label: currentMenu.name, closable: tabs.length > 0 }
  ]
  if (next.length > MAX_TABS) {
    const index = next.findIndex(tab => tab.closable && tab.key !== currentMenu.path)
    if (index >= 0) next.splice(index, 1)
  }
  return next
}

/**
 * 顶部页签栏：记录打开过的页面，支持切换和关闭。
 * 支持 4 种显示样式：card / line / pill / flat。
 */
const TabBar: React.FC<{
  currentMenu?: WorkbenchMenu
  initialPath?: string
  tabStyle?: TabStyle
  tabs: TabItem[]
  setTabs: Dispatch<SetStateAction<TabItem[]>>
}> = ({ currentMenu, initialPath, tabStyle = 'card', tabs, setTabs }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const initialPathRef = useRef(initialPath)

  // 当前页面进入 tabs
  useEffect(() => {
    setTabs(prev => appendMenuTab(prev, currentMenu))
  }, [currentMenu, setTabs])

  // 第一个 tab 设为不可关闭
  useEffect(() => {
    setTabs((prev) => {
      if (prev.length === 1 && prev[0].closable) {
        return [{ ...prev[0], closable: false }]
      }
      if (prev.length > 1 && !prev[0].closable === false) {
        return prev.map((t, i) => i === 0 ? { ...t, closable: false } : { ...t, closable: true })
      }
      return prev
    })
  }, [tabs.length])

  const activeKey = location.pathname

  const onChange = useCallback((key: string) => {
    navigate(key)
  }, [navigate])

  const onEdit = useCallback((targetKey: React.MouseEvent | React.KeyboardEvent | string, action: 'add' | 'remove') => {
    if (action !== 'remove' || typeof targetKey !== 'string') return
    setTabs((prev) => {
      const idx = prev.findIndex((t) => t.key === targetKey)
      if (idx < 0) return prev
      const next = prev.filter((t) => t.key !== targetKey)
      // 如果关闭的是当前 tab，跳转到相邻 tab
      if (targetKey === activeKey && next.length > 0) {
        const newActive = next[Math.min(idx, next.length - 1)]
        navigate(newActive.key)
      }
      return next
    })
  }, [activeKey, navigate])

  if (tabs.length === 0) return null

  // antd Tabs type 映射
  const antdType = tabStyle === 'card' ? 'editable-card' : 'editable-card'
  const styleClass = `crm-tab-bar tab-style-${tabStyle}`

  return (
    <div className={styleClass}>
      <Tabs
        type={antdType}
        hideAdd
        activeKey={activeKey}
        onChange={onChange}
        onEdit={onEdit}
        size="small"
        items={tabs.map((t) => ({
          key: t.key,
          label: t.label,
          closable: t.closable
        }))}
      />
    </div>
  )
}

export default TabBar
