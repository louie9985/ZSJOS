// Isolated real-component fixture; all data is fictional and no business requests are sent.
import React from 'react'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import FollowUpModal from '../src/components/FollowUpModal'
import LeadFollowUpPanel from '../src/components/LeadFollowUpPanel'
import { api, type ManagedLead, type LeadFollowUpCreateRequest, type LeadFollowUp, type DictData } from '../src/services/api'
import { DICT_TYPE } from '../src/constants'
import '../src/styles/index.css'
const fixture = { requests: [] as LeadFollowUpCreateRequest[], changed: 0 }
Object.assign(window, { followUpFixture: fixture })
api.dictDataByType = async type => (type === DICT_TYPE.LEAD_FOLLOW_UP_QUICK_NOTE ? [] :
  type === DICT_TYPE.LEAD_SALES_STAGE ? [{ value: 'contacted', label: '已触达' }, { value: 'intent_customer', label: '意向客户' }] :
  [{ value: 'test', label: '测试选项' }]) as DictData[]
api.leadFollowUpPage = async () => ({ list: [], total: 0 })
api.createLeadFollowUp = async (_id, data) => { fixture.requests.push(data); return { id: 1 } as LeadFollowUp }
const params = new URLSearchParams(location.search)
const lead = { id: 1, leadNo: 'KZ-TEST-001', status: params.get('status') || 'won', salesStage: 'contacted',
  salesStageLabelSnapshot: '已触达', availableActions: ['ADD_FOLLOW_UP'] } as ManagedLead
const changed = () => { fixture.changed++ }
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><OverlayCoordinatorProvider>
  {params.has('panel') ? <LeadFollowUpPanel lead={lead} open onOpen={() => {}} onClose={() => {}} onChanged={changed}/>
    : <FollowUpModal lead={lead} open onClose={() => {}} onSuccess={changed}/>}
</OverlayCoordinatorProvider></App></ThemeProvider>)
