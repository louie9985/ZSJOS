import axios, { type AxiosRequestConfig } from 'axios'
import { APP_CONFIG, STORAGE_KEYS } from '../constants'
import { handleImpersonationInvalid, resolveImpersonationSessionHeader } from './impersonation'
import { createIdempotencyKey } from './idempotency'
import type { Timestamp } from './time'

export type User = { id: number; nickname: string; avatar?: string; username?: string }
export type UserProfile = {
  id: number; username: string; nickname: string; email?: string; mobile?: string; sex: number; avatar?: string
  createTime: Timestamp; dept?: { id: number; name: string }; posts?: Array<{ id: number; name: string }>
}
export type UserProfileUpdate = { nickname?: string; email?: string; mobile?: string; sex?: number; avatar?: string }
export type SocialUser = { id: number; type: number; openid: string; nickname?: string; avatar?: string }
export type RawMenu = { id: number; name: string; path?: string; icon?: string; component?: string; componentName?: string; visible?: boolean; keepAlive?: boolean; alwaysShow?: boolean; type?: number; sort?: number; parentId: number; children?: RawMenu[] }
export type WorkbenchMenu = Omit<RawMenu, 'children' | 'path'> & { path: string; hidden: boolean; noCache: boolean; alwaysShow: boolean; children: WorkbenchMenu[] }
export type PermissionInfo = { user: User; roles: string[]; permissions: string[]; menus: RawMenu[]; defaultAvatar?: string }
export type DictData = { label: string; value: string; dictType: string; colorType?: string; cssClass?: string }
export type MediaAccountDetailSnapshot = { key: string; label: string; type: string; value: unknown; displayValue?: string; dictType?: string }
export type MediaAccountField = { key: string; label: string; type: 'text' | 'textarea' | 'number' | 'date' | 'select' | 'multi_select' | 'boolean'; required: boolean; enabled: boolean; sort: number; dictType?: string; searchable: boolean }
export type MediaAccountFieldConfig = { id: number; versionNo: number; version: number; fields: MediaAccountField[] }
export type MediaAccount = { id: number; accountNo: string; nickname: string; platformValue: string; platformLabelSnapshot: string; platformAccountId?: string; leadDirection?: string; studentPersonId?: number; directorUserId?: number; detailConfigVersionId?: number; detailValues?: Record<string, unknown>; detailSnapshots?: MediaAccountDetailSnapshot[]; accountGradeValue?: string; accountGradeLabelSnapshot?: string; healthStatusValue?: string; healthStatusLabelSnapshot?: string; riskLevelValue?: string; riskLevelLabelSnapshot?: string; healthJson?: string; rescueStatus?: string; rebindProcessInstanceId?: string; sStage: string; status?: string; version: number; availableActions: string[] }
export type MediaContent = { id: number; contentNo: string; accountId: number; title: string; status: string; version: number; availableActions: string[] }
export type MediaException = { id: number; exceptionNo: string; accountId: number; categoryLabelSnapshot: string; description: string; status: string; version: number; availableActions: string[] }
export type MediaReview = { id: number; reviewNo: string; reviewType: string; subjectType: string; subjectId: number; reviewerUserId?: number; rejectReason?: string; status: string; version: number; availableActions: string[] }
export type GraduationApplication = { id: number; applicationNo: string; serviceRelationId: number; studentPersonId: number; plannerUserId: number; reviewerUserId: number; status: string; processInstanceId?: string; resultReason?: string; version: number }
export type ProductionTicket = { id: number; ticketNo: string; accountId: number; status: string; version: number; expectedDeliveredAt?: Timestamp; deadlineAt?: Timestamp; availableActions: string[] }
export type PositioningCard = { id: number; cardNo: string; accountId: number; studentPersonId?: number; serviceRelationId?: number; directorUserId?: number; operatorUserId?: number; templateId?: number; templateVersionId?: number; fieldsSnapshot?: StudentContactFormField[]; valuesSnapshot?: Record<string, unknown>; dictSnapshot?: Record<string, unknown>; trialEndDate?: string; status: string; professionalRisk?: boolean; versionNo?: number; version: number; availableActions: string[] }
export type SalesUser = { id: number; nickname: string; maskedMobile?: string; deptName?: string; avatar?: string }
export type AssignmentUser = SalesUser & { deptId?: number; status: number }
export type AssignmentRelation = AssignmentUser & { salesUsers: AssignmentUser[]; validSalesCount: number; invalidSalesCount: number; updateTime?: Timestamp }
export type AssignmentLog = { id: number; sourceUsers: string; targetUsers: string; actionType: 'append' | 'replace' | 'remove'; operatorName: string; createTime: Timestamp }
export type PageResult<T> = { list: T[]; total: number }
export type CursorPageResult<T> = { list: T[]; nextCursor?: string; hasMore: boolean }
export type RegistrationAttachment = { id: number; infraFileId: number; fileUrl: string; originalName: string; contentType?: string; fileSize: number; uploadedByUserId: number; uploadedByUserName?: string; uploadedAt?: Timestamp }
export type RegistrationRoute = { id: number; optionKey: string; departmentId: number; departmentName: string; assigneeType: 'study_planner' | 'content_director'; assigneeTypeLabel: string; selected: boolean; assigneeUserId?: number; assigneeUserName?: string; sort: number }
export type RegistrationCase = { id: number; orderId: number; orderNo?: string; orderStatus?: string; orderStatusLabel?: string; studentName?: string; studentMobile?: string; leadNo?: string; status: string; statusLabel?: string; studyPlannerUserId?: number; studyPlannerUserName?: string; registrationApprovedAt?: Timestamp; version: number; completable?: boolean; completionBlockCode?: string; completionBlockReason?: string; items?: Array<{ id: number; itemKey: string; itemType: string; title: string; sort: number; checked?: boolean; checkedByUserName?: string; checkedAt?: Timestamp; attachmentRequired?: boolean; attachments?: RegistrationAttachment[] }>; routes?: RegistrationRoute[] }
export type StudyPlanner = { id: number; nickname: string }
export type RegistrationRouteOption = { id: number; optionKey: string; departmentId: number; departmentName: string; assigneeType: 'study_planner' | 'content_director'; assigneeTypeLabel: string; sort: number; enabled: boolean; systemRequired: boolean }
export type RegistrationChecklistConfig = { templateId: number; templateVersion: number; published?: { id: number; versionNo: number; status: string; items: RegistrationChecklistItem[]; routeOptions: RegistrationRouteOption[] }; draft?: { id: number; versionNo: number; status: string; items: RegistrationChecklistItem[]; routeOptions: RegistrationRouteOption[] } }
export type RegistrationChecklistItem = { id: number; itemKey: string; itemType: 'checkbox' | 'attachment' | 'study_planner'; title: string; sort: number; enabled: boolean; systemRequired: boolean; attachmentRequired?: boolean }
export type StudentTaskStage = { key: string; label: string; status: 'done' | 'current' | 'pending'; detail: string }
export type MyStudent = { personId: number; personNo?: string; leadId?: number; leadNo?: string; name?: string; mobile?: string; wechatId?: string; activatedAt?: Timestamp; services: Array<{ serviceRelationId: number; leadId?: number; leadNo?: string; orderId?: number; orderNo?: string; courseName?: string; skuName?: string; categoryPath?: string[]; attributeValues?: string[]; productSnapshot?: string; status: string; activatedAt?: Timestamp; acceptanceStatus?: string; acceptedAt?: Timestamp; version?: number; owner?: boolean; ownerUserId?: number; ownerUserName?: string; contentDirectorUserId?: number; contentDirectorUserName?: string; careerPlannerUserId?: number; careerPlannerUserName?: string; operatorUserId?: number; operatorUserName?: string; directorStage?: string; directorInterviewAt?: Timestamp }> }
export type MediaStudentDetail = {
  student: MyStudent
  accounts: Array<{ id: number; accountNo: string; nickname?: string; platformLabel?: string; stage?: string; runStatus?: string; version: number; lastActivityAt?: Timestamp; availableActions: string[]; detailSnapshots: MediaAccountDetailSnapshot[]; taskLine: StudentTaskStage[] }>
  positioningCards: Array<{ id: number; accountId: number; cardNo: string; status: string; versionNo?: number; professionalRisk?: boolean; version: number; lastActivityAt?: Timestamp; availableActions: string[] }>
  contents: Array<{ id: number; accountId: number; contentNo: string; title?: string; status: string; currentVersionNo?: number; publishedAt?: Timestamp; version: number; lastActivityAt?: Timestamp; availableActions: string[] }>
  productionTickets: Array<{ id: number; accountId: number; ticketNo: string; status: string; deadlineAt?: Timestamp; revisionCount?: number; lastActivityAt?: Timestamp }>
  operationTimeline: Array<{ key: string; type: string; title: string; detail?: string; operatorName?: string; occurredAt: Timestamp }>
  studentTaskLine: StudentTaskStage[]
  taskLine: StudentTaskStage[]
  pendingStats: { accountCount: number; positioningCount: number; contentCount: number; productionCount: number }
}
export type MediaStudentTalkRecord = { id: number; accountId?: number; operatorUserId: number; operatorUserName?: string; content: string; attachmentFileIds: number[]; occurredAt: Timestamp }
export type StudentContactChecklistItem = { key: string; title: string; type: string; enabled?: boolean; attachmentRequired?: boolean; sort?: number }
export type StudentContactConfig = { published?: { id: number; versionNo: number; version: number; firstContactTimeoutMinutes: number; studyPlanTimeoutMinutes: number; checklist: StudentContactChecklistItem[]; quickNotes: string[]; collaboratorTabs: Record<string, string[]> }; draft?: { id: number; versionNo: number; version: number; firstContactTimeoutMinutes: number; studyPlanTimeoutMinutes: number; checklist: StudentContactChecklistItem[]; quickNotes: string[]; collaboratorTabs: Record<string, string[]> } }
export type StudentContactFormField = { key: string; title: string; type: 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'dict' | 'select' | 'multi_select' | 'radio' | 'checkbox_group' | 'checkbox' | 'attachment' | 'region'; required: boolean; enabled: boolean; systemField: boolean; sort: number; description?: string; dictType?: string; multiple?: boolean; minSelections?: number; maxSelections?: number; minValue?: number; maxValue?: number; maxLength?: number; group?: string }
export type DirectorTemplateSnapshot = { templateId: number; templateVersionId: number; templateVersionNo: number; fields: StudentContactFormField[]; values: Record<string, unknown>; dictSnapshots: Record<string, unknown> }
export type DirectorTemplateVersion = { id: number; templateId: number; versionNo: number; status: 'draft' | 'published' | 'archived'; fields: StudentContactFormField[]; publishedByUserId?: number; publishedAt?: Timestamp; version: number }
export type DirectorTemplate = { id: number; scene: 'director_interview' | 'positioning_card'; templateCode: string; name: string; defaultTemplate: boolean; status: string; version: number; published?: DirectorTemplateVersion; draft?: DirectorTemplateVersion; versions: DirectorTemplateVersion[] }
export type DirectorConfig = { id: number; interviewAppointmentHours: number; positioningDueHours: number; trialDays: number; version: number }
export type DirectorStageForm = { state: 'empty' | 'draft' | 'submitted'; configId?: number; configVersion?: number; templateId?: number; templateVersionId?: number; templateVersionNo?: number; fields: StudentContactFormField[]; values: Record<string, unknown>; dictSnapshots: Record<string, unknown>; savedAt?: Timestamp; savedByUserId?: number; submittedAt?: Timestamp; interviewAt?: Timestamp }
export type StudentContactAction = 'ACCEPT' | 'FIRST_CONTACT' | 'STUDY_PLAN' | 'FOLLOW_UP' | 'EDIT_BASIC_INFO' | 'ASSIGN_CONTENT_DIRECTOR' | 'ASSIGN_CAREER_PLANNER' | 'UPDATE_EXAM_DATE' | 'EXAM_NOTICE_DONE' | 'POST_EXAM_DONE' | 'COMPLETE_STAGE' | 'END_SERVICE' | 'DIRECTOR_PRECHECK' | 'DIRECTOR_INTERVIEW' | 'DIRECTOR_OPERATOR_ASSIGN'
export type StudentDeliveryStage = { code: string; label: string; status: string; current?: boolean; available?: boolean }
export type StudentContactContext = { serviceRelationId: number; acceptanceStatus?: string; acceptedAt?: string; version: number; firstContactChecklist: StudentContactChecklistItem[]; quickNotes: string[]; firstContactTimeoutMinutes?: number; studyPlanTimeoutMinutes?: number; visibleTabs: string[]; availableActions: StudentContactAction[]; currentTask?: { id: number; type: string; status: string; dueAt?: string; overdue?: boolean }; ownerUserId?: number; ownerUserName?: string; contentDirectorUserId?: number; contentDirectorUserName?: string; careerPlannerUserId?: number; careerPlannerUserName?: string; operatorUserId?: number; operatorUserName?: string; directorStage?: string; directorInterviewAt?: Timestamp; defaultDirectorInterviewAt?: Timestamp; directorInterviewAppointmentHours?: number; directorTrialDays?: number; deliveryStage?: string; deliveryStageLabel?: string; deliveryStages?: StudentDeliveryStage[]; examDate?: string; formFields?: StudentContactFormField[]; directorForms?: { precheck: DirectorStageForm; interview: DirectorStageForm }; operatorAssignmentConflict?: boolean }
export type StudentContactRecord = { id: number; contactType: string; successful: boolean; unsuccessfulReasonValue?: string; unsuccessfulReasonLabel?: string; remark: string; attachmentFileIds: number[]; completedChecklistKeys: string[]; nextContactAt: string; operatorUserId: number; operatorUserName?: string; submittedAt: string; deliveryStage?: string; deliveryData?: string }
export type StudentContactExtension = { id: number; serviceRelationId: number; taskId: number; status: string; originalDueAt: string; requestedDueAt: string; reasonValue: string; reasonLabel?: string; description: string; attachmentFileIds: number[]; applicantUserId: number; reviewerUserId: number; processInstanceId?: string; decisionReason?: string; submittedAt: string; resolvedAt?: string; version: number }
// ========== HRM Types ==========
/** 打卡记录。字典字段（type/sourceType/status）为数字码，展示走 useDict。 */
export type HrmClockItem = {
  id: number; employeeId?: number; employeeName?: string; jobNumber?: string; deptId?: number; deptName?: string; postName?: string
  clockTime?: Timestamp; type?: number; attendanceTime?: Timestamp; sourceType?: number; status?: number; stage?: number
  address?: string; longitude?: number; latitude?: number; ssid?: string; mac?: string; remark?: string; createTime?: Timestamp
}
export type HrmClockSave = {
  id?: number; employeeId?: number; clockTime?: Timestamp; type: number; attendanceTime?: Timestamp
  sourceType?: number; status?: number; stage?: number; address?: string; remark?: string
}
/** 请假记录。type 是字典 value（字符串），approvalStatus 见 LEAVE_APPROVAL_STATUS。 */
export type HrmLeaveItem = {
  id: number; employeeId?: number; employeeName?: string; jobNumber?: string; deptId?: number; deptName?: string; postName?: string
  type: string; startTime?: Timestamp; endTime?: Timestamp; day: number; reason?: string; remark?: string
  approvalStatus?: number; processInstanceId?: string; approvalTime?: Timestamp; approvalReason?: string; createTime?: Timestamp
}
/** 员工端请假申请。startTime/endTime 为毫秒时间戳，与后端 VO 一致。 */
export type HrmLeaveCreate = { type: string; startTime: number; endTime: number; day: number; reason?: string; remark?: string }
export type HrmAttendanceMonthRecord = {
  employeeId: number; employeeName: string; jobNumber?: string; deptId?: number; deptName?: string; postName?: string
  attendanceGroupName?: string; entryTime?: Timestamp; employeeStatus?: number; workCity?: string
  year: number; month: number; attendDays: number; actualDays: number
  lateMinute: number; lateCount: number; earlyMinute: number; earlyCount: number
  misscardCount: number; absenteeismDays: number; absenteeismMinutes: number; leaveDays: number; leaveMinutes: number
  lateDeductAmount: number; earlyDeductAmount: number; misscardDeductAmount: number; absenteeismDeductAmount: number
  attendanceDeductAmount: number; fullAttendance: boolean
}
export type HrmAttendanceDailyDetail = {
  employeeId: number; employeeName?: string; jobNumber?: string; deptId?: number; deptName?: string; postName?: string
  attendanceTime: Timestamp; shiftName?: string; scheduled?: boolean; requiredClockCount?: number; scheduledMinutes?: number
  misscardCount?: number; absenteeism?: boolean; absenteeismMinutes?: number; absenteeismDays?: number
  leaveStatus?: boolean; leaveMinutes?: number; leaveDays?: number; attendanceResult?: string
  lateCount: number; lateMinutes?: number; earlyCount: number; earlyMinutes?: number; clockList: HrmClockItem[]
}
export type HrmAttendanceMonthDetail = {
  summary: HrmAttendanceMonthRecord; dailyDetails: HrmAttendanceDailyDetail[]; leaves: HrmLeaveItem[]
}
/** 工资项。可嵌套一层 children，展示时需递归摊平。 */
export type HrmSalaryOption = { name?: string; type?: number; code?: number; parentCode?: number; value?: number; remark?: string; sort?: number; children?: HrmSalaryOption[] }
export type HrmSalarySlip = {
  id: number; sendRecordId?: number; monthEmployeeRecordId?: number
  employeeId?: number; employeeName?: string; jobNumber?: string; mobile?: string
  deptId?: number; deptName?: string; postName?: string
  year: number; month: number; readStatus?: number; realPaySalary?: number; remark?: string
  options?: HrmSalaryOption[]; createTime?: Timestamp
}
export type HrmSalarySlipUnread = { unreadCount: number; reminder?: string }
export type HrmSalarySlipTemplate = { id?: number; name: string; hideEmpty?: boolean; defaultStatus?: boolean; options?: Array<{ name?: string; type?: number; code?: number; remark?: string; parentCode?: number; hidden?: boolean; sort?: number }> }
export type HrmSalarySlipSendRecord = { id: number; monthRecordId?: number; employeeCount?: number; sendEmployeeCount?: number; readCount?: number; year?: number; month?: number; creatorName?: string; createTime?: Timestamp }
export type HrmSalarySlipSendEmployee = {
  monthEmployeeRecordId: number; employeeId: number; employeeName?: string; jobNumber?: string; mobile?: string
  deptId?: number; deptName?: string; postName?: string; expectedPaySalary?: number; realPaySalary?: number; sent?: boolean
}
export type HrmSalaryEmployeeInfo = {
  id?: number; employeeId?: number; employeeName?: string; jobNumber?: string; mobile?: string
  deptId?: number; deptName?: string; postName?: string; entryStatus?: number; status?: number
  entryTime?: Timestamp; regularTime?: Timestamp; changeReason?: number; effectTime?: Timestamp; changeType?: number
  probationSalary?: number; regularSalary?: number; remark?: string
  salaryOptions?: HrmSalaryOption[]; probationSalaryOptions?: HrmSalaryOption[]; createTime?: Timestamp
}
export type HrmSalaryChangeRecord = {
  id: number; employeeId?: number; recordType?: number; recordTypeName?: string; changeReason?: number
  effectTime?: Timestamp; beforeTotal?: number; afterTotal?: number; probationBeforeTotal?: number
  probationAfterTotal?: number; status?: number; remark?: string; salaryOptions?: HrmSalaryOption[]
  probationSalaryOptions?: HrmSalaryOption[]; createTime?: Timestamp
}
export type HrmSalaryEmployeeBatchResult = { successEmployeeIds: number[]; failureEmployeeReasons: Record<number, string> }
export type HrmSalaryEmployeeImportResult = { successJobNumbers: string[]; failureJobNumbers: Record<string, string> }
export type HrmSalaryMonthRecord = {
  id: number; title?: string; year?: number; month?: number; employeeCount?: number
  startTime?: Timestamp; endTime?: Timestamp; expectedPaySalary?: number
  personalInsuranceAmount?: number; personalProvidentFundAmount?: number; personalTax?: number
  realPaySalary?: number; corporateInsuranceAmount?: number; corporateProvidentFundAmount?: number
  status?: number; optionHeaders?: HrmSalaryOption[]; createTime?: Timestamp
}
export type HrmSalaryMonthEmployeeRecord = {
  id?: number; monthRecordId?: number; employeeId?: number; year?: number; month?: number
  employeeName?: string; jobNumber?: string; deptId?: number; deptName?: string; postName?: string
  actualWorkDay?: number; needWorkDay?: number; expectedPaySalary?: number; taxableSalary?: number
  personalTax?: number; realPaySalary?: number; performanceCoefficient?: number; optionValues?: HrmSalaryOption[]
}
export type HrmSalaryPayrollReadinessEmployee = {
  employeeId?: number; employeeName?: string; jobNumber?: string; deptId?: number; deptName?: string
  postName?: string; entryStatus?: number; status?: number; entryTime?: Timestamp
}
export type HrmSalaryPayrollReadiness = {
  monthRecordId?: number; title?: string; year?: number; month?: number; startTime?: Timestamp; endTime?: Timestamp
  socialSecurityYearMonth?: string; payrollEmployeeCount?: number; salaryEmployeeCount?: number
  noSalaryEmployeeCount?: number; noSalaryGroupEmployeeCount?: number; changeEmployeeCount?: number
  changeTypeCountMap?: Record<number, number>; noSalaryEmployees?: HrmSalaryPayrollReadinessEmployee[]
  noSalaryGroupEmployees?: HrmSalaryPayrollReadinessEmployee[]
}
/** 员工精简档（simple-page/simple-list 返回）。字段比完整员工 VO 少，只有选择器必需的部分。 */
export type HrmPortableEmployee = { id: number; name?: string; jobNumber?: string; deptId?: number; deptName?: string; postName?: string; mobile?: string; status?: number }
/** 绩效计划。operationType 是后端下发的「当前可操作阶段」，据此渲染流转按钮而非硬编码状态机。 */
export type HrmPerformancePlan = {
  id: number; name: string; cycleType?: number; cycle?: string; quarter?: number
  startTime?: Timestamp; endTime?: Timestamp; description?: string
  assessmentTemplateId?: number; assessmentTemplateName?: string
  assessmentConfig?: HrmPerformanceAssessmentConfig
  resultTemplateId?: number; resultTemplateName?: string
  resultConfig?: HrmPerformanceResultConfig
  quotaSettingType?: number; targetConfirmation?: boolean; resultAudit?: boolean
  resultConfirmation?: boolean; syncToSalary?: boolean; paidForMonth?: string
  scopes?: HrmPerformanceScope[]; targetConfirmationStage?: HrmPerformanceHandlerStage
  reviewStages?: HrmPerformanceReviewStage[]; resultAuditStages?: HrmPerformanceHandlerStage[]
  appealStages?: HrmPerformanceHandlerStage[]; appealTimeoutDays?: number; appealTimeoutAction?: number
  stageType?: number; status?: number; operationType?: number
  terminateTime?: Timestamp; employeeCount?: number; finishedCount?: number
  scoringReady?: boolean; interviewReady?: boolean; archiveReady?: boolean
  stageCountMap?: Record<number, number>; createTime?: Timestamp
}
export type HrmPerformanceScope = { type: number; employeeIds?: number[]; deptIds?: number[]; employeeType?: number; employeeStatuses?: number[] }
export type HrmPerformanceHandlerStage = { type: number; level?: number; employeeId?: number }
export type HrmPerformanceReviewStage = {
  name: string; rater: HrmPerformanceHandlerStage; weight: number; scoringType: number
  visibleContent: number; requiredSetting: boolean; rejectAuthority: boolean
}
export type HrmPerformanceAssessmentConfig = {
  name: string; scoreCalculation: number; upperLimitType: number; upperLimitScore: number
  dimensions: HrmAssessmentDimension[]
}
export type HrmPerformanceResultConfig = { name: string; levels: HrmPerformanceResultLevel[] }
/** 绩效计划创建/编辑入参。字段与后端 PerformancePlanVO 对齐，含嵌套配置快照。 */
export type HrmPerformancePlanSave = {
  id?: number; name: string; cycleType: number; cycle?: string; quarter?: number
  startTime?: Timestamp; endTime?: Timestamp; description?: string
  assessmentTemplateId: number; assessmentConfig: HrmPerformanceAssessmentConfig
  resultTemplateId: number; resultConfig: HrmPerformanceResultConfig
  quotaSettingType: number; targetConfirmation: boolean; targetConfirmationStage?: HrmPerformanceHandlerStage
  reviewStages: HrmPerformanceReviewStage[]; resultAudit: boolean; resultAuditStages?: HrmPerformanceHandlerStage[]
  resultConfirmation: boolean; appealStages?: HrmPerformanceHandlerStage[]
  appealTimeoutDays: number; appealTimeoutAction: number
  syncToSalary: boolean; paidForMonth?: string; scopes: HrmPerformanceScope[]
}
/** 绩效维度。allowEdit 由后端下发，决定员工能否编辑该维度指标。 */
export type HrmPerformanceDimension = { id?: number; assessmentId?: number; name?: string; quotaType?: number; weight?: number; remark?: string; allowEdit?: boolean; sort?: number }
/** 绩效指标。allowEdit 决定员工能否改动；分数随阶段不同而变化。 */
export type HrmPerformanceQuota = {
  id?: number; assessmentId?: number; dimensionId?: number; allowEdit?: boolean; preset?: boolean
  dimensionName?: string; name?: string; description?: string; standard?: string; dimensionWeight?: number
  weight?: number; scoreType?: number; targetValue?: string; actualValue?: string
  selfScore?: number; reviewerScore?: number; finalScore?: number; comment?: string; sort?: number
}
export type HrmPerformanceStage = {
  id?: number; assessmentId?: number; type?: number; handlerEmployeeId?: number; handlerName?: string
  name?: string; raterType?: number; weight?: number; scoringType?: number; visibleContent?: number
  requiredSetting?: boolean; rejectAuthority?: boolean; sort?: number; status?: number
  score?: number; resultLevel?: string; comment?: string; rejectReason?: string
  submitTime?: Timestamp; deadlineTime?: Timestamp; canHandle?: boolean; canScore?: boolean
  quotaScoreList?: Array<{ id?: number; assessmentQuotaId?: number; score?: number; comment?: string }>
}
/** 员工绩效考核（员工端与管理端共用结构，具体字段因接口而异）。 */
export type HrmPerformanceAssessment = {
  id?: number; planId?: number; name?: string; cycleType?: number; cycle?: string
  startTime?: Timestamp; endTime?: Timestamp; employeeId?: number; employeeName?: string; jobNumber?: string
  deptId?: number; deptName?: string; postName?: string; employeeType?: number; employeeStatus?: number
  currentHandlerName?: string; status?: number; processStatus?: number; stageType?: number; stageSort?: number
  score?: number; resultLevel?: string; coefficient?: number
  canConfirmTarget?: boolean; selfComment?: string; reviewerComment?: string; resultComment?: string
  resultAuditStatus?: number; resultAuditTime?: Timestamp; resultAuditReason?: string
  appealReason?: string; appealStatus?: number; appealTime?: Timestamp; appealComment?: string
  archiveTime?: Timestamp; dimensions?: HrmPerformanceDimension[]; quotas?: HrmPerformanceQuota[]
  reviewStages?: HrmPerformanceStage[]; currentReviewStage?: HrmPerformanceStage
  stages?: HrmPerformanceStage[]; currentStage?: HrmPerformanceStage; createTime?: Timestamp
}
/** 员工端绩效列表的精简摘要。 */
export type HrmPerformanceAssessmentSummary = {
  id: number; planId?: number; name?: string; status?: number; stageType?: number
  score?: number; resultLevel?: string; coefficient?: number
  resultAuditStatus?: number; appealStatus?: number; appealReason?: string
  startTime?: Timestamp; endTime?: Timestamp; archiveTime?: Timestamp
}
export type HrmPerformanceProcessRecord = { title?: string; content?: string; source?: string; status?: number; operatorName?: string; operateTime?: Timestamp; fileUrls?: string[] }
export type HrmPerformanceQuotaSave = {
  id?: number; dimensionId?: number; name?: string; description?: string; standard?: string
  weight?: number; scoreType?: number; targetValue?: string; actualValue?: string
  selfScore?: number; reviewerScore?: number; finalScore?: number; comment?: string; sort?: number
}
/** 绩效确认/处理通用入参：pass=1 通过、0 不通过。 */
export type HrmPerformanceConfirm = { assessmentId: number; pass: number; comment?: string }
/** 绩效评分入参：一个考核在某个评分阶段的全部指标一起提交。 */
export type HrmEmployee = {
  id: number
  name?: string
  avatar?: string
  jobNumber?: string
  userId?: number
  mobile?: string
  country?: string
  nation?: string
  idType?: number
  idNumber?: string
  sex?: number
  email?: string
  nativePlace?: string
  birthday?: Timestamp
  age?: number
  address?: string
  highestEducation?: number
  deptId?: number
  deptName?: string
  leaderEmployeeId?: number
  leaderEmployeeName?: string
  entryStatus?: number
  status?: number
  type?: number
  entryTime?: Timestamp
  entryDay?: number
  probation?: number
  regularTime?: Timestamp
  leaveTime?: Timestamp
  postName?: string
  postLevel?: string
  workCity?: string
  workAddress?: string
  workDetailAddress?: string
  channelId?: number
  companyAgeStartTime?: Timestamp
  companyAge?: number
  candidateId?: number
  remark?: string
}

/** 员工档案增改入参，与 HrmEmployeeSaveReqVO 对齐；时间字段按后端约定传毫秒时间戳 */
export type HrmEmployeeSave = Omit<
  HrmEmployee,
  'id' | 'deptName' | 'leaderEmployeeName' | 'entryDay' | 'companyAge' | 'avatar' | 'birthday' | 'entryTime' | 'regularTime' | 'leaveTime' | 'companyAgeStartTime'
> & {
  id?: number
  birthday?: number
  entryTime?: number
  regularTime?: number
  leaveTime?: number
  companyAgeStartTime?: number
}
export type HrmEmployeeFieldConfig = {
  name: string; title: string; groupName: string; visible: boolean
  editable?: boolean; visibleLocked: boolean; editableLocked: boolean
}

/** 五类异动共用入参；probation 仅转为全职时使用 */
export type HrmEmployeeChangeReq = {
  employeeId: number
  reason?: number
  probation?: number
  newDeptId?: number
  newPostName?: string
  newPostLevel?: string
  newWorkAddress?: string
  newLeaderEmployeeId?: number
  effectTime?: number
  remark?: string
}

export type HrmEmployeeQuitReq = {
  employeeId: number
  planQuitTime?: number
  applyQuitTime?: number
  salarySettlementTime?: number
  type?: number
  reason?: number
  remark?: string
}

export type HrmEmployeeStatusCount = { status: number; count: number }

export type HrmEmployeeCreateFromUser = {
  userId: number; jobNumber: string; mobile: string; deptId?: number; leaderEmployeeId?: number
  type: number; status?: number; entryTime: number; probation?: number; postName?: string
  postLevel?: string; workCity?: string; workAddress?: string; remark?: string
}
export type HrmEmployeeNotifyResult = { successCount: number; skippedCount: number; failureCount: number }
export type HrmEmployeeImportResult = {
  createJobNumbers: string[]; updateJobNumbers: string[]; skipJobNumbers: string[]
  failureJobNumbers: Record<string, string>
}
export type HrmEmployeeFile = { id: number; employeeId: number; type: number; url: string; createTime?: Timestamp }
export type HrmEmployeeChangeRecord = {
  id: number; employeeId: number; type?: number; reason?: number
  oldDeptId?: number; oldDeptName?: string; newDeptId?: number; newDeptName?: string
  oldPostName?: string; newPostName?: string; oldPostLevel?: string; newPostLevel?: string
  oldWorkAddress?: string; newWorkAddress?: string
  oldLeaderEmployeeId?: number; oldLeaderEmployeeName?: string
  newLeaderEmployeeId?: number; newLeaderEmployeeName?: string
  probation?: number; effectTime?: Timestamp; remark?: string; createTime?: Timestamp
}

export type HrmPerformanceScoreSave = {
  assessmentId: number; reviewStageId: number; comment?: string; quotas: HrmPerformanceQuotaSave[]
}

/** 员工端社保项目明细。 */
export type HrmInsuranceProject = {
  schemeProjectId?: number; type?: number; name?: string
  baseAmount?: number; corporateRate?: number; personalRate?: number
  corporateAmount?: number; personalAmount?: number
}
/** 员工端社保记录。projects 仅详情返回。 */
export type HrmInsuranceRecord = {
  id: number; monthRecordId?: number; employeeId: number
  schemeId?: number; schemeName?: string; schemeType?: number; schemeCity?: string
  year: number; month: number
  personalInsuranceAmount?: number; personalProvidentFundAmount?: number
  corporateInsuranceAmount?: number; corporateProvidentFundAmount?: number
  status?: number; createTime?: Timestamp; projects?: HrmInsuranceProject[]
}
/** 员工端工作台日历事项。 */
export type HrmHomeCalendarItem = {
  personalNoteId?: number; type: number; typeName: string; content: string
  typeId?: number; date: string; eventTime?: Timestamp
}

// ========== HRM 管理端配置类型 ==========
/** 薪资项。children 是下一层；code/parentCode 构成层级。 */
export type HrmSalaryOptionCfg = {
  id: number; code: number; parentCode: number; name: string; remark?: string
  systemFlag: boolean; type: number; taxEnabled: boolean; visible: boolean
  calculateEnabled: boolean; enabled: boolean; templateId?: number
  children?: HrmSalaryOptionCfg[]; createTime?: Timestamp
}
/** 薪资组。deptIds/employeeIds 是适用范围，可并存。 */
export type HrmSalaryGroup = {
  id: number; name: string; salaryStandard?: number; changeRule?: string
  taxRuleId?: number; taxRuleName?: string
  deptIds?: number[]; deptNames?: string[]; employeeIds?: number[]; employeeNames?: string[]
  createTime?: Timestamp
}
/** 计税规则。 */
export type HrmSalaryTaxRule = {
  id?: number; name: string; type?: number; taxEnabled?: boolean; threshold?: number
  decimalScale?: number; cycleType?: number; usedGroupCount?: number; createTime?: Timestamp
}
/** 调薪项。 */
export type HrmSalaryChangeOption = { name: string; code: number }
/** 调薪模板。 */
export type HrmSalaryChangeTemplate = {
  id?: number; name: string; defaultStatus: boolean; options: HrmSalaryChangeOption[]
  createTime?: Timestamp
}
/** 计薪配置。 */
export type HrmSalaryConfig = {
  id: number; cycleStartDay?: number; cycleEndDay?: number; socialSecurityMonthType?: number
  startYear?: number; startMonth?: number; createTime?: Timestamp
}
/** 组织（部门）节点。 */
export type HrmDept = { id: number; parentId: number; name: string; sort?: number; status?: number; leaderUserId?: number; leaderName?: string; createTime?: Timestamp }
/** 生日关怀配置。 */
export type HrmBirthdayCareConfig = {
  enabled: boolean; advanceDays: number; triggerTime: string
  deptIds: number[]; includeChildDepartments: boolean; recipientUserIds?: number[];
  missingTaskPermissionUserIds?: number[]
}
/** 考勤节假日。date 与 type（节日类型字典）组成。 */
export type HrmAttendanceHoliday = { id: number; date?: Timestamp; type: number; createTime?: Timestamp }
/** 考勤组班次。 */
export type HrmAttendanceShift = {
  weeks: number[]; startTime: string; endTime: string
  clockInStartTime: string; clockInEndTime: string
  clockOutStartTime: string; clockOutEndTime: string
  restStartTime?: string; restEndTime?: string; excludeRestTime: boolean
}
/** 考勤组扣款规则。 */
export type HrmAttendanceDeductRule = {
  lateMethod: number; lateDeductMoney: number; earlyMethod: number; earlyDeductMoney: number
  absenteeismMethod: number; absenteeismDeductMoney: number; misscardMethod: number; misscardDeductMoney: number
}
/** 考勤组。班次/打卡点/WiFi/扣款规则为嵌套子结构。 */
export type HrmAttendanceGroup = {
  id?: number; name: string; openWifiCard?: boolean; openPointCard?: boolean
  rest?: boolean; defaultStatus?: boolean
  deptIds?: number[]; deptNames?: string[]; employeeIds?: number[]; employeeNames?: string[]
  shifts?: HrmAttendanceShift[]; deductRule?: HrmAttendanceDeductRule; createTime?: Timestamp
}
/** 社保方案项目。 */
export type HrmInsuranceProjectCfg = {
  id?: number; schemeId?: number; type?: number; name?: string; baseAmount?: number
  corporateRate?: number; personalRate?: number; corporateAmount?: number; personalAmount?: number; createTime?: Timestamp
}
/** 社保方案。方案项目分社保/公积金两组。 */
export type HrmInsuranceScheme = {
  id?: number; name: string; areaId?: number; areaName?: string; householdType?: string; type?: number
  projectList?: HrmInsuranceProjectCfg[]; socialSecurityProjectList?: HrmInsuranceProjectCfg[]
  providentFundProjectList?: HrmInsuranceProjectCfg[]
  personalInsuranceAmount?: number; corporateInsuranceAmount?: number
  personalProvidentFundAmount?: number; corporateProvidentFundAmount?: number
  useCount?: number; monthRecordCount?: number; createTime?: Timestamp
}
/** 社保月度记录。员工交费记录。 */
export type HrmInsuranceMonthRecord = {
  id?: number; title?: string; year?: number; month?: number; insuredEmployeeCount?: number; stoppedEmployeeCount?: number
  personalInsuranceAmount?: number; personalProvidentFundAmount?: number
  corporateInsuranceAmount?: number; corporateProvidentFundAmount?: number; status?: number; createTime?: Timestamp
}
export type HrmInsuranceMonthEmployeeRecord = {
  id: number; monthRecordId: number; employeeId: number; employeeName?: string; jobNumber?: string
  mobile?: string; idNumber?: string; sex?: number; age?: number; deptId?: number; deptName?: string
  postName?: string; entryStatus?: number; employeeStatus?: number; entryTime?: Timestamp
  schemeId?: number; schemeName?: string; areaName?: string; areaId?: number; houseType?: string
  schemeType?: number; socialSecurityNumber?: string; accumulationFundNumber?: string
  year?: number; month?: number; personalInsuranceAmount?: number; personalProvidentFundAmount?: number
  corporateInsuranceAmount?: number; corporateProvidentFundAmount?: number; status?: number
  socialSecurityProjectList: HrmInsuranceProject[]; providentFundProjectList: HrmInsuranceProject[]
  createTime?: Timestamp
}
export type HrmInsuranceProjectUpdate = {
  schemeProjectId: number; baseAmount?: number; corporateAmount?: number; personalAmount?: number
}
export type HrmInsuranceMonthEmployeeUpdate = {
  id: number; schemeId: number; projects: HrmInsuranceProjectUpdate[]
}
/** 绩效结果等级。 */
export type HrmPerformanceResultLevel = { name: string; minScore: number; maxScore: number; coefficient: number }
/** 绩效结果模板。levels 是等级列表。 */
export type HrmPerformanceResultTemplate = {
  id?: number; name: string; levels: HrmPerformanceResultLevel[]
  status?: number; creatorName?: string; createTime?: Timestamp; updateTime?: Timestamp
}
/** 考核指标模板（单个维度）。 */
export type HrmAssessmentQuota = { id?: number; name?: string; dimensionId?: number; weight?: number; illustrate?: string; description?: string; standard?: string; scoreType?: number; sort?: number }
export type HrmAssessmentDimension = { id?: number; name?: string; quotaType?: number; weight?: number; remark?: string; allowEdit?: boolean; sort?: number; quotas?: HrmAssessmentQuota[] }
/** 考核指标模板。 */
export type HrmAssessmentTemplate = {
  id?: number; name: string; illustrate?: string; upperLimitScore?: number; scoreCalculation?: number; upperLimitType?: number
  dimensions?: HrmAssessmentDimension[]; status?: number; creatorName?: string; createTime?: Timestamp
}
/** 招聘职位。 */
export type HrmRecruitPost = {
  id?: number; postName: string; deptId?: number; deptName?: string
  jobNature?: number; areaId?: number; areaName?: string; recruitNum?: number; reason?: string
  workTime?: number; educationRequire?: number; minSalary?: number; maxSalary?: number; salaryUnit?: number
  minAge?: number; maxAge?: number; latestEntryTime?: Timestamp
  ownerEmployeeId?: number; ownerEmployeeName?: string
  interviewEmployeeIds?: number[]; interviewEmployeeNames?: string[]
  description?: string; emergencyLevel?: number; postTypeId?: number; postTypeName?: string
  status?: number; stopReason?: string; hasEntryNum?: number; recruitSchedule?: number; createTime?: Timestamp
}
/** 招聘渠道。 */
export type HrmRecruitChannel = { id?: number; name: string; status?: number; createTime?: Timestamp }
/** 招聘候选人（列表摘要）。 */
export type HrmRecruitCandidate = {
  id?: number; name: string; postId?: number; postName?: string; deptName?: string
  mobile?: string; age?: number; sex?: number; email?: string; education?: number; workTime?: number
  channelId?: number; channelName?: string; status?: number; stage?: string
  postStatus?: number; deptId?: number; ownerEmployeeId?: number; ownerEmployeeName?: string; stageNumber?: number
  interviewId?: number; interviewType?: number; interviewEmployeeId?: number; interviewEmployeeName?: string
  otherInterviewEmployeeIds?: number[]; otherInterviewEmployeeNames?: string[]; interviewTime?: Timestamp
  interviewAddress?: string; interviewResult?: number; employeeId?: number; entryTime?: Timestamp
  eliminate?: string; statusUpdateTime?: Timestamp; creatorName?: string; createTime?: Timestamp; updateTime?: Timestamp
  graduateSchool?: string; latestWorkPlace?: string; remark?: string; resumeUrls?: string[]
}
export type HrmRecruitInterview = {
  id?: number; candidateId: number; type: number; stageNumber?: number; interviewEmployeeId: number
  interviewEmployeeName?: string; otherInterviewEmployeeIds?: number[]; otherInterviewEmployeeNames?: string[]
  interviewTime: Timestamp; address?: string; remark?: string; result?: number; evaluate?: string
  cancelReason?: string; createTime?: Timestamp
}
export type HrmRecruitInterviewResultSave = { id: number; result: number; evaluate?: string; cancelReason?: string }
/** 招聘候选人状态统计。 */
export type HrmRecruitCandidateStatusCount = { status: number; count: number }
/** HR 工作台统计汇总。四个 survey 分别聚合员工/招聘/薪资/待办。 */
export type HrmHrHomeStatistics = {
  employeeSurvey: { activeCount: number; entryThisMonthCount: number; pendingEntryThisMonthCount: number; leaveThisMonthCount: number; pendingLeaveThisMonthCount: number; regularThisMonthCount: number; transferThisMonthCount: number }
  recruitSurvey: { recruitingPostCount: number; candidateInProcessCount: number; pendingEntryCount: number; joinedCount: number }
  salarySurvey: { monthRecordId?: number; employeeCount: number; realPaySalary: number; deptProportions: Array<{ deptId: number; deptName: string; proportion: number; totalSalary: number }> }
  todoSurvey: { toEntryCount: number; toLeaveCount: number; toExpireContractCount: number; toRegularCount: number; toSalaryComputeCount: number; toBirthdayCount: number }
}
/** 团队工作台统计汇总。 */
export type HrmTeamHomeStatistics = {
  leaderEmployeeId?: number
  teamOverview: { employeeCount: number; entryThisMonthCount: number; leaveThisMonthCount: number; regularThisMonthCount: number }
  teamSurvey: {
    statusAnalysis: Array<{ type: number | null; count: number }>
    sexAnalysis: Array<{ type: number | null; count: number }>
    ageAnalysis: Array<{ type: number | null; count: number }>
    companyAgeAnalysis: Array<{ type: number | null; count: number }>
  }
}

// ========== HRM 员工档案子表 ==========
/** 合同。 */
export type HrmContract = {
  id?: number; employeeId?: number; no?: string; type?: number
  startTime?: number; endTime?: number; term?: number; status?: number
  signCompany?: string; signTime?: number; remark?: string; expireRemind?: boolean
  fileUrls?: string[]; sort?: number; createTime?: Timestamp
}
/** 证件。 */
export type HrmCertificate = {
  id?: number; employeeId?: number; name: string; level?: string; no?: string
  startTime?: number; endTime?: number; issuingAuthority?: string; issuingTime?: number
  remark?: string; sort?: number; createTime?: Timestamp
}
/** 教育经历。 */
export type HrmEducationExperience = {
  id?: number; employeeId?: number; education: number; graduateSchool?: string; major?: string
  admissionTime?: number; graduationTime?: number; teachingMethods?: number; firstDegree?: boolean
  sort?: number; createTime?: Timestamp
}
/** 工作经历。 */
export type HrmWorkExperience = {
  id?: number; employeeId?: number; workUnit: string; postName: string
  startTime?: number; endTime?: number; reason?: string; witnessName?: string; witnessPhone?: string
  remark?: string; sort?: number; createTime?: Timestamp
}
/** 培训经历。 */
export type HrmTrainingExperience = {
  id?: number; employeeId?: number; course: string; organizationName?: string
  startTime?: number; endTime?: number; duration?: string; result?: string; certificateName?: string
  remark?: string; sort?: number; createTime?: Timestamp
}
/** 联系人。 */
export type HrmContact = {
  id?: number; employeeId?: number; name: string; relation?: string; phone?: string
  workUnit?: string; postName?: string; address?: string; sort?: number; createTime?: Timestamp
}
/** 工资卡。 */
export type HrmSalaryCard = {
  bankCardNumber: string; bankAreaId?: number; bankAreaName?: string; bankName?: string; bankBranchName?: string
}
/** 离职信息（只读）。 */
export type HrmQuitInfo = {
  planQuitTime?: number; applyQuitTime?: number; salarySettlementTime?: number
  type?: number; reason?: number; remark?: string; oldEmployeeStatus?: number
}

// ========== EAM Types ==========
export type EamRepairItem = { id: number; assetId: number; assetName?: string; assetCode?: string; faultDesc: string; repairVendor?: string; cost?: number; startTime?: string; endTime?: string; result?: string }
export type EamRepairCreate = { assetId: number; faultDesc: string; repairVendor?: string; cost?: number; startTime?: string }
export type EamRepairFinish = { id: number; endTime?: string; cost?: number; result?: string }
export type EamInventoryItem = { id: number; no?: string; name: string; scopeType: number; scopeValue?: string; status?: number; totalCount?: number; checkedCount?: number; normalCount?: number; abnormalCount?: number; startTime?: string; endTime?: string; remark?: string }
export type EamInventoryCreate = { name: string; scopeType: number; scopeValue?: string; remark?: string }
export type EamInventoryDetail = { id: number; inventoryId: number; assetId: number; assetName?: string; assetCode?: string; expectUserId?: number; expectUserName?: string; expectDeptId?: number; expectLocation?: string; actualUserId?: number; actualDeptId?: number; actualLocation?: string; result: number; remark?: string; checkUserId?: number; checkTime?: string }
export type EamInventoryCheck = { detailId: number; result: number; actualUserId?: number; actualDeptId?: number; actualLocation?: string; remark?: string }
export type EamAsset = {
  id?: number; assetCode?: string; name: string; categoryId: number; categoryName?: string; managementMode?: number
  quantity?: number; unit?: string; status?: number; brand?: string; specification?: string; sn?: string; barcode?: string
  originalValue?: number; netValue?: number; purchaseDate?: string; source?: number; sourceLabelSnapshot?: string; warrantyDate?: string
  useDeptId?: number; useDeptName?: string; useUserId?: number; useUserName?: string; useUserNameSnapshot?: string
  expectedLife?: number
  extFieldLabels?: Record<string, string>; extFieldDictTypes?: Record<string, string>
  location?: string; remark?: string; fileUrls?: string[]; extFields?: Record<string, unknown>; createTime?: string
}
export type EamAssetListItem = EamAsset & { id: number }
export type EamAssetChangeLog = { id: number; assetId: number; changeType: number; beforeStatus?: number; afterStatus?: number; beforeUserId?: number; afterUserId?: number; beforeDeptId?: number; afterDeptId?: number; bizId?: number; content?: string; operatorId?: number; operatorName?: string; operateTime: string }
export type EamTransfer = { id: number; no?: string; type: number; assetId: number; assetName?: string; assetCode?: string; fromUserId?: number; fromUserName?: string; fromDeptId?: number; toUserId?: number; toUserName?: string; toDeptId?: number; expectedReturnDate?: string; actualReturnDate?: string; status?: number; processInstanceId?: string; reason?: string; applyUserId?: number; applyUserName?: string; applyTime?: string }
export type EamTransferCreate = { type: number; assetId: number; toUserId?: number; toDeptId?: number; expectedReturnDate?: string; actualReturnDate?: string; reason?: string }
export type EamScrap = { id: number; no?: string; assetId: number; assetName?: string; assetCode?: string; reasonType: number; reason?: string; scrapDate?: string; status?: number; processInstanceId?: string; applyUserId?: number; applyUserName?: string; applyTime?: string }
export type EamScrapCreate = { assetId: number; reasonType: number; reason?: string; scrapDate?: string }
export type EamCategory = { id: number; parentId: number; name: string; code: string; sort: number; status: number; managementMode: number; unit: string; remark?: string; createTime?: string }
export type EamCategorySave = { id?: number; parentId: number; name: string; code: string; sort: number; status: number; managementMode: number; unit: string; remark?: string }
export type EamCategoryField = { id?: number; categoryId: number; fieldKey: string; fieldName: string; fieldType: number; options?: string[]; optionSource?: 'STATIC' | 'SYSTEM_DICT'; dictType?: string; required: boolean; adminVisible: boolean; collectionVisible: boolean; collectionRequired: boolean; conditionRule?: Record<string, unknown>; sort: number; inherited?: boolean }
export type EamCodeRule = { id?: number; categoryId?: number; prefix?: string; useCategoryCode: boolean; dateFormat?: string; serialLength: number; separator?: string; currentSerial?: number }
export type EamStatisticsItem = { key: string; name: string; count: number }
export type EamStatistics = { totalCount: number; totalOriginalValue?: number; statusStats: EamStatisticsItem[]; categoryStats: EamStatisticsItem[]; deptStats: EamStatisticsItem[] }
export type EamAssetImportRow = {
  rowNum: number; assetCode?: string; name: string; categoryName: string; managementMode: number; quantity: number
  useUserName?: string; matchedUserName?: string
  action: 'CREATE' | 'UPDATE' | 'SKIP_EXISTING' | 'SKIP_SAME_FILE' | 'ERROR'
  mappedFields: Record<string, unknown>; defaultedFields: string[]; warnings: string[]; errors: string[]
}
export type EamAssetImportPreview = {
  fileHash: string; totalRows: number; createCount: number; updateCount: number; skipCount: number
  warningCount: number; errorCount: number; batchId?: number; rows: EamAssetImportRow[]
}
export type EamCategoryImportItem = { kind: 'CATEGORY' | 'FIELD'; code: string; name: string; action: 'CREATE' | 'UPDATE' | 'SKIP' | 'CONFLICT'; message?: string }
export type EamCategoryImportResult = {
  createCount: number; updateCount: number; skipCount: number; conflictCount: number
  categoryCount: number; leafCategoryCount: number; fieldCount: number; legacyFieldCount: number
  credentialFieldCount: number; allManagementFieldsOptional: boolean; items: EamCategoryImportItem[]
}

export type AdvancedFilterCondition = { fieldKey: string; operator: string; value?: unknown; valueFrom?: unknown; valueTo?: unknown }
export type AdvancedFilterGroup = { logic: 'AND' | 'OR'; conditions: AdvancedFilterCondition[]; groups: AdvancedFilterGroup[] }
export type AdvancedFilterScene = 'lead' | 'order' | 'lead_appeal' | 'duplicate_review' | 'registration' | 'student' | 'subordinate_sales'
export type AdvancedFilterField = { fieldKey: string; group: string; label: string; valueType: 'text' | 'select' | 'number' | 'date'; operators: string[]; optionSource?: string; options: Array<{ value: string | number; label: string }>; optionsLoading?: boolean; optionsError?: boolean; retryOptions?: () => void }
export type AdvancedFilterCatalog = { fields: AdvancedFilterField[]; relativeDateOptions: Array<{ value: string; label: string }> }
export type AreaNode = {
  id: number
  name: string
  selectionCode: string
  leafSelectable: boolean
  children?: AreaNode[]
}
export type LeadCategoryNode = { id: number; name: string; children: LeadCategoryNode[] }
export type LeadCatalogItem = {
  categoryId: number; categoryName: string; categoryPath: Array<{ id: number; name: string }>
  level1CategoryId?: number; level1CategoryName?: string; level2CategoryId?: number; level2CategoryName?: string
  spuRef: string; spuName: string
  attrs: Array<{ attrKey: string; attrName: string; required: boolean; values: Array<{ value: string; label: string }> }>
}
export type LeadCatalogSku = { spuRef: string; skuRef: string; skuName: string; attrValues: Record<string, string>; price: number }
export type LeadCatalog = { categoryTree: LeadCategoryNode[]; spus: LeadCatalogItem[]; skus: LeadCatalogSku[] }
export type LeadAttachment = { infraFileId: number; fileUrl: string; originalName: string; contentType: string; fileSize: number }
export type LeadCreateRequest = {
  name: string; mobile?: string; wechatId?: string; provinceCode: string; cityCode: string
  intendedProducts: Array<{ spuRef?: string; skuRef?: string; spuUnknown: boolean; skuUnknown: boolean; primary: boolean }>; sourceChannel: string; leadCategory: string
  remark?: string; attachments: Array<{ infraFileId: number }>; dispatchMode: 'auto' | 'specified'
  specifiedSalesUserId?: number; newMediaProviderUserId?: number; idempotencyKey: string
}
export type LeadCreateResult = {
  leadId?: number; leadNo?: string; reviewId?: number; outcome: 'created' | 'activated' | 'review_pending' | 'duplicate_rejected' | 'duplicate_auto_closed'
  assignmentStatus?: string; pendingAssigneeUserId?: number; existingLeadStatus?: string
  existingQualificationStatus?: string; existingOperationalStatus?: string
}
export type LeadDuplicateReview = {
  id: number; status: 'pending' | 'completed'; submitterUserId?: number; submissionSnapshot: string
  matchRules: string; candidateSnapshot: string; resultType?: string; reviewOpinion?: string
  selectedSalesUserId?: number; reviewerUserId?: number; reviewedAt?: Timestamp; createTime: Timestamp
}
export type LeadDuplicateReviewDecision = {
  resultType: 'new_person' | 'reuse_person' | 'reactivate_lead' | 'notify_owner'
  matchedPersonId?: number; matchedLeadId?: number; selectedSalesUserId?: number
  opinion: string; attachments: Array<{ infraFileId: number }>; idempotencyKey: string
}
export type PendingLead = {
  id: number; leadNo: string; dispatchMode: 'auto' | 'specified'; maskedName: string; maskedMobile?: string; maskedWechatId?: string
  provinceName: string; cityName: string; intendedProducts: string[]; primaryIntendedProduct?: string
  sourceChannel: string; sourceChannelLabel?: string; leadCategory: string; leadCategoryLabel?: string
  remark?: string; attachmentUrls: string[]
  submittedAt: Timestamp; expiresAt?: Timestamp
  remainingSeconds?: number; rejectable: boolean; deferrable: boolean; assignmentHistoryId?: number
}
export type SalesDispatchStatus = {
  eligible: boolean
  presence: 'online' | 'offline'
  mode: 'accepting' | 'paused'
  effectiveStatus: 'online' | 'busy' | 'offline'
}
export type ManagedLeadProduct = {
  id: number; spuRef?: string; spuName?: string; skuRef?: string; skuName?: string
  selectedAttrValues?: string; price?: number; categoryName?: string; primary: boolean
}
export type ManagedLeadAttachment = { id: number; fileUrl: string; originalName: string; contentType: string; fileSize: number }
export type ManagedLead = {
  id: number; leadNo: string; personId: number; submittedName: string; submittedMobile?: string; submittedWechatId?: string
  sourceType: string; sourceLabel?: string; sourceUserId?: number; sourceUserName?: string; sourceChannel?: string
  provinceCode?: string; provinceName?: string; cityCode?: string; cityName?: string; leadCategory?: string; leadCategoryLabelSnapshot?: string
  remark?: string; status: string; assignmentStatus: string; handlingStage: string
  qualificationStatus: 'pending' | 'valid' | 'invalid'
  followUpStatus?: 'first_follow_pending' | 'following' | 'deal_pending_approval' | 'won'
  operationalStatus: 'active' | 'suspended'; dispatchMode?: string
  ownerUserId?: number; ownerUserName?: string; pendingAssigneeUserId?: number; pendingAssigneeUserName?: string
  pendingExpiresAt?: Timestamp; assignmentAttemptCount?: number; publicPoolAt?: Timestamp; submittedAt: Timestamp
  nextFollowUpAt?: Timestamp
  currentAssignmentFirstFollowUpAt?: Timestamp; currentAssignmentFirstFollowUpDeadlineAt?: Timestamp
  qualificationStartedAt?: Timestamp; qualificationDeadlineAt?: Timestamp; suspendedAt?: Timestamp
  qualifiedByUserId?: number; qualifiedByUserName?: string; qualifiedAt?: Timestamp; validDescription?: string
  convertedAt?: Timestamp; salesOrderSubmittedAt?: Timestamp
  invalidReason?: string; invalidReasonLabelSnapshot?: string; invalidDescription?: string
  invalidEvidence?: LeadAppealEvidence[]
  recycleSourceOwnerUserId?: number; recycleSourceOwnerUserName?: string
  appealDeadlineAt?: Timestamp; closedAt?: Timestamp; closeReason?: string
  createTime: Timestamp; updateTime: Timestamp; lastActivityAt?: Timestamp; relationTypes: Array<'submitter' | 'owner' | 'student_service_owner'>
  overviewVisible?: boolean; visibleTabs?: LeadDetailTab[]; identityMaskMode?: 'counterparty_masked' | 'full'
  primaryProduct?: ManagedLeadProduct; intendedProducts?: ManagedLeadProduct[]; attachments?: ManagedLeadAttachment[]
  opportunity?: { id: number; status: string; nextFollowUpAt?: Timestamp; wonAt?: Timestamp }
  activeSalesOrderId?: number; activeSalesOrderStatus?: 'pending_approval' | 'revision_required'
  availableActions?: Array<{ code: 'EDIT_BASIC_INFO' | 'ADD_FOLLOW_UP' | 'JUDGE_VALID' | 'JUDGE_INVALID' | 'ENTER_DEAL' | 'ENTER_REPURCHASE' | 'REVISE_DEAL' | 'SUBMITTER_SUPPLEMENT' | 'SUBMITTER_URGE' | 'SUBMITTER_COMPLAINT' | 'QUALIFICATION_RESTORE' | 'QUALIFICATION_TRANSFER' | 'QUALIFICATION_RECYCLE' | 'QUALIFICATION_RELEASE'; enabled: boolean }>
}
export type LeadComplaintEvidence = { infraFileId: number; fileUrl: string; originalName?: string; contentType?: string; fileSize?: number }
export type LeadComplaint = {
  id: number; leadId: number; leadNo: string; complainantUserId: number; salesUserId: number; reason: string
  complainantUserName?: string; salesUserName?: string; evidence?: LeadComplaintEvidence[]
  evidenceRefs?: string; status: 'pending' | 'handled'; result?: 'founded' | 'unfounded'
  handlerUserId?: number; handlerUserName?: string; handlerOpinion?: string; handlerEvidenceRefs?: string
  handlerEvidence?: LeadComplaintEvidence[]; handledAt?: Timestamp; createTime: Timestamp
}
export type LeadQualificationException = {
  id: number; leadNo: string; submittedName: string; submittedMobile?: string; status: string; assignmentStatus: string
  handlingStage: string; ownerUserId?: number; ownerUserName?: string
  recycleSourceOwnerUserId?: number; recycleSourceOwnerUserName?: string
  qualificationDeadlineAt?: Timestamp; suspendedAt?: Timestamp
}
export type LeadInboxFilterOption = { key: string; label: string }
export type LeadInboxFilterSection = { key: string; label: string; options: LeadInboxFilterOption[] }
export type LeadInboxFilterGroup = { key: string; label: string; sections: LeadInboxFilterSection[] }
export type LeadInboxFilterProfile = { groups: LeadInboxFilterGroup[] }
export type LeadSimpleStatus = 'first_follow_pending' | 'qualification_pending' | 'following'
  | 'deal_pending_approval' | 'won' | 'invalid' | 'closed' | 'suspended'
export type ManagedLeadPageParams = {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
  inboxGroup?: string
  inboxStage?: string
  relationScope?: 'all' | 'submitted' | 'owned'
  simpleStatus?: LeadSimpleStatus
  advancedFilter?: AdvancedFilterGroup
}
export type LeadDetailTab = 'overview' | 'follow-ups' | 'orders' | 'appeals' | 'complaints' | 'flow-history'
export type LeadFlowAttachment = {
  infraFileId?: number; originalName?: string; contentType?: string; previewUrl?: string
  previewable: boolean; available: boolean
}
export type LeadFlowHistory = {
  id: string; occurredAt: Timestamp; businessObject: string; flowNode: string; source: string
  operator?: string; fromOwner?: string; toOwner?: string
  leadStatusBefore?: string; leadStatusAfter?: string
  assignmentStatusBefore?: string; assignmentStatusAfter?: string
  reason?: string; remark?: string; attachments: LeadFlowAttachment[]
}
export type LeadFollowUpImage = { infraFileId: number; originalName: string; contentType: string; fileSize: number; sort: number; url?: string }
export type LeadFollowUp = {
  id: number; leadId: number; assignmentHistoryId?: number; opportunityId?: number; recordScope: 'lead' | 'opportunity'; operatorUserId: number; operatorName?: string
  occurredAt: Timestamp; firstInAssignment: boolean; method: string; methodLabel: string; result: string; resultLabel: string
  categoryBefore?: string; categoryBeforeLabel?: string; categoryAfter?: string; categoryAfterLabel?: string
  remark?: string; nextFollowUpAt?: Timestamp; images: LeadFollowUpImage[]
}
export type LeadFollowUpCreateRequest = {
  method: string; result: string; leadCategory?: string; remark?: string; nextFollowUpAt?: Timestamp
  images: Array<{ infraFileId: number }>; idempotencyKey: string
}
export type LeadBasicInfoUpdateRequest = {
  name: string; mobile?: string; wechatId?: string; provinceCode: string; cityCode: string; leadCategory?: string
  intendedProducts: LeadCreateRequest['intendedProducts']; reason: string
}
export type LeadAppealEvidence = { infraFileId: number; fileUrl?: string; originalName: string; contentType: string; fileSize: number; sort?: number }
export type LeadAppeal = {
  id: number; leadId: number; leadNo: string; leadName: string; roundNo: number; reviewStage: 'sales_manager' | 'quality' | 'chairman'
  status: 'sales_manager_reviewing' | 'quality_reviewing' | 'chairman_reviewing' | 'overturned' | 'upheld' | 'withdrawn'
  applicantUserId: number; applicantUserName?: string; reason: string; evidence: LeadAppealEvidence[]
  invalidReasonSnapshot?: string; invalidDescriptionSnapshot?: string; invalidEvidenceSnapshot: LeadAppealEvidence[]
  processInstanceId?: string; taskId?: string; reviewerUserId?: number; reviewerUserName?: string
  decisionReason?: string; decisionEvidence: LeadAppealEvidence[]; submittedAt: Timestamp; decidedAt?: Timestamp
  canSubmitNextRound: boolean
}
export type SalesOrderVoucher = LeadAttachment
export type SalesOrderSubmitRequest = {
  buyerName?: string; studentName: string; studentNature: string; studentMobile?: string; studentWechatId?: string
  provinceCode: string; provinceName: string; cityCode: string; cityName: string
  agreedExamTime?: string; classType?: string; servicePeriod: string; studentSource: string
  customerPaidAt: Timestamp; feeMode: string; paymentMethod: string; remark?: string
  studentSpecialRequirements?: string; materialDeliveryContact?: string
  items: Array<{ spuRef: string; skuRef: string; actualAmount: number }>
  paymentVouchers: Array<{ infraFileId: number }>; idempotencyKey: string
}
export type SalesOrder = {
  id: number; orderNo: string; leadId?: number; opportunityId?: number; personId: number; orderType: 'first_purchase' | 'repurchase'
  supersedesOrderId?: number; supersededByOrderId?: number
  status: 'pending_approval' | 'revision_required' | 'effective' | 'superseded' | 'terminated'
  submitterUserId: number; formalSalesUserId?: number; buyerName: string; studentName: string; studentNature: string
  studentMobile?: string; studentWechatId?: string; provinceCode: string; provinceName: string; cityCode: string; cityName: string
  agreedExamTime?: string; classType?: string; servicePeriod: string; servicePeriodLabelSnapshot?: string; studentSource: string; studentSourceLabelSnapshot?: string; studentNatureLabelSnapshot?: string; totalAmount: number
  customerPaidAt: Timestamp; feeMode: string; feeModeLabelSnapshot?: string; paymentMethod: string; paymentMethodLabelSnapshot?: string; remark?: string
  studentSpecialRequirements?: string; materialDeliveryContact?: string
  items: Array<{ id: number; productRef: string; skuRef: string; productName: string; skuName: string; categoryPath: string[]; attrValues: Record<string, string>; actualAmount: number }>
  paymentVouchers: SalesOrderVoucher[]; approvalRoundNo: number; approvalRoundStatus: string
  processInstanceId?: string; taskId?: string; taskDefinitionKey?: 'registrationReview' | 'financeReview'
  taskStatus?: number; taskReason?: string; taskCreateTime?: Timestamp; taskEndTime?: Timestamp; decisionReason?: string; canRevise?: boolean; canTerminate?: boolean
  version: number; currentApprovalRoundId: number; approvalRoundVersion: number; repurchaseReason?: string; terminationReason?: string
  canRequestSupervisorConfirmation?: boolean
  submittedAt: Timestamp; effectiveAt?: Timestamp
  leadProfile?: {
    leadNo: string; submittedName: string; submittedMobile?: string; submittedWechatId?: string
    sourceType?: string; sourceLabel?: string; sourceUserName?: string; sourceChannel?: string; sourceChannelLabelSnapshot?: string
    provinceName?: string; cityName?: string; leadCategory?: string; leadCategoryLabelSnapshot?: string; dispatchMode?: string; ownerUserName?: string
  }
  registrationApproval?: SalesOrderApprovalStatus
  financeApproval?: SalesOrderApprovalStatus
  registrationSupervisorConfirmation?: SalesOrderSupervisorConfirmation
  financeSupervisorConfirmation?: SalesOrderSupervisorConfirmation
  supervisorApproval?: SalesOrderSupervisorApproval
}
export type SalesOrderSupervisorConfirmation = {
  id: number; status: 'pending' | 'confirmed' | 'rejected' | 'cancelled'; requesterUserId: number
  requesterUserName?: string; requestReason: string; decisionReason?: string; requestedAt?: Timestamp; decidedAt?: Timestamp
}
export type SalesOrderApprovalStatus = {
  status: 'pending' | 'approved' | 'rejected' | 'cancelled'
  reviewerUserId?: number; reviewerUserName?: string; createTime?: Timestamp; endTime?: Timestamp
}
export type SalesOrderListItem = Pick<SalesOrder, 'id' | 'orderNo' | 'leadId' | 'status' | 'studentName' | 'studentMobile' | 'totalAmount' | 'approvalRoundNo' | 'submittedAt' | 'effectiveAt'> & {
  personId?: number; orderType?: SalesOrder['orderType']
  taskId?: string; taskDefinitionKey?: 'registrationReview' | 'financeReview'; taskStatus?: number
  taskReason?: string; taskCreateTime?: Timestamp; taskEndTime?: Timestamp
  supervisorConfirmationId?: number; supervisorConfirmationStatus?: string; supervisorRequesterName?: string
}
export type SalesOrderSupervisorInboxItem = {
  id: number; orderId: number; orderNo: string; studentName: string; approvalRoundId: number
  taskDefinitionKey: 'registrationReview' | 'financeReview'; taskId: string; requesterUserId: number
  requesterUserName?: string; supervisorUserId: number; requestReason: string; decisionReason?: string
  status: 'pending' | 'confirmed' | 'rejected' | 'cancelled'; requestedAt?: Timestamp; decidedAt?: Timestamp
  version: number; orderVersion: number; roundVersion: number
}
export type SalesOrderStatusCounts = { total: number; pendingApproval: number; revisionRequired: number; effective: number; superseded: number }
export type SalesOrderApprovalFilterOption = { key: string; label: string; count: number }
export type SalesOrderApprovalFilterSection = { key: string; label: string; options: SalesOrderApprovalFilterOption[] }
export type SalesOrderApprovalFilterGroup = { key: string; label: string; count: number; sections: SalesOrderApprovalFilterSection[] }
export type SalesOrderApprovalCenter = { key: 'registration' | 'finance'; label: string }
export type SalesOrderApprovalFilterProfile = { groups: SalesOrderApprovalFilterGroup[]; centers: SalesOrderApprovalCenter[] }
export type SalesOrderApprovalTaskTarget = {
  workType: 'approval' | 'supervisor'; orderId: number; taskId: string
  taskDefinitionKey: 'registrationReview' | 'financeReview'; center: 'registration' | 'finance'
  confirmationId?: number; status: string
}
export type BusinessTaskBucket = 'unscheduled' | 'overdue' | 'today' | 'future'
export type BusinessTaskSummary = Record<BusinessTaskBucket, number>
export type BusinessTask = {
  id: number; taskType: string; bizType: string; bizId: number; title: string; summary?: string
  status: 'pending' | 'completed' | 'cancelled'; dueAt?: Timestamp; remindAt?: Timestamp
  completedAt?: Timestamp; cancelledAt?: Timestamp; createTime: Timestamp; overdue: boolean
  actionCode?: 'OPEN_LEAD_ASSIGNMENT' | 'OPEN_LEAD_FOLLOW_UP' | 'OPEN_WORK_PLAN_ITEM' | 'REVIEW_WORK_PLAN_ITEM' | 'OPEN_SALES_ORDER_REVISION' | 'COMPLETE_BIRTHDAY_CARE' | 'OPEN_STUDENT_FIRST_CONTACT' | 'OPEN_STUDENT_STUDY_PLAN' | 'OPEN_STUDENT_CONTACT' | 'OPEN_STUDENT_CONTACT_ASSISTANCE'
  serviceRelationId?: number
  targetTab?: string
  targetRecordId?: number
  actionable: boolean
}
export type BpmTask = {
  id: string; name: string; createTime: Timestamp; endTime?: Timestamp; status: number; reason?: string
  processInstanceId: string; taskDefinitionKey?: string; parentTaskId?: string
  processInstance?: { id: string; name: string; createTime: Timestamp; startUser?: { id: number; nickname: string } }
}
export type SalesOrderSupervisorApproval = SalesOrderSupervisorConfirmation & {
  taskDefinitionKey: 'registrationReview' | 'financeReview'; center: 'registration' | 'finance'
  supervisorUserId: number; supervisorUserName?: string
}
export type ExportTask = {
  id: number; taskNo: string; exportType: 'lead' | 'order' | 'finance_order' | 'cashback' | 'withdrawal'
  status: 'queued' | 'prechecking' | 'generating' | 'ready' | 'failed' | 'cancelled' | 'expired'
  attemptCount: number; resultFileName?: string; resultFileSize?: number; readyAt?: Timestamp; expiresAt?: Timestamp
  failureCode?: string; failureMessage?: string; createTime: Timestamp
}
export type SimpleUser = { id: number; nickname: string; username?: string; status?: number; avatar?: string; deptId?: number; deptName?: string }
export type SimpleDept = { id: number; name: string; parentId?: number }
export type WorkPlanAttachmentUpload = { infraFileId: number; originalName: string; contentType?: string; fileSize?: number }
export type WorkPlanChange = { id: number; subjectType: string; subjectId: number; changeType: string; beforeSnapshot?: string; afterSnapshot?: string; reason: string; operatorUserId: number; changedAt: Timestamp }
export type WorkReport = {
  id: number; revisionNo: number; completionSummary: string; submitterUserId: number; submittedAt: Timestamp
  confirmationDecision?: 'auto_confirmed' | 'confirmed' | 'returned'; confirmationComment?: string
  confirmedByUserId?: number; confirmedAt?: Timestamp; infraFileIds: number[]; reportFields?: Record<string, unknown>
}
export type WorkTask = {
  id: number; planId?: number; parentTaskId?: number; title: string; description?: string; deliverableRequirement?: string
  assigneeUserId: number; assigneeDeptId?: number; assignerUserId: number; dueAt?: Timestamp; remindAt?: Timestamp
  confirmationRequired: boolean; confirmerUserId?: number
  status: 'draft' | 'pending' | 'awaiting_confirmation' | 'completed' | 'cancelled'; reportedAt?: Timestamp
  completedAt?: Timestamp; cancelledAt?: Timestamp; cancelReason?: string; version: number; blockedByChildren: boolean
  completedChildCount: number; totalChildCount: number; taskFields?: Record<string, unknown>; reports: WorkReport[]
  availableActions: Array<'assign' | 'complete' | 'review' | 'cancel' | 'decompose'>
}
export type WorkPlan = {
  id: number; title: string; periodType: 'day' | 'month' | 'week' | 'quarter' | 'year' | 'custom'; startDate: string; endDate: string
  planTypeId: number; templateId: number; templateVersionId: number; ownerUserId: number; ownerDeptId?: number
  objective?: string; keyRequirements?: string; status: 'draft' | 'active' | 'completed' | 'cancelled'; summaryReady: boolean
  creatorUserId: number; publishedAt?: Timestamp; completedAt?: Timestamp; cancelledAt?: Timestamp; cancelReason?: string; version: number
  availableActions: Array<'update' | 'publish' | 'assign' | 'close' | 'cancel'>; fieldDefinitions: WorkPlanTemplateField[]
  planFields?: Record<string, unknown>; tasks: WorkTask[]; summary?: WorkPlanSummary; changes: WorkPlanChange[]
}
export type WorkTaskInput = {
  id?: number; parentTaskId?: number; title: string; description?: string; deliverableRequirement?: string; assigneeUserId: number
  dueAt?: string; remindAt?: string; confirmationRequired: boolean; confirmerUserId?: number
  taskFields?: Record<string, unknown>; version?: number; reason?: string
}
export type WorkPlanInput = {
  title: string; periodType: WorkPlan['periodType']; startDate: string; endDate: string; templateVersionId: number; ownerUserId: number
  objective?: string; keyRequirements?: string; planFields?: Record<string, unknown>; supplementalFields?: WorkPlanTemplateField[]
  version?: number; reason?: string; tasks?: WorkTaskInput[]
}
export type WorkPlanType = { id: number; code: string; name: string; description?: string; status: number; sort: number }
export type WorkPlanTemplateField = { id?: number; fieldKey?: string; label: string; section: 'plan' | 'task' | 'report' | 'summary'; fieldType: string; required?: boolean; unit?: string; placeholder?: string; filterable?: boolean; exportable?: boolean; optionsJson?: string; defaultValueJson?: string; sort?: number }
export type WorkPlanTemplateTask = { title: string; description?: string; deliverableRequirement?: string; dueOffsetDays?: number; dueOffsetBasis?: string; confirmationRequired?: boolean; sort?: number }
export type WorkPlanTemplate = { id: number; typeId: number; code: string; name: string; description?: string; status: string; currentVersionNo: number; versionId?: number; versionStatus?: string; periodMode?: WorkPlan['periodType']; fields?: WorkPlanTemplateField[]; applicableDeptIds?: number[]; includeChildDepartments?: boolean; presetItems?: WorkPlanTemplateTask[] }
export type LeadAssignmentRule = { id: number; code: string; name: string; strategyType: 'global_round_robin'; acceptTimeoutSeconds: number; maxAttempts: number; dailyClaimLimit: number; status: number }
export type LeadFollowUpRule = { id: number; code: string; name: string; firstFollowUpTimeoutMinutes: number; qualificationTimeoutMinutes: number; agingPoolTimeoutDays: number; noProgressWarningDays: number; noProgressGraceDays: number; notificationPopupDurationMinutes: number; duplicateAutoResolutionEnabled: boolean; status: number; version: number }
export type LeadFilterAudience = 'submitter' | 'owner' | 'reviewer'
export type LeadFilterCondition = { field: string; values: string[] }
export type LeadFilterOptionConfig = { key: string; label: string; sort: number; enabled: boolean; conditions: LeadFilterCondition[] }
export type LeadFilterGroupConfig = { key: string; label: string; sort: number; enabled: boolean; sectionLabel?: string; conditions: LeadFilterCondition[]; options: LeadFilterOptionConfig[] }
export type LeadFilterAdmin = { audience: LeadFilterAudience; audienceLabel: string; draftGroups: LeadFilterGroupConfig[]; publishedGroups: LeadFilterGroupConfig[]; publishedVersion: number; publishedAt?: Timestamp; updateTime?: Timestamp }
export type LeadFilterVersion = { versionNo: number; publishedBy: number; publishedAt: Timestamp }
export type ProductCategory = { id: number; parentId: number; level: number; name: string; status: number; sort: number; children?: ProductCategory[] }
export type ProductCategorySaveRequest = { id?: number; parentId?: number; name: string; status: number; sort: number; remark?: string }
export type ProductConfig = { id: number; productRef: string; name: string; subtitle?: string; categoryId: number; categoryName?: string; status: number; sort: number; updateTime?: Timestamp }
export type ProductSaveRequest = { id?: number; categoryId: number; name: string; subtitle?: string; description?: string; targetAudience?: string; studyDuration?: string; studyMode?: string; coverImage?: string; status: number; sort: number; remark?: string }
export type ProductSku = { id: number; spuId: number; skuRef: string; skuName: string; attrValues: Record<string, string>; price: number; status: number; sort: number; remark?: string; updateTime?: Timestamp }
export type ProductSkuSaveRequest = { id?: number; spuId: number; skuName: string; attrValues: Record<string, string>; price: number; status: number; sort: number; remark?: string }
export type ProductAttribute = { attrKey?: string; attrName: string; required: boolean; sort: number; values: Array<{ value: string; label: string; sort: number }> }
export type WorkPlanTemplateSaveRequest = { typeId: number; code?: string; name: string; description?: string; periodMode: NonNullable<WorkPlanTemplate['periodMode']>; fields: WorkPlanTemplateField[]; applicableDeptIds: number[]; includeChildDepartments: boolean; presetItems: WorkPlanTemplateTask[] }
export type WorkPlanSummary = { id: number; summary: string; submitterUserId: number; submittedAt: Timestamp; infraFileIds: number[]; summaryFields?: Record<string, unknown> }
export type SubordinateCategoryCount = { value: string; label: string; count: number; configured: boolean }
export type SubordinateSales = {
  userId: number; name: string; avatar?: string; username: string; mobile?: string; accountStatus: number
  presence: 'online' | 'offline'; accepting: boolean; eligible: boolean; canReceiveNewLeads: boolean
  newcomerPoolStatus: 'not_available'; todayPendingCount: number; todayFollowUpStatus: 'completed' | 'incomplete'
  firstFollowTimeoutCount: number; suspendedLeadCount: number; categoryCounts: SubordinateCategoryCount[]
  validLeadCount: number; convertedLeadCount: number; effectiveOrderCount: number; effectiveOrderAmount: number
}
export type LeadAgingPoolStatus = 'waiting_assignment' | 'assigned' | 'deal_pending'
export type LeadAgingPoolItem = {
  cycleId: number; leadId: number; leadNo: string; cycleNo: number; status: LeadAgingPoolStatus
  originalOwnerUserId: number; originalOwnerUserName?: string; collaboratorUserId?: number; collaboratorUserName?: string
  frozenDeptId: number; frozenDeptName?: string; submittedName: string; submittedMobile?: string; submittedWechatId?: string
  leadCategory?: string; sourceChannel?: string; ownershipStartedAt: Timestamp; dueAt: Timestamp; enteredAt: Timestamp
  assignedAt?: Timestamp; lastFollowUpAt?: Timestamp; nextFollowUpAt?: Timestamp
  activeSalesOrderId?: number; activeSalesOrderStatus?: 'pending_approval' | 'revision_required'
  availableActions: Array<'ASSIGN' | 'EXIT' | 'REQUEST_TRANSFER' | 'ADD_FOLLOW_UP' | 'ENTER_DEAL' | 'REVISE_DEAL'>
}
export type SubordinateTask = { id: number; taskType: string; leadId: number; leadNo: string; leadName?: string; dueAt?: Timestamp; overdue: boolean }
export type SubordinateBatchItem = { leadId: number; leadNo?: string; success: boolean; code: string; message: string }
export type SubordinateBatchResult = { successCount: number; failureCount: number; items: SubordinateBatchItem[] }
export type SubordinatePauseAllResult = { totalCount: number; changedCount: number; alreadyPausedCount: number }
export type NotifyMessage = {
  id: number
  templateNickname: string
  templateTitle?: string
  templateSummary?: string
  templateContent: string
  templateType: number
  readStatus: boolean
  readTime?: Timestamp
  createTime: Timestamp
  notifyRuleId?: number
  sceneCode?: string
  actionType?: 'none' | 'message_detail' | 'business_detail'
  bizType?: string
  bizId?: number
  sourceEventKey?: string
}

export type NotifyMessagePageParams = {
  pageNo: number
  pageSize: number
  readStatus?: boolean
}

export const http = axios.create({ baseURL: APP_CONFIG.API_BASE_URL, timeout: 30000 })
type RefreshResult =
  | { status: 'refreshed'; accessToken: string }
  | { status: 'failed'; expectedRefreshToken: string | null }
  | { status: 'stale' }
let refreshing: Promise<RefreshResult> | null = null

export class AuthenticationError extends Error {
  readonly code = 401
  constructor(message = '账号未登录') {
    super(message)
    this.name = 'AuthenticationError'
  }
}

export class ApiError extends Error {
  constructor(readonly code: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export const clearAuthStorage = () => {
  refreshing = null
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.CLIENT_ID)
  localStorage.removeItem(STORAGE_KEYS.EXPIRES_TIME)
  localStorage.removeItem(STORAGE_KEYS.IMPERSONATION)
}

export const AUTH_EXPIRED_EVENT = 'zsjos-auth-expired'
let authExpiredDispatched = false
export const isCurrentRefreshSession = (expectedRefreshToken: string | null, currentRefreshToken: string | null) =>
  expectedRefreshToken === currentRefreshToken
const expireAuthentication = (expectedRefreshToken: string | null) => {
  if (!isCurrentRefreshSession(expectedRefreshToken, localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN))) return
  clearAuthStorage()
  if (authExpiredDispatched) return
  authExpiredDispatched = true
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
}

http.interceptors.request.use(config => {
  const expectedOrigin = new URL(config.baseURL || APP_CONFIG.API_BASE_URL, window.location.origin).origin
  if (/^[a-z][a-z\d+.-]*:\/\//i.test(config.url || '') && new URL(config.url!).origin !== expectedOrigin) {
    config.headers.delete('tenant-id')
    config.headers.delete('Authorization')
    config.headers.delete('X-ZSJOS-Impersonation-Session')
    return config
  }
  config.headers['tenant-id'] = APP_CONFIG.DEFAULT_TENANT_ID
  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  if (token) config.headers.Authorization = `Bearer ${token}`
  const impersonation = localStorage.getItem(STORAGE_KEYS.IMPERSONATION)
  const impersonationId = resolveImpersonationSessionHeader(config.url, impersonation, expectedOrigin)
  const request = config as typeof config & { _zsjosImpersonationSessionId?: number }
  delete request._zsjosImpersonationSessionId
  config.headers.delete('X-ZSJOS-Impersonation-Session')
  if (impersonationId != null) {
    config.headers['X-ZSJOS-Impersonation-Session'] = impersonationId
    request._zsjosImpersonationSessionId = impersonationId
  }
  return config
})

export type NotifyMessageCursorParams = { cursor?: string; limit?: number; readStatus?: boolean }

const isAuthEndpoint = (url?: string) => ['/system/auth/login', '/system/auth/logout', '/system/auth/refresh-token']
  .some(path => url?.includes(path))

const retryAfterRefresh = async (config: AxiosRequestConfig, originalError: unknown) => {
  const request = config as AxiosRequestConfig & { _retry?: boolean }
  if (request._retry || isAuthEndpoint(request.url)) return Promise.reject(originalError)
  request._retry = true
  if (!refreshing) {
    const task = refreshToken()
    refreshing = task
    void task.finally(() => { if (refreshing === task) refreshing = null })
  }
  const result = await refreshing
  if (result.status === 'stale') return Promise.reject(originalError)
  if (result.status === 'failed') {
    expireAuthentication(result.expectedRefreshToken)
    return Promise.reject(new AuthenticationError())
  }
  request.headers = { ...request.headers, Authorization: `Bearer ${result.accessToken}` }
  return http(request)
}

const clearRejectedImpersonation = (code: unknown, config?: AxiosRequestConfig & { _zsjosImpersonationSessionId?: number }) => {
  if (typeof code === 'number') handleImpersonationInvalid(code, config?._zsjosImpersonationSessionId)
}

http.interceptors.response.use(async response => {
  clearRejectedImpersonation(response.data?.code, response.config)
  if (response.data?.code === 401) return retryAfterRefresh(response.config, new AuthenticationError(response.data.msg))
  return response
}, async error => {
  const original = error.config as AxiosRequestConfig & { _retry?: boolean } | undefined
  clearRejectedImpersonation(error.response?.data?.code, original)
  if (error.response?.status !== 401 || !original) return Promise.reject(error)
  return retryAfterRefresh(original, error)
})

let dictDataRequest: Promise<DictData[]> | undefined

export const unwrap = <T,>(response: { data: any }): T => {
  const payload = response.data
  if (payload && typeof payload.code === 'number') {
    if (payload.code === 401) throw new AuthenticationError(payload.msg)
    if (payload.code !== 0) throw new ApiError(payload.code, payload.msg || `请求失败（${payload.code}）`)
    return payload.data as T
  }
  return payload as T
}

async function refreshToken(): Promise<RefreshResult> {
  const refresh = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN)
  if (!refresh) return { status: 'failed', expectedRefreshToken: null }
  try {
    const clientId = localStorage.getItem(STORAGE_KEYS.CLIENT_ID)
    const clientIdParam = clientId ? `&clientId=${encodeURIComponent(clientId)}` : ''
    const response = await axios.post(`${APP_CONFIG.API_BASE_URL}/system/auth/refresh-token?refreshToken=${encodeURIComponent(refresh)}${clientIdParam}`, undefined, { headers: { 'tenant-id': APP_CONFIG.DEFAULT_TENANT_ID }, timeout: 30000 })
    const result = unwrap<{ accessToken: string; refreshToken: string; clientId?: string }>(response)
    if (!isCurrentRefreshSession(refresh, localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN))) {
      return { status: 'stale' }
    }
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, result.accessToken)
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, result.refreshToken)
    if (result.clientId) localStorage.setItem(STORAGE_KEYS.CLIENT_ID, result.clientId)
    return { status: 'refreshed', accessToken: result.accessToken }
  } catch {
    return isCurrentRefreshSession(refresh, localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN))
      ? { status: 'failed', expectedRefreshToken: refresh }
      : { status: 'stale' }
  }
}

const isUrl = (path: string) => /^https?:\/\//i.test(path)

// Keep this aligned with yudao-ui's pathResolve: child paths are relative to their parent.
export const resolveMenuPath = (parentPath: string, path?: string) => {
  if (path && isUrl(path)) return path
  if (!path) return parentPath
  const childPath = path.startsWith('/') ? path : `/${path}`
  return `${parentPath}${childPath}`.replace(/\/{2,}/g, '/')
}

export function buildMenuTree(rawMenus: RawMenu[], parentPath = '/'): WorkbenchMenu[] {
  return rawMenus.map(menu => {
    const path = resolveMenuPath(parentPath, menu.path)
    const children = buildMenuTree(menu.children || [], path)
    return {
      ...menu,
      path,
      hidden: !menu.visible,
      noCache: !menu.keepAlive,
      alwaysShow: children.length > 0 && (menu.alwaysShow ?? true),
      children
    }
  })
}

export const api = {
  login: async (username: string, password: string, platform: 'PC' | 'MOBILE' = 'PC') => {
    const result = unwrap<{ accessToken: string; refreshToken: string; expiresTime: string; clientId?: string }>(await http.post('/system/auth/login', { username, password, platform }))
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, result.accessToken)
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, result.refreshToken)
    localStorage.setItem(STORAGE_KEYS.EXPIRES_TIME, result.expiresTime)
    localStorage.setItem(STORAGE_KEYS.CLIENT_ID, result.clientId || (platform === 'MOBILE' ? 'zsjos-mobile' : 'zsjos-pc'))
    refreshing = null
    authExpiredDispatched = false
    return result
  },
  logout: async () => {
    try { await http.post('/system/auth/logout') } finally {
      clearAuthStorage()
    }
  },
  permissionInfo: async () => unwrap<PermissionInfo>(await http.get('/system/auth/get-permission-info')),
  userProfile: async () => unwrap<UserProfile>(await http.get('/system/user/profile/get')),
  updateUserProfile: async (data: UserProfileUpdate) => unwrap<boolean>(await http.put('/system/user/profile/update', data)),
  updateUserPassword: async (oldPassword: string, newPassword: string) => unwrap<boolean>(await http.put('/system/user/profile/update-password', { oldPassword, newPassword })),
  uploadAvatar: async (file: File) => {
    const data = new FormData(); data.append('file', file); data.append('directory', 'employee/avatar')
    return unwrap<string>(await http.post('/infra/file/avatar/upload', data))
  },
  boundSocialUsers: async () => unwrap<SocialUser[]>(await http.get('/system/social-user/get-bind-list')),
  socialAuthRedirect: async (type: number, redirectUri: string) => unwrap<string>(await http.get('/system/auth/social-auth-redirect', { params: { type, redirectUri } })),
  bindSocialUser: async (type: number, code: string, state: string) => unwrap<boolean>(await http.post('/system/social-user/bind', { type, code, state })),
  unbindSocialUser: async (type: number, openid: string) => unwrap<boolean>(await http.delete('/system/social-user/unbind', { data: { type, openid } })),
  dictDataByType: async (dictType: string) => {
    const request = dictDataRequest ?? (dictDataRequest = http.get('/system/dict-data/simple-list')
      .then(response => unwrap<DictData[]>(response))
      .catch((error: unknown) => {
        dictDataRequest = undefined
        throw error
      }))
    return request.then(dictData => dictData.filter(item => item.dictType === dictType))
  },
  areaTree: async () => unwrap<AreaNode[]>(await http.get('/system/area/tree')),
  leadCatalog: async () => unwrap<LeadCatalog>(await http.get('/zsjos/lead/product/catalog')),
  uploadLeadAttachment: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post('/zsjos/lead/attachment/upload', data))
  },
  createLead: async (data: LeadCreateRequest) => unwrap<LeadCreateResult>(await http.post('/zsjos/lead/create', data)),
  createSelfSourcedLead: async (data: LeadCreateRequest) => unwrap<LeadCreateResult>(await http.post('/zsjos/lead/self-sourced/create', data)),
  newMediaProviders: async () => unwrap<SalesUser[]>(await http.get('/zsjos/lead/self-sourced/new-media-providers')),
  mediaAccount: {
    create: async (data: { studentPersonId: number; platformValue: string; platformLabelSnapshot: string; detailValues: Record<string, unknown> }) => unwrap<number>(await http.post('/zsjos/media-account/create', data)),
    publishedFieldConfig: async () => unwrap<MediaAccountFieldConfig>(await http.get('/zsjos/media-account-field-config/published')),
    get: async (id: number) => unwrap<MediaAccount>(await http.get('/zsjos/media-account/get', { params: { id } })),
    page: async (params: { pageNo: number; pageSize: number; keyword?: string; sStage?: string }) => unwrap<PageResult<MediaAccount>>(await http.get('/zsjos/media-account/page', { params })),
    studentCandidates: async (keyword?: string) => unwrap<Array<{ personId: number; name?: string }>>(await http.get('/zsjos/media-account/student-candidates', { params: { keyword } })),
    bindStudent: async (id: number, studentPersonId: number, reason?: string) => unwrap<boolean>(await http.post(`/zsjos/media-account/${id}/bind-student`, null, { params: { studentPersonId, reason } })),
    unbindStudent: async (id: number, reason?: string) => unwrap<boolean>(await http.post(`/zsjos/media-account/${id}/unbind-student`, null, { params: { reason } })),
    advanceStage: async (id: number, toStage: string, version: number, basis: string) => unwrap<boolean>(await http.post(`/zsjos/media-account/${id}/advance-stage`, null, { params: { toStage, version, basis, criteriaSnapshotJson: JSON.stringify({ basis }) } })),
    rollbackStage: async (id: number, toStage: string, version: number, basis: string) => unwrap<boolean>(await http.post(`/zsjos/media-account/${id}/rollback-stage`, null, { params: { toStage, version, basis, criteriaSnapshotJson: JSON.stringify({ basis }) } })),
    update: async (id: number, data: Partial<MediaAccount> & { version: number; nickname: string }) => unwrap<boolean>(await http.put(`/zsjos/media-account/${id}`, data)),
    diagnose: async (id: number, data: { weekNo: string; statStart: string; statEnd: string; basicJson: string; productionFunnelJson: string; platformDataJson: string; contentPerfJson: string; leadFunnelJson: string; rootCauseJson: string; nextWeekPlanJson: string; suggestedGrade?: string; configVersionId: number }) => unwrap<number>(await http.post(`/zsjos/media-account/${id}/diagnoses`, data)),
    publishedDiagnosisConfig: async () => unwrap<number>(await http.get('/zsjos/media-account/diagnosis-config/published')),
    rescue: async (id: number, version: number, status: string) => unwrap<boolean>(await http.post(`/zsjos/media-account/${id}/rescue`, null, { params: { version, status } })),
    requestRebind: async (id: number, targetStudentId: number, version: number) => unwrap<string>(await http.post(`/zsjos/media-account/${id}/request-rebind`, null, { params: { targetStudentId, version } }))
  },
  mediaContent: {
    create: async (data: { accountId: number; title: string; topic?: string; contentClassValue: string; contentClassLabelSnapshot: string }) => unwrap<number>(await http.post('/zsjos/content/create', data)),
    get: async (id: number) => unwrap<MediaContent>(await http.get('/zsjos/content/get', { params: { id } })),
    page: async (params: { pageNo: number; pageSize: number; status?: string; keyword?: string }) => unwrap<PageResult<MediaContent>>(await http.get('/zsjos/content/page', { params })),
    completeTopic: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/content/${id}/complete-topic`, null, { params: { version } })),
    submitProduction: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/content/${id}/submit-production`, null, { params: { version } })),
    submitAcceptance: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/content/${id}/submit-acceptance`, null, { params: { version } })),
    approveAcceptance: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/content/${id}/approve-acceptance`, null, { params: { version } })),
    rejectAcceptance: async (id: number, version: number, reason: string) => unwrap<boolean>(await http.post(`/zsjos/content/${id}/reject-acceptance`, null, { params: { version, reason } })),
    startRevision: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/content/${id}/start-revision`, null, { params: { version } })),
    resubmitProduction: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/content/${id}/resubmit-production`, null, { params: { version } })),
    versions: async (contentId: number) => unwrap<unknown[]>(await http.get('/zsjos/content/version/list', { params: { contentId } }))
  },
  productionTicket: {
    create: async (data: { accountId: number; reviewerUserId: number; assigneeFilmingEditorUserId?: number; scriptText?: string; expectedDeliveredAt?: Timestamp; deadlineAt?: Timestamp; maxRevisionCount?: number }) => unwrap<number>(await http.post('/zsjos/production-ticket/create', data)),
    get: async (id: number) => unwrap<ProductionTicket>(await http.get('/zsjos/production-ticket/get', { params: { id } })),
    page: async (params: { pageNo: number; pageSize: number; status?: string; keyword?: string }) => unwrap<PageResult<ProductionTicket>>(await http.get('/zsjos/production-ticket/page', { params })),
    accept: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/production-ticket/${id}/accept`, null, { params: { version } })),
    startProduction: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/production-ticket/${id}/start-production`, null, { params: { version } })),
    submit: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/production-ticket/${id}/submit`, null, { params: { version } })),
    startCheck: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/production-ticket/${id}/start-check`, null, { params: { version } })),
    approve: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/production-ticket/${id}/approve`, null, { params: { version } })),
    reject: async (id: number, version: number, reason: string) => unwrap<boolean>(await http.post(`/zsjos/production-ticket/${id}/reject`, null, { params: { version, reason } })),
    reaccept: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/production-ticket/${id}/reaccept`, null, { params: { version } }))
  },
  positioningCard: {
    publishedTemplate: async (templateId?: number) => unwrap<DirectorTemplateSnapshot>(await http.get('/zsjos/positioning-card/published-template', { params: { templateId } })),
    create: async (data: { accountId: number; studentPersonId?: number; serviceRelationId?: number; templateId?: number; trialEndDate?: string; values?: Record<string, unknown>; version?: number; professionalRisk?: boolean; layer1Json?: string; layer2Json?: string; formulaJson?: string; feasibilityJson?: string; contentFormJson?: string; complianceJson?: string }) => unwrap<number>(await http.post('/zsjos/positioning-card/create', data)),
    get: async (id: number) => unwrap<PositioningCard>(await http.get('/zsjos/positioning-card/get', { params: { id } })),
    page: async (params: { pageNo: number; pageSize: number; status?: string }) => unwrap<PageResult<PositioningCard>>(await http.get('/zsjos/positioning-card/page', { params })),
    submitReview: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/positioning-card/${id}/submit-review`, null, { params: { version } })),
    operatorApprove: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/positioning-card/${id}/operator-approve`, null, { params: { version } })),
    operatorReject: async (id: number, version: number, reason: string) => unwrap<boolean>(await http.post(`/zsjos/positioning-card/${id}/operator-reject`, null, { params: { version, reason } })),
    confirmTrial: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/positioning-card/${id}/confirm-trial`, null, { params: { version } })),
    archive: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/positioning-card/${id}/archive`, null, { params: { version } })),
    versions: async (cardId: number) => unwrap<unknown[]>(await http.get('/zsjos/positioning/workspace/versions', { params: { cardId } })),
    execCard: async (cardId: number) => unwrap<unknown>(await http.get('/zsjos/positioning/workspace/exec-card', { params: { cardId } }))
  },
  directorConfig: {
    templates: async (positioning: boolean) => unwrap<DirectorTemplate[]>(await http.get(positioning ? '/zsjos/positioning-template/list' : '/zsjos/director-interview-template/list')),
    copyDraft: async (positioning: boolean, id: number, version: number) => unwrap<number>(await http.post(`${positioning ? '/zsjos/positioning-template' : '/zsjos/director-interview-template'}/${id}/draft/copy`, null, { params: { version } })),
    saveDraft: async (positioning: boolean, id: number, data: { versionId: number; version: number; name: string; defaultTemplate: boolean; fields: StudentContactFormField[] }) => unwrap<boolean>(await http.put(`${positioning ? '/zsjos/positioning-template' : '/zsjos/director-interview-template'}/${id}/draft`, data)),
    publish: async (positioning: boolean, id: number, data: { versionId: number; version: number }) => unwrap<boolean>(await http.post(`${positioning ? '/zsjos/positioning-template' : '/zsjos/director-interview-template'}/${id}/publish`, data)),
    get: async () => unwrap<DirectorConfig>(await http.get('/zsjos/director-config')),
    update: async (data: DirectorConfig) => unwrap<boolean>(await http.put('/zsjos/director-config', data))
  },
  studentOps: {
    exceptions: async () => unwrap<MediaException[]>(await http.get('/zsjos/student-ops/exceptions')),
    createException: async (data: Record<string, unknown>) => unwrap<number>(await http.post('/zsjos/student-ops/exceptions/create', data)),
    resolve: async (id: number, version: number, resolution: string) => unwrap<boolean>(await http.post(`/zsjos/student-ops/exceptions/${id}/resolve`, null, { params: { version, resolution } })),
    assess: async (data: Record<string, unknown>) => unwrap<number>(await http.post('/zsjos/student-ops/assessments/create', data)),
    graduations: async () => unwrap<GraduationApplication[]>(await http.get('/zsjos/student-ops/graduations')),
    graduate: async (data: { serviceRelationId: number; reason: string; snapshotJson: string }) => unwrap<number>(await http.post('/zsjos/student-ops/graduations/create', data))
  },
  mediaReview: {
    list: async () => unwrap<MediaReview[]>(await http.get('/zsjos/reviews/list')),
    create: async (data: { reviewType: string; subjectType: string; subjectId: number; reportJson: string; evidenceRefsJson?: string }) => unwrap<number>(await http.post('/zsjos/reviews/create', data)),
    submit: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/reviews/${id}/submit`, null, { params: { version } })),
    approve: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/reviews/${id}/approve`, null, { params: { version } })),
    reject: async (id: number, version: number, reason: string) => unwrap<boolean>(await http.post(`/zsjos/reviews/${id}/reject`, null, { params: { version, reason } })),
    archive: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/reviews/${id}/archive`, null, { params: { version } }))
  },
  duplicateReviewPage: async (params: { status: 'pending' | 'completed'; pageNo: number; pageSize: number; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter || params.keyword
      ? unwrap<PageResult<LeadDuplicateReview>>(await http.post('/zsjos/lead-duplicate-review/search-page', params))
      : unwrap<PageResult<LeadDuplicateReview>>(await http.get('/zsjos/lead-duplicate-review/page', { params })),
  duplicateReviewSalesCandidates: async () =>
    unwrap<AssignmentUser[]>(await http.get('/zsjos/lead-duplicate-review/sales-candidates')),
  uploadDuplicateReviewAttachment: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post('/zsjos/lead-duplicate-review/attachment/upload', data))
  },
  decideDuplicateReview: async (id: number, data: LeadDuplicateReviewDecision) =>
    unwrap<boolean>(await http.post(`/zsjos/lead-duplicate-review/${id}/decision`, data)),
  myPendingLeads: async () => unwrap<PendingLead[]>(await http.get('/zsjos/lead/assignment/my-pending')),
  myDispatchStatus: async () => unwrap<SalesDispatchStatus>(await http.get('/zsjos/lead/dispatch-status/my')),
  dispatchHeartbeat: async () => unwrap<SalesDispatchStatus>(await http.post('/zsjos/lead/dispatch-status/heartbeat')),
  updateDispatchMode: async (accepting: boolean) => unwrap<SalesDispatchStatus>(
    await http.put('/zsjos/lead/dispatch-status/mode', { accepting })
  ),
  dispatchOffline: async () => unwrap<SalesDispatchStatus>(await http.post('/zsjos/lead/dispatch-status/offline')),
  acceptLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/accept`)),
  rejectLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/reject`)),
  claimPoolPage: async (params: { pageNo: number; pageSize: number; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<PendingLead>>(await http.post('/zsjos/lead/claim-pool/search-page', params))
      : unwrap<PageResult<PendingLead>>(await http.get('/zsjos/lead/claim-pool/page', { params })),
  claimLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/claim`)),
  managedLeadInboxPage: async (audience: 'submitter' | 'owner', params: ManagedLeadPageParams) =>
    params.advancedFilter ? unwrap<PageResult<ManagedLead>>(await http.post(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/search-page`, params))
      : unwrap<PageResult<ManagedLead>>(await http.get(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/page`, { params })),
  managedLeadInboxCursor: async (audience: 'submitter' | 'owner', params: Omit<ManagedLeadPageParams, 'pageNo' | 'pageSize'> & { cursor?: string; limit?: number }) =>
    params.advancedFilter ? unwrap<CursorPageResult<ManagedLead>>(await http.post(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/search-cursor`, params))
      : unwrap<CursorPageResult<ManagedLead>>(await http.get(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/cursor`, { params })),
  managedLead: async (id: number) => unwrap<ManagedLead>(await http.get('/zsjos/lead/get', { params: { id } })),
  leadFlowHistory: async (id: number) => unwrap<LeadFlowHistory[]>(await http.get(`/zsjos/lead/${id}/flow-history`)),
  allLeadPage: async (params: ManagedLeadPageParams) =>
    params.advancedFilter ? unwrap<PageResult<ManagedLead>>(await http.post('/zsjos/lead/search-page', params))
      : unwrap<PageResult<ManagedLead>>(await http.get('/zsjos/lead/page', { params })),
  allLeadCursor: async (params: Omit<ManagedLeadPageParams, 'pageNo' | 'pageSize'> & { cursor?: string; limit?: number }) =>
    params.advancedFilter ? unwrap<CursorPageResult<ManagedLead>>(await http.post('/zsjos/lead/search-cursor', params))
      : unwrap<CursorPageResult<ManagedLead>>(await http.get('/zsjos/lead/cursor', { params })),
  agingPoolPage: async (params: { pageNo: number; pageSize: number; keyword?: string; status?: LeadAgingPoolStatus; inboxGroup?: string; inboxStage?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<LeadAgingPoolItem>>(await http.post('/zsjos/lead/aging-pool/search-page', params))
      : unwrap<PageResult<LeadAgingPoolItem>>(await http.get('/zsjos/lead/aging-pool/page', { params })),
  agingPoolCounts: async () => unwrap<Record<string, number>>(await http.get('/zsjos/lead/aging-pool/counts')),
  agingPoolFilterProfile: async () => unwrap<LeadInboxFilterProfile>(await http.get('/zsjos/lead/aging-pool/filter-profile')),
  agingPoolCandidates: async (cycleId: number) =>
    unwrap<Array<{ id: number; nickname: string }>>(await http.get(`/zsjos/lead/aging-pool/${cycleId}/candidates`)),
  assignAgingPool: async (cycleId: number, salesUserId: number) => unwrap<boolean>(
    await http.post(`/zsjos/lead/aging-pool/${cycleId}/assign`, { salesUserId, idempotencyKey: createIdempotencyKey() })
  ),
  exitAgingPool: async (cycleId: number, reason: string) => unwrap<boolean>(
    await http.post(`/zsjos/lead/aging-pool/${cycleId}/exit`, { reason, idempotencyKey: createIdempotencyKey() })
  ),
  requestAgingPoolTransfer: async (cycleId: number, reason: string) => unwrap<number>(
    await http.post(`/zsjos/lead/aging-pool/${cycleId}/transfer-request`, { reason, idempotencyKey: createIdempotencyKey() })
  ),
  managedLeadStatusCounts: async () => unwrap<Record<string, number>>(await http.get('/zsjos/lead/status-counts')),
  judgeLeadValid: async (id: number, data: { leadCategory?: string; remark: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/judge-valid`, data)),
  judgeLeadInvalid: async (id: number, data: { reasonCode: string; description: string; attachments: Array<{ infraFileId: number }>; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/judge-invalid`, data)),
  uploadLeadQualificationImage: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post('/zsjos/lead/qualification/attachment/upload', data))
  },
  updateLeadBasicInfo: async (id: number, data: LeadBasicInfoUpdateRequest) =>
    unwrap<boolean>(await http.put(`/zsjos/lead/${id}/basic-info`, data)),
  supplementLead: async (id: number, data: { provinceCode: string; cityCode: string; leadCategory: string; intendedProducts: LeadCreateRequest['intendedProducts']; remark?: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/lead/${id}/submitter-supplement`, data)),
  urgeLead: async (id: number, reason: string) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/urge`, { reason })),
  createLeadComplaint: async (id: number, reason: string, evidenceFileIds: number[]) => unwrap<number>(
    await http.post(`/zsjos/lead-complaint/lead/${id}`, { reason, evidenceFileIds, idempotencyKey: createIdempotencyKey() })
  ),
  leadComplaintPage: async (params: { status: 'pending' | 'handled'; pageNo: number; pageSize: number }) => unwrap<PageResult<LeadComplaint>>(
    await http.get('/zsjos/lead-complaint/page', { params })
  ),
  leadComplaints: async (leadId: number) => unwrap<LeadComplaint[]>(
    await http.get(`/zsjos/lead-complaint/lead/${leadId}/list`)
  ),
  decideLeadComplaint: async (id: number, result: 'founded' | 'unfounded', opinion: string, evidenceFileIds: number[]) => unwrap<boolean>(
    await http.post(`/zsjos/lead-complaint/${id}/decision`, { result, opinion, evidenceFileIds, idempotencyKey: createIdempotencyKey() })
  ),
  qualificationExceptionPage: async (type: 'suspended' | 'recycle_pending', params: { pageNo: number; pageSize: number; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<LeadQualificationException>>(await http.post('/zsjos/lead/qualification-exception/search-page', { type, ...params }))
      : unwrap<PageResult<LeadQualificationException>>(await http.get('/zsjos/lead/qualification-exception/page', { params: { type, ...params } })),
  leadTransferCandidates: async (id: number) =>
    unwrap<AssignmentUser[]>(await http.get(`/zsjos/lead/${id}/transfer-candidates`)),
  restoreLead: async (id: number, data: { reason: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/restore`, data)),
  transferLead: async (id: number, data: { salesUserId: number; reason: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/transfer`, data)),
  recycleLead: async (id: number, data: { reason: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/recycle`, data)),
  releaseLeadToClaimPool: async (id: number, data: { reason: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/release-to-claim-pool`, data)),
  leadInboxFilterProfile: async (audience: 'submitter' | 'owner') =>
    unwrap<LeadInboxFilterProfile>(await http.get(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/filter-profile`)),
  leadFollowUpPage: async (leadId: number, params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<LeadFollowUp>>(await http.get(`/zsjos/lead/${leadId}/follow-ups/page`, { params })),
  createLeadFollowUp: async (leadId: number, data: LeadFollowUpCreateRequest) =>
    unwrap<LeadFollowUp>(await http.post(`/zsjos/lead/${leadId}/follow-ups`, data)),
  uploadLeadFollowUpImage: async (leadId: number, file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post(`/zsjos/lead/${leadId}/follow-up-image/upload`, data))
  },
  leadAppeals: async (leadId: number) =>
    unwrap<LeadAppeal[]>(await http.get(`/zsjos/lead/appeal/lead/${leadId}/list`)),
  submitLeadAppeal: async (leadId: number, data: { reason: string; attachments: Array<{ infraFileId: number }>; idempotencyKey: string }) =>
    unwrap<number>(await http.post(`/zsjos/lead/appeal/lead/${leadId}/submit`, data)),
  leadAppealInboxPage: async (handled: boolean, params: { pageNo: number; pageSize: number; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter || params.keyword
      ? unwrap<PageResult<LeadAppeal>>(await http.post('/zsjos/lead/appeal/inbox/search-page', { handled, ...params }))
      : unwrap<PageResult<LeadAppeal>>(await http.get('/zsjos/lead/appeal/inbox-page', { params: { handled, ...params } })),
  leadAppealInboxCursor: async (handled: boolean, params: { cursor?: string; limit?: number; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter || params.keyword
      ? unwrap<CursorPageResult<LeadAppeal>>(await http.post('/zsjos/lead/appeal/inbox/search-cursor', { handled, ...params }))
      : unwrap<CursorPageResult<LeadAppeal>>(await http.get('/zsjos/lead/appeal/inbox-cursor', { params: { handled, ...params } })),
  decideLeadAppeal: async (appealId: number, decision: 'overturn' | 'uphold', data: { taskId: string; reason: string; attachments: Array<{ infraFileId: number }>; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/lead/appeal/${appealId}/${decision}`, data)),
  uploadLeadAppealImage: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post('/zsjos/lead/appeal/attachment/upload', data))
  },
  salesOrderCatalog: async () => unwrap<LeadCatalog>(await http.get('/zsjos/sales-order/product/catalog')),
  submitSalesOrder: async (leadId: number, data: SalesOrderSubmitRequest) =>
    unwrap<number>(await http.post(`/zsjos/sales-order/lead/${leadId}/submit`, data)),
  submitSystemRepurchase: async (leadId: number, repurchaseReason: string, order: SalesOrderSubmitRequest) =>
    unwrap<number>(await http.post(`/zsjos/sales-order/lead/${leadId}/repurchase`, { repurchaseReason, order })),
  submitExternalRepurchase: async (data: { customerName: string; customerMobile?: string; customerWechatId?: string; repurchaseReason: string; order: SalesOrderSubmitRequest }) =>
    unwrap<number>(await http.post('/zsjos/sales-order/external-repurchase', data)),
  submitStudentRepurchase: async (personId: number, data: { customerName?: string; customerMobile?: string; customerWechatId?: string; repurchaseReason: string; order: SalesOrderSubmitRequest }) =>
    unwrap<number>(await http.post(`/zsjos/sales-order/student/${personId}/repurchase`, data)),
  customerSalesOrders: async (leadId: number) => unwrap<SalesOrderListItem[]>(await http.get(`/zsjos/sales-order/lead/${leadId}/customer-orders`)),
  customerSalesOrder: async (leadId: number, orderId: number) =>
    unwrap<SalesOrder>(await http.get(`/zsjos/sales-order/lead/${leadId}/customer-orders/${orderId}`)),
  resubmitSalesOrder: async (orderId: number, data: SalesOrderSubmitRequest) =>
    unwrap<number>(await http.put(`/zsjos/sales-order/${orderId}/resubmit`, data)),
  salesOrder: async (orderId: number) => unwrap<SalesOrder>(await http.get(`/zsjos/sales-order/${orderId}`)),
  mySalesOrder: async (orderId: number) => unwrap<SalesOrder>(await http.get(`/zsjos/sales-order/my/${orderId}`)),
  mySalesOrderPage: async (params: { pageNo: number; pageSize: number; status?: SalesOrder['status']; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<SalesOrderListItem>>(await http.post('/zsjos/sales-order/my-search-page', params))
      : unwrap<PageResult<SalesOrderListItem>>(await http.get('/zsjos/sales-order/my-page', { params })),
  mySalesOrderCursor: async (params: { cursor?: string; limit?: number; status?: SalesOrder['status']; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<CursorPageResult<SalesOrderListItem>>(await http.post('/zsjos/sales-order/my-search-cursor', params))
      : unwrap<CursorPageResult<SalesOrderListItem>>(await http.get('/zsjos/sales-order/my-cursor', { params })),
  mySalesOrderStatusCounts: async () =>
    unwrap<SalesOrderStatusCounts>(await http.get('/zsjos/sales-order/my-status-counts')),
  salesOrderApprovalFilterProfile: async () => unwrap<SalesOrderApprovalFilterProfile>(await http.get('/zsjos/sales-order/approval/filter-profile')),
  salesOrderApprovalTaskTarget: async (taskId: string) => unwrap<SalesOrderApprovalTaskTarget>(
    await http.get('/zsjos/sales-order/approval/task-target', { params: { taskId } })),
  salesOrderApprovalNotificationTarget: async (orderId: number, sceneCode: string, sourceEventKey?: string) => unwrap<SalesOrderApprovalTaskTarget>(
    await http.get('/zsjos/sales-order/approval/notification-target', { params: { orderId, sceneCode, sourceEventKey } })),
  salesOrderApprovalInbox: async (params: { pageNo: number; pageSize: number; center?: 'registration' | 'finance'; groupKey?: string; optionKey?: string; keyword?: string; handled?: boolean; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<SalesOrderListItem>>(await http.post('/zsjos/sales-order/approval/search-page', params))
      : unwrap<PageResult<SalesOrderListItem>>(await http.get('/zsjos/sales-order/approval/inbox-page', { params })),
  salesOrderApprovalCursor: async (params: { cursor?: string; limit?: number; center?: 'registration' | 'finance'; groupKey?: string; optionKey?: string; keyword?: string; handled?: boolean; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<CursorPageResult<SalesOrderListItem>>(await http.post('/zsjos/sales-order/approval/search-cursor', params))
      : unwrap<CursorPageResult<SalesOrderListItem>>(await http.get('/zsjos/sales-order/approval/inbox-cursor', { params })),
  advancedFilterCatalog: async (scene: AdvancedFilterScene) =>
    unwrap<AdvancedFilterCatalog>(await http.get('/zsjos/advanced-filter/catalog', { params: { scene } })),
  decideSalesOrder: async (orderId: number, decision: 'approve' | 'reject', data: { taskId: string; reason: string; approvalRoundId: number; orderVersion: number; roundVersion: number; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/${decision}`, data)),
  requestSalesOrderSupervisor: async (orderId: number, data: { taskId: string; reason: string; approvalRoundId: number; orderVersion: number; roundVersion: number; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/supervisor-confirmation/request`, data)),
  salesOrderSupervisorInbox: async (params: { pageNo: number; pageSize: number; handled: boolean; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<SalesOrderSupervisorInboxItem>>(await http.post('/zsjos/sales-order/supervisor-confirmation/search-page', params))
      : unwrap<PageResult<SalesOrderSupervisorInboxItem>>(await http.get('/zsjos/sales-order/supervisor-confirmation/inbox-page', { params })),
  salesOrderSupervisorCursor: async (params: { cursor?: string; limit?: number; handled: boolean; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<CursorPageResult<SalesOrderSupervisorInboxItem>>(await http.post('/zsjos/sales-order/supervisor-confirmation/search-cursor', params))
      : unwrap<CursorPageResult<SalesOrderSupervisorInboxItem>>(await http.get('/zsjos/sales-order/supervisor-confirmation/inbox-cursor', { params })),
  salesOrderSupervisorConfirmation: async (confirmationId: number) => unwrap<SalesOrderSupervisorInboxItem>(
    await http.get(`/zsjos/sales-order/supervisor-confirmation/${confirmationId}`)),
  decideSalesOrderSupervisor: async (orderId: number, decision: 'confirm' | 'reject', data: { confirmationId: number; taskId: string; reason: string; approvalRoundId: number; orderVersion: number; roundVersion: number; confirmationVersion: number; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/supervisor-confirmation/${decision}`, data)),
  terminateSalesOrder: async (orderId: number, data: { reason: string; approvalRoundId: number; orderVersion: number; roundVersion: number; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/terminate`, data)),
  uploadSalesOrderVoucher: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<SalesOrderVoucher>(await http.post('/zsjos/sales-order/voucher/upload', data))
  },
  businessTaskSummary: async () => unwrap<BusinessTaskSummary>(await http.get('/zsjos/business-task/my-summary')),
  businessTaskPage: async (bucket: BusinessTaskBucket, params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<BusinessTask>>(await http.get('/zsjos/business-task/my-page', { params: { bucket, ...params } })),
  businessTaskList: async (params: { status: 'pending' | 'done'; bucket?: BusinessTaskBucket; pageNo: number; pageSize: number }) =>
    unwrap<PageResult<BusinessTask>>(await http.get('/zsjos/business-task/my-task-page', { params })),
  completeBirthdayCare: async (id: number) =>
    unwrap<boolean>(await http.post(`/zsjos/business-task/${id}/complete-birthday-care`)),
  createExportTask: async (exportType: ExportTask['exportType'], filter: unknown) =>
    unwrap<number>(await http.post('/zsjos/export-task', { exportType, filterJson: JSON.stringify(filter || {}) })),
  exportTaskPage: async (params: { pageNo: number; pageSize: number; exportType?: ExportTask['exportType'] }) =>
    unwrap<PageResult<ExportTask>>(await http.get('/zsjos/export-task/page', { params })),
  cancelExportTask: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/export-task/${id}/cancel`)),
  exportDownloadUrl: async (id: number) => unwrap<string>(await http.get(`/zsjos/export-task/${id}/download-url`)),
  bpmTaskPage: async (view: 'todo' | 'done', params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<BpmTask>>(await http.get(`/bpm/task/${view}-page`, { params })),
  approveBpmTask: async (id: string, reason: string) =>
    unwrap<boolean>(await http.put('/bpm/task/approve', { id, reason, variables: {} })),
  rejectBpmTask: async (id: string, reason: string) =>
    unwrap<boolean>(await http.put('/bpm/task/reject', { id, reason })),
  simpleUsers: async () => unwrap<SimpleUser[]>(await http.get('/system/user/simple-list')),
  simpleDepartments: async () => unwrap<SimpleDept[]>(await http.get('/system/dept/simple-list')),
  workPlanPage: async (params: { pageNo: number; pageSize: number; periodType?: WorkPlan['periodType']; status?: string; startDate?: string; endDate?: string }) =>
    unwrap<PageResult<WorkPlan>>(await http.get('/zsjos/work-plan/page', { params })),
  workPlan: async (id: number) => unwrap<WorkPlan>(await http.get('/zsjos/work-plan/get', { params: { id } })),
  workTask: async (id: number) => unwrap<WorkTask>(await http.get('/zsjos/work-plan/task/get', { params: { id } })),
  myWorkTaskPage: async (params: { pageNo: number; pageSize: number; status?: string }) =>
    unwrap<PageResult<WorkTask>>(await http.get('/zsjos/work-plan/task/my-page', { params })),
  createWorkPlan: async (data: WorkPlanInput) => unwrap<number>(await http.post('/zsjos/work-plan/create', data)),
  updateWorkPlan: async (id: number, data: WorkPlanInput) => unwrap<boolean>(await http.put(`/zsjos/work-plan/${id}`, data)),
  publishWorkPlan: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/work-plan/${id}/publish`, { version })),
  cancelWorkPlan: async (id: number, version: number, reason: string) => unwrap<boolean>(await http.post(`/zsjos/work-plan/${id}/cancel`, { version, reason })),
  createTemporaryTask: async (data: WorkTaskInput) => unwrap<number>(await http.post('/zsjos/work-plan/task/temporary', data)),
  addWorkTask: async (planId: number, data: WorkTaskInput) => unwrap<number>(await http.post(`/zsjos/work-plan/${planId}/task`, data)),
  adjustWorkTask: async (id: number, data: WorkTaskInput) => unwrap<boolean>(await http.put(`/zsjos/work-plan/task/${id}`, data)),
  submitWorkReport: async (id: number, data: { completionSummary: string; infraFileIds: number[]; version: number; reportFields?: Record<string, unknown> }) =>
    unwrap<boolean>(await http.post(`/zsjos/work-plan/task/${id}/report`, data)),
  uploadWorkPlanAttachment: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<WorkPlanAttachmentUpload>(await http.post('/zsjos/work-plan/attachment/upload', data))
  },
  confirmWorkReport: async (id: number, data: { decision: 'confirmed' | 'returned'; comment?: string; version: number }) =>
    unwrap<boolean>(await http.post(`/zsjos/work-plan/task/${id}/confirm`, data)),
  cancelWorkTask: async (id: number, data: { version: number; reason: string; cascadeChildren?: boolean }) =>
    unwrap<boolean>(await http.post(`/zsjos/work-plan/task/${id}/cancel`, data)),
  submitWorkPlanSummary: async (id: number, data: { version: number; summary: string; infraFileIds: number[]; summaryFields?: Record<string, unknown> }) =>
    unwrap<boolean>(await http.post(`/zsjos/work-plan/${id}/summary`, data)),
  workPlanTypes: async () => unwrap<WorkPlanType[]>(await http.get('/zsjos/work-plan-config/types')),
  workPlanTemplates: async () => unwrap<WorkPlanTemplate[]>(await http.get('/zsjos/work-plan/templates/available')),
  workPlanConfigTemplates: async (typeId?: number) => unwrap<WorkPlanTemplate[]>(await http.get('/zsjos/work-plan-config/templates', { params: { typeId } })),
  copyWorkPlanTemplateVersion: async (id: number) => unwrap<number>(await http.post(`/zsjos/work-plan-config/templates/${id}/versions/copy`)),
  publishWorkPlanTemplate: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/work-plan-config/templates/${id}/publish`)),
  disableWorkPlanTemplate: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/work-plan-config/templates/${id}/disable`)),
  leadAssignmentRule: async () => unwrap<LeadAssignmentRule>(await http.get('/zsjos/lead/assignment-rule/get')),
  updateLeadAssignmentRule: async (data: Pick<LeadAssignmentRule, 'acceptTimeoutSeconds' | 'maxAttempts' | 'dailyClaimLimit'>) => unwrap<boolean>(await http.put('/zsjos/lead/assignment-rule/update', data)),
  leadFollowUpRule: async () => unwrap<LeadFollowUpRule>(await http.get('/zsjos/lead-follow-up-rule/get')),
  leadRuntimeSetting: async () => unwrap<{ notificationPopupDurationMinutes: number }>(await http.get('/zsjos/lead-follow-up-rule/runtime-setting')),
  updateLeadFollowUpRule: async (data: Pick<LeadFollowUpRule, 'version' | 'firstFollowUpTimeoutMinutes' | 'qualificationTimeoutMinutes' | 'agingPoolTimeoutDays' | 'noProgressWarningDays' | 'noProgressGraceDays' | 'notificationPopupDurationMinutes' | 'duplicateAutoResolutionEnabled'>) => unwrap<boolean>(await http.put('/zsjos/lead-follow-up-rule/update', data)),
  leadFilterConfig: async (audience: LeadFilterAudience) => unwrap<LeadFilterAdmin>(await http.get('/zsjos/lead/inbox-filter/get', { params: { audience } })),
  leadFilterVersions: async (audience: LeadFilterAudience) => unwrap<LeadFilterVersion[]>(await http.get('/zsjos/lead/inbox-filter/versions', { params: { audience } })),
  publishLeadFilter: async (audience: LeadFilterAudience) => unwrap<number>(await http.post('/zsjos/lead/inbox-filter/publish', undefined, { params: { audience } })),
  rollbackLeadFilter: async (audience: LeadFilterAudience, versionNo: number) => unwrap<number>(await http.post('/zsjos/lead/inbox-filter/rollback', undefined, { params: { audience, versionNo } })),
  saveLeadFilterDraft: async (audience: LeadFilterAudience, groups: LeadFilterGroupConfig[]) => unwrap<boolean>(await http.put('/zsjos/lead/inbox-filter/draft', { audience, groups })),
  productConfigPage: async (params: { pageNo: number; pageSize: number; name?: string; status?: number }) => unwrap<PageResult<ProductConfig>>(await http.get('/zsjos/product/page', { params })),
  productConfig: async (id: number) => unwrap<ProductSaveRequest & { id: number }>(await http.get('/zsjos/product/get', { params: { id } })),
  createProductConfig: async (data: ProductSaveRequest) => unwrap<number>(await http.post('/zsjos/product/create', data)),
  updateProductConfig: async (data: ProductSaveRequest & { id: number }) => unwrap<boolean>(await http.put('/zsjos/product/update', data)),
  deleteProductConfig: async (id: number) => unwrap<boolean>(await http.delete('/zsjos/product/delete', { params: { id } })),
  productCategoryTree: async () => unwrap<ProductCategory[]>(await http.get('/zsjos/product/category/tree')),
  createProductCategory: async (data: ProductCategorySaveRequest) => unwrap<number>(await http.post('/zsjos/product/category/create', data)),
  updateProductCategory: async (data: ProductCategorySaveRequest & { id: number }) => unwrap<boolean>(await http.put('/zsjos/product/category/update', data)),
  updateProductConfigStatus: async (id: number, status: number) => unwrap<boolean>(await http.put('/zsjos/product/update-status', { id, status })),
  productSkus: async (spuId: number) => unwrap<ProductSku[]>(await http.get('/zsjos/product/sku/list', { params: { spuId } })),
  createProductSku: async (data: ProductSkuSaveRequest) => unwrap<number>(await http.post('/zsjos/product/sku/create', data)),
  updateProductSku: async (data: ProductSkuSaveRequest & { id: number }) => unwrap<boolean>(await http.put('/zsjos/product/sku/update', data)),
  deleteProductSku: async (id: number) => unwrap<boolean>(await http.delete('/zsjos/product/sku/delete', { params: { id } })),
  updateProductSkuStatus: async (id: number, status: number) => unwrap<boolean>(await http.put('/zsjos/product/sku/update-status', { id, status })),
  productAttributes: async (spuId: number) => unwrap<ProductAttribute[]>(await http.get('/zsjos/product/sku/attrs', { params: { spuId } })),
  saveProductAttributes: async (spuId: number, attrs: ProductAttribute[]) => unwrap<boolean>(await http.put('/zsjos/product/sku/attrs', { spuId, attrs })),
  createWorkPlanTemplate: async (data: WorkPlanTemplateSaveRequest) => unwrap<number>(await http.post('/zsjos/work-plan-config/templates', data)),
  updateWorkPlanTemplate: async (id: number, data: WorkPlanTemplateSaveRequest) => unwrap<boolean>(await http.put(`/zsjos/work-plan-config/templates/${id}`, data)),
  subordinateSalesPage: async (params: { pageNo: number; pageSize: number; keyword?: string; accountStatus?: number; presence?: string; accepting?: boolean; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<SubordinateSales>>(await http.post('/zsjos/subordinate-sales/search-page', params))
      : unwrap<PageResult<SubordinateSales>>(await http.get('/zsjos/subordinate-sales/page', { params })),
  subordinateSalesOverview: async (salesUserId: number) =>
    unwrap<SubordinateSales>(await http.get(`/zsjos/subordinate-sales/${salesUserId}/overview`)),
  subordinateSalesLeads: async (salesUserId: number, params: { pageNo: number; pageSize: number; keyword?: string; status?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<ManagedLead>>(await http.post(`/zsjos/subordinate-sales/${salesUserId}/leads/search-page`, params))
      : unwrap<PageResult<ManagedLead>>(await http.get(`/zsjos/subordinate-sales/${salesUserId}/leads`, { params })),
  subordinateSalesTasks: async (salesUserId: number, params: { pageNo: number; pageSize: number; bucket?: BusinessTaskBucket }) =>
    unwrap<PageResult<SubordinateTask>>(await http.get(`/zsjos/subordinate-sales/${salesUserId}/tasks`, { params })),
  subordinateTransferCandidates: async () =>
    unwrap<AssignmentUser[]>(await http.get('/zsjos/subordinate-sales/transfer-candidates')),
  updateSubordinateAccountStatus: async (salesUserId: number, status: number, reason: string) =>
    unwrap<boolean>(await http.put(`/zsjos/subordinate-sales/${salesUserId}/account-status`, { status, reason })),
  updateSubordinateDispatchMode: async (salesUserId: number, accepting: boolean, reason: string) =>
    unwrap<boolean>(await http.put(`/zsjos/subordinate-sales/${salesUserId}/dispatch-mode`, { accepting, reason })),
  pauseAllSubordinateDispatch: async () =>
    unwrap<SubordinatePauseAllResult>(await http.put('/zsjos/subordinate-sales/dispatch-mode/pause-all')),
  batchTransferSubordinateLeads: async (leadIds: number[], targetUserId: number, reason: string) =>
    unwrap<SubordinateBatchResult>(await http.post('/zsjos/subordinate-sales/leads/batch-transfer', { leadIds, targetUserId, reason })),
  batchReleaseSubordinateLeads: async (leadIds: number[], collaboratorUserId: number | undefined, reason: string) =>
    unwrap<SubordinateBatchResult>(await http.post('/zsjos/subordinate-sales/leads/batch-public-sea', { leadIds, collaboratorUserId, reason })),
  unreadNotifyCount: async () => unwrap<number>(await http.get('/system/notify-message/get-unread-count')),
  unreadNotifyMessages: async () => unwrap<NotifyMessage[]>(await http.get('/system/notify-message/get-unread-list')),
  myNotifyMessagePage: async (params: NotifyMessagePageParams) =>
    unwrap<PageResult<NotifyMessage>>(await http.get('/system/notify-message/my-page', { params })),
  myNotifyMessageCursor: async (params: NotifyMessageCursorParams) =>
    unwrap<CursorPageResult<NotifyMessage>>(await http.get('/system/notify-message/my-cursor', { params })),
  myNotifyMessage: async (id: number) =>
    unwrap<NotifyMessage>(await http.get('/system/notify-message/my-get', { params: { id } })),
  markNotifyMessagesRead: async (ids: number[]) => {
    const params = new URLSearchParams()
    ids.forEach(id => params.append('ids', String(id)))
    return unwrap<boolean>(await http.put('/system/notify-message/update-read', undefined, { params }))
  },
  markAllNotifyMessagesRead: async () => unwrap<boolean>(await http.put('/system/notify-message/update-all-read')),
  salesUsers: async () => unwrap<SalesUser[]>(await http.get('/zsjos/lead/sales-user/simple-list')),
  assignmentRelationPage: async (params: { pageNo: number; pageSize: number; keyword?: string; configured?: boolean }) =>
    unwrap<PageResult<AssignmentRelation>>(await http.get('/zsjos/lead-assignment/relation/page', { params })),
  eligibleSalesUsers: async () => unwrap<AssignmentUser[]>(await http.get('/zsjos/lead-assignment/eligible-sales')),
  saveAssignmentRelations: async (data: { sourceUserIds: number[]; targetUserIds: number[]; mode: 'append' | 'replace' | 'remove' }) =>
    unwrap<boolean>(await http.put('/zsjos/lead-assignment/relation/save', data)),
  assignmentLogPage: async (params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<AssignmentLog>>(await http.get('/zsjos/lead-assignment/log/page', { params })),
  registrationPoolPage: async (params: { pageNo: number; pageSize: number; status?: string; keyword?: string; advancedFilter?: AdvancedFilterGroup }) => params.advancedFilter
    ? unwrap<PageResult<RegistrationCase>>(await http.post('/zsjos/registration/pool/search-page', params))
    : unwrap<PageResult<RegistrationCase>>(await http.get('/zsjos/registration/pool-page', { params })),
  registrationCase: async (id: number) => unwrap<RegistrationCase>(await http.get(`/zsjos/registration/${id}`)),
  registrationPlannerCandidates: async () => unwrap<StudyPlanner[]>(await http.get('/zsjos/registration/study-planner-candidates')),
  registrationRouteCandidates: async (id: number, routeId: number) => unwrap<StudyPlanner[]>(await http.get(`/zsjos/registration/${id}/routes/${routeId}/candidates`)),
  updateRegistrationItem: async (id: number, itemId: number, data: { checked: boolean; version: number; idempotencyKey: string }) => unwrap<RegistrationCase>(await http.put(`/zsjos/registration/${id}/items/${itemId}`, data)),
  updateRegistrationPlanner: async (id: number, data: { studyPlannerUserId: number; version: number; idempotencyKey: string }) => unwrap<RegistrationCase>(await http.put(`/zsjos/registration/${id}/study-planner`, data)),
  updateRegistrationRoutes: async (id: number, data: { version: number; idempotencyKey: string; routes: Array<{ routeId: number; selected: boolean; assigneeUserId?: number }> }) => unwrap<RegistrationCase>(await http.put(`/zsjos/registration/${id}/routes`, data)),
  uploadRegistrationAttachment: async (id: number, itemId: number, file: File, version: number) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<{ id: number; infraFileId: number; fileUrl: string; originalName: string; contentType?: string; fileSize: number; version: number }>(await http.post(`/zsjos/registration/${id}/items/${itemId}/attachments`, data, { params: { version, idempotencyKey: createIdempotencyKey() } }))
  },
  deleteRegistrationAttachment: async (id: number, itemId: number, attachmentId: number, data: { version: number; idempotencyKey: string }) => unwrap<RegistrationCase>(await http.delete(`/zsjos/registration/${id}/items/${itemId}/attachments/${attachmentId}`, { data })),
  completeRegistration: async (id: number, data: { version: number; idempotencyKey: string }) => unwrap<boolean>(await http.post(`/zsjos/registration/${id}/complete`, data)),
  myStudents: async (params: { pageNo: number; pageSize: number; keyword?: string; serviceStatus?: 'active' | 'paused' | 'completed'; advancedFilter?: AdvancedFilterGroup }) => params.advancedFilter
    ? unwrap<PageResult<MyStudent>>(await http.post('/zsjos/student/my/search-page', params))
    : unwrap<PageResult<MyStudent>>(await http.get('/zsjos/student/my-page', { params })),
  mediaStudents: {
    page: async (params: { pageNo: number; pageSize: number; keyword?: string }) => unwrap<PageResult<MyStudent>>(await http.get('/zsjos/media-students/page', { params })),
    get: async (personId: number) => unwrap<MediaStudentDetail>(await http.get(`/zsjos/media-students/${personId}`)),
    target: async (bizType: string, bizId: number) => unwrap<{ personId: number; targetTab: string; recordId: number }>(await http.get('/zsjos/media-students/target', { params: { bizType, bizId } })),
    talks: async (personId: number) => unwrap<MediaStudentTalkRecord[]>(await http.get(`/zsjos/media-students/${personId}/talk-records`)),
    createTalk: async (personId: number, data: { accountId?: number; content: string; attachmentFileIds?: number[] }) => unwrap<number>(await http.post(`/zsjos/media-students/${personId}/talk-records`, data))
  },
  myStudent: async (personId: number) => unwrap<MyStudent>(await http.get(`/zsjos/student/my/${personId}`)),
  myStudentByService: async (relationId: number) => unwrap<MyStudent>(await http.get(`/zsjos/student/my/by-service/${relationId}`)),
  studentContactContext: async (relationId: number) => unwrap<StudentContactContext>(await http.get(`/zsjos/student/service/${relationId}/contact-context`)),
  studentContactRecords: async (relationId: number, pageNo = 1, pageSize = 100) => unwrap<PageResult<StudentContactRecord>>(await http.get(`/zsjos/student/service/${relationId}/contact-records`, { params: { pageNo, pageSize } })),
  studentAccept: async (relationId: number, version: number, idempotencyKey: string) => unwrap<boolean>(await http.post(`/zsjos/student/service/${relationId}/accept`, { version, idempotencyKey })),
  studentUpdateBasicInfo: async (relationId: number, data: { name: string; mobile?: string; wechatId?: string; reason: string }) => unwrap<boolean>(await http.put(`/zsjos/student/service/${relationId}/basic-info`, data)),
  studentFirstContact: async (relationId: number, data: Record<string, unknown>) => unwrap<number>(await http.post(`/zsjos/student/service/${relationId}/first-contact`, data)),
  studentStudyPlan: async (relationId: number, data: Record<string, unknown>) => unwrap<number>(await http.post(`/zsjos/student/service/${relationId}/study-plan`, data)),
  studentContact: async (relationId: number, data: Record<string, unknown>) => unwrap<number>(await http.post(`/zsjos/student/service/${relationId}/contacts`, data)),
  studentDeliveryStage: async (relationId: number, data: { stage: string; successful: boolean; remark: string; attachmentFileIds?: number[]; data?: Record<string, unknown>; idempotencyKey: string }) => unwrap<number>(await http.post(`/zsjos/student/service/${relationId}/delivery-stage`, data)),
  studentExamDate: async (relationId: number, data: { examDate: string; version: number; idempotencyKey: string }) => unwrap<boolean>(await http.put(`/zsjos/student/service/${relationId}/exam-date`, data)),
  studentDirectorPrecheckDraft: async (relationId: number, data: { interviewAt?: string; data: Record<string, unknown>; version: number; idempotencyKey: string }) => unwrap<boolean>(await http.post(`/zsjos/student/service/${relationId}/precheck/draft`, data)),
  studentDirectorPrecheckSubmit: async (relationId: number, data: { interviewAt?: string; data: Record<string, unknown>; version: number; idempotencyKey: string }) => unwrap<boolean>(await http.post(`/zsjos/student/service/${relationId}/precheck/submit`, data)),
  studentDirectorInterviewDraft: async (relationId: number, data: { interviewAt?: string; data: Record<string, unknown>; version: number; idempotencyKey: string }) => unwrap<boolean>(await http.post(`/zsjos/student/service/${relationId}/interview/draft`, data)),
  studentDirectorInterviewSubmit: async (relationId: number, data: { interviewAt?: string; data: Record<string, unknown>; version: number; idempotencyKey: string }) => unwrap<boolean>(await http.post(`/zsjos/student/service/${relationId}/interview/submit`, data)),
  studentCollaboratorCandidates: async (relationId: number, type: 'content_director' | 'career_planner' | 'operator') => unwrap<StudyPlanner[]>(await http.get(`/zsjos/student/service/${relationId}/collaborator-candidates`, { params: { type } })),
  studentAssignCollaborator: async (relationId: number, data: { collaboratorType: string; userId: number; version: number; idempotencyKey: string; correctionReason?: string }) => unwrap<boolean>(await http.post(`/zsjos/student/service/${relationId}/collaborators`, data)),
  studentContactUpload: async (relationId: number, file: File) => { const data = new FormData(); data.append('file', file); return unwrap<{ fileId: number; name: string; url: string; contentType?: string; size: number }>(await http.post(`/zsjos/student/service/${relationId}/attachments`, data)) },
  studentContactExtensions: async (pageNo = 1, pageSize = 20, statusScope = 'all') => unwrap<PageResult<StudentContactExtension>>(await http.get('/zsjos/student/service/extensions', { params: { pageNo, pageSize, statusScope } })),
  studentWithdrawExtension: async (extensionId: number, version: number, reason: string, idempotencyKey: string) => unwrap<boolean>(await http.post(`/zsjos/student/service/extensions/${extensionId}/withdraw`, { version, reason, idempotencyKey })),
  studentCompleteAssistance: async (taskId: number, remark: string) => unwrap<boolean>(await http.post(`/zsjos/student/service/assistance/${taskId}/complete`, { remark })),
  studentContactConfig: async () => unwrap<StudentContactConfig>(await http.get('/zsjos/student-contact-config')),
  copyStudentContactConfigDraft: async (publishedId: number, publishedVersion: number, idempotencyKey: string) => unwrap<number>(await http.post('/zsjos/student-contact-config/draft/copy', { publishedId, publishedVersion, idempotencyKey })),
  saveStudentContactConfigDraft: async (data: Record<string, unknown>) => unwrap<boolean>(await http.put('/zsjos/student-contact-config/draft', data)),
  publishStudentContactConfig: async (id: number, version: number, idempotencyKey: string) => unwrap<boolean>(await http.post('/zsjos/student-contact-config/publish', { id, version, idempotencyKey })),
  registrationChecklistConfig: async () => unwrap<RegistrationChecklistConfig>(await http.get('/zsjos/registration-checklist-config')),
  copyRegistrationChecklistDraft: async (version: number) => unwrap<number>(await http.post('/zsjos/registration-checklist-config/draft/copy', { version, idempotencyKey: crypto.randomUUID() })),
  saveRegistrationChecklistDraft: async (data: { templateVersion: number; items: Array<{ id?: number; itemKey?: string; itemType: string; title: string; sort: number; enabled: boolean; systemRequired?: boolean; attachmentRequired?: boolean }>; routeOptions: Array<{ id?: number; optionKey: string; departmentId: number; assigneeType: string; sort: number; enabled: boolean; systemRequired?: boolean }>; idempotencyKey: string }) => unwrap<boolean>(await http.put('/zsjos/registration-checklist-config/draft', data)),
  publishRegistrationChecklist: async (version: number) => unwrap<boolean>(await http.post('/zsjos/registration-checklist-config/publish', { version, idempotencyKey: crypto.randomUUID() }))
  // ========== HRM (Human Resource) ==========
  // portal.* 打的是「我自己」的数据，其余是全员数据。两者分开挂，调用点一眼能看出权限范围。
  ,hrm: {
    portal: {
      employee: {
        /** 当前账号是否已绑定员工档案。未绑定则「我的档案」页提示联系 HR。 */
        getBindStatus: async () => unwrap<boolean>(await http.get('/hrm/portal/employee/get-bind-status')),
        get: async () => unwrap<HrmEmployee>(await http.get('/hrm/portal/employee/get')),
        /** 只可改个人字段（姓名/手机号/证件/性别/邮箱/籍贯/学历/户籍地址等）。 */
        update: async (data: Partial<HrmEmployeeSave>) => unwrap<boolean>(await http.put('/hrm/portal/employee/update', data))
      },
      attendance: {
        /** 我的打卡记录。后端返回整月数组，不分页。 */
        clockList: async (params: { year?: number; month?: number }) => unwrap<HrmClockItem[]>(await http.get('/hrm/portal/attendance/clock/list', { params })),
        /** 我的请假申请。后端返回全量数组，不分页。 */
        leaveList: async () => unwrap<HrmLeaveItem[]>(await http.get('/hrm/portal/attendance/leave/list')),
        leaveCreate: async (data: HrmLeaveCreate) => unwrap<number>(await http.post('/hrm/portal/attendance/leave/create', data)),
        leaveCancel: async (id: number, reason: string) => unwrap<boolean>(await http.put('/hrm/portal/attendance/leave/cancel', { id, reason })),
        monthDetail: async (params: { year?: number; month?: number }) => unwrap<HrmAttendanceMonthDetail>(await http.get('/hrm/portal/attendance/statistics/month-detail', { params }))
      },
      salary: {
        /** 我的工资条。后端返回数组，不分页；startMonth/endMonth 形如 2026-08。 */
        slipList: async (params?: { startMonth?: string; endMonth?: string }) => unwrap<HrmSalarySlip[]>(await http.get('/hrm/portal/salary/slip/list', { params })),
        unreadSummary: async () => unwrap<HrmSalarySlipUnread>(await http.get('/hrm/portal/salary/slip/unread-summary')),
        /** 标记已读。后端按逗号分隔的 ids 查询参数接收。 */
        read: async (ids: number[]) => unwrap<boolean>(await http.put('/hrm/portal/salary/slip/read', null, { params: { ids: ids.join(',') } }))
      },
      performance: {
        /** 我的绩效列表。返回分页。 */
        page: async (params: { pageNo: number; pageSize: number }) => unwrap<PageResult<HrmPerformanceAssessmentSummary>>(await http.get('/hrm/portal/performance/assessment/page', { params })),
        /** 我的绩效参评详情。stageId 可选，用于定位到某个运行阶段。 */
        get: async (id: number, stageId?: number) => unwrap<HrmPerformanceAssessment>(await http.get('/hrm/portal/performance/assessment/get', { params: { id, stageId } })),
        /** 我的绩效流程记录。 */
        processRecordList: async (id: number, stageId?: number) => unwrap<HrmPerformanceProcessRecord[]>(await http.get('/hrm/portal/performance/assessment/process-record-list', { params: { id, stageId } })),
        /** 填写绩效指标。 */
        fillQuota: async (data: { assessmentId: number; quotas: HrmPerformanceQuotaSave[] }) => unwrap<boolean>(await http.put('/hrm/portal/performance/assessment/fill-quota', data)),
        /** 确认绩效目标。 */
        confirmTarget: async (data: HrmPerformanceConfirm) => unwrap<boolean>(await http.put('/hrm/portal/performance/assessment/confirm-target', data)),
        /** 提交绩效评分。带一个评分阶段 + 该阶段的全部指标。 */
        score: async (data: HrmPerformanceScoreSave) => unwrap<{ id: number; nextStageId?: number }>(await http.put('/hrm/portal/performance/assessment/score', data)),
        /** 驳回某个评分阶段。 */
        rejectReviewStage: async (data: { assessmentId: number; reviewStageId: number; reason: string }) => unwrap<boolean>(await http.put('/hrm/portal/performance/assessment/reject-review-stage', data)),
        /** 处理结果审核。 */
        handleResultAudit: async (data: HrmPerformanceConfirm & { stageId?: number; reviewStageIds?: number[] }) => unwrap<boolean>(await http.put('/hrm/portal/performance/assessment/handle-result-audit', data)),
        /** 确认绩效结果。 */
        confirmResult: async (data: HrmPerformanceConfirm) => unwrap<boolean>(await http.put('/hrm/portal/performance/assessment/confirm-result', data)),
        /** 提交绩效申诉。 */
        submitAppeal: async (data: { assessmentId: number; appealReason: string; appealFileUrls?: string[]; reviewStageIds: number[] }) => unwrap<{ id: number; nextStageId?: number }>(await http.put('/hrm/portal/performance/assessment/submit-appeal', data)),
        /** 处理绩效申诉。 */
        handleAppeal: async (data: HrmPerformanceConfirm & { stageId?: number; reviewStageIds?: number[] }) => unwrap<boolean>(await http.put('/hrm/portal/performance/assessment/handle-appeal', data))
      },
      insurance: {
        /** 我的社保记录。返回整年数组，不分页。 */
        recordList: async (params?: { year?: number }) => unwrap<HrmInsuranceRecord[]>(await http.get('/hrm/portal/insurance/record/list', { params })),
        recordGet: async (id: number) => unwrap<HrmInsuranceRecord>(await http.get('/hrm/portal/insurance/record/get', { params: { id } }))
      },
      home: {
        /** 我的工作台日历。startDate/endDate 形如 2026-08-01。 */
        calendar: async (params: { startDate: string; endDate: string }) => unwrap<HrmHomeCalendarItem[]>(await http.get('/hrm/portal/home/calendar', { params }))
      }
    },
    attendance: {
      clock: {
        page: async (params: { pageNo: number; pageSize: number; employeeId?: number; type?: number; status?: number }) => unwrap<PageResult<HrmClockItem>>(await http.get('/hrm/attendance/clock/page', { params })),
        get: async (id: number) => unwrap<HrmClockItem>(await http.get('/hrm/attendance/clock/get', { params: { id } })),
        create: async (data: HrmClockSave) => unwrap<number>(await http.post('/hrm/attendance/clock/create', data)),
        update: async (data: HrmClockSave) => unwrap<boolean>(await http.put('/hrm/attendance/clock/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/attendance/clock/delete', { params: { id } })),
        deleteList: async (ids: number[]) => unwrap<boolean>(await http.delete('/hrm/attendance/clock/delete-list', { params: { ids: ids.join(',') } }))
      },
      leave: {
        page: async (params: { pageNo: number; pageSize: number; employeeId?: number; type?: string; approvalStatus?: number }) => unwrap<PageResult<HrmLeaveItem>>(await http.get('/hrm/attendance/leave/page', { params })),
        get: async (id: number) => unwrap<HrmLeaveItem>(await http.get('/hrm/attendance/leave/get', { params: { id } }))
      },
      statistics: {
        monthRecordPage: async (params: { pageNo: number; pageSize: number; year: number; month: number; employeeId?: number; deptId?: number }) => unwrap<PageResult<HrmAttendanceMonthRecord>>(await http.get('/hrm/attendance/statistics/month-record-page', { params })),
        monthDetail: async (params: { employeeId: number; year: number; month: number }) => unwrap<HrmAttendanceMonthDetail>(await http.get('/hrm/attendance/statistics/month-detail', { params }))
      },
      group: {
        page: async (params: { pageNo: number; pageSize: number }) => unwrap<PageResult<HrmAttendanceGroup>>(await http.get('/hrm/attendance/group/page', { params })),
        get: async (id: number) => unwrap<HrmAttendanceGroup>(await http.get('/hrm/attendance/group/get', { params: { id } })),
        create: async (data: HrmAttendanceGroup) => unwrap<number>(await http.post('/hrm/attendance/group/create', data)),
        update: async (data: HrmAttendanceGroup) => unwrap<boolean>(await http.put('/hrm/attendance/group/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/attendance/group/delete', { params: { id } }))
      },
      holiday: {
        page: async (params: { pageNo: number; pageSize: number }) => unwrap<PageResult<HrmAttendanceHoliday>>(await http.get('/hrm/attendance/holiday/page', { params })),
        create: async (data: { date?: number; type: number }) => unwrap<number>(await http.post('/hrm/attendance/holiday/create', data)),
        update: async (data: { id: number; date?: number; type: number }) => unwrap<boolean>(await http.put('/hrm/attendance/holiday/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/attendance/holiday/delete', { params: { id } }))
      }
    },
    salary: {
      employeeInfo: {
        page: async (params: { pageNo: number; pageSize: number; status?: number; search?: string; deptId?: number; postName?: string; entryStatus?: number; statusCategory?: number }) => unwrap<PageResult<HrmSalaryEmployeeInfo>>(await http.get('/hrm/salary/employee-info/page', { params })),
        /** 注意按 employeeId 取，不是薪资档案自身的 id */
        get: async (employeeId: number) => unwrap<HrmSalaryEmployeeInfo>(await http.get('/hrm/salary/employee-info/get', { params: { employeeId } })),
        update: async (data: { id?: number; employeeId: number; recordType: number; changeReason: number; effectTime?: number; remark?: string; salaryOptions?: HrmSalaryOption[]; probationSalaryOptions?: HrmSalaryOption[] }) => unwrap<number>(await http.put('/hrm/salary/employee-info/update', data)),
        minEffectDate: async () => unwrap<string | null>(await http.get('/hrm/salary/employee-info/get-adjustment-min-effect-date')),
        updateList: async (data: { employeeIds: number[]; deptIds: number[]; type: number; changeReason: number; effectTime: number; remark?: string; salaryOptions: HrmSalaryOption[] }) => unwrap<HrmSalaryEmployeeBatchResult>(await http.put('/hrm/salary/employee-info/update-list', data)),
        importFix: async (file: File) => { const data = new FormData(); data.append('file', file); return unwrap<HrmSalaryEmployeeImportResult>(await http.post('/hrm/salary/employee-info/import-fix', data)) },
        importChange: async (file: File) => { const data = new FormData(); data.append('file', file); return unwrap<HrmSalaryEmployeeImportResult>(await http.post('/hrm/salary/employee-info/import-change', data)) }
      },
      changeRecord: {
        list: async (employeeId: number) => unwrap<HrmSalaryChangeRecord[]>(await http.get('/hrm/salary/change-record/list', { params: { employeeId } })),
        get: async (id: number) => unwrap<HrmSalaryChangeRecord>(await http.get('/hrm/salary/change-record/get', { params: { id } })),
        cancel: async (id: number) => unwrap<boolean>(await http.put('/hrm/salary/change-record/cancel', null, { params: { id } })),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/salary/change-record/delete', { params: { id } }))
      },
      monthRecord: {
        page: async (params: { pageNo: number; pageSize: number; year?: number; status?: number }) => unwrap<PageResult<HrmSalaryMonthRecord>>(await http.get('/hrm/salary/month-record/page', { params })),
        get: async (id: number) => unwrap<HrmSalaryMonthRecord>(await http.get('/hrm/salary/month-record/get', { params: { id } })),
        createNext: async () => unwrap<number>(await http.post('/hrm/salary/month-record/create-next')),
        compute: async (id: number) => unwrap<boolean>(await http.post('/hrm/salary/month-record/compute', null, { params: { id } })),
        computeImport: async (data: FormData) => unwrap<boolean>(await http.post('/hrm/salary/month-record/compute-import', data)),
        payrollReadiness: async (monthRecordId?: number) => unwrap<HrmSalaryPayrollReadiness>(await http.get('/hrm/salary/month-record/payroll-readiness', { params: { monthRecordId } })),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/salary/month-record/delete', { params: { id } })),
        employeePage: async (params: { pageNo: number; pageSize: number; monthRecordId: number; employeeName?: string; jobNumber?: string; deptId?: number; employeeChangeType?: number }) => unwrap<PageResult<HrmSalaryMonthEmployeeRecord>>(await http.get('/hrm/salary/month-employee-record/page', { params })),
        employeeList: async (params: { monthRecordId: number; employeeName?: string; jobNumber?: string; deptId?: number; employeeChangeType?: number; employeeIds?: number[]; salarySlipSent?: boolean }) => unwrap<HrmSalaryMonthEmployeeRecord[]>(await http.get('/hrm/salary/month-employee-record/list', { params })),
        employeeUpdateList: async (data: Array<{ id: number; optionValues?: HrmSalaryOption[] }>) => unwrap<boolean>(await http.put('/hrm/salary/month-employee-record/update-list', data)),
        optionSummary: async (params: { monthRecordId: number; employeeName?: string; jobNumber?: string; deptId?: number; employeeChangeType?: number }) => unwrap<HrmSalaryOption[]>(await http.get('/hrm/salary/month-record/option-summary', { params }))
      },
      slip: {
        page: async (params: { pageNo: number; pageSize: number; year?: number; month?: number; sendRecordId?: number; employeeId?: number; search?: string; deptId?: number; readStatus?: number; remark?: string }) => unwrap<PageResult<HrmSalarySlip>>(await http.get('/hrm/salary/slip/page', { params })),
        get: async (id: number) => unwrap<HrmSalarySlip>(await http.get('/hrm/salary/slip/get', { params: { id } })),
        remark: async (data: { id: number; remark?: string }) => unwrap<boolean>(await http.put('/hrm/salary/slip/remark', data)),
        templates: async () => unwrap<HrmSalarySlipTemplate[]>(await http.get('/hrm/salary/slip-template/list')),
        sendRecords: {
          page: async (params: { pageNo: number; pageSize: number; year?: number; month?: number }) => unwrap<PageResult<HrmSalarySlipSendRecord>>(await http.get('/hrm/salary/slip-send-record/page', { params })),
          get: async (id: number) => unwrap<HrmSalarySlipSendRecord>(await http.get('/hrm/salary/slip-send-record/get', { params: { id } })),
          create: async (data: { monthRecordId: number; hideEmpty: boolean; options?: HrmSalarySlipTemplate['options']; all: boolean; employeeIds?: number[]; search?: string; deptId?: number; sent?: boolean }) => unwrap<number>(await http.post('/hrm/salary/slip-send-record/create', data)),
          employeePage: async (params: { pageNo: number; pageSize: number; monthRecordId: number; search?: string; deptId?: number; sent?: boolean }) => unwrap<PageResult<HrmSalarySlipSendEmployee>>(await http.get('/hrm/salary/slip-send-record/employee-page', { params })),
          delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/salary/slip-send-record/delete', { params: { id } }))
        }
      }
    },
    /** 通用 HRM 员工精简分页，供员工选择器远程搜索。返回结构含 id/员工姓名/部门。 */
    employeeSimplePage: async (params: { pageNo: number; pageSize: number; name?: string }) => unwrap<PageResult<HrmPortableEmployee>>(await http.get('/hrm/employee/simple-page', { params })),
    /** 通用部门树，供范围选择器按部门筛选。 */
    deptSimpleList: async () => unwrap<Array<{ id: number; name: string; parentId: number }>>(await http.get('/system/dept/simple-list')),
    dept: {
      list: async () => unwrap<Array<{ id: number; name: string; parentId: number; sort?: number; status?: number; leaderUserId?: number; createTime?: string }>>(await http.get('/system/dept/list')),
      get: async (id: number) => unwrap<HrmDept>(await http.get('/system/dept/get', { params: { id } })),
      create: async (data: Partial<HrmDept>) => unwrap<number>(await http.post('/system/dept/create', data)),
      update: async (data: Partial<HrmDept>) => unwrap<boolean>(await http.put('/system/dept/update', data)),
      delete: async (id: number) => unwrap<boolean>(await http.delete('/system/dept/delete', { params: { id } }))
    },
    employee: {
      page: async (params: { pageNo: number; pageSize: number; name?: string; statusCategory?: number }) => unwrap<PageResult<HrmEmployee>>(await http.get('/hrm/employee/page', { params })),
      get: async (id: number) => unwrap<HrmEmployee>(await http.get('/hrm/employee/get', { params: { id } })),
      create: async (data: HrmEmployeeSave) => unwrap<number>(await http.post('/hrm/employee/create', data)),
      createList: async (data: HrmEmployeeCreateFromUser[]) => unwrap<number[]>(await http.post('/hrm/employee/create-list', data)),
      boundUserIdList: async () => unwrap<number[]>(await http.get('/hrm/employee/bound-user-id-list')),
      sendProfileFillMessage: async (ids: number[]) => unwrap<HrmEmployeeNotifyResult>(await http.post('/hrm/employee/send-profile-fill-message', null, { params: { ids: ids.join(',') } })),
      update: async (data: HrmEmployeeSave) => unwrap<boolean>(await http.put('/hrm/employee/update', data)),
      delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/employee/delete', { params: { id } })),
      deleteList: async (ids: number[]) => unwrap<boolean>(await http.delete('/hrm/employee/delete-list', { params: { ids: ids.join(',') } })),
      import: async (file: File, duplicateStrategy: number) => {
        const data = new FormData(); data.append('file', file); data.append('duplicateStrategy', String(duplicateStrategy))
        return unwrap<HrmEmployeeImportResult>(await http.post('/hrm/employee/import', data))
      },
      uploadFile: async (file: File) => {
        const data = new FormData(); data.append('file', file); data.append('directory', 'hrm/employee/material')
        return unwrap<string>(await http.post('/infra/file/upload', data))
      },
      statusCount: async (params: { statusCategory?: number }) => unwrap<HrmEmployeeStatusCount[]>(await http.get('/hrm/employee/status-count', { params })),
      confirmEntry: async (data: HrmEmployeeSave) => unwrap<boolean>(await http.put('/hrm/employee/confirm-entry', data)),
      rehire: async (data: { employeeId: number }) => unwrap<boolean>(await http.post('/hrm/employee/rehire', data)),
      cancelQuit: async (data: { employeeId: number; reason: string }) => unwrap<boolean>(await http.put('/hrm/employee/cancel-quit', data)),
      regular: async (data: HrmEmployeeChangeReq) => unwrap<boolean>(await http.post('/hrm/employee/regular', data)),
      transfer: async (data: HrmEmployeeChangeReq) => unwrap<boolean>(await http.post('/hrm/employee/transfer', data)),
      promote: async (data: HrmEmployeeChangeReq) => unwrap<boolean>(await http.post('/hrm/employee/promote', data)),
      demote: async (data: HrmEmployeeChangeReq) => unwrap<boolean>(await http.post('/hrm/employee/demote', data)),
      convertToFullTime: async (data: HrmEmployeeChangeReq) => unwrap<boolean>(await http.post('/hrm/employee/convert-to-full-time', data)),
      quit: async (data: HrmEmployeeQuitReq) => unwrap<boolean>(await http.post('/hrm/employee/quit', data)),
      // 档案子表：按 employeeId 查询 + 标准 CRUD
      contract: {
        list: async (employeeId: number) => unwrap<HrmContract[]>(await http.get('/hrm/employee/contract/list', { params: { employeeId } })),
        create: async (data: HrmContract) => unwrap<number>(await http.post('/hrm/employee/contract/create', data)),
        update: async (data: HrmContract) => unwrap<boolean>(await http.put('/hrm/employee/contract/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/employee/contract/delete', { params: { id } }))
      },
      certificate: {
        list: async (employeeId: number) => unwrap<HrmCertificate[]>(await http.get('/hrm/employee/certificate/list', { params: { employeeId } })),
        create: async (data: HrmCertificate) => unwrap<number>(await http.post('/hrm/employee/certificate/create', data)),
        update: async (data: HrmCertificate) => unwrap<boolean>(await http.put('/hrm/employee/certificate/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/employee/certificate/delete', { params: { id } }))
      },
      education: {
        list: async (employeeId: number) => unwrap<HrmEducationExperience[]>(await http.get('/hrm/employee/education-experience/list', { params: { employeeId } })),
        create: async (data: HrmEducationExperience) => unwrap<number>(await http.post('/hrm/employee/education-experience/create', data)),
        update: async (data: HrmEducationExperience) => unwrap<boolean>(await http.put('/hrm/employee/education-experience/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/employee/education-experience/delete', { params: { id } }))
      },
      workExperience: {
        list: async (employeeId: number) => unwrap<HrmWorkExperience[]>(await http.get('/hrm/employee/work-experience/list', { params: { employeeId } })),
        create: async (data: HrmWorkExperience) => unwrap<number>(await http.post('/hrm/employee/work-experience/create', data)),
        update: async (data: HrmWorkExperience) => unwrap<boolean>(await http.put('/hrm/employee/work-experience/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/employee/work-experience/delete', { params: { id } }))
      },
      training: {
        list: async (employeeId: number) => unwrap<HrmTrainingExperience[]>(await http.get('/hrm/employee/training-experience/list', { params: { employeeId } })),
        create: async (data: HrmTrainingExperience) => unwrap<number>(await http.post('/hrm/employee/training-experience/create', data)),
        update: async (data: HrmTrainingExperience) => unwrap<boolean>(await http.put('/hrm/employee/training-experience/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/employee/training-experience/delete', { params: { id } }))
      },
      contact: {
        list: async (employeeId: number) => unwrap<HrmContact[]>(await http.get('/hrm/employee/contact/list', { params: { employeeId } })),
        create: async (data: HrmContact) => unwrap<number>(await http.post('/hrm/employee/contact/create', data)),
        update: async (data: HrmContact) => unwrap<boolean>(await http.put('/hrm/employee/contact/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/employee/contact/delete', { params: { id } }))
      },
      salaryCard: {
        get: async (employeeId: number) => unwrap<HrmSalaryCard>(await http.get('/hrm/employee/salary-card/get', { params: { employeeId } })),
        save: async (employeeId: number, data: HrmSalaryCard) => unwrap<number>(await http.put('/hrm/employee/salary-card/save', { ...data, employeeId })),
        delete: async (employeeId: number) => unwrap<boolean>(await http.delete('/hrm/employee/salary-card/delete', { params: { employeeId } }))
      },
      quitInfo: {
        get: async (employeeId: number) => unwrap<HrmQuitInfo>(await http.get('/hrm/employee/quit-info/get', { params: { employeeId } }))
      },
      file: {
        list: async (employeeId: number) => unwrap<HrmEmployeeFile[]>(await http.get('/hrm/employee/file/list', { params: { employeeId } })),
        save: async (data: { employeeId: number; type: number; fileUrls: string[] }) => unwrap<boolean>(await http.put('/hrm/employee/file/save', data))
      },
      changeRecord: {
        list: async (employeeId: number) => unwrap<HrmEmployeeChangeRecord[]>(await http.get('/hrm/employee/change-record/list', { params: { employeeId } }))
      },
      config: {
        createFieldList: async (entryStatus: number) => unwrap<HrmEmployeeFieldConfig[]>(await http.get('/hrm/employee/config/create-field/list', { params: { entryStatus } })),
        saveCreateField: async (entryStatus: number, fields: Array<{ name: string; visible: boolean }>) => unwrap<boolean>(await http.put('/hrm/employee/config/create-field/save', { entryStatus, fields })),
        archiveFieldList: async () => unwrap<HrmEmployeeFieldConfig[]>(await http.get('/hrm/employee/config/archive-field/list')),
        saveArchiveField: async (fields: Array<{ name: string; visible: boolean; editable?: boolean }>) => unwrap<boolean>(await http.put('/hrm/employee/config/archive-field/save', { fields }))
      }
    },
    salaryCfg: {
      option: {
        list: async () => unwrap<HrmSalaryOptionCfg[]>(await http.get('/hrm/salary/option/list')),
        create: async (data: { parentCode?: number; name: string; remark?: string }) => unwrap<number>(await http.post('/hrm/salary/option/create', data)),
        updateEnabled: async (id: number, enabled: boolean) => unwrap<boolean>(await http.put('/hrm/salary/option/update-enabled', { id, enabled })),
        updateVisible: async (id: number, visible: boolean) => unwrap<boolean>(await http.put('/hrm/salary/option/update-visible', { id, visible })),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/salary/option/delete', { params: { id } }))
      },
      group: {
        page: async (params: { pageNo: number; pageSize: number }) => unwrap<PageResult<HrmSalaryGroup>>(await http.get('/hrm/salary/group/page', { params })),
        list: async () => unwrap<HrmSalaryGroup[]>(await http.get('/hrm/salary/group/list')),
        simpleList: async () => unwrap<HrmSalaryGroup[]>(await http.get('/hrm/salary/group/simple-list')),
        get: async (id: number) => unwrap<HrmSalaryGroup>(await http.get('/hrm/salary/group/get', { params: { id } })),
        create: async (data: HrmSalaryGroup) => unwrap<number>(await http.post('/hrm/salary/group/create', data)),
        update: async (data: HrmSalaryGroup) => unwrap<boolean>(await http.put('/hrm/salary/group/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/salary/group/delete', { params: { id } }))
      },
      taxRule: {
        list: async () => unwrap<HrmSalaryTaxRule[]>(await http.get('/hrm/salary/tax-rule/list')),
        create: async (data: HrmSalaryTaxRule) => unwrap<number>(await http.post('/hrm/salary/tax-rule/create', data)),
        update: async (data: HrmSalaryTaxRule) => unwrap<boolean>(await http.put('/hrm/salary/tax-rule/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/salary/tax-rule/delete', { params: { id } }))
      },
      changeTemplate: {
        list: async () => unwrap<HrmSalaryChangeTemplate[]>(await http.get('/hrm/salary/change-template/list')),
        create: async (data: HrmSalaryChangeTemplate) => unwrap<number>(await http.post('/hrm/salary/change-template/create', data)),
        update: async (data: HrmSalaryChangeTemplate) => unwrap<boolean>(await http.put('/hrm/salary/change-template/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/salary/change-template/delete', { params: { id } }))
      },
      config: {
        get: async () => unwrap<HrmSalaryConfig>(await http.get('/hrm/salary/config/get')),
        create: async (data: { cycleStartDay: number; socialSecurityMonthType: number; startYear: number; startMonth: number }) => unwrap<number>(await http.post('/hrm/salary/config/create', data)),
        update: async (data: { socialSecurityMonthType: number }) => unwrap<boolean>(await http.put('/hrm/salary/config/update', data))
      }
    },
    birthdayCare: {
      get: async () => unwrap<HrmBirthdayCareConfig>(await http.get('/hrm/birthday-care/config')),
      save: async (data: HrmBirthdayCareConfig) => unwrap<boolean>(await http.put('/hrm/birthday-care/config', data))
    },
    home: {
      hrStatistics: async () => unwrap<HrmHrHomeStatistics>(await http.get('/hrm/home/hr-statistics-summary')),
      hrCalendar: async (params: { startDate: string; endDate: string }) => unwrap<HrmHomeCalendarItem[]>(await http.get('/hrm/home/hr-calendar', { params })),
      teamStatistics: async () => unwrap<HrmTeamHomeStatistics>(await http.get('/hrm/home/team-statistics-summary')),
      teamCalendar: async (params: { startDate: string; endDate: string }) => unwrap<HrmHomeCalendarItem[]>(await http.get('/hrm/home/team-calendar', { params }))
    },
    insurance: {
      scheme: {
        list: async () => unwrap<HrmInsuranceScheme[]>(await http.get('/hrm/insurance/scheme/list')),
        get: async (id: number) => unwrap<HrmInsuranceScheme>(await http.get('/hrm/insurance/scheme/get', { params: { id } })),
        create: async (data: HrmInsuranceScheme) => unwrap<number>(await http.post('/hrm/insurance/scheme/create', data)),
        update: async (data: HrmInsuranceScheme) => unwrap<boolean>(await http.put('/hrm/insurance/scheme/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/insurance/scheme/delete', { params: { id } }))
      },
      monthRecord: {
        page: async (params: { pageNo: number; pageSize: number; year?: number }) => unwrap<PageResult<HrmInsuranceMonthRecord>>(await http.get('/hrm/insurance/month-record/page', { params })),
        createFirst: async (data: { year: number; month: number }) => unwrap<number>(await http.post('/hrm/insurance/month-record/create-first', data)),
        createNext: async () => unwrap<number>(await http.post('/hrm/insurance/month-record/create-next')),
        get: async (id: number) => unwrap<HrmInsuranceMonthRecord>(await http.get('/hrm/insurance/month-record/get', { params: { id } })),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/insurance/month-record/delete', { params: { id } })),
        employeePage: async (params: { pageNo: number; pageSize: number; monthRecordId: number; employeeName?: string; schemeId?: number; areaId?: number; status?: number }) => unwrap<PageResult<HrmInsuranceMonthEmployeeRecord>>(await http.get('/hrm/insurance/month-employee-record/page', { params })),
        employeeGet: async (id: number) => unwrap<HrmInsuranceMonthEmployeeRecord>(await http.get('/hrm/insurance/month-employee-record/get', { params: { id } })),
        employeeUpdate: async (data: HrmInsuranceMonthEmployeeUpdate) => unwrap<boolean>(await http.put('/hrm/insurance/month-employee-record/update', data)),
        employeeStopList: async (ids: number[]) => unwrap<boolean>(await http.put('/hrm/insurance/month-employee-record/stop-list', { ids })),
        employeeCreateList: async (data: { monthRecordId: number; employeeIds: number[] }) => unwrap<boolean>(await http.post('/hrm/insurance/month-employee-record/create-list', data)),
        uninsuredEmployeeList: async (monthRecordId: number) => unwrap<HrmEmployee[]>(await http.get('/hrm/insurance/month-employee-record/uninsured-employee-list', { params: { monthRecordId } }))
      }
    },
    perfCfg: {
      resultTemplate: {
        page: async (params: { pageNo: number; pageSize: number }) => unwrap<PageResult<HrmPerformanceResultTemplate>>(await http.get('/hrm/performance/result-template/page', { params })),
        get: async (id: number) => unwrap<HrmPerformanceResultTemplate>(await http.get('/hrm/performance/result-template/get', { params: { id } })),
        simpleList: async (status?: number) => unwrap<HrmPerformanceResultTemplate[]>(await http.get('/hrm/performance/result-template/simple-list', { params: { status } })),
        create: async (data: HrmPerformanceResultTemplate) => unwrap<number>(await http.post('/hrm/performance/result-template/create', data)),
        update: async (data: HrmPerformanceResultTemplate) => unwrap<boolean>(await http.put('/hrm/performance/result-template/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/performance/result-template/delete', { params: { id } }))
      },
      assessmentTemplate: {
        page: async (params: { pageNo: number; pageSize: number }) => unwrap<PageResult<HrmAssessmentTemplate>>(await http.get('/hrm/performance/assessment-template/page', { params })),
        get: async (id: number) => unwrap<HrmAssessmentTemplate>(await http.get('/hrm/performance/assessment-template/get', { params: { id } })),
        simpleList: async () => unwrap<HrmAssessmentTemplate[]>(await http.get('/hrm/performance/assessment-template/simple-list')),
        create: async (data: HrmAssessmentTemplate) => unwrap<number>(await http.post('/hrm/performance/assessment-template/create', data)),
        update: async (data: HrmAssessmentTemplate) => unwrap<boolean>(await http.put('/hrm/performance/assessment-template/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/performance/assessment-template/delete', { params: { id } }))
      }
    },
    recruit: {
      post: {
        page: async (params: { pageNo: number; pageSize: number; status?: number }) => unwrap<PageResult<HrmRecruitPost>>(await http.get('/hrm/recruit/post/page', { params })),
        get: async (id: number) => unwrap<HrmRecruitPost>(await http.get('/hrm/recruit/post/get', { params: { id } })),
        create: async (data: HrmRecruitPost) => unwrap<number>(await http.post('/hrm/recruit/post/create', data)),
        update: async (data: HrmRecruitPost) => unwrap<boolean>(await http.put('/hrm/recruit/post/update', data)),
        updateStatus: async (data: { id: number; status: number; stopReason?: string }) => unwrap<boolean>(await http.put('/hrm/recruit/post/update-status', data)),
        statusCount: async () => unwrap<HrmRecruitCandidateStatusCount[]>(await http.get('/hrm/recruit/post/status-count'))
      },
      candidate: {
        page: async (params: { pageNo: number; pageSize: number; postId?: number; status?: number; name?: string; mobile?: string; channelId?: number }) => unwrap<PageResult<HrmRecruitCandidate>>(await http.get('/hrm/recruit/candidate/page', { params })),
        get: async (id: number) => unwrap<HrmRecruitCandidate>(await http.get('/hrm/recruit/candidate/get', { params: { id } })),
        create: async (data: HrmRecruitCandidate) => unwrap<number>(await http.post('/hrm/recruit/candidate/create', data)),
        update: async (data: HrmRecruitCandidate) => unwrap<boolean>(await http.put('/hrm/recruit/candidate/update', data)),
        updateStatus: async (data: { id: number; status: number }) => unwrap<boolean>(await http.put('/hrm/recruit/candidate/update-status', data)),
        updatePost: async (data: { id: number; postId: number }) => unwrap<boolean>(await http.put('/hrm/recruit/candidate/update-post', data)),
        updateChannel: async (data: { id: number; channelId: number }) => unwrap<boolean>(await http.put('/hrm/recruit/candidate/update-channel', data)),
        eliminate: async (data: { id: number; eliminate?: string; remark?: string }) => unwrap<boolean>(await http.put('/hrm/recruit/candidate/eliminate', data)),
        convertEmployee: async (data: HrmEmployeeSave & { candidateId: number }) => unwrap<number>(await http.post('/hrm/recruit/candidate/convert-employee', data)),
        cleanIds: async (statuses: number[], days: number) => unwrap<number[]>(await http.get('/hrm/recruit/candidate/clean-ids', { params: { statuses: statuses.join(','), days } })),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/recruit/candidate/delete', { params: { id } }))
      },
      interview: {
        get: async (id: number) => unwrap<HrmRecruitInterview>(await http.get('/hrm/recruit/interview/get', { params: { id } })),
        listByCandidate: async (candidateId: number) => unwrap<HrmRecruitInterview[]>(await http.get('/hrm/recruit/interview/list-by-candidate', { params: { candidateId } })),
        create: async (data: HrmRecruitInterview) => unwrap<number>(await http.post('/hrm/recruit/interview/create', data)),
        update: async (data: HrmRecruitInterview) => unwrap<boolean>(await http.put('/hrm/recruit/interview/update', data)),
        updateResult: async (data: HrmRecruitInterviewResultSave) => unwrap<boolean>(await http.put('/hrm/recruit/interview/update-result', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/recruit/interview/delete', { params: { id } }))
      },
      uploadResume: async (file: File) => {
        const data = new FormData(); data.append('file', file); data.append('directory', 'hrm/recruit/candidate/resume')
        return unwrap<string>(await http.post('/infra/file/upload', data))
      },
      channel: {
        page: async (params: { pageNo: number; pageSize: number }) => unwrap<PageResult<HrmRecruitChannel>>(await http.get('/hrm/recruit/channel/page', { params })),
        create: async (data: { name: string }) => unwrap<number>(await http.post('/hrm/recruit/channel/create', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/recruit/channel/delete', { params: { id } }))
      },
      eliminateReason: {
        list: async () => unwrap<string[]>(await http.get('/hrm/recruit/config/eliminate-reason/list')),
        save: async (reasons: string[]) => unwrap<boolean>(await http.post('/hrm/recruit/config/eliminate-reason/save', { reasons }))
      }
    },
    performance: {
      plan: {
        page: async (params: { pageNo: number; pageSize: number; status?: number }) => unwrap<PageResult<HrmPerformancePlan>>(await http.get('/hrm/performance/plan/page', { params })),
        get: async (id: number) => unwrap<HrmPerformancePlan>(await http.get('/hrm/performance/plan/get', { params: { id } })),
        create: async (data: HrmPerformancePlanSave) => unwrap<number>(await http.post('/hrm/performance/plan/create', data)),
        update: async (data: HrmPerformancePlanSave) => unwrap<boolean>(await http.put('/hrm/performance/plan/update', data)),
        delete: async (id: number) => unwrap<boolean>(await http.delete('/hrm/performance/plan/delete', { params: { id } })),
        start: async (id: number) => unwrap<boolean>(await http.post('/hrm/performance/plan/start', null, { params: { id } })),
        openScoring: async (id: number) => unwrap<boolean>(await http.post('/hrm/performance/plan/open-scoring', null, { params: { id } })),
        startInterview: async (id: number) => unwrap<boolean>(await http.post('/hrm/performance/plan/start-interview', null, { params: { id } })),
        archive: async (id: number) => unwrap<boolean>(await http.post('/hrm/performance/plan/archive', null, { params: { id } })),
        terminate: async (id: number) => unwrap<boolean>(await http.post('/hrm/performance/plan/terminate', null, { params: { id } }))
      },
      assessment: {
        page: async (params: { pageNo: number; pageSize: number; planId?: number }) => unwrap<PageResult<HrmPerformanceAssessment>>(await http.get('/hrm/performance/assessment/page', { params })),
        get: async (id: number) => unwrap<HrmPerformanceAssessment>(await http.get('/hrm/performance/assessment/get', { params: { id } })),
        processRecordList: async (id: number) => unwrap<HrmPerformanceProcessRecord[]>(await http.get('/hrm/performance/assessment/process-record-list', { params: { id } }))
      }
    }
  }
  // ========== EAM (Asset Management) ==========
  ,eam: {
    repair: {
      page: async (params: { pageNo: number; pageSize: number; assetId?: number }) => unwrap<PageResult<EamRepairItem>>(await http.get('/eam/repair/page', { params })),
      get: async (id: number) => unwrap<EamRepairItem>(await http.get('/eam/repair/get', { params: { id } })),
      listByAsset: async (assetId: number) => unwrap<EamRepairItem[]>(await http.get('/eam/repair/list-by-asset', { params: { assetId } })),
      create: async (data: EamRepairCreate) => unwrap<number>(await http.post('/eam/repair/create', data)),
      finish: async (data: EamRepairFinish) => unwrap<boolean>(await http.put('/eam/repair/finish', data)),
      delete: async (id: number) => unwrap<boolean>(await http.delete('/eam/repair/delete', { params: { id } }))
    },
    inventory: {
      page: async (params: { pageNo: number; pageSize: number; name?: string; status?: number }) => unwrap<PageResult<EamInventoryItem>>(await http.get('/eam/inventory/page', { params })),
      get: async (id: number) => unwrap<EamInventoryItem>(await http.get('/eam/inventory/get', { params: { id } })),
      create: async (data: EamInventoryCreate) => unwrap<number>(await http.post('/eam/inventory/create', data)),
      delete: async (id: number) => unwrap<boolean>(await http.delete('/eam/inventory/delete', { params: { id } })),
      detailList: async (inventoryId: number) => unwrap<EamInventoryDetail[]>(await http.get('/eam/inventory/detail-list', { params: { inventoryId } })),
      check: async (data: EamInventoryCheck) => unwrap<boolean>(await http.put('/eam/inventory/check', data)),
      finish: async (id: number) => unwrap<boolean>(await http.put('/eam/inventory/finish', null, { params: { id } })),
      syncDetail: async (detailId: number) => unwrap<boolean>(await http.put('/eam/inventory/sync-detail', null, { params: { detailId } })),
      markLost: async (detailId: number) => unwrap<boolean>(await http.put('/eam/inventory/mark-lost', null, { params: { detailId } }))
    },
    asset: {
      page: async (params: { pageNo: number; pageSize: number; name?: string; assetCode?: string; categoryId?: number; status?: number; extFieldKey?: string; extFieldValue?: string }) => unwrap<PageResult<EamAssetListItem>>(await http.get('/eam/asset/page', { params })),
      get: async (id: number) => unwrap<EamAsset>(await http.get('/eam/asset/get', { params: { id } })),
      create: async (data: EamAsset) => unwrap<number>(await http.post('/eam/asset/create', data)),
      update: async (data: EamAsset) => unwrap<boolean>(await http.put('/eam/asset/update', data)),
      delete: async (id: number) => unwrap<boolean>(await http.delete('/eam/asset/delete', { params: { id } })),
      changeLog: async (assetId: number) => unwrap<EamAssetChangeLog[]>(await http.get('/eam/asset/change-log', { params: { assetId } })),
      importPreview: async (file: File, updateExisting = false) => {
        const data = new FormData(); data.append('file', file); data.append('updateExisting', String(updateExisting))
        return unwrap<EamAssetImportPreview>(await http.post('/eam/asset/import/preview', data))
      },
      importCommit: async (file: File, updateExisting = false) => {
        const data = new FormData(); data.append('file', file); data.append('updateExisting', String(updateExisting))
        return unwrap<EamAssetImportPreview>(await http.post('/eam/asset/import/commit', data))
      }
    },
    transfer: {
      page: async (params: { pageNo: number; pageSize: number; no?: string; type?: number; status?: number }) => unwrap<PageResult<EamTransfer>>(await http.get('/eam/transfer/page', { params })),
      get: async (id: number) => unwrap<EamTransfer>(await http.get('/eam/transfer/get', { params: { id } })),
      create: async (data: EamTransferCreate) => unwrap<number>(await http.post('/eam/transfer/create', data)),
      approve: async (id: number) => unwrap<boolean>(await http.put('/eam/transfer/approve', null, { params: { id } })),
      reject: async (id: number, reason?: string) => unwrap<boolean>(await http.put('/eam/transfer/reject', null, { params: { id, reason } })),
      cancel: async (id: number) => unwrap<boolean>(await http.put('/eam/transfer/cancel', null, { params: { id } }))
    },
    scrap: {
      page: async (params: { pageNo: number; pageSize: number; no?: string; status?: number }) => unwrap<PageResult<EamScrap>>(await http.get('/eam/scrap/page', { params })),
      get: async (id: number) => unwrap<EamScrap>(await http.get('/eam/scrap/get', { params: { id } })),
      create: async (data: EamScrapCreate) => unwrap<number>(await http.post('/eam/scrap/create', data)),
      approve: async (id: number) => unwrap<boolean>(await http.put('/eam/scrap/approve', null, { params: { id } })),
      reject: async (id: number, reason?: string) => unwrap<boolean>(await http.put('/eam/scrap/reject', null, { params: { id, reason } }))
    },
    category: {
      list: async () => unwrap<EamCategory[]>(await http.get('/eam/category/list')),
      get: async (id: number) => unwrap<EamCategory>(await http.get('/eam/category/get', { params: { id } })),
      create: async (data: EamCategorySave) => unwrap<number>(await http.post('/eam/category/create', data)),
      update: async (data: EamCategorySave) => unwrap<boolean>(await http.put('/eam/category/update', data)),
      delete: async (id: number) => unwrap<boolean>(await http.delete('/eam/category/delete', { params: { id } })),
      importPreview: async (file: File) => {
        const data = new FormData(); data.append('file', file)
        return unwrap<EamCategoryImportResult>(await http.post('/eam/category/import/preview', data))
      },
      importCommit: async (file: File) => {
        const data = new FormData(); data.append('file', file)
        return unwrap<EamCategoryImportResult>(await http.post('/eam/category/import/commit', data))
      }
    },
    categoryField: {
      list: async (categoryId: number) => unwrap<EamCategoryField[]>(await http.get('/eam/category-field/list', { params: { categoryId } })),
      effectiveList: async (categoryId: number) => unwrap<EamCategoryField[]>(await http.get('/eam/category-field/effective-list', { params: { categoryId } })),
      create: async (data: EamCategoryField) => unwrap<number>(await http.post('/eam/category-field/create', data)),
      update: async (data: EamCategoryField) => unwrap<boolean>(await http.put('/eam/category-field/update', data)),
      delete: async (id: number) => unwrap<boolean>(await http.delete('/eam/category-field/delete', { params: { id } }))
    },
    codeRule: {
      list: async () => unwrap<EamCodeRule[]>(await http.get('/eam/code-rule/list')),
      get: async (id: number) => unwrap<EamCodeRule>(await http.get('/eam/code-rule/get', { params: { id } })),
      create: async (data: EamCodeRule) => unwrap<number>(await http.post('/eam/code-rule/create', data)),
      update: async (data: EamCodeRule) => unwrap<boolean>(await http.put('/eam/code-rule/update', data)),
      delete: async (id: number) => unwrap<boolean>(await http.delete('/eam/code-rule/delete', { params: { id } }))
    },
    statistics: async () => unwrap<EamStatistics>(await http.get('/eam/statistics/overview')),
    /** 资产附件走 infra 通用上传，返回可直接存进 fileUrls 的 URL */
    uploadFile: async (file: File) => {
      const data = new FormData(); data.append('file', file)
      return unwrap<string>(await http.post('/infra/file/upload', data))
    },
    deptSimpleList: async () => unwrap<Array<{ id: number; name: string; parentId: number }>>(await http.get('/system/dept/simple-list')),
    userSimpleList: async () => unwrap<Array<{ id: number; nickname: string }>>(await http.get('/system/user/simple-list'))
  }
}
