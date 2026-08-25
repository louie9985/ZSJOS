import { Drawer, Menu, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import type { PrimaryNavigationItem, SecondaryNavigationItem } from '../services/menu'
import BackendMenuIcon from './BackendMenuIcon'

interface MobileNavDrawerProps {
  open: boolean
  onClose: () => void
  navigation: PrimaryNavigationItem[]
  activePrimaryKey?: string
  activePagePath?: string
  onSelect: (path: string) => void
}

/**
 * 移动端用两个平级列表展示一级分类与二级叶子页面，不产生折叠或下拉菜单。
 */
export default function MobileNavDrawer({ open, onClose, navigation, activePrimaryKey, activePagePath, onSelect }: MobileNavDrawerProps) {
  const [primaryKey, setPrimaryKey] = useState(activePrimaryKey)
  useEffect(() => { setPrimaryKey(activePrimaryKey) }, [activePrimaryKey, open])
  const primary = useMemo(
    () => navigation.find(item => item.key === primaryKey) || navigation[0],
    [navigation, primaryKey]
  )
  const flattenPages = (items: SecondaryNavigationItem[]): SecondaryNavigationItem[] => items.flatMap(item =>
    item.children.length > 0 ? flattenPages(item.children) : [item]
  )
  const primaryItems = navigation.map(item => ({
    key: item.key,
    label: item.label,
    icon: <BackendMenuIcon icon={item.icon}/>
  }))
  const secondaryItems = flattenPages(primary?.pages || []).map(item => ({
    key: item.menu.path,
    label: item.label,
    icon: <BackendMenuIcon icon={item.icon}/>
  }))

  return <Drawer
    open={open}
    onClose={onClose}
    placement="left"
    size={260}
    title="导航菜单"
    styles={{ body: { padding: 0 } }}
  >
    <Typography.Text type="secondary" className="mobile-nav-section-title">一级分类</Typography.Text>
    <Menu
      mode="inline"
      selectedKeys={primary ? [primary.key] : []}
      items={primaryItems}
      onClick={({ key }) => {
        const selected = navigation.find(item => item.key === String(key))
        setPrimaryKey(String(key))
        if (selected?.pages.length === 0) { onSelect(selected.menu.path); onClose() }
      }}
    />
    <Typography.Text type="secondary" className="mobile-nav-section-title">{primary?.label || '二级菜单'}</Typography.Text>
    <Menu
      mode="inline"
      selectedKeys={activePagePath ? [activePagePath] : []}
      items={secondaryItems}
      onClick={({ key }) => { onSelect(String(key)); onClose() }}
    />
  </Drawer>
}
