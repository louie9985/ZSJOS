// UTF-8. Synthetic display-only fixture; no backend requests.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button } from 'antd'
import LeadDetailOverview from '../src/components/LeadDetailOverview'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import type { ManagedLead } from '../src/services/api'
const long = 'wechat_abcdefghijklmnopqrstuvwxyz0123456789'.repeat(5)
function Fixture() {
  const [id, setId] = useState(1)
  const params = new URLSearchParams(location.search)
  const lead = { id, leadNo: 'TEST-CONTACT-0001', submittedName: params.has('empty') ? undefined : '测试姓名', submittedMobile: params.has('empty') ? undefined : '+8613800000000', submittedWechatId: params.has('empty') ? undefined : long } as ManagedLead
  return <App><Button onClick={() => setId(id + 1)}>切换客资</Button><LeadDetailOverview lead={lead} profileVariant={params.has('default') ? 'default' : 'contact-rows'} categoryLabel={() => '-'} channelLabel={() => '-'} showFollowUp={false} slots={{ latestActivity: <span>测试动态</span>, timeline: <span>测试流转</span>, taskStatus: <span>测试状态</span> }} /></App>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><Fixture /></ThemeProvider>)
