// UTF-8. Actual page with isolated synthetic transport; never submits real business data.
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import { STORAGE_KEYS } from '../src/constants'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import LeadAppealPage from '../src/pages/LeadAppealPage'
import { api, http, type LeadAppeal, type ManagedLead } from '../src/services/api'
import '../src/styles/index.css'
const params = new URLSearchParams(location.search)
localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify({ inboxLayoutMode: params.get('layout') || 'table' }))
const state = { posts: [] as unknown[], fail: '', uploadFail: false, reads: 0, lists: 0, denied: params.has('denied'), done: false }
Object.assign(window, { appealReviewFixture: state })
const evidence = { infraFileId: 10, originalName: '核实记录.png', contentType: 'image/png', fileSize: 128, fileUrl: location.origin + '/test/appeal-evidence.png' }
const old = { id: 100, leadId: 1, leadNo: 'KZ-APPEAL-TEST', leadName: '申诉布局测试', roundNo: 3, invalidEventId: 10, currentCycle: false,
  reviewStage: 'chairman', status: 'upheld', applicantUserId: 8, applicantUserName: '申请人', reviewerUserName: '历史处理人', reason: '历史申诉说明',
  evidence: [], invalidEvidenceSnapshot: [], decisionEvidence: [], submittedAt: Date.UTC(2026, 9, 10), decidedAt: Date.UTC(2026, 9, 11),
  decisionReason: '历史裁决意见', canSubmitNextRound: false } as LeadAppeal
let current = { ...old, id: 101, roundNo: 1, invalidEventId: 20, currentCycle: true, taskId: 'task-101', reviewStage: 'sales_manager', status: 'sales_manager_reviewing',
  reason: '客户补充了近期咨询和明确的学习意向，请根据附件重新核实本次无效判定。', invalidReasonSnapshot: '非目标客户',
  invalidDescriptionSnapshot: '首次沟通未明确学习需求，提交人补充材料后发起申诉。', evidence: [evidence], invalidEvidenceSnapshot: [{ ...evidence, infraFileId: 11, originalName: '原判凭证.png' }],
  submittedAt: Date.UTC(2026, 9, 12, 6, 2), decidedAt: undefined, decisionReason: undefined } as LeadAppeal
const other = { ...current, id: 102, leadId: 2, leadNo: 'KZ-OTHER', leadName: '另一条待办', taskId: 'task-102' }
const lead = (): ManagedLead => ({ id: 1, leadNo: current.leadNo, submittedName: current.leadName, status: current.status === 'overturned' ? 'valid' : 'invalid',
  qualificationStatus: current.status === 'overturned' ? 'valid' : 'invalid', availableActions: [], relationTypes: [], attachments: [], intendedProducts: [],
  visibleTabs: params.has('restricted') ? ['appeals'] : ['overview', 'appeals'], overviewVisible: true,
  appealSummary: { id: 101, roundNo: 1, status: current.status, reviewStage: 'sales_manager', submittedAt: current.submittedAt, canHandle: !state.denied && !state.done } } as ManagedLead)
const previousRounds: LeadAppeal[] = params.has('full') ? [1, 2].map(roundNo => ({ ...current, id: 110 + roundNo, roundNo, taskId: undefined, reviewStage: roundNo === 1 ? 'sales_manager' : 'quality', status: 'upheld', reason: `第 ${roundNo} 轮补充说明`, decisionReason: `第 ${roundNo} 轮维持无效意见`, evidence: [], invalidEvidenceSnapshot: [], decisionEvidence: [], decidedAt: Date.UTC(2026, 9, 11, roundNo) })) : []
if (params.has('full')) current = { ...current, roundNo: 3, reviewStage: 'chairman', status: 'chairman_reviewing' }
const context = () => ({ invalidEventId: 20, nextRoundNo: null, canSubmit: false, records: [old, ...previousRounds, { ...current, taskId: state.denied || state.done ? undefined : 'task-101' }] })
api.leadAppealReviewDetail = async () => {
  state.reads++; if (params.has('loadError')) throw new Error('当前账号无权读取申诉任务')
  return { lead: lead(), context: context() }
}
api.leadAppealContext = async () => context()
api.leadAppealInboxPage = async handled => { state.lists++; return { list: handled ? state.done ? [current] : [] : state.done ? [other] : [current, other], total: state.done ? 1 : 2 } }
api.leadAppealInboxCursor = async handled => { const result = await api.leadAppealInboxPage(handled, {}); return { list: result.list, hasMore: false, nextCursor: undefined } }
api.decideLeadAppeal = async (id, decision, payload) => {
  state.posts.push({ id, decision, payload }); await new Promise(resolve => setTimeout(resolve, 100))
  if (state.fail) throw new Error(state.fail)
  state.done = true; current = { ...current, status: decision === 'overturn' ? 'overturned' : 'upheld', decisionReason: payload.reason, decisionEvidence: [], decidedAt: Date.now() }
  return true
}
api.uploadLeadAppealImage = async () => { if (state.uploadFail) throw new Error('附件上传失败'); return evidence }
http.defaults.adapter = async config => ({ data: { code: 0, data: [] }, status: 200, statusText: 'OK', headers: {}, config })
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><LeadAppealPage /></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
