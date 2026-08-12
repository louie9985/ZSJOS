export type ThemePreset =
  | 'default-light'
  | 'mui'
  | 'shadcn'
  | 'bootstrap'
  | 'cartoon'
  | 'default-dark'
  | 'illustration'
  | 'glass'
  | 'geek'
  | 'lark'
  | 'blossom'
  | 'v4'
  | 'serene'

export type BackgroundKey = 'theme' | 'aurora' | 'sunset' | 'mint' | 'lavender' | 'peach' | 'ocean' | 'dark'

export interface ThemeMeta {
  key: ThemePreset
  label: string
  swatch: string
  dark: boolean
  customizable?: boolean
}

export interface BackgroundMeta {
  key: BackgroundKey
  label: string
  value?: string
  preview: string
}

// ========== Application ==========

export const APP_CONFIG = {
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || '/admin-api',
  DEFAULT_TENANT_ID: String(import.meta.env.VITE_TENANT_ID || '1')
} as const

export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'zsjos_access_token',
  REFRESH_TOKEN: 'zsjos_refresh_token',
  CLIENT_ID: 'zsjos_client_id',
  EXPIRES_TIME: 'zsjos_expires_time',
  LOGIN_FORM: 'zsjos_login_form',
  THEME: 'crm-theme'
} as const

export const APP_ROUTES = {
  LEAD_SUBMISSION: '/zsjos/leads/submit',
  SUBMITTED_LEADS: '/zsjos/leads/submitted',
  OWNED_LEADS: '/zsjos/leads/owned',
  LEAD_ASSIGNMENT: '/zsjos/leads/assignment-relations',
  LEAD_DUPLICATE_REVIEW: '/zsjos/leads/duplicate-review',
  LEAD_CLAIM_POOL: '/zsjos/claim-pool',
  LEAD_AGING_POOL: '/zsjos/lead-aging-pool',
  SUBORDINATE_SALES: '/zsjos/subordinate-sales',
  TODAY_TASKS: '/zsjos/tasks/today',
  WORK_PLANS: '/zsjos/work-plans',
  BPM_TODO: '/bpm/task/todo',
  QUALIFICATION_EXCEPTIONS: '/zsjos/leads/qualification-exceptions',
  LEAD_APPEALS: '/zsjos/leads/appeals',
  MY_SALES_ORDERS: '/zsjos/sales-orders/my',
  SALES_ORDER_APPROVALS: '/zsjos/sales-order-approvals',
  ALL_MESSAGES: '/messages/all',
  UNREAD_MESSAGES: '/messages/unread'
} as const

export const RENDERABLE_APP_ROUTES = new Set([
  APP_ROUTES.LEAD_SUBMISSION,
  APP_ROUTES.SUBMITTED_LEADS,
  APP_ROUTES.OWNED_LEADS,
  APP_ROUTES.LEAD_ASSIGNMENT,
  APP_ROUTES.LEAD_DUPLICATE_REVIEW,
  APP_ROUTES.LEAD_CLAIM_POOL,
  APP_ROUTES.TODAY_TASKS,
  APP_ROUTES.WORK_PLANS,
  APP_ROUTES.QUALIFICATION_EXCEPTIONS,
  APP_ROUTES.LEAD_APPEALS,
  APP_ROUTES.ALL_MESSAGES,
  APP_ROUTES.UNREAD_MESSAGES
])

// ========== Dictionaries ==========

export const DICT_TYPE = {
  LEAD_SOURCE_CHANNEL: 'zsjos_lead_source_channel',
  LEAD_CATEGORY: 'zsjos_lead_category',
  LEAD_FOLLOW_UP_METHOD: 'zsjos_lead_follow_up_method',
  LEAD_FOLLOW_UP_RESULT: 'zsjos_lead_follow_up_result',
  LEAD_FOLLOW_UP_QUICK_NOTE: 'zsjos_lead_follow_up_quick_note',
  LEAD_INVALID_REASON: 'zsjos_lead_invalid_reason',
  LEAD_INVALID_REMARK_TEMPLATE: 'zsjos_lead_invalid_remark_template',
  LEAD_VALID_REMARK_TEMPLATE: 'zsjos_lead_valid_remark_template'
  ,ORDER_STUDENT_NATURE: 'zsjos_order_student_nature'
  ,ORDER_SERVICE_PERIOD: 'zsjos_order_service_period'
  ,ORDER_STUDENT_SOURCE: 'zsjos_order_student_source'
  ,ORDER_FEE_MODE: 'zsjos_order_fee_mode'
  ,ORDER_PAYMENT_METHOD: 'zsjos_order_payment_method'
} as const

// ========== Lead Management ==========

export const LEAD_ASSIGNMENT_MODE = {
  AUTO: 'auto',
  SPECIFIED: 'specified'
} as const

export const LEAD_ASSIGNMENT_OPTIONS: Array<{
  label: string
  value: typeof LEAD_ASSIGNMENT_MODE[keyof typeof LEAD_ASSIGNMENT_MODE]
}> = [
  { label: '自动分配', value: LEAD_ASSIGNMENT_MODE.AUTO },
  { label: '指定销售', value: LEAD_ASSIGNMENT_MODE.SPECIFIED }
]

export const LEAD_STATUS_LABELS: Record<string, string> = {
  submitted: '待处理',
  suspended: '已挂起',
  valid: '有效',
  invalid: '无效',
  closed: '已关闭',
  won: '已成交'
}

export const LEAD_ASSIGNMENT_STATUS_LABELS: Record<string, string> = {
  unassigned: '未分配',
  pending_acceptance: '待接单',
  owned: '已归属',
  public_pool: '抢单池'
  ,recycle_pending: '回收待处理'
}

export const LEAD_HANDLING_STAGE_LABELS: Record<string, string> = {
  first_follow_pending: '待首跟',
  qualification_pending: '待判定',
  suspended: '已挂起',
  recycle_pending: '回收待处理',
  pending_claim: '待接单',
  pending_acceptance: '待接单',
  unassigned: '未分配',
  valid: '有效',
  invalid: '无效',
  closed: '已关闭',
  won: '已成交'
}

export const LEAD_QUALIFICATION_STATUS_LABELS: Record<string, string> = {
  pending: '待判定', valid: '已判有效', invalid: '已判无效'
}

export const LEAD_FOLLOW_UP_STATUS_LABELS: Record<string, string> = {
  first_follow_pending: '待首跟', following: '跟进中', deal_pending_approval: '成交待审核', won: '已成交'
}

export const LEAD_DISPATCH_MODE_LABELS: Record<string, string> = {
  auto: '自动分配',
  specified: '指定销售'
}

export const PHONE_PATTERN = /^1\d{10}$/

// ========== Theme ==========

export const PRESET_COLORS = [
  { key: 'daybreak', label: '拂晓蓝', color: '#1677ff' },
  { key: 'green', label: '极光绿', color: '#52c41a' },
  { key: 'purple', label: '酱紫', color: '#722ed1' },
  { key: 'orange', label: '日暮橙', color: '#fa8c16' },
  { key: 'red', label: '薄暮红', color: '#f5222d' },
  { key: 'cyan', label: '明青', color: '#13c2c2' }
] as const

export const DEFAULT_THEME = {
  preset: 'default-light' as ThemePreset,
  colorPrimary: '#1677ff',
  compact: false,
  background: 'theme' as BackgroundKey
} as const

export const THEME_METAS: ThemeMeta[] = [
  { key: 'default-light', label: '默认浅色', swatch: '#1677ff', dark: false, customizable: true },
  { key: 'mui', label: 'Material', swatch: '#1976d2', dark: false },
  { key: 'shadcn', label: 'shadcn', swatch: '#18181b', dark: false },
  { key: 'bootstrap', label: 'Bootstrap', swatch: '#337ab7', dark: false },
  { key: 'cartoon', label: '卡通', swatch: '#225555', dark: false },
  { key: 'default-dark', label: '默认深色', swatch: '#1677ff', dark: true, customizable: true },
  { key: 'illustration', label: '插画', swatch: '#52c41a', dark: false },
  { key: 'glass', label: '玻璃', swatch: '#4096ff', dark: false },
  { key: 'geek', label: '极客', swatch: '#39ff14', dark: true },
  { key: 'lark', label: '飞书', swatch: '#00b96b', dark: false },
  { key: 'blossom', label: '樱花', swatch: '#ed4192', dark: false },
  { key: 'v4', label: 'v4 经典', swatch: '#1890ff', dark: false },
  { key: 'serene', label: '静谧', swatch: '#312721', dark: false }
]

export const BACKGROUND_METAS: BackgroundMeta[] = [
  { key: 'theme', label: '跟随主题', preview: 'transparent' },
  { key: 'aurora', label: '极光', value: 'linear-gradient(135deg, #a1c4fd 0%, #c2e9fb 40%, #fbc2eb 100%)', preview: 'linear-gradient(135deg, #a1c4fd, #fbc2eb)' },
  { key: 'sunset', label: '日落', value: 'linear-gradient(135deg, #ff9a9e 0%, #fad0c4 50%, #fbc2eb 100%)', preview: 'linear-gradient(135deg, #ff9a9e, #fbc2eb)' },
  { key: 'mint', label: '薄荷', value: 'linear-gradient(135deg, #d4fc79 0%, #96e6a1 100%)', preview: 'linear-gradient(135deg, #d4fc79, #96e6a1)' },
  { key: 'lavender', label: '薰衣草', value: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)', preview: 'linear-gradient(135deg, #e0c3fc, #8ec5fc)' },
  { key: 'peach', label: '蜜桃', value: 'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)', preview: 'linear-gradient(135deg, #ffecd2, #fcb69f)' },
  { key: 'ocean', label: '深海', value: 'linear-gradient(135deg, #2b5876 0%, #4e4376 100%)', preview: 'linear-gradient(135deg, #2b5876, #4e4376)' },
  { key: 'dark', label: '暗夜', value: 'linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%)', preview: 'linear-gradient(135deg, #0f2027, #2c5364)' }
]
