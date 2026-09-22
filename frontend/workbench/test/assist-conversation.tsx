// UTF-8. Synthetic HTTP only; no real account or database writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import LeadSubmitterAssistHistoryPanel from '../src/components/LeadSubmitterAssistHistoryPanel'
import { http, type ManagedLead } from '../src/services/api'
import type { AssistHistory } from '../src/services/leadSubmitterAssist'
const state = { error: '', empty: false, replyError: false, uploads: 0, payload: null as unknown, pages: [] as number[] }
Object.assign(window, { assistFixture: state })
const rows: AssistHistory[] = [{ id: 1, leadNo: 'TEST-ASSIST', status: 'completed', version: 1,
  requesterName: '发起销售', submitterName: '客资提交人', assigneeName: '协助处理人', requestedAt: '2026-09-22 10:00:00',
  problem: '第一行问题\n第二行问题', expectedAssistance: '请协助确认资料\n并联系客户', remark: '申请备注完整保留',
  responseRemark: '已联系客户\n资料确认完成', responderName: '实际回复人', respondedAt: '2026-09-22 11:00:00',
  requestAttachments: [{ infraFileId: 11, name: '申请资料.png', type: 'image/png', size: 120, url: location.origin + '/test/assist-image.png' }],
  responseAttachments: [{ infraFileId: 12, name: '回复资料.pdf', type: 'application/pdf', size: 240, url: location.origin + '/test/assist-file.pdf' }],
}, { id: 2, leadNo: 'TEST-ASSIST', status: 'pending', version: 0, problem: '第二次申请', expectedAssistance: '补充信息', requesterName: '发起销售', requestedAt: '2026-09-22 12:00:00' }]
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (url.endsWith('/history/page')) {
    if (state.error) throw new Error(state.error)
    state.pages.push(config.params.pageNo)
    data = { list: state.empty ? [] : config.params.pageNo === 2 ? [{ ...rows[1], id: 3, problem: '下一页申请' }] : rows, total: state.empty ? 0 : 11 }
  } else if (url.endsWith('/attachment/upload')) {
    state.uploads++
    data = { infraFileId: 33, originalName: '补充.png', contentType: 'image/png', fileSize: 4, fileUrl: location.origin + '/test/assist-image.png' }
  } else if (url.endsWith('/reply')) {
    state.payload = JSON.parse(config.data)
    if (state.replyError) throw new Error('回复保存失败，请重试')
    rows[1] = { ...rows[1], status: 'completed', responseRemark: JSON.parse(config.data).remark, responderName: '当前回复人', respondedAt: '2026-09-22 13:00:00' }
    data = true
  } else throw new Error(`Unexpected fixture request: ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><LeadSubmitterAssistHistoryPanel lead={{ id: 1 } as ManagedLead} canReply={!location.search.includes('readonly')} onChanged={() => {}} /></App></ThemeProvider>)
