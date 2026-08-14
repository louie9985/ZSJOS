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
  LEAD_CLAIM_POOL: '/zsjos/claim-pool',
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
  converted: '已判有效',
  closed: '已关闭'
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
  converted: '已判有效',
  closed: '已关闭'
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
  tabs: false,
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

export const BORDER_RADIUS_VALUES: Record<BorderRadiusPreset, { sm: number; md: number; lg: number }> = {
  sharp: { sm: 2, md: 3, lg: 4 },
  small: { sm: 4, md: 6, lg: 8 },
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

/** 同步注入 antd 的 fontSize token，使组件字号与 CSS 变量一致 */
export const FONT_SCALE_SIZE: Record<FontScale, number> = {
  small: 13,
  default: 14,
  large: 15
}

// ========== Layout Mode ==========

/**
 * 布局模式：
 * - side: 侧边双列（一级窄栏 + 二级栏），默认
 * - top:  顶部一级 + 侧边二级（内容区多出 144px 宽度）
 * - top-only: 纯顶栏（一二级菜单全部横排在顶部）
 * - single-sider: 左单列（一级展开/收起包含二级子项）
 * - mini-float: 左 mini 图标 + hover 浮层弹出二级
 */
export type LayoutMode = 'side' | 'top' | 'top-only' | 'single-sider' | 'mini-float'

export const LAYOUT_MODES: readonly LayoutMode[] = ['side', 'top', 'top-only', 'single-sider', 'mini-float']

/**
 * mini-float 图标栏宽度。
 * 须与 Menu 的 collapsedWidth token 一起设定：token 默认 controlHeightLG*2，
 * 与此值不一致时 ul 溢出被裁，图标偏离居中。CSS 侧对应 --crm-sider-1-collapsed。
 */
export const MINI_RAIL_W = 56

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
