import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import PointsRedemptionPage from '../src/pages/PointsRedemptionPage'
import PointsPackagePage from '../src/pages/PointsPackagePage'
import { pointsApi, type PointsRequest } from '../src/services/pointsRedemptionApi'
import { http } from '../src/services/api'
import { executeNotifyMessageAction } from '../src/services/notifyMessageAction'
const state = new URLSearchParams(location.search).get('state')
http.defaults.adapter = async () => { throw new Error('Isolated fixture: no live API') }
let rows: PointsRequest[] = []
let reads = 0
pointsApi.packages = async () => [{ id: 1, name: '5000积分套餐', creditsAmount: 5000, validDays: 30, enabled: true }]
pointsApi.page = async () => { if (state === 'error' && reads++ === 0) throw new Error('申请加载失败（模拟）'); return { list: rows, total: rows.length } }
pointsApi.upload = async () => ({ id: 9, name: '申请说明.pdf' })
pointsApi.create = async data => { if (data.attachmentIds.length !== 1) throw new Error('附件缺失'); rows = [{ id: 1, requestNo: 'JFTEST001', applicantName: '测试员工', packageName: '5000积分套餐', creditsAmount: 5000, validDays: 30,
  reason: data.reason, approvalStatus: 'pending', issueStatus: 'waiting', processInstanceId: 'process', canReveal: false, submittedAt: Date.now(), attachments: [{ id: 9, name: '申请说明.pdf', url: 'about:blank' }] }]; return 1 }
pointsApi.get = async id => { const row = rows.find(r => r.id === id); if (!row) throw new Error('申请不存在'); return { ...row } }
pointsApi.reveal = async () => 'KM-ISOLATED-TEST-CODE-0123456789'
pointsApi.action = async (_id, action) => { if (action === 'cancel') rows[0].approvalStatus = 'cancelled'; return true }
Object.assign(window, { approvePoints: () => { rows[0].approvalStatus = 'approved'; rows[0].issueStatus = 'issued'; rows[0].codeStatus = 'available'; rows[0].canReveal = true } })
Object.assign(window, { testPointsNotification: async (id: number) => {
  const routes: string[] = [], warnings: string[] = []
  await executeNotifyMessageAction({ id: 5, bizId: id, bizType: 'points-redemption', actionType: 'business_detail', readStatus: true,
    templateNickname: '', templateContent: '', templateType: 1, createTime: Date.now() },
    { navigate: ((path: string) => { routes.push(path) }) as never, warn: text => { warnings.push(text) }, refreshUnreadCount: async () => {} })
  return { routes, warnings }
} })
const permissions = state === 'denied' ? [] : state === 'packages-readonly' ? ['zsjos:points-redemption:package'] : ['*:*:*']
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter>{state?.startsWith('packages') ? <PointsPackagePage permissions={permissions}/> : <PointsRedemptionPage permissions={permissions}/>}</BrowserRouter></App></ThemeProvider>)
