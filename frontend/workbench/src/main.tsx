import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Alert,
  App,
  Badge,
  Button,
  Card,
  ConfigProvider,
  Dropdown,
  Layout,
  Menu,
  Result,
  Space,
  Tooltip,
  Typography,
  Watermark,
  theme
} from 'antd'
import type { MenuProps } from 'antd'
import {
  DownOutlined,
  InboxOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RobotOutlined,
  SettingOutlined
} from '@ant-design/icons'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { api, AUTH_EXPIRED_EVENT, AuthenticationError, buildMenuTree, clearAuthStorage, getAuthAccessToken, migrateLegacyAuthStorage, type PermissionInfo } from './services/api'
import {
  buildTwoLevelNavigation,
  canOpenLeadDetailDeepLink,
  filterRenderableMenus,
  findMenuByPath,
  findPageByPath,
  findPrimaryByPath,
  getInaccessiblePathFallback,
  getAuthenticatedHomeTarget,
  getInitialTarget,
  getPrimaryTarget,
  type PrimaryNavigationItem
} from './services/menu'
import { APP_ROUTES, LAYOUT_SIZES, MINI_RAIL_W, NAV_INLINE_INDENT, RENDERABLE_APP_ROUTES, type AuthPlatform } from './constants'
import { initializeAuthPlatform, redirectToMobileEntryForPlatformReload, resolveAdminEmbedPresentation, shouldReloadForMobileEntry } from './services/authSession'
import LeadAssignmentHost from './components/LeadAssignmentHost'
import { OverlayCoordinatorProvider } from './components/OverlayCoordinator'
import { RealtimeProvider } from './components/RealtimeProvider'
import { NotifyMessageProvider } from './components/NotifyMessageProvider'
import MessageCenter from './components/MessageCenter'
import { AnnouncementProvider } from './components/AnnouncementProvider'
import { ForcedFormProvider } from './components/ForcedFormProvider'
import SalesDispatchStatusControl from './components/SalesDispatchStatusControl'
import { SalesDispatchStatusProvider } from './components/SalesDispatchStatusProvider'
import SalesDispatchStatusAlert from './components/SalesDispatchStatusAlert'
import EmployeeAvatar, { DefaultEmployeeAvatarProvider } from './components/EmployeeAvatar'
import ThemeProvider from './components/Theme/ThemeProvider'
import SettingsDrawer from './components/SettingsDrawer'
import TabBar, { type TabItem } from './components/TabBar'
import { useTheme } from './components/Theme/ThemeContext'
import LoginPage from './layouts/LoginPage'
import BackendMenuIcon from './layouts/BackendMenuIcon'
import RouteHost from './layouts/RouteHost'
import AdminEmbedFrame, { type AdminEmbedFrameHandle } from './layouts/AdminEmbedPage'
import MobileNavDrawer from './layouts/MobileNavDrawer'
import { buildHierarchicalSecondaryItems, buildNavMenuItems } from './layouts/navItems'
import UserProfilePage from './pages/UserProfilePage'
import WecomClickPage from './pages/WecomClickPage'
import LeadManagementPage from './pages/LeadManagementPage'
import { ProductionTicketAssignmentHost } from './pages/MediaFeaturePage'
import { getStoredImpersonation, IMPERSONATION_CHANGE_EVENT } from './services/impersonation'
// 聚合样式表；内部 @import 顺序即层叠优先级，tokens.css 在最前
import './styles/index.css'

const { Sider, Header, Content } = Layout
type MenuItem = Required<MenuProps>['items'][number]
const NAV_POPUP_CLASS_NAME = 'workbench-nav-popup'

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

function toPrimaryItems(items: PrimaryNavigationItem[]): MenuItem[] {
  return items.map(item => ({
    key: item.key,
    label: item.label,
    title: item.label,
    icon: <BackendMenuIcon icon={item.icon}/>
  }))
}

function NoAccessibleMenu({ hasMenus }: { hasMenus: boolean }) {
  return <Result
    status="403"
    title={hasMenus ? '暂无可访问的内部页面' : '暂无可访问菜单'}
    subTitle={hasMenus ? '请选择左侧的外部菜单，或联系管理员检查菜单配置。' : '请联系管理员为当前账号配置角色菜单。'}
  />
}

function MobileEntryReloadGuard({ platform }: { platform: AuthPlatform }) {
  const location = useLocation()

  useEffect(() => {
    if (!shouldReloadForMobileEntry(platform, location.pathname)) return
    redirectToMobileEntryForPlatformReload(`${location.pathname}${location.search}${location.hash}`)
  }, [location.hash, location.pathname, location.search, platform])

  return null
}

function Shell({ info, authPlatform, onLogout, onUserChange }: { info: PermissionInfo; authPlatform: AuthPlatform; onLogout: () => void; onUserChange: (user: { nickname: string; avatar?: string }) => void }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = theme.useToken()
  const { isDark, backgroundValue, layoutMode, watermark: watermarkEnabled, headerFixed, tabs: tabsEnabled, tabStyle } = useTheme()
  const [primaryCollapsed, setPrimaryCollapsed] = useState(false)
  const [secondaryCollapsed, setSecondaryCollapsed] = useState(false)
  const [singleSiderOpenKeys, setSingleSiderOpenKeys] = useState<string[]>([])
  const [aiOpen, setAiOpen] = useState(false)
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const [pendingAssignmentCount, setPendingAssignmentCount] = useState(0)
  const [openAssignmentRequest, setOpenAssignmentRequest] = useState(0)
  const [impersonation, setImpersonation] = useState<ReturnType<typeof getStoredImpersonation>>()
  const [tabs, setTabs] = useState<TabItem[]>([])
  const adminEmbedFrameRef = useRef<AdminEmbedFrameHandle>(null)
  const previousTabsRef = useRef<TabItem[]>([])

  useEffect(() => {
    const sync = () => setImpersonation(authPlatform === 'PC' ? getStoredImpersonation() : undefined)
    sync()
    window.addEventListener('storage', sync)
    window.addEventListener(IMPERSONATION_CHANGE_EVENT, sync)
    return () => {
      window.removeEventListener('storage', sync)
      window.removeEventListener(IMPERSONATION_CHANGE_EVENT, sync)
    }
  }, [authPlatform])

  // 移动端侧栏由 layout.css 在整个 ≤768px 视口隐藏（抽屉成为唯一导航入口），
  // 无需再用 matchMedia 同步 primarySider 的 collapsed 状态。
  const authorizedMenus = useMemo(
    () => filterRenderableMenus(buildMenuTree(info.menus || []), RENDERABLE_APP_ROUTES),
    [info.menus]
  )
  const navigationMenus = useMemo(() => {
    if (!info.workbenchMenus || info.workbenchLayoutMeta?.fallback) return authorizedMenus
    return filterRenderableMenus(buildMenuTree(info.workbenchMenus, '/', true), RENDERABLE_APP_ROUTES)
  }, [authorizedMenus, info.workbenchLayoutMeta?.fallback, info.workbenchMenus])
  const navigation = useMemo(() => buildTwoLevelNavigation(navigationMenus), [navigationMenus])
  const initialTarget = useMemo(() => getInitialTarget(navigation), [navigation])
  const leadDetailDeepLink = useMemo(() => {
    return canOpenLeadDetailDeepLink(location.pathname, location.search, info.permissions || [])
  }, [info.permissions, location.pathname, location.search])
  const inaccessiblePathFallback = useMemo(
    () => leadDetailDeepLink ? undefined : getInaccessiblePathFallback(navigation, location.pathname, authorizedMenus),
    [leadDetailDeepLink, navigation, location.pathname, authorizedMenus]
  )
  const activePrimary = useMemo(
    () => findPrimaryByPath(navigation, location.pathname),
    [navigation, location.pathname]
  )
  const currentMenu = useMemo(
    () => findMenuByPath(authorizedMenus, location.pathname),
    [authorizedMenus, location.pathname]
  )
  const adminEmbedPresentation = resolveAdminEmbedPresentation(authPlatform, currentMenu?.workbenchRenderMode)
  const mobileAdminEmbed = adminEmbedPresentation === 'mobile-blocked'
  const activeAdminEmbedPath = adminEmbedPresentation === 'frame'
    ? currentMenu?.path
    : undefined

  const handleAdminRouteChange = useCallback((path: string) => {
    const menu = findMenuByPath(authorizedMenus, path)
    if (menu?.workbenchRenderMode === 'admin_embed' && path !== location.pathname) {
      navigate(path)
    }
  }, [authorizedMenus, location.pathname, navigate])

  useEffect(() => {
    if (!tabsEnabled) setTabs([])
  }, [tabsEnabled])

  useEffect(() => {
    const currentPaths = new Set(tabs.map(tab => tab.key))
    previousTabsRef.current.forEach(tab => {
      if (currentPaths.has(tab.key)) return
      const menu = findMenuByPath(authorizedMenus, tab.key)
      if (menu?.workbenchRenderMode === 'admin_embed') {
        adminEmbedFrameRef.current?.closeRoute(tab.key)
      }
    })
    previousTabsRef.current = tabs
  }, [authorizedMenus, tabs])

  useEffect(() => {
    if (inaccessiblePathFallback) navigate(inaccessiblePathFallback, { replace: true })
  }, [inaccessiblePathFallback, navigate])

  // 左单列模式：路由切换时自动展开当前一级组。
  useEffect(() => {
    if (activePrimary) {
      setSingleSiderOpenKeys(prev => prev.includes(activePrimary.key) ? prev : [...prev, activePrimary.key])
    }
  }, [activePrimary])

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

  const hasBackground = Boolean(backgroundValue)
  const shellStyle = { background: backgroundValue || token.colorBgLayout } as React.CSSProperties

  // 布局模式分支
  const showPrimarySider = layoutMode === 'side'
  const showSecondarySider = layoutMode === 'side' || layoutMode === 'top'
  const showSingleSider = layoutMode === 'single-sider'
  const showMiniSider = layoutMode === 'mini-float'
  const showTopPrimary = layoutMode === 'top' || layoutMode === 'top-only'
  const showTopSecondary = layoutMode === 'top-only'

  const singleSiderItems: MenuItem[] = useMemo(
    () => showSingleSider ? buildNavMenuItems(navigation, { popupClassName: NAV_POPUP_CLASS_NAME }) : [],
    [navigation, showSingleSider]
  )
  const miniSiderItems: MenuItem[] = useMemo(
    () => showMiniSider
      ? buildNavMenuItems(navigation, { groupChildren: true, popupClassName: `${NAV_POPUP_CLASS_NAME} mini-flyout` })
      : [],
    [navigation, showMiniSider]
  )
  const topOnlyItems: MenuItem[] = useMemo(
    () => showTopSecondary
      ? buildNavMenuItems(navigation, {
          childIcons: false,
          expandSuffix: <DownOutlined style={{ fontSize: 10, marginLeft: 2 }}/>,
          popupClassName: NAV_POPUP_CLASS_NAME
        })
      : [],
    [navigation, showTopSecondary]
  )

  const layoutClass = [
    'crm-shell',
    hasBackground && 'custom-background',
    `layout-${layoutMode}`,
    !headerFixed && 'header-scroll'
  ].filter(Boolean).join(' ')

  const watermarkText = info.user?.nickname || info.user?.username || ''
  const showWatermark = watermarkEnabled && watermarkText.length > 0

  const shellContent = (
    <Layout className={layoutClass} style={shellStyle}>
    <LeadAssignmentHost
      canAccept={(info.permissions || []).includes('zsjos:lead:accept')}
      onCountChange={setPendingAssignmentCount}
      openRequest={openAssignmentRequest}
    />
    <MobileNavDrawer
      open={mobileNavOpen}
      onClose={() => setMobileNavOpen(false)}
      navigation={navigation}
      activePrimaryKey={activePrimary?.key}
      activePagePath={currentMenu?.path}
      onSelect={go}
    />

    {/* 一级侧栏：仅 side 模式渲染 */}
    {showPrimarySider && (
      <Sider theme={isDark ? 'dark' : 'light'} width={LAYOUT_SIZES.PRIMARY_SIDER_W} collapsedWidth={LAYOUT_SIZES.PRIMARY_SIDER_COLLAPSED} collapsed={primaryCollapsed} trigger={null} className="crm-sider primary-sider">
        <div className="brand"><span className="brand-default">{primaryCollapsed ? 'CRM' : '中世健\nAI-CRM'}</span><span className="brand-mobile">CRM</span></div>
        <div className="sider-menu-scroll">
          <nav className="primary-nav">
            {navigation.map(item => {
              const isActive = activePrimary?.key === item.key
              const btn = (
                <button
                  key={item.key}
                  className={`primary-nav-item${isActive ? ' active' : ''}`}
                  onClick={() => selectPrimary(item.key)}
                  aria-label={item.label}
                  aria-current={isActive ? 'page' : undefined}
                >
                  <span className="primary-nav-icon"><BackendMenuIcon icon={item.icon}/></span>
                  {!primaryCollapsed && <span className="primary-nav-label">{item.label}</span>}
                </button>
              )
              return primaryCollapsed
                ? <Tooltip key={item.key} title={item.label} placement="right">{btn}</Tooltip>
                : btn
            })}
          </nav>
        </div>
        <div className="sider-toggle">
          <Tooltip title={primaryCollapsed ? '展开一级菜单' : '收起一级菜单'} placement="right">
            <Button type="text" aria-label={primaryCollapsed ? '展开一级菜单' : '收起一级菜单'} icon={primaryCollapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>} onClick={() => setPrimaryCollapsed(value => !value)}/>
          </Tooltip>
        </div>
      </Sider>
    )}

    {/* 二级侧栏：side / top 模式 */}
    {showSecondarySider && (
      <Sider theme={isDark ? 'dark' : 'light'} width={LAYOUT_SIZES.SECONDARY_SIDER_W} collapsedWidth={LAYOUT_SIZES.SECONDARY_SIDER_COLLAPSED} collapsed={secondaryCollapsed} trigger={null} className="crm-sider secondary-sider">
        <div className="secondary-title">
          {!secondaryCollapsed && <Typography.Text strong>{activePrimary?.label || '菜单'}</Typography.Text>}
          <Tooltip title={secondaryCollapsed ? '展开二级菜单' : '收起二级菜单'}>
            <Button type="text" size="small" aria-label={secondaryCollapsed ? '展开二级菜单' : '收起二级菜单'} icon={secondaryCollapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>} onClick={() => setSecondaryCollapsed(value => !value)}/>
          </Tooltip>
        </div>
        <div className="secondary-menu-scroll">
          <Menu
            mode="inline"
            inlineIndent={NAV_INLINE_INDENT}
            inlineCollapsed={secondaryCollapsed}
            selectedKeys={currentMenu ? [currentMenu.path] : []}
            items={buildHierarchicalSecondaryItems(activePrimary?.menu, { popupClassName: NAV_POPUP_CLASS_NAME })}
            onClick={({ key }) => go(String(key))}
            className="transparent-menu"
          />
        </div>
      </Sider>
    )}

    {/* 左单列：一二级合并为可折叠目录树 */}
    {showSingleSider && (
      <Sider theme={isDark ? 'dark' : 'light'} width={LAYOUT_SIZES.SINGLE_SIDER_W} collapsedWidth={LAYOUT_SIZES.PRIMARY_SIDER_COLLAPSED} collapsed={primaryCollapsed} trigger={null} className="crm-sider single-sider">
        <div className="brand"><span className="brand-default">{primaryCollapsed ? 'CRM' : '中世健\nAI-CRM'}</span><span className="brand-mobile">CRM</span></div>
        <div className="sider-menu-scroll">
          <Menu
            mode="inline"
            inlineIndent={NAV_INLINE_INDENT}
            inlineCollapsed={primaryCollapsed}
            selectedKeys={currentMenu ? [currentMenu.path] : []}
            openKeys={singleSiderOpenKeys}
            onOpenChange={(keys) => setSingleSiderOpenKeys(keys as string[])}
            items={singleSiderItems}
            onClick={({ key }) => go(String(key))}
            className="transparent-menu"
          />
        </div>
        <div className="sider-toggle">
          <Tooltip title={primaryCollapsed ? '展开菜单' : '收起菜单'} placement="right">
            <Button type="text" aria-label={primaryCollapsed ? '展开菜单' : '收起菜单'} icon={primaryCollapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>} onClick={() => setPrimaryCollapsed(value => !value)}/>
          </Tooltip>
        </div>
      </Sider>
    )}

    {/* mini-float：图标侧栏 + popup 二级菜单 */}
    {showMiniSider && (
      <Sider theme={isDark ? 'dark' : 'light'} width={MINI_RAIL_W} collapsedWidth={MINI_RAIL_W} collapsed trigger={null} className="crm-sider mini-sider">
        <div className="brand">CRM</div>
        <div className="sider-menu-scroll">
          <ConfigProvider theme={{ components: { Menu: { collapsedWidth: MINI_RAIL_W } } }}>
            <Menu
              mode="inline"
              selectedKeys={currentMenu ? [currentMenu.path] : []}
              items={miniSiderItems}
              onClick={({ key }) => go(String(key))}
              className="transparent-menu"
            />
          </ConfigProvider>
        </div>
      </Sider>
    )}

    <Layout>
      <Header className="crm-header">
        {/* 移动端汉堡按钮 */}
        <Button type="text" className="mobile-nav-trigger" aria-label="打开导航菜单" icon={<MenuUnfoldOutlined/>} onClick={() => setMobileNavOpen(true)}/>
        {/* 移动端品牌 + 当前页标识；桌面端由侧栏 .brand 承担，此单元格仅窄屏显示 */}
        <span className="mobile-header-brand" aria-hidden="true">
          <span className="mobile-header-brand-name">中世健 AI-CRM</span>
          {currentMenu && <span className="mobile-header-brand-page">{currentMenu.name}</span>}
        </span>
        {/* 顶部模式：一级菜单放在 header */}
        {showTopPrimary && !showTopSecondary && (
          <Menu
            mode="horizontal"
            selectedKeys={activePrimary ? [activePrimary.key] : []}
            items={toPrimaryItems(navigation)}
            onClick={({ key }) => selectPrimary(String(key))}
            className="top-primary-menu"
          />
        )}
        {/* 纯顶栏模式：一二级合并为递归下拉菜单 */}
        {showTopSecondary && (
          <Menu
            mode="horizontal"
            selectedKeys={currentMenu ? [currentMenu.path] : []}
            items={topOnlyItems}
            onClick={({ key }) => go(String(key))}
            className="top-primary-menu"
          />
        )}
        <Space size={8} className="header-actions">
          <span className="header-dispatch-control"><SalesDispatchStatusControl/></span>
          <SettingsDrawer/>
          <span className="ai-action"><Tooltip title={aiOpen ? '收起 AI 助手' : '打开 AI 助手'}><Button type={aiOpen ? 'primary' : 'text'} icon={<RobotOutlined/>} onClick={() => setAiOpen(value => !value)}/></Tooltip></span>
          <MessageCenter/>
          {(info.permissions || []).includes('zsjos:lead:accept') && (
            <Tooltip title="待接客资"><Badge count={pendingAssignmentCount}><Button type="text" aria-label="待接客资" icon={<InboxOutlined/>} onClick={() => setOpenAssignmentRequest(value => value + 1)}/></Badge></Tooltip>
          )}
          <Dropdown menu={{ items: [
            { key: 'user', label: info.user?.nickname || info.user?.username || '当前用户', disabled: true },
            { type: 'divider' },
            { key: 'profile', icon: <SettingOutlined/>, label: '个人中心', onClick: () => navigate(APP_ROUTES.USER_PROFILE) },
            { type: 'divider' },
            { key: 'logout', label: <><LogoutOutlined/> 退出登录</>, onClick: onLogout }
          ] }}>
            <EmployeeAvatar avatar={info.user?.avatar} name={info.user?.nickname || info.user?.username} style={{ backgroundColor: 'transparent', cursor: 'pointer' }}/>
          </Dropdown>
        </Space>
      </Header>
      {impersonation && <Alert
        className="impersonation-global-alert"
        type="warning"
        showIcon
        banner
        message={`只读借视图：当前以 ${impersonation.targetNameSnapshot} 的数据权限查看，所有 ZSJOS 写操作均会被服务端拒绝。`}
      />}
      <SalesDispatchStatusAlert />
      <ProductionTicketAssignmentHost permissions={info.permissions || []} />
      {tabsEnabled && <TabBar currentMenu={currentMenu} initialPath={initialTarget} tabStyle={tabStyle} tabs={tabs} setTabs={setTabs}/>}
      <Layout className="content-layout">
        <Content>
          {authPlatform === 'PC' && <AdminEmbedFrame
              ref={adminEmbedFrameRef}
              activePath={activeAdminEmbedPath}
              title={currentMenu?.name}
              onRouteChange={handleAdminRouteChange}
            />}
          {mobileAdminEmbed
            ? <Result status="info" title="请使用电脑端访问此页面" subTitle="该页面由管理端承载，手机端会话不会复用电脑端登录状态。"/>
            : !activeAdminEmbedPath && <Routes>
            <Route path={APP_ROUTES.USER_PROFILE} element={<UserProfilePage onUserChange={onUserChange}/>}/>
            <Route path={APP_ROUTES.WECOM_CLICK} element={<WecomClickPage authPlatform={authPlatform} onNeedLogin={targetPath => navigate(targetPath, { replace: true })}/>}/>
            <Route path={APP_ROUTES.LEAD_MANAGEMENT} element={currentMenu
              ? <RouteHost menu={currentMenu} permissions={info.permissions || []} roles={info.roles || []} onOpenAssignment={() => setOpenAssignmentRequest(value => value + 1)}/>
              : leadDetailDeepLink
                ? <LeadManagementPage permissions={info.permissions || []} detailOnly/>
                : <Result status="403" title="无权查看客资详情"/>}/>
            <Route path={APP_ROUTES.SALES_ORDER_SUPERVISOR_CONFIRMATIONS} element={<Navigate to={APP_ROUTES.SALES_ORDER_APPROVALS} replace/>}/>
            <Route path="/" element={initialTarget ? <Navigate to={initialTarget} replace/> : <NoAccessibleMenu hasMenus={navigation.length > 0}/>}/>
            <Route path="*" element={currentMenu ? <RouteHost menu={currentMenu} permissions={info.permissions || []} roles={info.roles || []} onOpenAssignment={() => setOpenAssignmentRequest(value => value + 1)}/> : <Result status="404" title="页面不存在"/>}/>
          </Routes>}
        </Content>
        {aiOpen && <Sider width={LAYOUT_SIZES.AI_SIDER_W} className="ai-sider"><Typography.Title level={5}><RobotOutlined/> AI 助手</Typography.Title><Result status="info" title="AI 助手暂未接入"/></Sider>}
      </Layout>
    </Layout>
  </Layout>
  )

  return <>
    <RealtimeProvider platform={authPlatform}><ForcedFormProvider><AnnouncementProvider enabled={(info.permissions || []).includes('system:notice:read')}><SalesDispatchStatusProvider canAccept={(info.permissions || []).includes('zsjos:lead:accept')}><NotifyMessageProvider>
      {showWatermark
        ? <Watermark content={[watermarkText]} className="crm-watermark-wrapper">{shellContent}</Watermark>
        : <div className="crm-watermark-wrapper">{shellContent}</div>
      }
    </NotifyMessageProvider></SalesDispatchStatusProvider></AnnouncementProvider></ForcedFormProvider></RealtimeProvider>
  </>
}

function Root({ authPlatform }: { authPlatform: AuthPlatform }) {
  const navigate = useNavigate()
  const location = useLocation()
  const navigateRef = useRef(navigate)
  const [publicLoginRedirect, setPublicLoginRedirect] = useState('')
  const [loginRedirectPending, setLoginRedirectPending] = useState(false)
  const [logged, setLogged] = useState(() => {
    migrateLegacyAuthStorage()
    return Boolean(getAuthAccessToken(authPlatform))
  })
  const [info, setInfo] = useState<PermissionInfo>()
  const [error, setError] = useState('')
  const [permissionAttempt, setPermissionAttempt] = useState(0)

  useEffect(() => {
    const onAuthExpired = (event: Event) => {
      const expiredPlatform = (event as CustomEvent<{ platform?: string }>).detail?.platform
      if (expiredPlatform && expiredPlatform !== authPlatform) return
      setInfo(undefined)
      setError('')
      setLogged(false)
      setPublicLoginRedirect('')
      setLoginRedirectPending(false)
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, onAuthExpired)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onAuthExpired)
  }, [authPlatform])

  useEffect(() => {
    // useNavigate() 在这个路由器里会随着当前位置变化而变；这里只给显式重新登录和回跳目标做一次落点，
    // 不能让普通刷新把当前页面重新拉回首页。
    navigateRef.current = navigate
  }, [navigate])

  useEffect(() => {
    if (!logged) return
    api.permissionInfo()
      .then(permissionInfo => {
        const authorizedMenus = buildMenuTree(permissionInfo.menus || [])
        const homeTarget = getAuthenticatedHomeTarget(authorizedMenus)
        const fallbackTarget = getInitialTarget(buildTwoLevelNavigation(
          filterRenderableMenus(authorizedMenus, RENDERABLE_APP_ROUTES)
        ))
        if (publicLoginRedirect || loginRedirectPending) {
          navigateRef.current(publicLoginRedirect || homeTarget || fallbackTarget || '/', { replace: true })
        }
        setPublicLoginRedirect('')
        setLoginRedirectPending(false)
        setInfo(permissionInfo)
      })
      .catch(permissionError => {
        setInfo(undefined)
        if (permissionError instanceof AuthenticationError) {
          clearAuthStorage(authPlatform)
          setError('登录状态已失效，请重新登录')
          setLoginRedirectPending(false)
          setLogged(false)
          return
        }
        setError(permissionError.response?.data?.msg || permissionError.message || '权限信息加载失败')
      })
  }, [authPlatform, logged, permissionAttempt])

  if (!logged) return location.pathname === APP_ROUTES.WECOM_CLICK && !publicLoginRedirect
    ? <WecomClickPage authPlatform={authPlatform} onNeedLogin={targetPath => setPublicLoginRedirect(targetPath)} />
    : <LoginPage platform={authPlatform} initialError={error} onLogin={() => { setError(''); setInfo(undefined); setLoginRedirectPending(true); setLogged(true) }}/>
  if (error) return <div className="center-page"><Card title="权限信息加载失败"><Alert type="error" message={error}/><Space><Button type="primary" onClick={() => { setError(''); setPermissionAttempt(value => value + 1) }}>重试</Button><Button onClick={() => { clearAuthStorage(authPlatform); setError(''); setInfo(undefined); setPublicLoginRedirect(''); setLoginRedirectPending(false); setLogged(false) }}>返回登录</Button></Space></Card></div>
  if (!info) return <div className="center-page">正在读取权限菜单...</div>
  return <DefaultEmployeeAvatarProvider defaultAvatar={info.defaultAvatar}><OverlayCoordinatorProvider><Shell authPlatform={authPlatform} info={info} onUserChange={user => setInfo(current => current ? { ...current, user: { ...current.user, ...user } } : current)} onLogout={async () => {
    try {
      if ((info.permissions || []).includes('zsjos:lead:accept')) await api.dispatchOffline().catch(() => undefined)
      await api.logout(authPlatform)
    } finally { setInfo(undefined); setError(''); setPublicLoginRedirect(''); setLoginRedirectPending(false); setLogged(false) }
  }}/></OverlayCoordinatorProvider></DefaultEmployeeAvatarProvider>
}

const authPlatform = initializeAuthPlatform()

createRoot(document.getElementById('root')!).render(
  <React.StrictMode><RuntimeBoundary><ThemeProvider><BrowserRouter><App><MobileEntryReloadGuard platform={authPlatform}/><Root authPlatform={authPlatform}/></App></BrowserRouter></ThemeProvider></RuntimeBoundary></React.StrictMode>
)
