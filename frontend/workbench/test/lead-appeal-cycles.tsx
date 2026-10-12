import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import LeadAppealPanel from '../src/components/LeadAppealPanel'
import { api, type LeadAppeal, type ManagedLead } from '../src/services/api'

const mode = new URLSearchParams(location.search).get('mode') ?? 'new'
const old: LeadAppeal = { id: 1, leadId: 1, leadNo: 'KZ-CYCLE-FIXTURE', leadName: '周期测试', roundNo: 3,
  applicantUserName: '提交人', reviewerUserName: '审核人', reviewStage: 'chairman', status: 'overturned', reason: '历史申诉', evidence: [], invalidEvidenceSnapshot: [],
  decisionEvidence: [], submittedAt: Date.now(), canSubmitNextRound: false, currentCycle: false, invalidEventId: 10 } as LeadAppeal
const current = { ...old, id: 2, invalidEventId: 20, currentCycle: true,
  roundNo: mode === 'final' ? 3 : 1, reviewStage: mode === 'final' ? 'chairman' : 'sales_manager',
  status: mode === 'pending' ? 'sales_manager_reviewing' : 'upheld' } as LeadAppeal
api.leadAppealContext = async () => ({ invalidEventId: 20,
  canSubmit: mode === 'new' || mode === 'next', nextRoundNo: mode === 'new' ? 1 : mode === 'next' ? 2 : null,
  records: mode === 'new' ? [old] : [old, current] })
api.submitLeadAppeal = async (_id, data) => { (window as unknown as { submitted: unknown }).submitted = data; return 3 }
const lead = { id: 1, submittedName: '周期测试', status: 'invalid', relationTypes: ['submitter'] } as ManagedLead
createRoot(document.getElementById('root')!).render(<App><main style={{ maxWidth: 900, margin: '24px auto', padding: 16 }}>
  <LeadAppealPanel lead={lead} onChanged={() => {}} />
</main></App>)
