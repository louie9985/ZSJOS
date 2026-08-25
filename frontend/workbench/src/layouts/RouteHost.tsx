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
import { ProductionTicketsPage, StudentOpsPage, ReviewsPage } from '../pages/MediaFeaturePage'
import MediaStudentsPage from '../pages/MediaStudentsPage'
import EamRepairPage from '../pages/EamRepairPage'
import EamInventoryPage from '../pages/EamInventoryPage'
import EamAssetPage from '../pages/EamAssetPage'
import EamTransferPage from '../pages/EamTransferPage'
import EamScrapPage from '../pages/EamScrapPage'
import EamCategoryPage from '../pages/EamCategoryPage'
import EamCodeRulePage from '../pages/EamCodeRulePage'
import EamStatisticsPage from '../pages/EamStatisticsPage'
import HrmMyAttendancePage from '../pages/HrmMyAttendancePage'
import HrmClockPage from '../pages/HrmClockPage'
import HrmLeavePage from '../pages/HrmLeavePage'
import HrmAttendanceMonthPage from '../pages/HrmAttendanceMonthPage'
import HrmMySalarySlipPage from '../pages/HrmMySalarySlipPage'
import HrmSalaryEmployeeInfoPage from '../pages/HrmSalaryEmployeeInfoPage'
import HrmSalaryMonthRecordPage from '../pages/HrmSalaryMonthRecordPage'
import HrmSalarySlipPage from '../pages/HrmSalarySlipPage'
import HrmSalarySendRecordPage from '../pages/HrmSalarySendRecordPage'
import HrmPerformancePlanPage from '../pages/HrmPerformancePlanPage'
import HrmMyPerformancePage from '../pages/HrmMyPerformancePage'
import HrmMyPerformanceHistoryPage from '../pages/HrmMyPerformanceHistoryPage'
import HrmPerformanceAssessmentPage from '../pages/HrmPerformanceAssessmentPage'
import HrmMyProfilePage from '../pages/HrmMyProfilePage'
import HrmEmployeePage from '../pages/HrmEmployeePage'
import HrmEmployeeConfigPage from '../pages/HrmEmployeeConfigPage'
import HrmPortalHomePage from '../pages/HrmPortalHomePage'
import HrmMyInsurancePage from '../pages/HrmMyInsurancePage'
import HrmSalaryOptionPage from '../pages/HrmSalaryOptionPage'
import HrmSalaryGroupPage from '../pages/HrmSalaryGroupPage'
import HrmSalaryTaxRulePage from '../pages/HrmSalaryTaxRulePage'
import HrmSalaryChangeTemplatePage from '../pages/HrmSalaryChangeTemplatePage'
import HrmSalaryConfigPage from '../pages/HrmSalaryConfigPage'
import HrmOpeningGuidePage from '../pages/HrmOpeningGuidePage'
import HrmDeptPage from '../pages/HrmDeptPage'
import HrmBirthdayCarePage from '../pages/HrmBirthdayCarePage'
import HrmAttendanceGroupPage from '../pages/HrmAttendanceGroupPage'
import HrmAttendanceHolidayPage from '../pages/HrmAttendanceHolidayPage'
import HrmInsuranceSchemePage from '../pages/HrmInsuranceSchemePage'
import HrmInsuranceMonthRecordPage from '../pages/HrmInsuranceMonthRecordPage'
import HrmResultTemplatePage from '../pages/HrmResultTemplatePage'
import HrmAssessmentTemplatePage from '../pages/HrmAssessmentTemplatePage'
import HrmRecruitPostPage from '../pages/HrmRecruitPostPage'
import HrmRecruitCandidatePage from '../pages/HrmRecruitCandidatePage'
import HrmRecruitChannelPage from '../pages/HrmRecruitChannelPage'
import HrmRecruitEliminatePage from '../pages/HrmRecruitEliminatePage'
import HrmHrHomePage from '../pages/HrmHrHomePage'
import HrmTeamHomePage from '../pages/HrmTeamHomePage'
import { resolveFmsRoute } from './fmsRoutes'

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
  if (menu?.path === APP_ROUTES.TODAY_TASKS) return <TodayTasksPage permissions={permissions} onOpenAssignment={onOpenAssignment}/>
  if (menu?.path === APP_ROUTES.WORK_PLANS) return <WorkPlanPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.LEAD_APPEALS) return <LeadAppealPage/>
  if (menu?.path === APP_ROUTES.MY_SALES_ORDERS) return <MySalesOrderPage/>
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
  if (menu?.path === APP_ROUTES.STUDENT_CONTACT_CONFIG) return <StudentContactConfigPage/>
  if (menu?.path === APP_ROUTES.STUDENT_CONTACT_EXCEPTIONS) return <StudentContactExceptionsPage/>
  if (menu?.path === APP_ROUTES.ALL_MESSAGES) return <MessageInboxPage key={menu.path} view="all"/>
  if (menu?.path === APP_ROUTES.UNREAD_MESSAGES) return <MessageInboxPage key={menu.path} view="unread"/>
  if (menu?.path === APP_ROUTES.MEDIA_PRODUCTION_TICKETS) return <ProductionTicketsPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.MEDIA_STUDENT_OPS) return <StudentOpsPage/>
  if (menu?.path === APP_ROUTES.MEDIA_REVIEWS) return <ReviewsPage/>
  if (menu?.path === APP_ROUTES.EAM_REPAIR) return <EamRepairPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.EAM_INVENTORY) return <EamInventoryPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.EAM_ASSET) return <EamAssetPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.EAM_TRANSFER) return <EamTransferPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.EAM_SCRAP) return <EamScrapPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.EAM_CATEGORY) return <EamCategoryPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.EAM_CODE_RULE) return <EamCodeRulePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.EAM_STATISTICS) return <EamStatisticsPage/>
  if (menu?.path === APP_ROUTES.HRM_PORTAL_ATTENDANCE) return <HrmMyAttendancePage/>
  if (menu?.path === APP_ROUTES.HRM_ATTENDANCE_CLOCK) return <HrmClockPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_ATTENDANCE_LEAVE) return <HrmLeavePage/>
  if (menu?.path === APP_ROUTES.HRM_ATTENDANCE_MONTH) return <HrmAttendanceMonthPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_PORTAL_SALARY_SLIP) return <HrmMySalarySlipPage/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_EMPLOYEE_INFO) return <HrmSalaryEmployeeInfoPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_MONTH_RECORD) return <HrmSalaryMonthRecordPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_HISTORY) return <HrmSalaryMonthRecordPage permissions={permissions} historyOnly/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_SLIP) return <HrmSalarySendRecordPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_PORTAL_PERFORMANCE) return <HrmMyPerformancePage/>
  if (menu?.path === APP_ROUTES.HRM_PORTAL_PERFORMANCE_HISTORY) return <HrmMyPerformanceHistoryPage/>
  if (menu?.path === APP_ROUTES.HRM_PERFORMANCE_PLAN) return <HrmPerformancePlanPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_PERFORMANCE_ASSESSMENT) return <HrmPerformanceAssessmentPage/>
  if (menu?.path === APP_ROUTES.HRM_PORTAL_EMPLOYEE) return <HrmMyProfilePage/>
  if (menu?.path === APP_ROUTES.HRM_EMPLOYEE_LIST) return <HrmEmployeePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_EMPLOYEE_CONFIG) return <HrmEmployeeConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_PORTAL_HOME) return <HrmPortalHomePage/>
  if (menu?.path === APP_ROUTES.HRM_PORTAL_INSURANCE) return <HrmMyInsurancePage/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_OPTION) return <HrmSalaryOptionPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_GROUP) return <HrmSalaryGroupPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_TAX_RULE) return <HrmSalaryTaxRulePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_CHANGE_TEMPLATE) return <HrmSalaryChangeTemplatePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_SALARY_CONFIG) return <HrmSalaryConfigPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_PORTAL_OPENING_GUIDE) return <HrmOpeningGuidePage/>
  if (menu?.path === APP_ROUTES.HRM_DEPT) return <HrmDeptPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_BIRTHDAY_CARE) return <HrmBirthdayCarePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_ATTENDANCE_GROUP) return <HrmAttendanceGroupPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_ATTENDANCE_HOLIDAY) return <HrmAttendanceHolidayPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_INSURANCE_SCHEME) return <HrmInsuranceSchemePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_INSURANCE_MONTH) return <HrmInsuranceMonthRecordPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_RESULT_TEMPLATE) return <HrmResultTemplatePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_ASSESSMENT_TEMPLATE) return <HrmAssessmentTemplatePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_RECRUIT_POST) return <HrmRecruitPostPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_RECRUIT_CANDIDATE) return <HrmRecruitCandidatePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_RECRUIT_CHANNEL) return <HrmRecruitChannelPage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_RECRUIT_ELIMINATE) return <HrmRecruitEliminatePage permissions={permissions}/>
  if (menu?.path === APP_ROUTES.HRM_HR_HOME) return <HrmHrHomePage/>
  if (menu?.path === APP_ROUTES.HRM_TEAM_HOME) return <HrmTeamHomePage/>
  // FMS 财务管理（26 页，集中在 fmsRoutes.tsx 避免本文件膨胀）
  const fmsPage = resolveFmsRoute(menu, permissions)
  if (fmsPage) return fmsPage
  return <section className="workspace-page"><Card bordered={false} title={menu?.name || '员工工作台'}>
    <Result status="info" title="页面尚未迁移" subTitle="该菜单已由统一权限系统下发，前端页面尚未迁移。"/>
    <Typography.Paragraph type="secondary">路径：{menu?.path || location.pathname}　组件：{menu?.component || '未配置'}</Typography.Paragraph>
  </Card></section>
}
