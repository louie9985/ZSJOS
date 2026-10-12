// UTF-8. Synthetic fixture; business APIs are never called.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import LeadDetailOverview from '../src/components/LeadDetailOverview'
import { http, type ManagedLead } from '../src/services/api'

http.defaults.adapter = async config => ({ data: { code: 0, data: { list: [], total: 0 } }, status: 200, statusText: 'OK', headers: {}, config })
const scenarios: Partial<ManagedLead>[] = [
  { sourceType: 'internal_new_media', sourceLabel: '新媒体提交', sourceUserName: '验证新媒体', ownerUserName: '验证销售', providerOwnerType: 'system_user', providerOwnerId: 1, providerOwnerNameSnapshot: '验证新媒体', providerIdentityLabel: '新媒体提供方' },
  { providerOwnerType: 'partner', providerOwnerId: 2, providerIdentityLabel: '兼职' },
  { providerIdentityLabel: '销售本人（未关联新媒体）' },
  { providerIdentityLabel: '教务本人（未关联新媒体）' },
  { providerIdentityLabel: '提供方待核实' },
  { providerOwnerType: 'system_user', providerOwnerId: 3, providerIdentityLabel: '员工（身份待核实）' },
  { providerOwnerType: 'system_user', providerOwnerId: 4, providerOwnerNameSnapshot: '历史提供方' },
  { providerOwnerType: 'partner', providerOwnerId: 5, providerOwnerNameSnapshot: '已隐藏', providerIdentityLabel: '兼职' },
]
function Fixture() {
  const [index, setIndex] = useState(0)
  const lead = { id: 1, leadNo: 'KZ-VERIFY', personId: 1, submittedName: '验证客资', sourceType: 'sales_self_sourced', attachments: [], ...scenarios[index] } as ManagedLead
  return <><Space wrap>{scenarios.map((_, i) => <Button key={i} onClick={() => setIndex(i)}>场景{i + 1}</Button>)}</Space>
    <LeadDetailOverview lead={lead} categoryLabel={() => '未记录'} channelLabel={() => '未记录'} showFollowUp={false} profileVariant="contact-rows"/>
  </>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture/></App></ThemeProvider>)
