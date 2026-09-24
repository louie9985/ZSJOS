// Isolated browser fixture: no production data or message delivery.
import React from 'react'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { BrowserRouter } from 'react-router-dom'
import LeadAssignmentHost from '../src/components/LeadAssignmentHost'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import { api, type PendingLead } from '../src/services/api'
import '../src/styles/index.css'
const fixture = { error: false, expired: false, accepted: undefined as number | undefined }
Object.assign(window, { assignmentFixture: fixture })
api.myPendingLeads = async () => {
  if (fixture.error) throw new Error('待接客资查询暂不可用')
  return [{ id: 1, leadNo: 'KZ-TEST-001', assignmentHistoryId: fixture.expired ? 21 : 20,
    dispatchMode: 'specified', maskedName: '测试客资', intendedProducts: [], attachmentUrls: [],
    submittedAt: Date.now(), rejectable: false, deferrable: true } as PendingLead]
}
api.acceptLead = async (_id, round) => { fixture.accepted = round; return true }
const canAccept = !new URLSearchParams(location.search).has('denied')
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter>
  <OverlayCoordinatorProvider><RealtimeProvider platform="PC">
    <LeadAssignmentHost canAccept={canAccept} onCountChange={() => {}} openRequest={0}/>
  </RealtimeProvider></OverlayCoordinatorProvider>
</BrowserRouter></App></ThemeProvider>)
