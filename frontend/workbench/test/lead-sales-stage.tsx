// UTF-8. Synthetic HTTP transport; no real business requests or writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import LeadFollowUpPanel from '../src/components/LeadFollowUpPanel'
import FollowUpModal from '../src/components/FollowUpModal'
import { AdvancedFilterToolbar } from '../src/components/AdvancedFilter'
import { http, type ManagedLead, type LeadFollowUp, type AdvancedFilterGroup } from '../src/services/api'
const state = { dictError: location.search.includes('error'), saveError: false, saves: 0, payload: null as Record<string, unknown> | null, filter: null as unknown }
Object.assign(window, { stageFixture: state })
const dictionaries = [
  { dictType: 'zsjos_lead_sales_stage', value: 'contacted', label: '管理员改名后的已触达' },
  { dictType: 'zsjos_lead_sales_stage', value: 'pending_contact', label: '待触达' },
  { dictType: 'zsjos_lead_sales_stage', value: 'intent_customer', label: '意向客户' },
  { dictType: 'zsjos_lead_follow_up_method', value: 'phone', label: '电话' },
  { dictType: 'zsjos_lead_follow_up_result', value: 'contact', label: '取得联系' },
]
const original = { id: 1, leadNo: 'TEST-STAGE', submittedName: '销售阶段测试', salesStage: 'contacted', salesStageLabelSnapshot: '历史已触达名称' } as ManagedLead
const records = [{ id: 1, leadId: 1, recordScope: 'lead', operatorUserId: 1, operatorName: '测试人员', occurredAt: 1790042400000, method: 'phone', methodLabel: '电话', result: 'contact', resultLabel: '取得联系', images: [], firstInAssignment: false, salesStageBefore: 'pending_contact', salesStageBeforeLabelSnapshot: '历史待触达', salesStageAfter: 'contacted', salesStageAfterLabelSnapshot: '历史已触达名称' }, { id: 2, leadId: 1, recordScope: 'opportunity', operatorUserId: 1, occurredAt: 1790000000000, method: 'phone', methodLabel: '电话', result: 'contact', resultLabel: '取得联系', images: [], firstInAssignment: false }] as LeadFollowUp[]
http.defaults.adapter = async config => {
  const url = config.url || ''; let data: unknown
  if (url.includes('/dict-data/')) { if (state.dictError) throw new Error('销售阶段字典读取失败'); data = dictionaries }
  else if (url.endsWith('/follow-ups/page')) data = { list: records, total: records.length }
  else if (url.endsWith('/follow-ups')) {
    state.saves++
    state.payload = JSON.parse(config.data)
    if (state.saveError) throw new Error('阶段提交失败，请重试')
    const value = String(state.payload!.salesStage)
    original.salesStage = value; original.salesStageLabelSnapshot = value === 'contacted' ? '历史已触达名称' : dictionaries.find(item => item.dictType === 'zsjos_lead_sales_stage' && item.value === value)?.label
    data = {}; records.unshift({ ...records[0], id: records.length + 1, salesStageAfter: value, salesStageAfterLabelSnapshot: original.salesStageLabelSnapshot })
  } else if (url.endsWith('/catalog')) data = { fields: [
    { fieldKey: 'lead.salesStage', group: '状态与进度', label: '当前销售阶段', valueType: 'select', operators: ['in','not_in','is_empty','is_not_empty'], optionSource: 'dict:zsjos_lead_sales_stage', options: [] },
    { fieldKey: 'lead.ownerDeptId', group: '归属与人员', label: '负责人所属组织（含下级）', valueType: 'select', operators: ['in','not_in'], options: [{ value: '10', label: '测试中心' }, { value: '11', label: '测试中心 / 测试部门' }] },
    { fieldKey: 'lead.convertedAt', group: '时间', label: '成交时间', valueType: 'date', operators: ['relative','between'], options: [] },
  ], relativeDateOptions: [{ value: 'this_month', label: '本月' }] }
  else if (url.endsWith('/visible-list')) data = []
  else throw new Error(`Unexpected request: ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
function Fixture() {
  const [lead,setLead] = useState({ ...original }); const [open,setOpen] = useState(true); const [modal,setModal] = useState(false)
  const [filter,setFilter] = useState<AdvancedFilterGroup>()
  return <main style={{ padding: 12 }}><p data-testid="current">当前销售阶段：{lead.salesStageLabelSnapshot || '未记录'}</p>
    <AdvancedFilterToolbar scene="lead" pageKey="lead_management" keyword="" value={filter} onKeyword={() => {}} onChange={value => {state.filter=value;setFilter(value)}}/>
    <Button onClick={() => setModal(true)}>弹窗跟进</Button>
    <LeadFollowUpPanel lead={lead} open={open} onOpen={location.search.includes('readonly') ? undefined : () => setOpen(true)} onClose={() => setOpen(false)} onChanged={() => setLead({ ...original })}/>
    <FollowUpModal lead={lead} open={modal} onClose={() => setModal(false)} onSuccess={() => setLead({ ...original })}/>
  </main>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><OverlayCoordinatorProvider><Fixture/></OverlayCoordinatorProvider></App></ThemeProvider>)
