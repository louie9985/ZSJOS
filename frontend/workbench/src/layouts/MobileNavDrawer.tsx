import { useEffect, useState } from 'react'
import { Drawer, Menu } from 'antd'
import { NAV_INLINE_INDENT } from '../constants'
import type { PrimaryNavigationItem } from '../services/menu'
import { getNavigationOpenKeys } from '../services/menu'
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
  // 用受控展开键而不是 defaultOpenKeys：defaultOpenKeys 只在挂载时读取一次，
  // 直达深层路由 / 刷新 / 浏览器返回时无法展开完整祖先链。
  const [openKeys, setOpenKeys] = useState<string[]>(() => getNavigationOpenKeys(navigation, activePagePath ?? ''))

  // 每次打开抽屉时，把展开状态同步到当前活动页面的完整祖先链，
  // 保证返回、刷新和直接打开深层路由后当前目录仍被展开。
  useEffect(() => {
    if (open && activePagePath) {
      setOpenKeys(getNavigationOpenKeys(navigation, activePagePath))
    }
  }, [open, activePagePath, navigation])

  // 打开抽屉时锁定背景滚动，关闭后恢复 —— 移动端不能让底层页面跟着抽屉一起滚。
  useEffect(() => {
    if (!open) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previous }
  }, [open])

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
      inlineIndent={NAV_INLINE_INDENT}
      openKeys={openKeys}
      onOpenChange={setOpenKeys}
      selectedKeys={activePagePath ? [activePagePath] : []}
      items={items}
      onClick={({ key }) => { onSelect(String(key)); onClose() }}
    />
  </Drawer>
}
