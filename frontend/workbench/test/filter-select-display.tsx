import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider, Select } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { AdvancedFilterToolbar } from '../src/components/AdvancedFilter'
import StudentServiceFilters, { type StudentQuickFilters } from '../src/components/StudentServiceFilters'
import DeliveryClassFilters, { defaultClassFilters, type ClassFilters } from '../src/pages/DeliveryClassFilters'
import BusinessReadScope, { type BusinessReadScopeValue } from '../src/components/BusinessReadScope'
import { filterSelectDisplayProps } from '../src/components/filterSelectDisplay'
import { api, http, type AdvancedFilterGroup } from '../src/services/api'
import '../src/styles/index.css'

const long = '健康管理师高级技能等级认定项目及课程服务中心（周末学习、考前辅导与职业发展专项计划）'
const person = '测试班主任 · 中世健健康管理师项目交付服务中心第一教学工作组'
const state = { quick: {} as StudentQuickFilters, classes: defaultClassFilters, scope: {} as BusinessReadScopeValue, multiple: [] as string[], applied: undefined as AdvancedFilterGroup | undefined, template: '长期使用的人员筛选模板与课程交付专项工作组查询方案' }
Object.assign(window, { filterDisplayFixture: state })
http.defaults.adapter = async () => { throw Error('No live HTTP allowed in filter fixture') }
api.advancedFilterCatalog = async () => ({ fields: [{ fieldKey: 'service.ownerUserId', label: long, group: long, valueType: 'select', operators: ['in'], options: [{ value: '21', label: person }, { value: '22', label: long }] }], relativeDateOptions: [] })
api.advancedFilterTemplates = async () => [{ id: 1, name: state.template, filter: { logic: 'AND', conditions: [{ fieldKey: 'service.ownerUserId', operator: 'in', value: ['21'] }], groups: [] } }] as Awaited<ReturnType<typeof api.advancedFilterTemplates>>
api.simpleUsers = async () => [{ id: 21, nickname: person }] as Awaited<ReturnType<typeof api.simpleUsers>>

function Fixture() {
  const [quick, setQuick] = useState<StudentQuickFilters>({})
  const [classes, setClasses] = useState<ClassFilters>(defaultClassFilters)
  const [scope, setScope] = useState<BusinessReadScopeValue>({ readScope: 'USER' })
  const [multiple, setMultiple] = useState<string[]>([])
  const [advanced, setAdvanced] = useState<AdvancedFilterGroup>()
  const [keyword, setKeyword] = useState('')
  return <div style={{ padding: 12, display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: 24 }}>
    <section className="student-filter-toolbar"><StudentServiceFilters value={quick} onChange={value => { state.quick = value; setQuick(value) }} /></section>
    <DeliveryClassFilters value={classes} onChange={value => { state.classes = value; setClasses(value) }} context={{ projects: [{ id: 1, name: long, path: long }], homerooms: [{ id: 21, name: '测试班主任', deptName: '中世健健康管理师项目交付服务中心第一教学工作组' }] }} loading={false} keyword="" onKeyword={() => undefined} onSearch={() => undefined} onReset={() => { state.classes = defaultClassFilters; setClasses(defaultClassFilters) }} />
    <BusinessReadScope value={scope} onChange={value => { state.scope = value; setScope(value) }} />
    <Select aria-label="多选显示验证" mode="multiple" allowClear showSearch optionFilterProp="label" style={{ width: 200 }} options={[{ value: 'a', label: long }, { value: 'b', label: person }]} value={multiple} onChange={value => { state.multiple = value; setMultiple(value) }} {...filterSelectDisplayProps} />
    <AdvancedFilterToolbar scene="student_service" pageKey="student_my" placeholder="搜索" keyword={keyword} onKeyword={setKeyword} value={advanced} onChange={value => { state.applied = value; setAdvanced(value) }} />
  </div>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider theme={{ token: { motion: false } }}><App><Fixture /></App></ConfigProvider></ThemeProvider>)
