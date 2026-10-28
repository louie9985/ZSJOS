import React, { useCallback } from 'react'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { MemoryRouter } from 'react-router-dom'
import BusinessTaskApproval from '../src/components/bpm/BusinessTaskApproval'
import { api, type BpmTask, type LeadAppealReviewDetail } from '../src/services/api'
import '../src/styles/index.css'
const done = new URLSearchParams(location.search).has('done')
let fail = new URLSearchParams(location.search).has('error')
const item = { id: 23, taskId: 'task-23', leadId: 1, leadNo: 'KZ-DEMO', currentCycle: true, invalidEventId: 1, roundNo: 1, status: done ? 'upheld' : 'sales_manager_reviewing', reviewStage: 'sales_manager', reason: '请核查联系记录', invalidReasonSnapshot: '联系失败', evidence: [], invalidEvidenceSnapshot: [], decisionEvidence: [] }
api.bpmBusinessTaskTarget = async () => { if (fail) { fail = false; throw new Error('定位失败测试') } return { supported: true, bizType: 'lead_appeal', route: '/zsjos/appeals', query: { appealId: 23, leadId: 1, handled: done } } }
api.leadAppealReviewDetail = async (id, taskId) => {
  if (id !== 23 || taskId !== 'task-23') throw new Error('任务关联错误')
  return { lead: { id: 1, leadNo: 'KZ-DEMO', submittedName: '申诉测试客资', qualificationStatus: 'invalid', visibleTabs: ['appeals'], availableActions: [] }, context: { records: [item], canSubmit: false, nextRoundNo: null, invalidEventId: 1 } } as unknown as LeadAppealReviewDetail
}
function Demo() {
 const onState = useCallback((dirty: boolean, busy: boolean) => { document.body.dataset.dirty = String(dirty); document.body.dataset.busy = String(busy) }, [])
 return <BusinessTaskApproval task={{ id: 'task-23', processDefinitionKey: 'zsjos_lead_appeal_review' } as BpmTask} permissions={[]} done={done} onState={onState} onComplete={() => { document.body.dataset.complete = 'true' }} />
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter><Demo/></MemoryRouter></App></ThemeProvider>)
