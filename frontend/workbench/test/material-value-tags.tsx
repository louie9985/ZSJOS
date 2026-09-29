// Isolated rendering fixture; no live API requests or records.
import { createRoot } from 'react-dom/client'
import { MemoryRouter } from 'react-router-dom'
import { App } from 'antd'
import ViralContentMaterialForm from '../src/components/ViralContentMaterialForm'
import type { Material, MaterialType, MaterialFieldDefinition } from '../src/services/materialApi'
import '../src/styles/index.css'

const choices = [
  ['account_platform', '账号平台', ['小红书']],
  ['viral_content_types', '爆款类型', ['形式爆款', '图文爆款', '流量爆款']],
  ['adapted_account_types', '适配账号类型', ['类型甲', '类型乙']],
  ['adapted_business_positions', '适配业务定位', ['业务甲', '包含顿号、但属于一个选项']],
  ['adapted_account_stages', '适配账号期段', ['S2 冷启动', 'S3 内容验证', 'S4 咨询验证', 'S5 客资验证', 'S6 稳定增长']],
] as const
const fields: MaterialFieldDefinition[] = choices.map(([key, label], index) => ({
  key, label, type: index ? 'dict-multi' : 'dict-single', dictType: 'fixture', required: true,
  section: index < 2 ? 'ACCOUNT_DETAIL' : 'BUILD_SUGGESTION'
}))
fields.push({ key: 'account_id', label: '账号ID', type: 'text', section: 'ACCOUNT_DETAIL' },
  { key: 'missing', label: '缺失历史快照', type: 'dict-single', dictType: 'fixture', section: 'BUILD_SUGGESTION' },
  { key: 'empty', label: '空选项', type: 'dict-multi', section: 'BUILD_SUGGESTION' })
const values: Record<string, unknown> = { account_id: '27247641852', missing: 'old', empty: [] }
const dictSnapshot: Record<string, unknown> = {}
choices.forEach(([key, , labels], index) => {
  const snapshots = labels.map((label, i) => ({ value: `${key}-${i}`, label }))
  values[key] = index ? snapshots.map(item => item.value) : snapshots[0].value
  dictSnapshot[key] = index ? snapshots : snapshots[0]
})
const material = { id: 1, title: '素材标签测试', currentVersion: { id: 1, status: 'EFFECTIVE', fields, values, dictSnapshot, files: [] } } as unknown as Material
const type = { id: 1, code: 'viral_content', currentSchema: { fields } } as MaterialType
createRoot(document.getElementById('root')!).render(<App><MemoryRouter><section className="workspace-page">
  <ViralContentMaterialForm mode="view" material={material} type={type} dicts={{ fixture: [{ value: 'old', label: '当前名称不可替代历史' }] }} onClose={() => {}} onSaved={() => {}} />
</section></MemoryRouter></App>)
