/**
 * HRM 模块共享常量与工具。
 * 枚举取值与后端 yudao-module-hrm 的 Enum 保持一致；带字典类型的字段一律走 useDict，
 * 这里只放后端没有下发字典、或需要前端语义化判断的部分。
 */

/** 请假审批状态，与 BpmProcessInstanceStatusEnum 对齐 */
export const LEAVE_APPROVAL_STATUS = {
  RUNNING: 1, APPROVE: 2, REJECT: 3, CANCEL: 4
} as const

export const LEAVE_APPROVAL_STATUS_LABELS: Record<number, string> = {
  1: '审批中', 2: '已通过', 3: '已拒绝', 4: '已取消'
}

export const LEAVE_APPROVAL_STATUS_COLORS: Record<number, string> = {
  1: 'processing', 2: 'success', 3: 'error', 4: 'default'
}

/** 只有审批中的请假允许撤销，与后端 cancel 校验一致 */
export function canCancelLeave(approvalStatus?: number) {
  return approvalStatus === LEAVE_APPROVAL_STATUS.RUNNING
}

/** HRM 相关字典类型，集中声明避免各页面拼错字符串 */
export const HRM_DICT = {
  CLOCK_TYPE: 'hrm_attendance_clock_type',
  CLOCK_SOURCE: 'hrm_attendance_clock_source',
  CLOCK_STATUS: 'hrm_attendance_clock_status',
  LEAVE_TYPE: 'hrm_attendance_leave_type',
  HOLIDAY_TYPE: 'hrm_attendance_holiday_type',
  ATTENDANCE_DEDUCT_METHOD: 'hrm_attendance_late_early_deduct_method',
  INSURANCE_SCHEME_TYPE: 'hrm_insurance_scheme_type',
  INSURANCE_PROJECT_TYPE: 'hrm_insurance_project_type',
  INSURANCE_MONTH_STATUS: 'hrm_insurance_month_status',
  INSURANCE_EMPLOYEE_STATUS: 'hrm_insurance_emp_status',
  RECRUIT_POST_STATUS: 'hrm_recruit_post_status',
  RECRUIT_JOB_NATURE: 'hrm_recruit_job_nature',
  RECRUIT_WORK_TIME: 'hrm_recruit_work_time',
  RECRUIT_POST_EDUCATION: 'hrm_recruit_post_education',
  RECRUIT_SALARY_UNIT: 'hrm_recruit_salary_unit',
  RECRUIT_EMERGENCY_LEVEL: 'hrm_recruit_emergency_level',
  RECRUIT_CANDIDATE_STATUS: 'hrm_recruit_candidate_status',
  RECRUIT_CANDIDATE_EDUCATION: 'hrm_recruit_candidate_education',
  RECRUIT_INTERVIEW_TYPE: 'hrm_recruit_interview_type',
  RECRUIT_INTERVIEW_RESULT: 'hrm_recruit_interview_result',
  SYSTEM_USER_SEX: 'system_user_sex',
  EMPLOYEE_STATUS: 'hrm_employee_status',
  EMPLOYEE_TYPE: 'hrm_employee_type',
  EMPLOYEE_ENTRY_STATUS: 'hrm_employee_entry_status',
  EMPLOYEE_EDUCATION: 'hrm_employee_education',
  SALARY_MONTH_STATUS: 'hrm_salary_month_status',
  SALARY_SLIP_READ_STATUS: 'hrm_salary_slip_read_status',
  SALARY_OPTION_TYPE: 'hrm_salary_option_type',
  SALARY_TAX_TYPE: 'hrm_salary_tax_type',
  SALARY_CHANGE_REASON: 'hrm_salary_change_reason',
  SALARY_CHANGE_RECORD_STATUS: 'hrm_salary_change_record_status',
  PERFORMANCE_PLAN_STATUS: 'hrm_performance_plan_status',
  PERFORMANCE_STAGE_STATUS: 'hrm_performance_stage_status',
  PERFORMANCE_ASSESSMENT_STAGE_STATUS: 'hrm_performance_assessment_stage_status',
  PERFORMANCE_APPEAL_STATUS: 'hrm_performance_appeal_status',
  PERFORMANCE_YES_NO: 'hrm_performance_yes_no'
} as const

/** 工资条阅读状态，与 HrmSalarySlipReadStatusEnum 对齐 */
export const SLIP_READ_STATUS = { UNREAD: 0, READ: 1 } as const

/** 月度社保与员工参保状态，用于决定是否允许修改；展示文案仍走系统字典。 */
export const INSURANCE_MONTH_STATUS = { UNARCHIVED: 0, ARCHIVED: 1 } as const
export const INSURANCE_EMPLOYEE_STATUS = { STOPPED: 0, NORMAL: 1 } as const
export const INSURANCE_SCHEME_TYPE = { PROPORTION: 1, AMOUNT: 2 } as const

/** 工资表状态与系统计算项编码；状态决定历史表只读，计算项不得人工覆盖。 */
export const SALARY_MONTH_STATUS = { UNCOMPUTED: 5, HISTORY: 10, COMPUTED: 11 } as const
export const SALARY_COMPUTED_OPTION_CODES = new Set([
  210101, 220101, 230101, 240101, 270101, 270102, 270103, 270104, 270105, 270106
])
export const SALARY_BATCH_ADJUST_TYPE = { PERCENT: 1, AMOUNT: 2 } as const
export const SALARY_CHANGE_RECORD_STATUS = { PENDING: 0, EFFECTIVE: 1, CANCELLED: 2 } as const

/** 绩效计划状态，与 HrmPerformancePlanStatusEnum 对齐 */
export const PERFORMANCE_PLAN_STATUS = {
  DRAFT: 1, NOT_STARTED: 2, RUNNING: 3, ARCHIVED: 4, TERMINATED: 5
} as const

export const PERFORMANCE_PLAN_STATUS_LABELS: Record<number, string> = {
  1: '草稿', 2: '未开始', 3: '进行中', 4: '已归档', 5: '已终止'
}

export const PERFORMANCE_PLAN_STATUS_COLORS: Record<number, string> = {
  1: 'default', 2: 'warning', 3: 'processing', 4: 'success', 5: 'error'
}

/** 绩效业务阶段，与 HrmPerformanceStageTypeEnum 对齐 */
export const PERFORMANCE_STAGE_TYPE = {
  NOT_STARTED: 0, FILL_QUOTA: 1, TARGET_CONFIRM: 2, SELF_SCORE: 3, OTHER_SCORE: 4,
  RESULT_AUDIT: 5, RESULT_CONFIRM: 6, APPEAL_CONFIRM: 7, ARCHIVED: 8, EXECUTING: 9, END: 10
} as const

export const PERFORMANCE_STAGE_TYPE_LABELS: Record<number, string> = {
  0: '未开始', 1: '员工填写', 2: '目标确认', 3: '自评', 4: '他人评分',
  5: '结果审核', 6: '结果确认', 7: '申诉确认', 8: '归档', 9: '执行中', 10: '结束'
}

/** 结果审核状态，与 HrmPerformanceResultAuditStatusEnum 对齐 */
export const RESULT_AUDIT_STATUS = { PENDING: 1, PASS: 2, REJECT: 3, CANCEL: 4 } as const

export const RESULT_AUDIT_STATUS_LABELS: Record<number, string> = {
  1: '审核中', 2: '已通过', 3: '已驳回', 4: '已取消'
}

export const RESULT_AUDIT_STATUS_COLORS: Record<number, string> = {
  1: 'processing', 2: 'success', 3: 'error', 4: 'default'
}

/** 申诉状态，与 HrmPerformanceAppealStatusEnum 对齐 */
export const APPEAL_STATUS = { NONE: 0, PENDING: 1, PASS: 2, REJECT: 3, CANCEL: 4 } as const

export const APPEAL_STATUS_LABELS: Record<number, string> = {
  0: '无申诉', 1: '待处理', 2: '已通过', 3: '已驳回', 4: '已取消'
}

export const APPEAL_STATUS_COLORS: Record<number, string> = {
  0: 'default', 1: 'processing', 2: 'success', 3: 'error', 4: 'default'
}

/** 目标确认结果：是否通过。与后端 pass 参数语义一致 */
export const CONFIRM_PASS = { PASS: 1, FAIL: 0 } as const

/** 金额展示：保留两位小数并加千分位。后端金额单位为元。 */
export function fmtAmount(value?: number | null) {
  if (value == null) return '-'
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 分钟数转“x小时y分”，用于迟到/早退/请假时长展示 */
export function fmtMinutes(value?: number | null) {
  if (value == null || value === 0) return '-'
  const hours = Math.floor(value / 60)
  const minutes = value % 60
  if (!hours) return `${minutes}分钟`
  return minutes ? `${hours}小时${minutes}分钟` : `${hours}小时`
}

/** 当前年月，作为月度报表类页面的默认筛选值 */
export function currentYearMonth() {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

/** 生成年份下拉选项：当前年往前 recent 年 */
export function yearOptions(recent = 5) {
  const thisYear = new Date().getFullYear()
  return Array.from({ length: recent }, (_, index) => {
    const year = thisYear - index
    return { value: year, label: `${year}年` }
  })
}

export const MONTH_OPTIONS = Array.from({ length: 12 }, (_, index) => ({
  value: index + 1,
  label: `${index + 1}月`
}))

/** 员工入职状态，与 HrmEmployeeEntryStatusEnum 对齐 */
export const ENTRY_STATUS = {
  ACTIVE: 1,
  PENDING_ENTRY: 2,
  PENDING_LEAVE: 3,
  LEFT: 4
} as const

export const ENTRY_STATUS_LABELS: Record<number, string> = {
  1: '在职',
  2: '待入职',
  3: '待离职',
  4: '离职'
}

export const ENTRY_STATUS_COLORS: Record<number, string> = {
  1: 'green',
  2: 'blue',
  3: 'orange',
  4: 'default'
}

/** 员工状态页签，与 HrmEmployeeStatusTabEnum 对齐 */
export const EMPLOYEE_STATUS_TAB = {
  ACTIVE: 11,
  FULL_TIME: 12,
  PENDING_ENTRY: 13,
  PENDING_LEAVE: 14,
  LEFT: 15
} as const

export const EMPLOYEE_STATUS_TAB_LABELS: Record<number, string> = {
  11: '在职',
  12: '全职',
  13: '待入职',
  14: '待离职',
  15: '已离职'
}

/** 聘用形式，与 HrmEmployeeTypeEnum 对齐 */
export const EMPLOYEE_TYPE = { FORMAL: 1, INFORMAL: 2 } as const

export const EMPLOYEE_TYPE_LABELS: Record<number, string> = { 1: '正式', 2: '非正式' }

/** 员工状态，与 HrmEmployeeStatusEnum 对齐；type 表明该状态归属的聘用形式 */
export const EMPLOYEE_STATUS_OPTIONS = [
  { value: 1, label: '正式', type: 1 },
  { value: 2, label: '试用', type: 1 },
  { value: 3, label: '实习', type: 2 },
  { value: 4, label: '兼职', type: 2 },
  { value: 5, label: '劳务', type: 2 },
  { value: 6, label: '顾问', type: 2 },
  { value: 7, label: '返聘', type: 2 },
  { value: 8, label: '外包', type: 2 }
] as const

export const EMPLOYEE_STATUS_LABELS: Record<number, string> = Object.fromEntries(
  EMPLOYEE_STATUS_OPTIONS.map((item) => [item.value, item.label])
)

/** 员工材料附件类型由后端 HrmEmployeeFileTypeEnum 固定定义。 */
export const EMPLOYEE_FILE_GROUPS = [
  { label: '员工基本资料', options: [
    { label: '身份证原件照片', value: 11 }, { label: '学历证明', value: 12 },
    { label: '个人证件照', value: 13 }, { label: '身份证复印件', value: 14 },
    { label: '工资银行卡', value: 15 }, { label: '社保卡', value: 16 },
    { label: '公积金卡', value: 17 }, { label: '获奖证书', value: 18 },
    { label: '其他基本资料', value: 19 }
  ] },
  { label: '员工档案资料', options: [
    { label: '劳动合同', value: 21 }, { label: '入职简历', value: 22 },
    { label: '入职登记表', value: 23 }, { label: '入职体检单', value: 24 },
    { label: '上家公司离职证明', value: 25 }, { label: '转正申请表', value: 26 },
    { label: '其他档案资料', value: 27 }
  ] },
  { label: '员工离职资料', options: [
    { label: '离职审批', value: 31 }, { label: '离职证明', value: 32 },
    { label: '其他离职资料', value: 33 }
  ] }
] as const

/** 按聘用形式过滤可选的员工状态 */
export function employeeStatusOptionsOf(type?: number) {
  const list = type ? EMPLOYEE_STATUS_OPTIONS.filter((item) => item.type === type) : EMPLOYEE_STATUS_OPTIONS
  return list.map((item) => ({ value: item.value, label: item.label }))
}

/** 证件类型，与 HrmEmployeeIdTypeEnum 对齐 */
export const ID_TYPE_OPTIONS = [
  { value: 1, label: '身份证' },
  { value: 2, label: '港澳通行证' },
  { value: 3, label: '台湾通行证' },
  { value: 4, label: '护照' },
  { value: 5, label: '其他' }
]

export const ID_TYPE_LABELS: Record<number, string> = Object.fromEntries(
  ID_TYPE_OPTIONS.map((item) => [item.value, item.label])
)

/** 学历，与 HrmEmployeeEducationEnum 对齐 */
export const EDUCATION_OPTIONS = [
  { value: 1, label: '小学' },
  { value: 2, label: '初中' },
  { value: 3, label: '中专' },
  { value: 4, label: '中职' },
  { value: 5, label: '技校' },
  { value: 6, label: '高中' },
  { value: 7, label: '大专' },
  { value: 8, label: '本科' },
  { value: 9, label: '硕士' },
  { value: 10, label: '博士' },
  { value: 11, label: '博士后' },
  { value: 12, label: '其他' }
]

export const EDUCATION_LABELS: Record<number, string> = Object.fromEntries(
  EDUCATION_OPTIONS.map((item) => [item.value, item.label])
)

/** 性别，与 system_user_sex 字典对齐 */
export const SEX_OPTIONS = [
  { value: 1, label: '男' },
  { value: 2, label: '女' }
]

export const SEX_LABELS: Record<number, string> = { 1: '男', 2: '女' }

/** 社保对应月份类型，与 HrmSalarySocialSecurityMonthTypeEnum 对齐 */
export const SOCIAL_SECURITY_MONTH_TYPE_OPTIONS = [
  { value: 0, label: '上月' },
  { value: 1, label: '当月' },
  { value: 2, label: '次月' }
]

/** 计税规则类型选项 */
export const TAX_TYPE_OPTIONS = [
  { value: 1, label: '综合所得月度预扣' },
  { value: 2, label: '全年一次性奖金' },
  { value: 3, label: '劳务报酬' }
]

/** 薪资项类型，与 HrmSalaryOptionTypeEnum 对齐 */
export const SALARY_OPTION_TYPE_OPTIONS = [
  { value: 1, label: '收入项' },
  { value: 2, label: '社保项' },
  { value: 3, label: '公积金项' },
  { value: 4, label: '专项附加扣除' },
  { value: 5, label: '代扣项' }
]

/** 招聘职位状态，与 HrmRecruitPostStatusEnum 对齐 */
export const RECRUIT_POST_STATUS = { STOPPED: 0, RECRUITING: 1 } as const

export const RECRUIT_POST_STATUS_LABELS: Record<number, string> = { 0: '停止招聘', 1: '招聘中' }

export const RECRUIT_POST_STATUS_COLORS: Record<number, string> = { 0: 'default', 1: 'success' }

/** 招聘职位薪资单位，-1 表示面议，1/2 为月/年（与 hrm_recruit_salary_unit 字典对齐） */
export const SALARY_UNIT_LABELS: Record<number, string> = { [-1]: '面议', [1]: '元/月', [2]: '元/年' }

/** 招聘候选人状态，与 HrmRecruitCandidateStatusEnum 对齐 */
export const RECRUIT_CANDIDATE_STATUS = {
  NEW: 1, PRIMARY_PASS: 2, INTERVIEW: 3, INTERVIEW_PASS: 4,
  OFFER_SENT: 5, PENDING_ENTRY: 6, ELIMINATED: 7, JOINED: 8
} as const

export const RECRUIT_CANDIDATE_STATUS_LABELS: Record<number, string> = {
  1: '新候选人', 2: '初选通过', 3: '安排面试', 4: '面试通过',
  5: '已发 Offer', 6: '待入职', 7: '已淘汰', 8: '已入职'
}

export const RECRUIT_CANDIDATE_STATUS_COLORS: Record<number, string> = {
  1: 'default', 2: 'blue', 3: 'processing', 4: 'cyan',
  5: 'warning', 6: 'purple', 7: 'error', 8: 'success'
}

/** 候选人可前进的下一步状态（每状态只能前进到下一个，淘汰除外） */
export const RECRUIT_CANDIDATE_NEXT: Record<number, number> = {
  1: 2, 2: 3, 3: 4, 4: 5, 5: 6, 6: 8
}

/** 员工异动类型，与 HrmEmployeeChangeTypeEnum 对齐 */
export const CHANGE_TYPE = {
  REGULAR: 4,
  TRANSFER: 5,
  PROMOTION: 6,
  DEMOTION: 7,
  FULL_TIME: 8,
  REHIRE: 9
} as const

export const CHANGE_TYPE_LABELS: Record<number, string> = {
  4: '转正',
  5: '调岗',
  6: '晋升',
  7: '降级',
  8: '转为全职',
  9: '再入职'
}

/** 异动原因，与 HrmEmployeeChangeReasonEnum 对齐 */
export const CHANGE_REASON_OPTIONS = [
  { value: 1, label: '组织架构调整' },
  { value: 2, label: '个人申请' },
  { value: 3, label: '工作安排' },
  { value: 4, label: '违规违纪' },
  { value: 5, label: '绩效不达标' },
  { value: 6, label: '个人身体原因' },
  { value: 7, label: '不适应当前岗位' }
]

export const CHANGE_REASON_LABELS: Record<number, string> = Object.fromEntries(
  CHANGE_REASON_OPTIONS.map((item) => [item.value, item.label])
)

/** 离职类型，与 HrmEmployeeQuitTypeEnum 对齐 */
export const QUIT_TYPE = { VOLUNTARY: 1, INVOLUNTARY: 2, RETIREMENT: 3 } as const

export const QUIT_TYPE_OPTIONS = [
  { value: 1, label: '主动离职' },
  { value: 2, label: '被动离职' },
  { value: 3, label: '退休' }
]

export const QUIT_TYPE_LABELS: Record<number, string> = Object.fromEntries(
  QUIT_TYPE_OPTIONS.map((item) => [item.value, item.label])
)

/** 离职原因，与 HrmEmployeeQuitReasonEnum 对齐；quitType 用于按离职类型级联 */
export const QUIT_REASON_OPTIONS = [
  { value: 1, label: '家庭原因', quitType: 1 },
  { value: 2, label: '身体原因', quitType: 1 },
  { value: 3, label: '薪资原因', quitType: 1 },
  { value: 4, label: '交通不便', quitType: 1 },
  { value: 5, label: '工作压力', quitType: 1 },
  { value: 6, label: '管理问题', quitType: 1 },
  { value: 7, label: '无晋升机会', quitType: 1 },
  { value: 8, label: '职业规划', quitType: 1 },
  { value: 9, label: '合同到期放弃续签', quitType: 1 },
  { value: 10, label: '其他个人原因', quitType: 1 },
  { value: 11, label: '试用期内辞退', quitType: 2 },
  { value: 12, label: '违反公司条例', quitType: 2 },
  { value: 13, label: '组织调整/裁员', quitType: 2 },
  { value: 14, label: '绩效不达标辞退', quitType: 2 },
  { value: 15, label: '合同到期不续签', quitType: 2 },
  { value: 16, label: '其他原因', quitType: 2 }
] as const

export const QUIT_REASON_LABELS: Record<number, string> = Object.fromEntries(
  QUIT_REASON_OPTIONS.map((item) => [item.value, item.label])
)

/** 按离职类型过滤可选原因；退休无细分原因，返回空表示不需要选 */
export function quitReasonOptionsOf(quitType?: number) {
  if (!quitType || quitType === QUIT_TYPE.RETIREMENT) return []
  return QUIT_REASON_OPTIONS.filter((item) => item.quitType === quitType).map((item) => ({
    value: item.value,
    label: item.label
  }))
}

/** 员工可执行的异动动作，取决于入职状态与聘用形式 */
export function employeeActionsOf(employee: { entryStatus?: number; type?: number; status?: number }) {
  const { entryStatus, type, status } = employee
  if (entryStatus === ENTRY_STATUS.PENDING_ENTRY) return ['confirmEntry', 'edit', 'delete'] as const
  if (entryStatus === ENTRY_STATUS.LEFT) return ['rehire', 'edit'] as const
  if (entryStatus === ENTRY_STATUS.PENDING_LEAVE) return ['cancelQuit', 'edit'] as const
  const actions: string[] = ['edit', 'transfer', 'promote', 'demote', 'quit']
  if (status === 2) actions.splice(1, 0, 'regular')
  if (type === EMPLOYEE_TYPE.INFORMAL) actions.splice(1, 0, 'convertToFullTime')
  return actions as readonly string[]
}
