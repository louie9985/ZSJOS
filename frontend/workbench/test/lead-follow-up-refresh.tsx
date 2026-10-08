// UTF-8. Isolated browser fixture with synthetic API responses only.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import { RealtimeProvider } from '../src/components/RealtimeProvider'
import LeadManagementPage from '../src/pages/LeadManagementPage'
import LeadFollowUpCalendarPage from '../src/pages/LeadFollowUpCalendarPage'
import { api, type ManagedLead, type LeadFollowUp, type DictData } from '../src/services/api'
import { leadCalendarApi } from '../src/services/leadCalendar'
import { DICT_TYPE, STORAGE_KEYS } from '../src/constants'
import dayjs from 'dayjs'
import '../src/styles/index.css'
const state = { records: [] as LeadFollowUp[], reads: 0, fail: false, posts: 0, leadId: 1 }
Object.assign(window, { refreshFixture: state })
localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify({ inboxLayoutMode: new URLSearchParams(location.search).get('layout') || 'split' }))
const lead = (id = 1) => ({ id, leadNo: `KZ-TEST-${id}`, submittedName: `刷新测试${id}`, status: 'won', leadCategory: 'A',
  leadCategoryLabelSnapshot: 'A类', salesStage: 'contacted', salesStageLabelSnapshot: '已触达',
  visibleTabs: ['follow-ups'], overviewVisible: true, availableActions: [{ code: 'ADD_FOLLOW_UP', enabled: true }], attachments: [], intendedProducts: [] }) as ManagedLead
api.dictDataByType = async type => (type === DICT_TYPE.LEAD_FOLLOW_UP_QUICK_NOTE ? [] : type === DICT_TYPE.LEAD_SALES_STAGE ? [{ value: 'contacted', label: '已触达' }] : type === DICT_TYPE.LEAD_CATEGORY ? [{value:'A',label:'A类'}] : [{value:'test',label:'测试选项'}]) as DictData[]
api.leadInboxFilterProfile = async () => ({ groups: [{ key: 'all', label: '全部', sections: [] }] })
api.allLeadPage = async () => ({ list: [lead()], total: 1 })
api.managedLead = async id => lead(id)
api.leadFollowUpPage = async () => { state.reads++; if (state.fail) throw new Error('测试记录加载失败'); return { list: [...state.records], total: state.records.length } }
api.createLeadFollowUp = async (id, data) => { state.posts++; const record = { id: state.records.length+1, leadId: id, occurredAt: Date.now(), remark: data.remark, methodLabel:'测试方式', resultLabel:'测试结果', operatorName:'测试销售', images:[] } as LeadFollowUp; state.records.unshift(record); return record }
leadCalendarApi.days = async () => [{ date: dayjs().format('YYYY-MM-DD'), count: 1 }]
leadCalendarApi.cards = async () => ({ list: [{ lead: lead(state.leadId), deadline: Date.now(), canReadFollowUp: true }], total: 1 })
function Fixture() {
  const [calendar, setCalendar] = useState(false)
  return <><button onClick={() => setCalendar(!calendar)}>切换测试页面</button>
    <div hidden={calendar}><LeadManagementPage permissions={['*:*:*']}/></div>
    <div hidden={!calendar}><LeadFollowUpCalendarPage permissions={['*:*:*']}/></div></>
}
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider><RealtimeProvider platform="PC"><Fixture/></RealtimeProvider></OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
