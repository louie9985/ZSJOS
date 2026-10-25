// Browser fixture uses synthetic transport; no business API writes.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MaterialLibraryPage from '../src/pages/MaterialLibraryPage'
import ViralContentDecomposePage from '../src/pages/ViralContentDecomposePage'
import { http } from '../src/services/api'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
const account = params.has('account')
const key = account ? 'account_name' : 'work_title'
const fields = [{ key, label: '验收标题', type: 'text', section: 'ACCOUNT_DETAIL' }]
const types = [{ id: 2, code: account ? 'viral_account' : 'viral_content', name: '验收类型', status: 0, currentSchema: { fields } }]
let material = { id: 42, materialTypeId: 2, title: '验收草稿', materialNo: 'TEST-42', status: 'DRAFT', version: 1,
  coverFileId: 99, availableActions: params.has('no-action') ? [] : ['UPDATE', ...(params.has('delete') ? ['DELETE_DRAFT'] : [])],
  currentVersion: { id: 43, versionNo: 1, status: 'DRAFT', processInstanceId: 'withdrawn-process', fields, values: { [key]: '验收草稿' }, dictSnapshot: {}, files: [] } }
let writes = 0
let failed = false
let deleted = false
let deletes = 0
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (url.endsWith('/material-type/list')) data = types
  else if (url.endsWith('/material/page')) {
    if (deleted && document.documentElement.dataset.refreshError) throw new Error('删除后列表刷新失败')
    data = { list: deleted ? [] : [material], total: deleted ? 0 : 1 }
  }
  else if (url.endsWith('/material/42/draft') && config.method === 'delete') {
    if (config.params?.expectedVersion !== material.version) throw new Error('expectedVersion mismatch')
    deletes++; document.documentElement.dataset.deletes = String(deletes)
    await new Promise(resolve => setTimeout(resolve, 300))
    if (document.documentElement.dataset.deleteError) throw new Error(document.documentElement.dataset.deleteError)
    deleted = true; data = true
  }
  else if ((url.endsWith('/material/42') && config.method === 'put') || (url.endsWith('/material') && config.method === 'post')) {
    if (params.has('save-error')) throw new Error('验收保存失败')
    const body = JSON.parse(config.data)
    material = { ...material, title: body.title, version: material.version + 1,
      currentVersion: { ...material.currentVersion, values: body.values } }
    writes++; document.documentElement.dataset.writes = String(writes); data = 42
  } else if (url.endsWith('/material/42/submit')) { document.documentElement.dataset.submitted = '42'; data = true }
  else if (url.endsWith('/material/42')) {
    await new Promise(resolve => setTimeout(resolve, 150))
    if (params.has('error') && !failed) { failed = true; throw new Error('验收详情失败') }
    data = structuredClone(material)
  } else if (config.method === 'get') data = []
  else throw new Error(`Unexpected write: ${config.method} ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const permissions = [ ...(params.has('no-permission') ? [] : ['zsjos:material:update']),
  ...(params.has('delete') && !params.has('no-delete-permission') ? ['zsjos:material:delete-draft'] : []),
  ...(params.has('no-submit') ? [] : ['zsjos:material:submit']) ]
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={[location.pathname + location.search]}><App>{params.has('standalone')
  ? <ViralContentDecomposePage /> : <MaterialLibraryPage permissions={permissions} />}</App></MemoryRouter>)
