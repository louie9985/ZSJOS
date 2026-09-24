// Isolated browser fixture: all transport is synthetic, including save/upload.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Space } from 'antd'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { APP_ROUTES } from '../src/constants'
import { RETAINED_PAGE_PATHS } from '../src/retainedPagePaths'
import { http, type WorkbenchMenu } from '../src/services/api'
import { materialApi } from '../src/services/materialApi'
import TabBar, { type TabItem } from '../src/components/TabBar'
import { WorkbenchPageNavigation, useWorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import RetainedReviewRoute from '../src/layouts/RetainedReviewRoutes'
import ViralAccountDecomposePage from '../src/pages/ViralAccountDecomposePage'
import ViralContentDecomposePage from '../src/pages/ViralContentDecomposePage'
import '../src/styles/index.css'

const paths = [APP_ROUTES.VIRAL_ACCOUNT_DECOMPOSE, APP_ROUTES.VIRAL_CONTENT_DECOMPOSE]
const menus = ['/home', ...paths, APP_ROUTES.CONTENT_REVIEW, APP_ROUTES.MEDIA_STUDENTS].map((path, index) => ({
  id: index + 1, path, name: ['首页', '账号拆解', '内容拆解', '内容审核', '媒体学员'][index],
  parentId: 0, hidden: false, noCache: false, alwaysShow: false, children: []
})) as WorkbenchMenu[]
const fields = (index: number) => [
  { key: index ? 'work_title' : 'account_name', label: '验收标题', type: 'text', section: 'ACCOUNT_DETAIL' },
  { key: 'rows', label: '重复记录', type: 'repeat-group', section: 'DIRECTOR_ANALYSIS', initialCount: 1,
    children: [{ key: 'text', label: '记录正文', type: 'text' }] },
  ...Array.from({ length: 12 }, (_, n) => ({ key: `note${n}`, label: `分析${n}`, type: 'textarea', section: 'BUILD_SUGGESTION', group: '折叠分组' }))
]
const types = ['viral_account', 'viral_content'].map((code, index) => ({ id: index + 1, code, name: code, status: 0, currentSchema: { fields: fields(index) } }))
const materials = types.map((type, index) => ({ id: index + 41, materialTypeId: type.id, materialNo: `TEST-${index}`, title: `已保存${index}`, version: 1,
  coverFileId: 99, coverPreviewUrl: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="80" height="80"%3E%3Crect width="80" height="80" fill="lightblue"/%3E%3C/svg%3E',
  availableActions: ['UPDATE'], currentVersion: { status: 'DRAFT', fields: type.currentSchema.fields, values: { [index ? 'work_title' : 'account_name']: `已保存${index}` } } }))
let release: (() => void) | undefined
let fail = false
let loads = 0
materialApi.uploadCover = async file => {
  document.documentElement.dataset.pending = 'upload'
  await new Promise<void>(resolve => { release = resolve })
  delete document.documentElement.dataset.pending
  return { fileId: 100, name: file.name, contentType: file.type, size: file.size, previewUrl: materials[0].coverPreviewUrl }
}
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (url.endsWith('/material-type/list')) { data = types; document.documentElement.dataset.loads = String(++loads) }
  else if (url.endsWith('/material/page')) data = { list: materials.filter(item => item.materialTypeId === Number(config.params.materialTypeId)), total: 1 }
  else if (config.method === 'get' && /\/material\/\d+$/.test(url)) data = materials.find(item => url.endsWith(`/${item.id}`))
  else if (config.method === 'get') data = []
  else if (config.method === 'put' || url.endsWith('/material')) {
    document.documentElement.dataset.pending = 'save'
    document.documentElement.dataset.lastWrite = url
    await new Promise<void>(resolve => { release = resolve })
    delete document.documentElement.dataset.pending
    if (fail) throw new Error('验收保存失败')
    const body = JSON.parse(config.data)
    const material = materials.find(item => item.materialTypeId === body.materialTypeId)!
    material.currentVersion.values = body.values
    material.title = body.title
    material.version++
    data = material.id
  } else if (url.endsWith('/submit')) data = true
  else throw new Error(`Unexpected fixture request: ${config.method} ${url}`)
  return { data: { code: 0, data: structuredClone(data) }, status: 200, statusText: 'OK', headers: {}, config }
}

function Fixture() {
  const location = useLocation()
  const navigation = useWorkbenchPageNavigation()!
  const [tabs, setTabs] = useState<TabItem[]>([{ key: '/home', label: '首页', closable: false }])
  const [identity, setIdentity] = useState(1)
  const [allowed, setAllowed] = useState(true)
  const currentMenu = menus.find(menu => menu.path === location.pathname)
  return <>
    <Space wrap>{menus.map(menu => <Button key={menu.path} onClick={() => void navigation.open(menu.path)}>打开{menu.name}</Button>)}
      <Button onClick={() => release?.()}>完成请求</Button>
      <Button onClick={() => { fail = !fail }}>切换保存失败</Button>
      <Button onClick={() => setIdentity(value => value + 1)}>更换身份</Button>
      <Button onClick={() => setAllowed(value => !value)}>切换授权</Button>
    </Space>
    <TabBar tabs={tabs} setTabs={setTabs} currentMenu={currentMenu} />
    <div style={{ height: 'calc(100vh - 130px)' }}>
      {allowed && RETAINED_PAGE_PATHS.map(path => (location.pathname === path || tabs.some(tab => tab.key === path)) &&
        <RetainedReviewRoute key={`${path}:${identity}`} active={location.pathname === path}>
          {path === paths[0] ? <ViralAccountDecomposePage /> : path === paths[1] ? <ViralContentDecomposePage /> : <input aria-label={path} defaultValue="旧保留页状态" />}
        </RetainedReviewRoute>)}
      {location.pathname === '/home' && <p>首页内容</p>}
    </div>
  </>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={['/home']}><App>
  <WorkbenchPageNavigation canOpen={path => menus.some(menu => menu.path === path)}><Fixture /></WorkbenchPageNavigation>
</App></MemoryRouter>)
