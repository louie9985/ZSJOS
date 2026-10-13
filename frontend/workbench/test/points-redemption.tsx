import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, useLocation, useNavigate } from 'react-router-dom'
import { App, Menu } from 'antd'
import RouteHost from '../src/layouts/RouteHost'
import { RENDERABLE_APP_ROUTES, MOBILE_RENDERABLE_APP_ROUTES } from '../src/constants'
import { buildMenuTree } from '../src/services/api'
import { buildTwoLevelNavigation, filterRenderableMenus, findMenuByPath } from '../src/services/menu'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import PointsRedemptionPage from '../src/pages/PointsRedemptionPage'
import PointsPackagePage from '../src/pages/PointsPackagePage'
import { pointsApi, type PointsRequest } from '../src/services/pointsRedemptionApi'
import { http } from '../src/services/api'
import { executeNotifyMessageAction } from '../src/services/notifyMessageAction'
const state = new URLSearchParams(location.search).get('state')
http.defaults.adapter = async () => { throw new Error('Isolated fixture: no live API') }
let rows: PointsRequest[] = []
let reads = 0
let packageReads = 0, uploadCalls = 0
let urged = false, approvalReads = 0
pointsApi.approval = async () => {
  if (state === 'approval-error' && approvalReads++ === 0) throw new Error('审批流程读取失败（模拟）')
  const ended = rows[0]?.approvalStatus !== 'pending'
  return { availability: 'AVAILABLE', canUrge: !ended, nextUrgeAt: urged ? Date.now() + 1800000 : undefined,
    progress: { status: ended ? 2 : 1, currentTasks: ended ? [] : [{ id: 'task-1', name: '上级审批', assigneeName: '测试审批人' }],
      nodes: [{ id: 'node-1', name: '上级审批', status: ended ? 2 : 1, candidates: [], tasks: [{ id: 'task-1', status: ended ? 2 : 1, assigneeName: '测试审批人', createTime: Date.now(), reason: ended ? '同意' : undefined }] }] } }
}
pointsApi.urge = async () => { urged = true; return true }
const uploaded: { id: number; name: string; url: string; type: string; size: number }[] = []
const pageScopes: boolean[] = []
pointsApi.packages = async () => {
  packageReads++
  if (state === 'package-error' && packageReads === 1) throw new Error('套餐加载失败（模拟）')
  return state === 'package-empty' ? [] : [{ id: 1, name: '5000积分套餐', creditsAmount: 5000, validDays: 30, enabled: true }]
}
pointsApi.page = async (_page, management) => { pageScopes.push(management); if (state === 'error' && reads++ === 0) throw new Error('申请加载失败（模拟）'); return { list: rows, total: rows.length } }
pointsApi.upload = async file => {
  uploadCalls++
  if (state === 'upload-retry' && file.name === '重试.pdf' && uploadCalls === 2) throw new Error('上传失败（模拟）')
  const result = { id: uploadCalls + 8, name: file.name, url: new URL('/test/points-attachment.pdf', location.origin).href, type: file.type, size: file.size }
  uploaded.push(result); return result
}
pointsApi.create = async data => { if (!data.attachmentIds.length) throw new Error('附件缺失'); rows = [{ id: 1, requestNo: 'JFTEST001', applicantName: '测试员工', packageName: '5000积分套餐', creditsAmount: 5000, validDays: 30,
  reason: data.reason, approvalStatus: 'pending', issueStatus: 'waiting', processInstanceId: 'process', canReveal: false, submittedAt: Date.now(), attachments: uploaded.filter(a => data.attachmentIds.includes(a.id)) }]; return 1 }
pointsApi.get = async id => { const row = rows.find(r => r.id === id); if (!row) throw new Error('申请不存在'); return { ...row } }
pointsApi.reveal = async () => 'KM-ISOLATED-TEST-CODE-0123456789'
pointsApi.action = async (_id, action) => { if (action === 'cancel') rows[0].approvalStatus = 'cancelled'; return true }
Object.assign(window, { approvePoints: () => { rows[0].approvalStatus = 'approved'; rows[0].issueStatus = 'issued'; rows[0].codeStatus = 'available'; rows[0].canReveal = true } })
Object.assign(window, { pointsFixtureStats: () => ({ uploadCalls, packageReads, pageScopes }) })
Object.assign(window, { testPointsNotification: async (id: number) => {
  const routes: string[] = [], warnings: string[] = []
  await executeNotifyMessageAction({ id: 5, bizId: id, bizType: 'points-redemption', actionType: 'business_detail', readStatus: true,
    templateNickname: '', templateContent: '', templateType: 1, createTime: Date.now() },
    { navigate: ((path: string) => { routes.push(path) }) as never, warn: text => { warnings.push(text) }, refreshUnreadCount: async () => {} })
  return { routes, warnings }
} })
const permissions = state === 'denied' ? [] : state === 'query-only' ? ['zsjos:points-redemption:query']
  : state === 'create-only' ? ['zsjos:points-redemption:query', 'zsjos:points-redemption:create']
  : state === 'packages-readonly' ? ['zsjos:points-redemption:package'] : ['*:*:*']
function PointsEntryFixture() {
  const location = useLocation(), navigate = useNavigate()
  const mobile = window.innerWidth < 768
  const menus = filterRenderableMenus(buildMenuTree(state === 'navigation-denied' ? [] : [{
    id: 6735, parentId: 0, name: '工作台', path: '/zsjos', visible: true, children: [
      { id: 602348, parentId: 6735, name: '积分兑换码', path: 'points-redemption', component: 'zsjos-workbench', visible: true, workbenchRenderMode: 'native' },
      { id: 602349, parentId: 6735, name: '积分套餐', path: 'points-packages', component: 'zsjos/pointsPackages/index', visible: true, workbenchRenderMode: 'native' }
    ]
  }]), mobile ? MOBILE_RENDERABLE_APP_ROUTES : RENDERABLE_APP_ROUTES)
  const pages = buildTwoLevelNavigation(menus)[0]?.pages || []
  const current = findMenuByPath(menus, location.pathname)
  return <><Menu aria-label="工作台导航" mode="inline" items={pages.map(p => ({ key: p.key, label: p.label }))}
    onClick={({ key }) => navigate(key)} selectedKeys={[location.pathname]} />
    {current ? <RouteHost menu={current} permissions={permissions} roles={[]} authPlatform={mobile ? 'MOBILE' : 'PC'} onOpenAssignment={() => {}} /> : <p>请选择已授权的菜单</p>}</>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter>{state?.startsWith('navigation') ? <PointsEntryFixture/> : state?.startsWith('packages') ? <PointsPackagePage permissions={permissions}/> : <PointsRedemptionPage permissions={permissions}/>}</BrowserRouter></App></ThemeProvider>)
