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

export type BackgroundKey = 'theme' | 'aurora' | 'sunset' | 'mint' | 'lavender' | 'peach' | 'ocean' | 'midnight' | 'dusk'

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
  /** 渐变 CSS 值（'theme' 项无此字段，跟随主题） */
  value?: string
  preview: string
  /** 该背景是否为暗色系（供按 preset 明暗过滤） */
  dark?: boolean
}

// ========== Application ==========

export const APP_CONFIG = {
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || '/admin-api',
  DEFAULT_TENANT_ID: String(import.meta.env.VITE_TENANT_ID || '1'),
  ADMIN_EMBED_BASE: import.meta.env.VITE_ADMIN_EMBED_BASE || '/admin-embed/'
} as const

export const STORAGE_KEYS = {
  // 与 Vue Admin 共享同源 localStorage。不要把令牌放进 iframe URL 或 postMessage。
  ACCESS_TOKEN: 'ACCESS_TOKEN',
  REFRESH_TOKEN: 'REFRESH_TOKEN',
  CLIENT_ID: 'CLIENT_ID',
  EXPIRES_TIME: 'EXPIRES_TIME',
  LOGIN_FORM: 'zsjos_login_form',
  IMPERSONATION: 'zsjos.impersonation.session',
  THEME: 'crm-theme'
} as const

export const APP_ROUTES = {
  USER_PROFILE: '/user/profile',
  LEAD_SUBMISSION: '/zsjos/leads/submit',
  LEAD_MANAGEMENT: '/zsjos/leads/manage',
  LEAD_SELF_SOURCED: '/zsjos/leads/self-sourced',
  LEAD_COMPLAINTS: '/zsjos/leads/complaints',
  SUBMITTED_LEADS: '/zsjos/leads/submitted',
  OWNED_LEADS: '/zsjos/leads/owned',
  LEAD_ASSIGNMENT: '/zsjos/leads/assignment-relations',
  LEAD_DUPLICATE_REVIEW: '/zsjos/leads/duplicate-review',
  LEAD_CLAIM_POOL: '/zsjos/claim-pool',
  LEAD_AGING_POOL: '/zsjos/lead-aging-pool',
  LEAD_RULE: '/zsjos/lead-rule',
  LEAD_FILTER: '/zsjos/lead-filter',
  LEAD_FOLLOW_UP_RULE: '/zsjos/lead-follow-up-rule',
  PRODUCT_CONFIG: '/zsjos/product',
  WORK_PLAN_CONFIG: '/zsjos/work-plan-config',
  REGISTRATION_POOL: '/zsjos/registration-pool',
  REGISTRATION_CHECKLIST_CONFIG: '/zsjos/registration-checklist-config',
  MY_STUDENTS: '/zsjos/my-students',
  STUDENT_CONTACT_CONFIG: '/zsjos/student-contact-config',
  STUDENT_CONTACT_EXCEPTIONS: '/zsjos/student-contact-exceptions',
  SUBORDINATE_SALES: '/zsjos/subordinate-sales',
  TODAY_TASKS: '/zsjos/tasks/today',
  WORK_PLANS: '/zsjos/work-plans',
  BPM_TODO: '/bpm/task/todo',
  LEAD_APPEALS: '/zsjos/appeals',
  MY_SALES_ORDERS: '/zsjos/sales-orders/my',
  SALES_ORDER_APPROVALS: '/zsjos/sales-order-approvals',
  SALES_ORDER_SUPERVISOR_CONFIRMATIONS: '/zsjos/sales-order-supervisor-confirmations',
  EXTERNAL_REPURCHASE: '/zsjos/orders/external-repurchase',
  EXPORT_TASKS: '/zsjos/export-task',
  PERSONNEL: '/zsjos/personnel',
  PARTNER: '/zsjos/partner',
  IMPERSONATION: '/zsjos/impersonation',
  BUSINESS_AUDIT: '/zsjos/business-audit',
  CASHBACK: '/zsjos/cashback',
  WITHDRAWAL: '/zsjos/withdrawal',
  USER_RELATION: '/system/user-relation',
  MAINTENANCE: '/system/maintenance',
  NOTIFY_RULE: '/messages/notify/notify-rule',
  ALL_MESSAGES: '/messages/all',
  UNREAD_MESSAGES: '/messages/unread'
  ,MEDIA_PRODUCTION_TICKETS: '/zsjos/production-tickets'
  ,MEDIA_STUDENT_OPS: '/zsjos/student-ops'
  ,MEDIA_REVIEWS: '/zsjos/reviews'
  ,MEDIA_STUDENTS: '/zsjos/media-students'
} as const

export const RENDERABLE_APP_ROUTES = new Set([
  APP_ROUTES.LEAD_SUBMISSION,
  APP_ROUTES.LEAD_MANAGEMENT,
  APP_ROUTES.LEAD_SELF_SOURCED,
  APP_ROUTES.LEAD_COMPLAINTS,
  APP_ROUTES.SUBMITTED_LEADS,
  APP_ROUTES.OWNED_LEADS,
  APP_ROUTES.LEAD_ASSIGNMENT,
  APP_ROUTES.LEAD_DUPLICATE_REVIEW,
  APP_ROUTES.LEAD_CLAIM_POOL,
  APP_ROUTES.LEAD_AGING_POOL,
  APP_ROUTES.LEAD_RULE,
  APP_ROUTES.LEAD_FILTER,
  APP_ROUTES.LEAD_FOLLOW_UP_RULE,
  APP_ROUTES.PRODUCT_CONFIG,
  APP_ROUTES.WORK_PLAN_CONFIG,
  APP_ROUTES.REGISTRATION_POOL,
  APP_ROUTES.REGISTRATION_CHECKLIST_CONFIG,
  APP_ROUTES.MY_STUDENTS,
  APP_ROUTES.STUDENT_CONTACT_CONFIG,
  APP_ROUTES.STUDENT_CONTACT_EXCEPTIONS,
  APP_ROUTES.TODAY_TASKS,
  APP_ROUTES.WORK_PLANS,
  APP_ROUTES.SUBORDINATE_SALES,
  APP_ROUTES.LEAD_APPEALS,
  APP_ROUTES.MY_SALES_ORDERS,
  APP_ROUTES.SALES_ORDER_APPROVALS,
  APP_ROUTES.EXTERNAL_REPURCHASE,
  APP_ROUTES.EXPORT_TASKS,
  APP_ROUTES.PERSONNEL,
  APP_ROUTES.PARTNER,
  APP_ROUTES.IMPERSONATION,
  APP_ROUTES.BUSINESS_AUDIT,
  APP_ROUTES.CASHBACK,
  APP_ROUTES.WITHDRAWAL,
  APP_ROUTES.USER_RELATION,
  APP_ROUTES.MAINTENANCE,
  APP_ROUTES.NOTIFY_RULE,
  APP_ROUTES.ALL_MESSAGES,
  APP_ROUTES.UNREAD_MESSAGES
  ,APP_ROUTES.MEDIA_PRODUCTION_TICKETS
  ,APP_ROUTES.MEDIA_STUDENT_OPS
  ,APP_ROUTES.MEDIA_REVIEWS
  ,APP_ROUTES.MEDIA_STUDENTS
])

// ========== Dictionaries ==========

export const DICT_TYPE = {
  COMMON_STATUS: 'common_status',
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
  ,STUDENT_CONTACT_UNSUCCESSFUL_REASON: 'zsjos_student_contact_unsuccessful_reason'
  ,STUDENT_CONTACT_EXTENSION_REASON: 'zsjos_student_contact_extension_reason'
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

/** 玻璃背景模糊滑块范围（px）。ThemeContext 的钳制与 ThemeSwitcher 的 Slider 共用，避免两处漂移。 */
export const GLASS_BLUR_MIN = 0
export const GLASS_BLUR_MAX = 40

export const DEFAULT_THEME = {
  preset: 'default-light' as ThemePreset,
  colorPrimary: '#1677ff',
  /** @deprecated 由 density 取代，仅保留用于旧 localStorage 数据迁移 */
  compact: false,
  background: 'theme' as BackgroundKey,
  /** 玻璃不透明度 0–100，仅自定义背景时生效 */
  glassOpacity: 60,
  /** 玻璃背景模糊（backdrop-filter 模糊半径）0–40px，仅自定义背景时生效；0 表示完全关闭磨砂 */
  glassBlur: 20,
  density: 'default' as Density,
  fontScale: 'default' as FontScale,
  layoutMode: 'side' as LayoutMode,
  borderRadius: 'small' as BorderRadiusPreset,
  headerFixed: true,
  animation: true,
  watermark: false,
  tabs: true,
  tabStyle: 'card' as TabStyle
} as const

// ========== Layout ==========

export type BorderRadiusPreset = 'sharp' | 'small' | 'round' | 'full'

export const BORDER_RADIUS_OPTIONS: Array<{ label: string; value: BorderRadiusPreset }> = [
  { label: '方正', value: 'sharp' },
  { label: '小圆', value: 'small' },
  { label: '圆润', value: 'round' },
  { label: '全圆', value: 'full' }
]

/**
 * 圆角四档。**必须与 `styles/tokens.css` 的 `--crm-radius-*` 逐档一致** ——
 * 这里的值注入 antd 的 borderRadius token（管 antd 组件），tokens.css 的值管自有 CSS，
 * 两边不一致就会出现「antd 卡片圆角 8px、自绘面板 10px」的割裂。
 * small 档曾是 4/6/8 而 CSS 是 6/8/10，由 styles.guard.test.ts 的双源比对守卫防止再次漂移。
 */
export const BORDER_RADIUS_VALUES: Record<BorderRadiusPreset, { sm: number; md: number; lg: number }> = {
  sharp: { sm: 2, md: 3, lg: 4 },
  small: { sm: 6, md: 8, lg: 10 },
  round: { sm: 8, md: 10, lg: 12 },
  full: { sm: 12, md: 14, lg: 16 }
}

// ========== Tab Style ==========

export type TabStyle = 'card' | 'line' | 'pill' | 'flat'

export const TAB_STYLE_OPTIONS: Array<{ label: string; value: TabStyle }> = [
  { label: '卡片', value: 'card' },
  { label: '线条', value: 'line' },
  { label: '胶囊', value: 'pill' },
  { label: '平铺', value: 'flat' }
]

// ========== Density ==========

/**
 * 界面密度与字号。与配色方案无关，故对全部 13 套 preset 生效
 * （不受 ThemeMeta.customizable 限制）。
 */
export type Density = 'loose' | 'default' | 'compact'
export type FontScale = 'small' | 'default' | 'large'

export const DENSITIES: readonly Density[] = ['loose', 'default', 'compact']
export const FONT_SCALES: readonly FontScale[] = ['small', 'default', 'large']

export const DENSITY_OPTIONS: Array<{ label: string; value: Density }> = [
  { label: '宽松', value: 'loose' },
  { label: '默认', value: 'default' },
  { label: '紧凑', value: 'compact' }
]

export const FONT_SCALE_OPTIONS: Array<{ label: string; value: FontScale }> = [
  { label: '小', value: 'small' },
  { label: '标准', value: 'default' },
  { label: '大', value: 'large' }
]

/**
 * 同步注入 antd 的 fontSize token，使组件字号与 CSS 变量一致。
 *
 * default 取 13 而非 antd 默认的 14：这是密集型业务后台（表格、主从列表、
 * 详情字段为主），14px 在信息密度高的页面里偏大。三档取值须与
 * `styles/tokens.css` 的 `--crm-font-base` 逐档一致（有守卫）。
 */
export const FONT_SCALE_SIZE: Record<FontScale, number> = {
  small: 12,
  default: 13,
  large: 14
}

/**
 * 密度档的尺寸真值，供 antd 组件 token 注入（见 scaleTokens.ts）。
 *
 * 为什么要在 JS 侧再存一份：antd 的 bodyPadding 一类 token 只接受 number，
 * 读不到 `--crm-card-pad` 这样的 CSS 变量。若不注入，antd <Card> 会一直用
 * 自己的 24px / 12px 默认值，与自绘卡片的 14px 并存 —— 同一屏三种卡片内边距，
 * 且切密度档时只有自绘那套会动。
 *
 * **取值须与 styles/tokens.css 的对应变量逐档一致**，由 scaleTokens.test.ts 守卫。
 */
export const DENSITY_SCALE: Record<Density, { cardPad: number; cardPadSM: number; pagePad: number; panePad: number }> = {
  loose: { cardPad: 18, cardPadSM: 14, pagePad: 16, panePad: 20 },
  default: { cardPad: 14, cardPadSM: 10, pagePad: 12, panePad: 16 },
  compact: { cardPad: 10, cardPadSM: 8, pagePad: 8, panePad: 12 }
}

/**
 * 表格单元格内边距，随字号档缩放。
 *
 * antd v5 的 Table 没有独立字号 token，单元格字号继承全局 fontSize，
 * 但 cellPaddingBlock / cellPaddingInline 是写死的常量、不随 fontSize 派生。
 * 结果切「字号大」时字变大而行高不动，字被挤胀 —— 故在此按档位显式给出。
 *
 * 随 FONT_SCALE_SIZE 整体下调：default 13px 对应 block 7 / inline 14。
 */
export const FONT_SCALE_TABLE: Record<FontScale, { cellBlock: number; cellInline: number }> = {
  small: { cellBlock: 5, cellInline: 10 },
  default: { cellBlock: 7, cellInline: 14 },
  large: { cellBlock: 9, cellInline: 18 }
}

// ========== Layout Mode ==========

/**
 * 布局模式：
 * - side: 侧边双列（一级窄栏 + 二级栏），默认
 * - top:  顶部一级 + 侧边二级（内容区多出 144px 宽度）
 * - top-only: 纯顶栏（一二级菜单全部横排在顶部）
 * - single-sider: 左单列（一级展开/收起包含二级子项）
 * - mini-float: 左 mini 图标 + hover 浮层弹出二级
 * 菜单层级来自服务端；不同布局按自身交互呈现递归目录、下拉或浮层。
 */
export type LayoutMode = 'side' | 'top' | 'top-only' | 'single-sider' | 'mini-float'

export const LAYOUT_MODES: readonly LayoutMode[] = ['side', 'top', 'top-only', 'single-sider', 'mini-float']

/**
 * mini-float 图标栏宽度。
 * 须与 Menu 的 collapsedWidth token 一起设定，避免图标因 antd 默认宽度过大而被裁切。
 */
export const MINI_RAIL_W = 56

/**
 * 布局尺寸真值。antd Sider 的 width / collapsedWidth prop 只接受 number，
 * 读不到 CSS 变量，故 JS 侧持有真值，`styles/tokens.css` 的 `--crm-sider-*`
 * 为 CSS 侧镜像（calc 需要），两边由 styles.guard.test.ts 比对。
 */
export const LAYOUT_SIZES = {
  PRIMARY_SIDER_W: 72,
  PRIMARY_SIDER_COLLAPSED: 56,
  SECONDARY_SIDER_W: 180,
  SECONDARY_SIDER_COLLAPSED: 48,
  /** 左单列模式：一栏同时容纳一二级，故比 primary 宽 */
  SINGLE_SIDER_W: 220,
  AI_SIDER_W: 320,
  MINI_RAIL_W
} as const

export const LAYOUT_MODE_OPTIONS: Array<{ label: string; value: LayoutMode }> = [
  { label: '左双列', value: 'side' },
  { label: '顶+左', value: 'top' },
  { label: '纯顶栏', value: 'top-only' },
  { label: '左单列', value: 'single-sider' },
  { label: 'Mini浮层', value: 'mini-float' }
]

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
  // 跟随主题（无渐变，不分明暗）
  { key: 'theme', label: '跟随主题', preview: 'transparent' },
  // 浅色组（饱和度适中，让磨砂玻璃层有可感知的色调）
  { key: 'aurora', label: '极光', dark: false, value: 'linear-gradient(135deg, #c8daf0 0%, #dde4f4 50%, #ebd8ed 100%)', preview: 'linear-gradient(135deg, #c8daf0, #ebd8ed)' },
  { key: 'sunset', label: '日落', dark: false, value: 'linear-gradient(135deg, #f0d4cc 0%, #f2ddd5 50%, #ebcfdb 100%)', preview: 'linear-gradient(135deg, #f0d4cc, #ebcfdb)' },
  { key: 'mint', label: '薄荷', dark: false, value: 'linear-gradient(135deg, #c8e6d0 0%, #d6ebd8 50%, #dcefd4 100%)', preview: 'linear-gradient(135deg, #c8e6d0, #dcefd4)' },
  { key: 'lavender', label: '薰衣草', dark: false, value: 'linear-gradient(135deg, #d8d0f0 0%, #d6ddf4 50%, #dde0f6 100%)', preview: 'linear-gradient(135deg, #d8d0f0, #dde0f6)' },
  { key: 'peach', label: '蜜桃', dark: false, value: 'linear-gradient(135deg, #f2ddc8 0%, #f0d8c2 50%, #edcfbe 100%)', preview: 'linear-gradient(135deg, #f2ddc8, #edcfbe)' },
  // 暗色雾面组（供 default-dark / geek）
  { key: 'ocean', label: '深海', dark: true, value: 'linear-gradient(135deg, #1c2530 0%, #202a35 50%, #252b38 100%)', preview: 'linear-gradient(135deg, #1c2530, #252b38)' },
  { key: 'midnight', label: '暗夜', dark: true, value: 'linear-gradient(135deg, #14181d 0%, #171c22 50%, #1a1f26 100%)', preview: 'linear-gradient(135deg, #14181d, #1a1f26)' },
  { key: 'dusk', label: '暮山', dark: true, value: 'linear-gradient(135deg, #22202a 0%, #26232f 50%, #2a2733 100%)', preview: 'linear-gradient(135deg, #22202a, #2a2733)' }
]
