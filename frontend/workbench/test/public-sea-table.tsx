import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import LeadPublicSeaPoolPage from '../src/pages/LeadPublicSeaPoolPage'
import { api, type LeadPublicSeaPoolItem } from '../src/services/api'
import '../src/styles/index.css'
// Synthetic fixture only; no business service writes.
const state = { fail: false, calls: [] as unknown[], assignments: [] as unknown[] }
Object.assign(window, { publicSeaFixture: state })
const rows: LeadPublicSeaPoolItem[] = Array.from({length: 25}, (_, i) => ({cycleId: i + 1, leadId: i + 1, cycleNo: 1, leadNo: `L-TEST-${i + 1}`, submittedName: `验收客资${i + 1}`, submittedMobile: '13800000000', originalOwnerUserId: 1, originalOwnerUserName: '验收负责人', frozenDeptId: 1, frozenDeptName: '验收部门', status: 'waiting_assignment', ownershipStartedAt: '2026-10-01T08:00:00', enteredAt: '2026-10-20T08:00:00', dueAt: '2026-11-01T08:00:00', availableActions: i === 0 ? ['ASSIGN','EXIT','REQUEST_TRANSFER'] : [] }))
api.publicSeaPoolPage = async params => { state.calls.push(params); if(state.fail) throw new Error('验收查询失败'); const filtered = rows.filter(r => !params.keyword || r.leadNo.includes(params.keyword)); return {list: filtered.slice((params.pageNo-1)*params.pageSize, params.pageNo*params.pageSize), total: filtered.length} }
api.publicSeaPoolCounts = async () => ({all:25})
api.publicSeaPoolFilterProfile = async () => ({groups:[{key:'all',label:'全部',sections:[{key:'status',label:'状态',options:[{key:'waiting',label:'待指派'}]}]}]})
api.managedLead = async () => { throw new Error('验收详情错误') }
api.publicSeaPoolCandidates = async () => [{id:2,nickname:'验收协同销售'}]
api.assignPublicSeaPool = async (...args) => {state.assignments.push(args)}
api.advancedFilterCatalog = async () => ({scene:'lead',fields:[],relativeDateOptions:[]})
api.advancedFilterTemplates = async () => []
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><ConfigProvider locale={zhCN}><App><LeadPublicSeaPoolPage/></App></ConfigProvider></ThemeProvider></MemoryRouter>)
