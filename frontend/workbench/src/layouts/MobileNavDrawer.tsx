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

/** 移动端导航保留服务端目录层级，通过 SubMenu 展开二级和更深层页面。 */
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
