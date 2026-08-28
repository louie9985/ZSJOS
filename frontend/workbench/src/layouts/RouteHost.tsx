import { Card, Result, Typography } from 'antd'
import { APP_ROUTES } from '../constants'
import { resolveWorkbenchComponent, WORKBENCH_COMPONENT } from '../services/menuComponentRegistry'
import type { WorkbenchMenu } from '../services/api'
import { Navigate, useLocation } from 'react-router-dom'
import LeadSubmissionPage from '../pages/LeadSubmissionPage'
import LeadManagementPage from '../pages/LeadManagementPage'
import LeadAssignmentPage from '../pages/LeadAssignmentPage'
import LeadClaimPoolPage from '../pages/LeadClaimPoolPage'
import LeadAgingPoolPage from '../pages/LeadAgingPoolPage'
import LeadDuplicateReviewPage from '../pages/LeadDuplicateReviewPage'
import LeadComplaintPage from '../pages/LeadComplaintPage'
import TodayTasksPage from '../pages/TodayTasksPage'
import WorkPlanPage from '../pages/WorkPlanPage'
import MessageInboxPage from '../pages/MessageInboxPage'
import LeadAppealPage from '../pages/LeadAppealPage'
import SalesOrderApprovalPage from '../pages/SalesOrderApprovalPage'
import MySalesOrderPage from '../pages/MySalesOrderPage'
import SubordinateSalesPage from '../pages/SubordinateSalesPage'
import SubordinatePartnerPage from '../pages/SubordinatePartnerPage'
import ExternalRepurchasePage from '../pages/ExternalRepurchasePage'
import ExportTaskPage from '../pages/ExportTaskPage'
import {
  BusinessAuditPage,
  CashbackPage,
  ImpersonationPage,
  MaintenancePage,
  NotifyRulePage,
  PartnerPage,
  PersonnelPage,
  UserRelationPage,
  WithdrawalPage
} from '../pages/ManagementPages'
import {
  LeadFilterConfigPage,
  LeadFollowUpRuleConfigPage,
  LeadRuleConfigPage,
  ProductConfigPage,
  WorkPlanConfigPage
} from '../pages/ConfigurationPages'
import { MyStudentsPage, RegistrationChecklistConfigPage, RegistrationPoolPage, StudentContactConfigPage, StudentContactExceptionsPage } from '../pages/RegistrationPages'
import { ProductionTicketsPage } from '../pages/MediaFeaturePage'
import MediaStudentsPage from '../pages/MediaStudentsPage'
import MediaCalendarPage from '../pages/MediaCalendarPage'
import EamAssetPage from '../pages/EamAssetPage'
import FeedbackPage from '../pages/FeedbackPage'
import WorkOrderCenterPage from '../pages/WorkOrderCenterPage'

interface RouteHostProps {
  menu?: WorkbenchMenu
  permissions: string[]
  roles: string[]
  onOpenAssignment: () => void
}

/**
 * 根据当前菜单路径/组件名渲染对应业务页面。
 * 未迁移的菜单显示占位提示。
 */
export default function RouteHost({ menu, permissions, roles, onOpenAssignment }: RouteHostProps) {
  const location = useLocation()
  if (resolveWorkbenchComponent(menu?.component) === WORKBENCH_COMPONENT.LEAD_APPEAL) return <LeadAppealPage/>
  if (resolveWorkbenchComponent(menu?.component) === WORKBENCH_COMPONENT.SUBORDINATE_SALES) return <SubordinateSalesPage permissions={permissions}/>
  if (resolveWorkbenchComponent(menu?.component) === WORKBENCH_COMPONENT.SUBORDINATE_PARTNER) return <SubordinatePartnerPage permissions={permissions}/>
  if (resolveWorkbenchComponent(menu?.component) === WORKBENCH_COMPONENT.MEDIA_CALENDAR) return <MediaCalendarPage/>
  if (menu?.path === APP_ROUTES.LEAD_MANAGEMENT) return <LeadManagementPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_SUBMISSION) return <LeadSubmissionPage/>
  if (menu?.path === APP_ROUTES.LEAD_SELF_SOURCED) return <LeadSubmissionPage selfSourced/>
  if (menu?.path === APP_ROUTES.LEAD_COMPLAINTS) return <LeadComplaintPage/>
  if (menu?.path === APP_ROUTES.SUBMITTED_LEADS) return <Navigate replace to={APP_ROUTES.LEAD_MANAGEMENT}
    state={{ ...(location.state || {}), relationScope: 'submitted' }}/>
  if (menu?.path === APP_ROUTES.OWNED_LEADS) return <Navigate replace to={APP_ROUTES.LEAD_MANAGEMENT}
    state={{ ...(location.state || {}), relationScope: 'owned' }}/>
  if (menu?.path === APP_ROUTES.LEAD_ASSIGNMENT) return <LeadAssignmentPage/>
  if (menu?.path === APP_ROUTES.LEAD_DUPLICATE_REVIEW) return <LeadDuplicateReviewPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_CLAIM_POOL) {
    return <LeadClaimPoolPage canClaim={permissions.includes('zsjos:lead:claim')}/>
  }
  if (menu?.path === APP_ROUTES.LEAD_AGING_POOL) return <LeadAgingPoolPage/>
  if (menu?.path === APP_ROUTES.SUBORDINATE_SALES) return <SubordinateSalesPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.SUBORDINATE_PARTNERS) return <SubordinatePartnerPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.TODAY_TASKS) return <TodayTasksPage permissions={permissions} onOpenAssignment={onOpenAssignment}/>
  if (menu?.path === APP_ROUTES.WORK_PLANS) return <WorkPlanPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_APPEALS) return <LeadAppealPage/>
  if (menu?.path === APP_ROUTES.MY_SALES_ORDERS) return <MySalesOrderPage/>
  if (menu?.path === APP_ROUTES.TEAM_SALES_ORDERS) return <MySalesOrderPage team/>
  if (menu?.path === APP_ROUTES.SALES_ORDER_APPROVALS) return <SalesOrderApprovalPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.EXTERNAL_REPURCHASE) return <ExternalRepurchasePage/>
  if (menu?.path === APP_ROUTES.EXPORT_TASKS) return <ExportTaskPage/>
  if (menu?.path === APP_ROUTES.PERSONNEL) return <PersonnelPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.PARTNER) return <PartnerPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.IMPERSONATION) return <ImpersonationPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.BUSINESS_AUDIT) return <BusinessAuditPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.CASHBACK) return <CashbackPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.WITHDRAWAL) return <WithdrawalPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.USER_RELATION) return <UserRelationPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.MAINTENANCE) return <MaintenancePage roles={roles}/>
  if (menu?.path === APP_ROUTES.NOTIFY_RULE) return <NotifyRulePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_RULE) return <LeadRuleConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_FILTER) return <LeadFilterConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_FOLLOW_UP_RULE) return <LeadFollowUpRuleConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.PRODUCT_CONFIG) return <ProductConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.WORK_PLAN_CONFIG) return <WorkPlanConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.REGISTRATION_POOL) return <RegistrationPoolPage/>
  if (menu?.path === APP_ROUTES.REGISTRATION_CHECKLIST_CONFIG) return <RegistrationChecklistConfigPage/>
  if (menu?.path === APP_ROUTES.MY_STUDENTS) return <MyStudentsPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.MEDIA_STUDENTS) return <MediaStudentsPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.MEDIA_CALENDAR) return <MediaCalendarPage/>
  if (menu?.path === APP_ROUTES.MY_ASSETS) return <EamAssetPage permissions={permissions} view="assets"/>
  if (menu?.path === APP_ROUTES.ASSET_DEMANDS) return <EamAssetPage permissions={permissions} view="demands"/>
  if (menu?.path === APP_ROUTES.FEEDBACK) return <FeedbackPage permissions={permissions}/>
  if (menu && [APP_ROUTES.WORK_ORDER_CREATE, APP_ROUTES.WORK_ORDER_AVAILABLE, APP_ROUTES.WORK_ORDER_MINE].some(path => path === menu.path)) return <WorkOrderCenterPage/>
  if (menu?.path === APP_ROUTES.STUDENT_CONTACT_CONFIG) return <StudentContactConfigPage/>
  if (menu?.path === APP_ROUTES.STUDENT_CONTACT_EXCEPTIONS) return <StudentContactExceptionsPage/>
  if (menu?.path === APP_ROUTES.ALL_MESSAGES) return <MessageInboxPage key={menu.path} view="all"/>
  if (menu?.path === APP_ROUTES.UNREAD_MESSAGES) return <MessageInboxPage key={menu.path} view="unread"/>
  if (menu?.path === APP_ROUTES.MEDIA_PRODUCTION_TICKETS) return <ProductionTicketsPage permissions={permissions}/>
  return <section className="workspace-page"><Card bordered={false} title={menu?.name || '员工工作台'}>
    <Result status="info" title="页面尚未迁移" subTitle="该菜单已由统一权限系统下发，前端页面尚未迁移。"/>
    <Typography.Paragraph type="secondary">路径：{menu?.path || location.pathname}　组件：{menu?.component || '未配置'}</Typography.Paragraph>
  </Card></section>
}
