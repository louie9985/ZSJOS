import { Drawer, Menu } from 'antd'
import type { PrimaryNavigationItem } from '../services/menu'
import { buildNavMenuItems } from './navItems'

interface MobileNavDrawerProps {
  open: boolean
  onClose: () => void
  navigation: PrimaryNavigationItem[]
  activePrimaryKey?: string
  activePagePath?: string
  onSelect: (path: string) => void
}

/**
 * 移动端二级菜单入口。
 *
 * 桌面端二级菜单在 .secondary-sider 里，但移动端该元素被 CSS 隐藏（display:none），
 * 导致用户无法切换二级页面。本抽屉提供替代入口：一级用 SubMenu 展开，二级为叶子项。
 */
export default function MobileNavDrawer({ open, onClose, navigation, activePrimaryKey, activePagePath, onSelect }: MobileNavDrawerProps) {
  const items = buildNavMenuItems(navigation)

  return <Drawer
    open={open}
    onClose={onClose}
    placement="left"
    size={260}
    title="导航菜单"
    styles={{ body: { padding: 0 } }}
  >
    <Menu
      mode="inline"
      defaultOpenKeys={activePrimaryKey ? [activePrimaryKey] : []}
      selectedKeys={activePagePath ? [activePagePath] : []}
      items={items}
      onClick={({ key }) => { onSelect(String(key)); onClose() }}
    />
  </Drawer>
}
