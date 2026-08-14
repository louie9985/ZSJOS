import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Alert,
  App,
  Badge,
  Breadcrumb,
  Button,
  Card,
  Dropdown,
  Input,
  Layout,
  Menu,
  Result,
  Space,
  Tooltip,
  Typography,
  theme
} from 'antd'
import type { MenuProps } from 'antd'
import {
  AppstoreOutlined,
  InboxOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RobotOutlined
} from '@ant-design/icons'
import { Icon, loadIcon } from '@iconify/react'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { api, AuthenticationError, buildMenuTree, clearAuthStorage, type PermissionInfo, type WorkbenchMenu } from './services/api'
import {
  buildTwoLevelNavigation,
  filterRenderableMenus,
  findPageByPath,
  findPrimaryByPath,
  getInaccessiblePathFallback,
  getInitialTarget,
  getPrimaryTarget,
  type PrimaryNavigationItem,
  type SecondaryNavigationItem
} from './services/menu'
import { resolveWorkbenchComponent, WORKBENCH_COMPONENT } from './services/menuComponentRegistry'
import LeadSubmissionPage from './pages/LeadSubmissionPage'
import LeadManagementPage from './pages/LeadManagementPage'
import LeadAssignmentPage from './pages/LeadAssignmentPage'
import LeadClaimPoolPage from './pages/LeadClaimPoolPage'
import LeadAgingPoolPage from './pages/LeadAgingPoolPage'
import TodayTasksPage from './pages/TodayTasksPage'
import WorkPlanPage from './pages/WorkPlanPage'
import LeadQualificationExceptionPage from './pages/LeadQualificationExceptionPage'
import LeadDuplicateReviewPage from './pages/LeadDuplicateReviewPage'
import LeadAssignmentHost from './components/LeadAssignmentHost'
import { OverlayCoordinatorProvider } from './components/OverlayCoordinator'
import { RealtimeProvider } from './components/RealtimeProvider'
import { NotifyMessageProvider } from './components/NotifyMessageProvider'
import MessageCenter from './components/MessageCenter'
import MessageInboxPage from './pages/MessageInboxPage'
import LeadAppealPage from './pages/LeadAppealPage'
import SalesOrderApprovalPage from './pages/SalesOrderApprovalPage'
import MySalesOrderPage from './pages/MySalesOrderPage'
import SubordinateSalesPage from './pages/SubordinateSalesPage'
import LeadComplaintPage from './pages/LeadComplaintPage'
import ExternalRepurchasePage from './pages/ExternalRepurchasePage'
import { LeadFilterConfigPage, LeadFollowUpRuleConfigPage, LeadRuleConfigPage, ProductConfigPage, WorkPlanConfigPage } from './pages/ConfigurationPages'
import SalesDispatchStatusControl from './components/SalesDispatchStatusControl'
import EmployeeAvatar, { DefaultEmployeeAvatarProvider } from './components/EmployeeAvatar'
import { APP_ROUTES, RENDERABLE_APP_ROUTES, STORAGE_KEYS } from './constants'
import {
  clearLoginFormCache,
  loadLoginFormCache,
  saveLoginFormCache
} from './services/loginFormCache'
import ThemeProvider from './components/Theme/ThemeProvider'
import ThemeSwitcher from './components/Theme/ThemeSwitcher'
import { useTheme } from './components/Theme/ThemeContext'
import './styles.css'

const { Sider, Header, Content } = Layout
type MenuItem = Required<MenuProps>['items'][number]

class RuntimeBoundary extends React.Component<React.PropsWithChildren, { error?: Error }> {
  state: { error?: Error } = {}

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  render() {
    return this.state.error
      ? <div className="center-page"><Card title="员工工作台加载失败"><Alert type="error" showIcon message={this.state.error.message}/><Button type="primary" onClick={() => location.reload()}>重新加载</Button></Card></div>
      : this.props.children
  }
}

function Login({ onLogin, initialError = '' }: { onLogin: () => void; initialError?: string }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => setError(initialError), [initialError])

  const login = async () => {
    setLoading(true)
    setError('')
    try {
      const platform = window.location.pathname.startsWith('/zsjos/mobile') ? 'MOBILE' : 'PC'
      await api.login(username, password, platform)
      onLogin()
    } catch (loginError: any) {
      setError(loginError.response?.data?.msg || loginError.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return <div className="login-page"><Card className="login-card">
    <div className="login-mark">ZSJOS</div>
    <Typography.Title level={2}>员工工作台</Typography.Title>
    <Typography.Paragraph type="secondary">统一账号登录</Typography.Paragraph>
    {error && <Alert className="form-alert" type="error" showIcon message={error}/>} 
    <Input placeholder="用户名" size="large" value={username} onChange={event => setUsername(event.target.value)} className="login-input"/>
    <Input.Password placeholder="密码" size="large" value={password} onChange={event => setPassword(event.target.value)} onPressEnter={login} className="login-input"/>
    <Button type="primary" block size="large" loading={loading} onClick={login}>登录</Button>
  </Card></div>
}

function BackendMenuIcon({ icon, className }: { icon?: string; className?: string }) {
  const iconName = icon?.trim()
  const isValidName = Boolean(iconName && /^[a-z0-9-]+:[a-z0-9-]+$/i.test(iconName))
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    let active = true
    setLoaded(false)
    if (!isValidName || !iconName) return () => { active = false }
    loadIcon(iconName)
      .then(() => { if (active) setLoaded(true) })
      .catch(() => { if (active) setLoaded(false) })
    return () => { active = false }
  }, [iconName, isValidName])

  return loaded && iconName
    ? <Icon className={className} icon={iconName} width="1em" height="1em"/>
    : <AppstoreOutlined className={className}/>
}

function toPrimaryItems(items: PrimaryNavigationItem[]): MenuItem[] {
  return items.map(item => ({
    key: item.key,
    label: item.label,
    title: item.label,
    icon: <BackendMenuIcon icon={item.icon}/>
  }))
}

function toSecondaryItems(items: SecondaryNavigationItem[]): MenuItem[] {
  return items.map(item => ({
    key: item.key,
    label: item.label,
    title: item.label,
    icon: <BackendMenuIcon icon={item.icon}/>
  }))
}

function Placeholder({ menu, permissions, onOpenAssignment }: { menu?: WorkbenchMenu; permissions: string[]; onOpenAssignment: () => void }) {
  if (resolveWorkbenchComponent(menu?.component) === WORKBENCH_COMPONENT.LEAD_APPEAL) return <LeadAppealPage/>
  if (resolveWorkbenchComponent(menu?.component) === WORKBENCH_COMPONENT.SUBORDINATE_SALES) return <SubordinateSalesPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_SUBMISSION) return <LeadSubmissionPage/>
  if (menu?.path === APP_ROUTES.LEAD_SELF_SOURCED) return <LeadSubmissionPage selfSourced/>
  if (menu?.path === APP_ROUTES.LEAD_COMPLAINTS) return <LeadComplaintPage/>
  if (menu?.path === APP_ROUTES.SUBMITTED_LEADS) return <LeadManagementPage audience="submitter"/>
  if (menu?.path === APP_ROUTES.OWNED_LEADS) return <LeadManagementPage audience="owner"/>
  if (menu?.path === APP_ROUTES.LEAD_ASSIGNMENT) return <LeadAssignmentPage/>
  if (menu?.path === APP_ROUTES.LEAD_DUPLICATE_REVIEW) return <LeadDuplicateReviewPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_CLAIM_POOL) {
    return <LeadClaimPoolPage canClaim={permissions.includes('zsjos:lead:claim')}/>
  }
  if (menu?.path === APP_ROUTES.LEAD_AGING_POOL) return <LeadAgingPoolPage/>
  if (menu?.path === APP_ROUTES.SUBORDINATE_SALES) return <SubordinateSalesPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.TODAY_TASKS) return <TodayTasksPage permissions={permissions} onOpenAssignment={onOpenAssignment}/>
  if (menu?.path === APP_ROUTES.WORK_PLANS) return <WorkPlanPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.QUALIFICATION_EXCEPTIONS) return <LeadQualificationExceptionPage/>
  if (menu?.path === APP_ROUTES.LEAD_APPEALS) return <LeadAppealPage/>
  if (menu?.path === APP_ROUTES.MY_SALES_ORDERS) return <MySalesOrderPage/>
  if (menu?.path === APP_ROUTES.SALES_ORDER_APPROVALS) return <SalesOrderApprovalPage/>
  if (menu?.path === APP_ROUTES.EXTERNAL_REPURCHASE) return <ExternalRepurchasePage/>
  if (menu?.path === APP_ROUTES.LEAD_RULE) return <LeadRuleConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_FILTER) return <LeadFilterConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_FOLLOW_UP_RULE) return <LeadFollowUpRuleConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.PRODUCT_CONFIG) return <ProductConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.WORK_PLAN_CONFIG) return <WorkPlanConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.ALL_MESSAGES) return <MessageInboxPage key={menu.path} view="all"/>
  if (menu?.path === APP_ROUTES.UNREAD_MESSAGES) return <MessageInboxPage key={menu.path} view="unread"/>
  return <section className="workspace-page"><Card bordered={false} title={menu?.name || '员工工作台'}>
    <Result status="info" title="页面尚未迁移" subTitle="该菜单已由统一权限系统下发，前端页面尚未迁移。"/>
    <Typography.Paragraph type="secondary">路径：{menu?.path || location.pathname}　组件：{menu?.component || '未配置'}</Typography.Paragraph>
  </Card></section>
}

function NoAccessibleMenu({ hasMenus }: { hasMenus: boolean }) {
  return <Result
    status="403"
    title={hasMenus ? '暂无可访问的内部页面' : '暂无可访问菜单'}
    subTitle={hasMenus ? '请选择左侧的外部菜单，或联系管理员检查菜单配置。' : '请联系管理员为当前账号配置角色菜单。'}
  />
}

function Shell({ info, onLogout }: { info: PermissionInfo; onLogout: () => void }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = theme.useToken()
  const { isDark, backgroundValue } = useTheme()
  const [primaryCollapsed, setPrimaryCollapsed] = useState(false)
  const [secondaryCollapsed, setSecondaryCollapsed] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)
  const [pendingAssignmentCount, setPendingAssignmentCount] = useState(0)
  const [openAssignmentRequest, setOpenAssignmentRequest] = useState(0)

  const menus = useMemo(
    () => filterRenderableMenus(buildMenuTree(info.menus || []), RENDERABLE_APP_ROUTES),
    [info.menus]
  )
  const navigation = useMemo(() => buildTwoLevelNavigation(menus), [menus])
  const initialTarget = useMemo(() => getInitialTarget(navigation), [navigation])
  const inaccessiblePathFallback = useMemo(
    () => getInaccessiblePathFallback(navigation, location.pathname),
    [navigation, location.pathname]
  )
  const activePrimary = useMemo(
    () => findPrimaryByPath(navigation, location.pathname),
    [navigation, location.pathname]
  )
  const currentMenu = useMemo(
    () => findPageByPath(navigation, location.pathname),
    [navigation, location.pathname]
  )

  useEffect(() => {
    if (inaccessiblePathFallback) navigate(inaccessiblePathFallback, { replace: true })
  }, [inaccessiblePathFallback, navigate])

  useEffect(() => {
    if (!activePrimary || location.pathname !== activePrimary.menu.path || activePrimary.pages.length === 0) return
    const target = getPrimaryTarget(activePrimary, false)
    if (target && target !== location.pathname) navigate(target, { replace: true })
  }, [activePrimary, location.pathname, navigate])

  const go = (path: string) => {
    if (/^https?:/i.test(path)) {
      window.open(path, '_blank', 'noopener,noreferrer')
      return
    }
    navigate(path)
  }
  const selectPrimary = (key: string) => {
    const item = navigation.find(candidate => candidate.key === key)
    if (!item) return
    const target = getPrimaryTarget(item)
    if (target) go(target)
  }

  const breadcrumbItems = activePrimary
    ? [
        { title: activePrimary.label },
        ...(currentMenu && currentMenu.id !== activePrimary.menu.id ? [{ title: currentMenu.name }] : [])
      ]
    : []

  const hasBackground = Boolean(backgroundValue)
  const shellStyle = {
    '--crm-bg-container': hasBackground ? 'rgba(255, 255, 255, 0.08)' : token.colorBgContainer,
    '--crm-bg-layout': hasBackground ? 'transparent' : token.colorBgLayout,
    '--crm-border': hasBackground ? 'rgba(255, 255, 255, 0.18)' : token.colorBorderSecondary,
    background: backgroundValue || token.colorBgLayout
  } as React.CSSProperties

  return <RealtimeProvider><NotifyMessageProvider><Layout className={hasBackground ? 'crm-shell custom-background' : 'crm-shell'} style={shellStyle}>
    <LeadAssignmentHost
      canAccept={(info.permissions || []).includes('zsjos:lead:accept')}
      onCountChange={setPendingAssignmentCount}
      openRequest={openAssignmentRequest}
    />
    <Sider theme={isDark ? 'dark' : 'light'} width={144} collapsedWidth={56} collapsed={primaryCollapsed} trigger={null} className="crm-sider primary-sider">
      <div className="brand"><span className="brand-default">{primaryCollapsed ? 'CRM' : '中世健 AI-CRM'}</span><span className="brand-mobile">CRM</span></div>
      <div className="sider-menu-scroll">
        <Menu
          mode="inline"
          inlineCollapsed={primaryCollapsed}
          selectedKeys={activePrimary ? [activePrimary.key] : []}
          items={toPrimaryItems(navigation)}
          onClick={({ key }) => selectPrimary(String(key))}
          className="transparent-menu"
        />
      </div>
      <div className="sider-toggle">
        <Tooltip title={primaryCollapsed ? '展开一级菜单' : '收起一级菜单'} placement="right">
          <Button type="text" aria-label={primaryCollapsed ? '展开一级菜单' : '收起一级菜单'} icon={primaryCollapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>} onClick={() => setPrimaryCollapsed(value => !value)}/>
        </Tooltip>
      </div>
    </Sider>

    <Sider theme={isDark ? 'dark' : 'light'} width={180} collapsedWidth={48} collapsed={secondaryCollapsed} trigger={null} className="crm-sider secondary-sider">
      <div className="secondary-title">
        {!secondaryCollapsed && <Typography.Text strong>{activePrimary?.label || '菜单'}</Typography.Text>}
        <Tooltip title={secondaryCollapsed ? '展开二级菜单' : '收起二级菜单'}>
          <Button type="text" size="small" aria-label={secondaryCollapsed ? '展开二级菜单' : '收起二级菜单'} icon={secondaryCollapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>} onClick={() => setSecondaryCollapsed(value => !value)}/>
        </Tooltip>
      </div>
      <div className="secondary-menu-scroll">
        <Menu
          mode="inline"
          inlineCollapsed={secondaryCollapsed}
          selectedKeys={currentMenu ? [currentMenu.path] : []}
          items={toSecondaryItems(activePrimary?.pages || [])}
          onClick={({ key }) => go(String(key))}
          className="transparent-menu"
        />
      </div>
    </Sider>

    <Layout>
      <Header className="crm-header">
        <Breadcrumb items={breadcrumbItems}/>
        <Space size={8} className="header-actions">
          <SalesDispatchStatusControl canAccept={(info.permissions || []).includes('zsjos:lead:accept')}/>
          <span className="theme-action"><ThemeSwitcher/></span>
          <span className="ai-action"><Tooltip title={aiOpen ? '收起 AI 助手' : '打开 AI 助手'}><Button type={aiOpen ? 'primary' : 'text'} icon={<RobotOutlined/>} onClick={() => setAiOpen(value => !value)}/></Tooltip></span>
          <MessageCenter/>
          <Tooltip title="待接客资"><Badge count={pendingAssignmentCount}><Button type="text" aria-label="待接客资" icon={<InboxOutlined/>} onClick={() => setOpenAssignmentRequest(value => value + 1)}/></Badge></Tooltip>
          <Dropdown menu={{ items: [
            { key: 'user', label: info.user?.nickname || info.user?.username || '当前用户', disabled: true },
            { type: 'divider' },
            { key: 'logout', label: <><LogoutOutlined/> 退出登录</>, onClick: onLogout }
          ] }}>
            <EmployeeAvatar avatar={info.user?.avatar} name={info.user?.nickname || info.user?.username} style={{ backgroundColor: 'transparent', cursor: 'pointer' }}/>
          </Dropdown>
        </Space>
      </Header>
      <Layout className="content-layout">
        <Content>
          <Routes>
            <Route path="/" element={initialTarget ? <Navigate to={initialTarget} replace/> : <NoAccessibleMenu hasMenus={navigation.length > 0}/>}/>
            <Route path="*" element={currentMenu ? <Placeholder menu={currentMenu} permissions={info.permissions || []} onOpenAssignment={() => setOpenAssignmentRequest(value => value + 1)}/> : <Result status="404" title="页面不存在"/>}/>
          </Routes>
        </Content>
        {aiOpen && <Sider width={320} className="ai-sider"><Typography.Title level={5}><RobotOutlined/> AI 助手</Typography.Title><Result status="info" title="AI 助手暂未接入"/></Sider>}
      </Layout>
    </Layout>
  </Layout></NotifyMessageProvider></RealtimeProvider>
}

function Root() {
  const [logged, setLogged] = useState(Boolean(localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)))
  const [info, setInfo] = useState<PermissionInfo>()
  const [error, setError] = useState('')
  const [permissionAttempt, setPermissionAttempt] = useState(0)

  useEffect(() => {
    if (!logged) return
    api.permissionInfo()
      .then(setInfo)
      .catch(permissionError => {
        setInfo(undefined)
        if (permissionError instanceof AuthenticationError) {
          clearAuthStorage()
          setError('登录状态已失效，请重新登录')
          setLogged(false)
          return
        }
        setError(permissionError.response?.data?.msg || permissionError.message || '权限信息加载失败')
      })
  }, [logged, permissionAttempt])

  if (!logged) return <Login initialError={error} onLogin={() => { setError(''); setInfo(undefined); setLogged(true) }}/>
  if (error) return <div className="center-page"><Card title="权限信息加载失败"><Alert type="error" message={error}/><Space><Button type="primary" onClick={() => { setError(''); setPermissionAttempt(value => value + 1) }}>重试</Button><Button onClick={() => { clearAuthStorage(); setError(''); setInfo(undefined); setLogged(false) }}>返回登录</Button></Space></Card></div>
  if (!info) return <div className="center-page">正在读取权限菜单...</div>
  return <DefaultEmployeeAvatarProvider defaultAvatar={info.defaultAvatar}><OverlayCoordinatorProvider><Shell info={info} onLogout={async () => {
    try {
      if ((info.permissions || []).includes('zsjos:lead:accept')) await api.dispatchOffline().catch(() => undefined)
      await api.logout()
    } finally { setInfo(undefined); setError(''); setLogged(false) }
  }}/></OverlayCoordinatorProvider></DefaultEmployeeAvatarProvider>
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode><RuntimeBoundary><ThemeProvider><BrowserRouter><App><Root/></App></BrowserRouter></ThemeProvider></RuntimeBoundary></React.StrictMode>
)
