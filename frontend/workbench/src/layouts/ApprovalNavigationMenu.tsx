import { useEffect, useState } from 'react'
import { Menu, type MenuProps } from 'antd'
import type { WorkbenchMenu } from '../services/api'
import { approvalOpenPaths } from '../services/approvalNavigation'

/** 进入目录或切换路由时展开审批与当前页祖先；用户仍可手动收起。 */
export default function ApprovalNavigationMenu({ menus, activePath, ...props }: MenuProps & { menus: WorkbenchMenu[]; activePath: string }) {
  const [openKeys, setOpenKeys] = useState(() => props.inlineCollapsed ? [] : approvalOpenPaths(menus, activePath))
  useEffect(() => { setOpenKeys(props.inlineCollapsed ? [] : approvalOpenPaths(menus, activePath)) }, [menus, activePath, props.inlineCollapsed])
  return <Menu {...props} openKeys={openKeys} onOpenChange={setOpenKeys}/>
}
