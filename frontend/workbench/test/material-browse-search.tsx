// Isolated synthetic transport: no live material, account or dictionary records are changed.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MaterialLibraryPage from '../src/pages/MaterialLibraryPage'
import { http } from '../src/services/api'
import type { Material, MaterialFieldDefinition } from '../src/services/materialApi'
import '../src/styles/index.css'

const fields: MaterialFieldDefinition[] = [
  { key: 'topic', label: '主题', type: 'text', searchable: true },
  { key: 'category', label: '分类', type: 'dict-multi', searchable: true, dictType: 'synthetic' },
  { key: 'copy', label: '文案', type: 'textarea', searchable: true },
  { key: 'rich', label: '富文本', type: 'rich-text', searchable: true },
  { key: 'works', label: '作品', type: 'repeat-group', children: [{ key: 'text', label: '说明', type: 'text', searchable: true }] },
  { key: 'private', label: '未配置检索字段', type: 'text' }
]
const svg = (width: number, height: number) => `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="#cbe5f5"/><text x="20" y="45" font-size="24">${width} × ${height}</text></svg>`)}`
const covers = [svg(800, 400), svg(400, 700), svg(400, 1800), undefined, '/synthetic-missing-cover.png']
const rows: Material[] = Array.from({ length: 23 }, (_, index) => ({
  id: index + 1, materialNo: `MAT-${index + 1}`, materialTypeId: 2, materialTypeName: '验收素材', title: `素材 ${index + 1}`,
  status: 'EFFECTIVE', source: 'MANUAL', version: 1, ownerUserId: 1, ownerName: '测试作者',
  likeCount: 0, referenceCount: 0, favoriteCount: 0, liked: false, favorited: false, pinned: false, priority: 0,
  availableActions: [], coverPreviewUrl: covers[index % 5], currentEffectiveVersionId: index + 100,
  currentVersion: { id: index + 100, materialId: index + 1, versionNo: 1, schemaVersionId: 1, status: 'EFFECTIVE',
    title: `素材 ${index + 1}`, version: 1, fields, files: [],
    values: { topic: '摄影', category: ['old'], copy: '前文'.repeat(80) + '长文命中' + '后文'.repeat(80),
      rich: '<p>富文本 &amp; 中文</p>', works: [{ text: '第一条' }, { text: '独立命中' }], private: '不可展示' },
    dictSnapshot: { category: [{ value: 'old', label: '历史分类' }] }
  }
}))
const params = new URLSearchParams(location.search)
let failed = false
http.defaults.adapter = async config => {
  const url = config.url || ''; let data: unknown
  if (url.endsWith('/material-type/list')) data = [{ id: 2, code: 'synthetic', name: '验收素材', status: 0 }]
  else if (url.endsWith('/material/page')) {
    document.documentElement.dataset.request = JSON.stringify(config.params)
    if (params.has('error') && !failed) { failed = true; throw new Error('素材加载失败（验收）') }
    if (params.has('denied')) return { data: { code: 403, msg: '无权访问素材库' }, status: 200, statusText: 'OK', headers: {}, config }
    const keyword = config.params?.keyword || ''
    const list = keyword === '不存在' ? [] : rows
    const offset = (config.params.pageNo - 1) * config.params.pageSize
    data = { list: list.slice(offset, offset + config.params.pageSize), total: list.length }
    await new Promise(resolve => setTimeout(resolve, 80))
  } else if (/\/material\/\d+$/.test(url)) data = rows.find(row => row.id === Number(url.split('/').pop()))
  else if (url.endsWith('/recommendation-account-candidates')) { document.documentElement.dataset.unexpectedRecommendation = 'true'; throw new Error('Unexpected recommendation request') }
  else if (url.includes('/dict-data/')) data = []
  else throw new Error(`Unexpected fixture request: ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<App><MemoryRouter>
  <MaterialLibraryPage permissions={['zsjos:material:query']} />
</MemoryRouter></App>)
