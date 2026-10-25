import { useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Badge, ConfigProvider } from 'antd'
import MenuTaskBadgeProvider, { useMenuTaskBadges } from '../src/components/MenuTaskBadgeProvider'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import ApprovalNavigationMenu from '../src/layouts/ApprovalNavigationMenu'
import MobileNavDrawer from '../src/layouts/MobileNavDrawer'
import { buildHierarchicalSecondaryItems } from '../src/layouts/navItems'
import { buildTwoLevelNavigation } from '../src/services/menu'
import { api, ApiError, type WorkbenchMenu } from '../src/services/api'
import { createNavigationBadgeResolver } from '../src/services/navigationUnread'
import { invalidateMenuTasks } from '../src/services/menuTaskRefresh'
import { BUSINESS_APPROVAL_PROCESS_KEYS } from '../src/services/approvalNavigation'
import '../src/styles/index.css'

// Isolated transport; no real account or business mutations.
const state = { count: 7, failure: 0, calls: 0, delay: 0 }
Object.assign(window, { workflow: state })
api.menuTaskSummary = async () => ({ generatedAt: Date.now(), total: 0, items: [] })
api.bpmTaskPage = async (view, params) => {
  if (view !== 'todo' || params.excludeProcessDefinitionKeys !== BUSINESS_APPROVAL_PROCESS_KEYS) throw Error('Wrong approval scope')
  state.calls++
  const { count, failure, delay } = state
  if (delay) await new Promise(resolve => setTimeout(resolve, delay))
  if (failure) throw new ApiError(failure, 'isolated failure')
  return { total: count, list: [] }
}
const menus = [{ id: 1, name: '工作流程', path: '/flow', children: [{ id: 2, name: '审批中心', path: '/approval', children: [
  { id: 3, name: '待办任务', path: '/bpm/task/todo', children: [] },
  { id: 4, name: '已办任务', path: '/bpm/task/done', children: [] }
] }] }] as WorkbenchMenu[]
const navigation = buildTwoLevelNavigation(menus)
function View() {
  const { resolve } = useMenuTaskBadges()
  const badge = useMemo(() => createNavigationBadgeResolver(resolve, { announcement: 0, message: 0 }, menus), [resolve])
  const [open, setOpen] = useState(false)
  return <main style={{ width: 300, padding: 12 }}>
    <button className="primary-nav-item">工作流程<Badge className="menu-task-badge primary-nav-badge" count={badge('/flow')?.count}/></button>
    <section className={window.innerWidth > 768 ? "secondary-sider" : undefined} data-testid="secondary"><ApprovalNavigationMenu menus={menus[0].children} activePath="/calendar" mode="inline" items={buildHierarchicalSecondaryItems(menus[0], { badgeResolver: badge })}/></section>
    <button onClick={() => invalidateMenuTasks(true)}>处理后刷新</button>
    <button onClick={() => setOpen(true)}>移动导航</button>
    <MobileNavDrawer open={open} onClose={() => setOpen(false)} navigation={navigation} activePagePath="/calendar" onSelect={() => {}} badgeResolver={badge}/>
  </main>
}
function Fixture() {
  const [enabled, setEnabled] = useState(true)
  return <ConfigProvider><RealtimeProvider platform="PC"><button onClick={() => setEnabled(value => !value)}>切换权限</button><MenuTaskBadgeProvider bpmEnabled={enabled}><View/></MenuTaskBadgeProvider></RealtimeProvider></ConfigProvider>
}
createRoot(document.getElementById('root')!).render(<Fixture/> )
