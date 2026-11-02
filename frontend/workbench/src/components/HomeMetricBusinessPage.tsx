import { lazy, Suspense, useMemo } from 'react'
import { Alert, Skeleton } from 'antd'
import { MetricPageScopeContext, type MetricPageScope } from '../services/metricPageScope'
import MetricPageRouter from './MetricPageRouter'

const Leads = lazy(() => import('../pages/LeadManagementPage'))
const Orders = lazy(() => import('../pages/MySalesOrderPage'))
const Students = lazy(() => import('../pages/RegistrationPages').then(m => ({ default: m.MyStudentsPage })))
const Registrations = lazy(() => import('../pages/RegistrationPages').then(m => ({ default: m.RegistrationPoolPage })))
const Appeals = lazy(() => import('../pages/LeadAppealPage'))
const Duplicates = lazy(() => import('../pages/LeadDuplicateReviewPage'))
const MediaStudents = lazy(() => import('../pages/MediaStudentsPage'))
const Sales = lazy(() => import('../pages/SubordinateSalesPage'))
const Education = lazy(() => import('../pages/EducationTeamPage'))
const Cashbacks = lazy(() => import('../pages/ManagementPages').then(m => ({ default: m.CashbackPage })))
const Withdrawals = lazy(() => import('../pages/ManagementPages').then(m => ({ default: m.WithdrawalPage })))
const Approvals = lazy(() => import('../pages/BpmApprovalCenterPage'))
const Tasks = lazy(() => import('../pages/TodayTasksPage').then(m => ({ default: m.MetricBusinessTasks })))

export default function HomeMetricBusinessPage({ contextId, operand, source, permissions, onDirtyChange }: MetricPageScope & {
  permissions: string[]; onDirtyChange: (dirty: boolean) => void
}) {
  const scope = useMemo(() => ({ contextId, operand, source }), [contextId, operand, source])
  const props = { permissions }
  const page = (() => {
    switch (source) {
      case 'lead': return <Leads {...props} metricEmbedded onDirtyChange={onDirtyChange}/>
      case 'order': return <Orders {...props}/>
      case 'student': return <Students {...props}/>
      case 'registration': return <Registrations {...props}/>
      case 'lead_appeal': return <Appeals/>
      case 'duplicate_review': return <Duplicates {...props}/>
      case 'media_student': return <MediaStudents {...props}/>
      case 'subordinate_sales': return <Sales {...props}/>
      case 'education_team': return <Education {...props}/>
      case 'cashback': return <Cashbacks {...props}/>
      case 'withdrawal': return <Withdrawals {...props}/>
      case 'bpm_todo': return <Approvals {...props} initialView="todo" metricTodo/>
      case 'business_todo': return <Tasks {...props}/>
      default: return <Alert type="error" title="该指标来源尚无可用业务页面"/>
    }
  })()
  return <MetricPageScopeContext.Provider value={scope}>
    <MetricPageRouter source={source}><Suspense fallback={<Skeleton active/>}>{page}</Suspense></MetricPageRouter>
  </MetricPageScopeContext.Provider>
}
