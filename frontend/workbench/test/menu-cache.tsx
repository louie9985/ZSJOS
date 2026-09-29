// UTF-8. Isolated permission responses and page state; no backend requests.
import { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Card, Input, Space, Switch } from 'antd'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { buildMenuTree, type WorkbenchMenu } from '../src/services/api'
import { getRetainedPageMenus } from '../src/retainedPagePaths'
import RetainedPageHost from '../src/layouts/RetainedPageHost'
import TabBar, { type TabItem } from '../src/components/TabBar'
import { WorkbenchPageNavigation, useWorkbenchPageGuard, useWorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import '../src/styles/index.css'

const runtime = new Set(['/home', '/cached', '/plain'])
const metrics = { mounts: {} as Record<string, number>, unmounts: {} as Record<string, number>, guards: 0 }
Object.assign(window, { menuCacheFixture: metrics })

function Page({ menu }: { menu: WorkbenchMenu }) {
  const location = useLocation()
  const [input, setInput] = useState('')
  const [dirty, setDirty] = useState(false)
  const { modal } = App.useApp()
  useEffect(() => {
    metrics.mounts[menu.path] = (metrics.mounts[menu.path] || 0) + 1
    return () => { metrics.unmounts[menu.path] = (metrics.unmounts[menu.path] || 0) + 1 }
  }, [menu.path])
  useWorkbenchPageGuard(menu.path, async destination => {
    if (destination !== undefined || !dirty) return true
    metrics.guards++
    return new Promise(resolve => modal.confirm({ title: '放弃未保存内容？', okText: '确认放弃', cancelText: '继续编辑',
      onOk: () => resolve(true), onCancel: () => resolve(false) }))
  })
  return <Card title={menu.name} data-testid={`page${menu.path}`}>
    <Input aria-label={`${menu.name}输入`} value={input} onChange={event => setInput(event.target.value)}/>
    <Space wrap><span>未保存</span><Switch aria-label={`${menu.name}未保存`} checked={dirty} onChange={setDirty}/></Space>
    <p data-testid={`query${menu.path}`}>{location.search || 'empty'}</p>
  </Card>
}

function Pages({ menus, retainedMenus, retainedPaths, scopeKey, tabsEnabled }: {
  menus: WorkbenchMenu[]; retainedMenus: WorkbenchMenu[]; retainedPaths: string[]; scopeKey: string; tabsEnabled: boolean
}) {
  const location = useLocation()
  const navigation = useWorkbenchPageNavigation()!
  const [tabs, setTabs] = useState<TabItem[]>([{ key: '/home', label: '首页', closable: false }])
  useEffect(() => { if (!tabsEnabled) setTabs([]) }, [tabsEnabled])
  const currentMenu = menus.find(menu => menu.path === location.pathname)
  return <>
    <Space wrap>
      <Button onClick={() => void navigation.open('/home')}>打开首页</Button>
      <Button onClick={() => void navigation.open('/cached?record=A')}>打开缓存页 A</Button>
      <Button onClick={() => void navigation.open('/cached?record=B')}>打开缓存页 B</Button>
      <Button onClick={() => void navigation.open('/plain?record=P')}>打开普通页</Button>
    </Space>
    <p data-testid="active-route">{location.pathname}{location.search}</p>
    {tabsEnabled && <TabBar currentMenu={currentMenu} tabs={tabs} setTabs={setTabs} retainedPaths={retainedPaths}/>}
    <RetainedPageHost menus={retainedMenus} activePath={location.pathname} openPaths={tabs.map(tab => tab.key)}
      tabsEnabled={tabsEnabled} scopeKey={scopeKey} renderPage={menu => <Page menu={menu}/>}/>
    {!retainedPaths.includes(location.pathname) && (currentMenu
      ? <Page key={`${scopeKey}:${location.pathname}`} menu={currentMenu}/> : <p>无权访问</p>)}
  </>
}

function Fixture() {
  const [cached, setCached] = useState(true), [allowed, setAllowed] = useState(true)
  const [tabsEnabled, setTabsEnabled] = useState(true), [identity, setIdentity] = useState(1)
  const menus = useMemo(() => buildMenuTree([
    { id: 1, name: '首页', path: 'home', parentId: 0, type: 2, visible: true, keepAlive: false },
    ...(allowed ? [{ id: 2, name: '缓存页', path: 'cached', parentId: 0, type: 2, visible: true, keepAlive: cached }] : []),
    { id: 3, name: '普通页', path: 'plain', parentId: 0, type: 2, visible: true, keepAlive: false }
  ]), [cached, allowed])
  const retainedMenus = useMemo(() => getRetainedPageMenus(menus, runtime), [menus])
  const retainedPaths = useMemo(() => retainedMenus.map(menu => menu.path), [retainedMenus])
  return <div style={{ padding: 12 }}>
    <Space wrap>
      <span>服务端缓存</span><Switch aria-label="服务端缓存" checked={cached} onChange={setCached}/>
      <span>授权</span><Switch aria-label="授权" checked={allowed} onChange={setAllowed}/>
      <span>多页签</span><Switch aria-label="多页签" checked={tabsEnabled} onChange={setTabsEnabled}/>
      <Button onClick={() => setIdentity(value => value + 1)}>切换租户身份</Button>
    </Space>
    <WorkbenchPageNavigation canOpen={path => menus.some(menu => menu.path === path)}
      canRetain={path => tabsEnabled && retainedPaths.includes(path)}>
      <Pages menus={menus} retainedMenus={retainedMenus} retainedPaths={retainedPaths} scopeKey={String(identity)} tabsEnabled={tabsEnabled}/>
    </WorkbenchPageNavigation>
  </div>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={['/home']}><App><Fixture/></App></MemoryRouter>)
