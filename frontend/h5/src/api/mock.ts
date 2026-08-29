import { ref } from 'vue'

export interface MockRequestConfig {
  method?: string
  baseURL?: string
  url?: string
  params?: Record<string, unknown>
  data?: unknown
}

export interface FeatureMockResult {
  data: unknown
  endpoint: string
}

const MISSING_MESSAGES = [
  '请求地址不存在', '接口不存在', '接口未实现', '功能不存在', '暂未提供',
  'endpoint not found', 'not implemented', 'feature unavailable'
]

export function isMissingImplementation(status?: number, message?: unknown): boolean {
  if (status === 401 || status === 403 || status === 500) return false
  if (status === 404 || status === 405 || status === 501) return true
  if (typeof message !== 'string') return false
  const normalized = message.toLowerCase()
  return MISSING_MESSAGES.some((item) => normalized.includes(item))
}

function endpointOf(config: MockRequestConfig): string {
  const raw = `${config.baseURL || ''}${config.url || ''}`.split('?')[0]
  return raw.replace(/^https?:\/\/[^/]+/i, '').replace(/^\/(?:part-api|app-api)/, '') || '/'
}

function pageParams(config: MockRequestConfig) {
  return { pageNo: Number(config.params?.pageNo || 1), pageSize: Number(config.params?.pageSize || 10) }
}

function requestBody(config: MockRequestConfig): Record<string, unknown> {
  if (!config.data) return {}
  if (typeof config.data === 'string') {
    try { return JSON.parse(config.data) as Record<string, unknown> } catch { return {} }
  }
  return config.data instanceof FormData ? {} : config.data as Record<string, unknown>
}

const usedMockEndpoints = new Set<string>()
const mockUsageVersion = ref(0)

export function wasMockedEndpoint(prefix: string): boolean {
  void mockUsageVersion.value
  return [...usedMockEndpoints].some(endpoint => endpoint.startsWith(prefix))
}

export function wasMockedExactEndpoint(endpoint: string): boolean {
  void mockUsageVersion.value
  return usedMockEndpoints.has(endpoint)
}

export function clearMockedEndpoint(endpoint: string): void {
  if (usedMockEndpoints.delete(endpoint)) mockUsageVersion.value += 1
}

const mockCards = [
  { id: 30001, accountName: '演示用户', maskedCardNumber: '**** **** **** 1234', bankName: '中国银行', branchName: '合肥市演示支行', defaultCard: true }
]
const BANK_CARD_OVERRIDES_KEY = 'zsjos-h5-dev-bank-card-overrides'

function readBankCardOverrides(): Record<string, Record<string, string>> {
  if (typeof sessionStorage === 'undefined') return {}
  try { return JSON.parse(sessionStorage.getItem(BANK_CARD_OVERRIDES_KEY) || '{}') as Record<string, Record<string, string>> } catch { return {} }
}

export function applyDevBankCardOverrides<T extends { id: number }>(cards: T[]): T[] {
  if (!import.meta.env.DEV) return cards
  const overrides = readBankCardOverrides()
  return cards.map(card => ({ ...card, ...(overrides[String(card.id)] || {}) }))
}

const mockFeedbackOptions = {
  categories: [
    { value: 'display', label: '页面显示问题' },
    { value: 'data', label: '数据错误' },
    { value: 'permission', label: '权限问题' },
    { value: 'notification', label: '通知问题' },
    { value: 'operation', label: '操作失败' },
    { value: 'other', label: '其他问题' }
  ],
  severities: [
    { value: 'normal', label: '一般问题' },
    { value: 'affects_work', label: '影响工作' },
    { value: 'blocking', label: '阻塞工作' }
  ]
}

const mockFeedbacks: Array<Record<string, unknown>> = [{
  id: 81001, feedbackNo: 'FB-MOCK-001', category: 'display', categoryText: '页面显示问题',
  severity: 'normal', severityText: '一般问题', title: '演示反馈记录', description: '这是开发环境的演示反馈数据。',
  reproduceSteps: '进入页面后查看展示效果。', status: 'need_more_info', statusText: '待补充', publicReply: '请补充问题发生页面。',
  attachments: [], events: [{ eventId: 1, eventType: 'submitted', eventTypeText: '已提交', content: '等待处理', createdAt: '2026-08-24T09:00:00' }],
  createdAt: '2026-08-24T09:00:00', updatedAt: '2026-08-24T10:00:00'
}]
const mockFeedbackAttachments = new Map<number, Record<string, unknown>>()

function feedbackAttachmentsOf(value: unknown): Array<Record<string, unknown>> {
  if (!Array.isArray(value)) return []
  return value.reduce<Array<Record<string, unknown>>>((result, id) => {
    const attachment = mockFeedbackAttachments.get(Number(id))
    if (attachment) result.push(attachment)
    return result
  }, [])
}

function lead(id = 10001) {
  return {
    id, leadNo: `L2026082400${id % 100}`, submittedName: 'Mock 客户', submittedMobile: '13800000000',
    sourceChannel: 'other', sourceLabel: '其他', leadCategory: 'adult_education', leadCategoryLabelSnapshot: '成人教育', status: 'submitted', assignmentStatus: 'unassigned',
    simpleStatus: 'first_follow_pending', mainProductRef: 'MOCK-SPU-001', appealStatus: 'pending', orderReviewStatus: 'pending',
    submittedAt: '2026-08-24T10:00:00', provinceCode: '340000', provinceName: '安徽省', cityCode: '340100',
    cityName: '合肥市', remark: '本地 Mock 展示数据',
    intendedProducts: [{ spuRef: 'MOCK-SPU-001', spuName: '职业技能课程', skuRef: 'MOCK-SKU-001', skuName: '标准班', primary: true }],
    attachments: [],
    availableActions: [
      { code: 'SUBMITTER_SUPPLEMENT', enabled: true }, { code: 'SUBMITTER_URGE', enabled: true },
      { code: 'SUBMITTER_COMPLAINT', enabled: true }, { code: 'CREATE_APPEAL', enabled: true }
    ]
  }
}

function leadActivity(id: number) {
  return {
    currentStatus: {
      code: 'following', text: '处理中', description: '平台已开始处理，请关注后续进度',
      tone: 'primary', updatedAt: '2026-08-24T14:00:00'
    },
    followUps: [{
      id: 1, leadId: id, occurredAt: '2026-08-24T14:00:00', firstInAssignment: true,
      result: 'connected', resultLabel: '已联系客户', method: 'phone', methodLabel: '电话',
      categoryBefore: 'adult_education', categoryBeforeLabel: '成人教育', categoryAfter: 'adult_education',
      categoryAfterLabel: '成人教育', remark: '客户希望进一步了解课程安排', nextFollowUpAt: '2026-08-26T10:00:00', images: []
    }],
    timeline: [
      { id: 'lead-submitted', type: 'submitted', title: '客资已提交', description: '提交成功，已进入平台处理流程', occurredAt: '2026-08-24T10:00:00', tone: 'success', current: false },
      { id: 'lead-processing', type: 'processing', title: '平台开始处理', description: '客资已进入处理阶段', occurredAt: '2026-08-24T10:15:00', tone: 'success', current: false },
      { id: 'first-follow-up', type: 'followed', title: '已完成首次跟进', description: '平台已完成首次联系，正在持续跟进', occurredAt: '2026-08-24T14:00:00', tone: 'primary', current: true }
    ],
    cashbackItems: [{ id: 1, typeText: '有效客资奖励', statusText: '待结算', amount: 10, availableAt: '2026-09-01T00:00:00' }],
    complaints: [{ id: 1, recordNo: 'CP-MOCK-001', status: 'handled', statusText: '已反馈', content: '演示投诉内容', result: '平台已受理并跟进', createdAt: '2026-08-24T16:00:00', attachments: [] }],
    orders: [{ id: 1, orderNo: 'SO-MOCK-001', status: 'pending', statusText: '待审核', purchaseTypeText: '首次购买', totalAmount: 2980, createdAt: '2026-08-24T17:00:00' }]
  }
}

const mockLeaderboardConfig = {
  enabled: true,
  enabledTypes: ['estimated_income', 'withdrawn_amount', 'lead_count', 'valid_lead_count'],
  defaultType: 'estimated_income',
  defaultPeriod: 'month',
  pageSize: 10,
  maskName: true,
  typeOptions: [
    { key: 'estimated_income', label: '预计收入', valueLabel: '预计收入', valueUnit: 'money', ruleText: '按周期内预计获得的收益金额排序。' },
    { key: 'withdrawn_amount', label: '累计提现', valueLabel: '提现金额', valueUnit: 'money', ruleText: '按周期内已完成提现的金额排序。' },
    { key: 'lead_count', label: '客资数', valueLabel: '客资数量', valueUnit: 'count', ruleText: '按周期内成功提交的客资数量排序。' },
    { key: 'valid_lead_count', label: '有效客资', valueLabel: '有效客资数', valueUnit: 'count', ruleText: '按周期内审核有效的客资数量排序。' }
  ]
}

const mockHomeStatistics = {
  today: { leadCount: 3, withdrawnAmount: 0, validLeadCount: 2, convertedLeadCount: 1 },
  week: { leadCount: 18, withdrawnAmount: 240, validLeadCount: 11, convertedLeadCount: 4 },
  month: { leadCount: 64, withdrawnAmount: 860, validLeadCount: 39, convertedLeadCount: 14 },
  year: { leadCount: 520, withdrawnAmount: 7680, validLeadCount: 318, convertedLeadCount: 106 },
  total: { leadCount: 680, withdrawnAmount: 10240, validLeadCount: 412, convertedLeadCount: 148 }
} as const

function homeStatisticsData(config: MockRequestConfig) {
  const period = String(config.params?.period || 'total') as keyof typeof mockHomeStatistics
  const resolvedPeriod = period in mockHomeStatistics ? period : 'total'
  return { period: resolvedPeriod, ...mockHomeStatistics[resolvedPeriod] }
}

const mockWithdrawalCounts = { today: 0, week: 4, month: 10, year: 48, total: 64 } as const

function localDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function mockPeriodDate(period: keyof typeof mockHomeStatistics, index: number, hour: number): string {
  const date = new Date()
  const dayOfWeek = (date.getDay() + 6) % 7
  const yearStart = new Date(date.getFullYear(), 0, 1)
  const dayOfYear = Math.floor((date.getTime() - yearStart.getTime()) / 86400000)
  const availableDays = period === 'today' ? 1
    : period === 'week' ? dayOfWeek + 1
      : period === 'month' ? date.getDate()
        : period === 'year' ? dayOfYear + 1 : 720
  date.setDate(date.getDate() - (index % Math.max(1, availableDays)))
  date.setHours(hour, (index * 7) % 60, 0, 0)
  return localDateTime(date)
}

function addHours(value: string, hours: number): string {
  const date = new Date(value)
  date.setHours(date.getHours() + hours)
  return localDateTime(date)
}

function mockLeadDetail(period: keyof typeof mockHomeStatistics, metric: string, index: number) {
  const status = metric === 'converted_lead_count' ? 'won'
    : metric === 'valid_lead_count' ? (index % 4 === 0 ? 'won' : 'valid')
      : ['submitted', 'closed', 'valid', 'won', 'invalid'][index % 5]
  const submittedAt = mockPeriodDate(period, index, 9 + (index % 7))
  const timeline = [
    { id: `submitted-${index}`, title: '客资已提交', description: '演示客资已提交到平台', occurredAt: submittedAt }
  ]
  if (status !== 'submitted') {
    timeline.push({ id: `followed-${index}`, title: '销售已跟进', description: '销售已完成首次联系', occurredAt: addHours(submittedAt, 1) })
  }
  if (status === 'valid' || status === 'won') {
    timeline.push({ id: `valid-${index}`, title: '判定为有效客资', description: '平台已完成有效性判定', occurredAt: addHours(submittedAt, 2) })
  }
  if (status === 'won') {
    timeline.push({ id: `won-${index}`, title: '客户已成交', description: '关联订单已确认成交', occurredAt: addHours(submittedAt, 4) })
  }
  if (status === 'invalid') {
    timeline.push({ id: `invalid-${index}`, title: '判定为无效客资', description: '演示数据未包含真实无效原因', occurredAt: addHours(submittedAt, 2) })
  }
  const sequence = String(index + 1).padStart(4, '0')
  return {
    kind: 'lead', id: -(100000 + index), mock: true,
    leadNo: `KZ-MOCK-${period.toUpperCase()}-${sequence}`, submittedName: `演示客户${String((index % 99) + 1).padStart(2, '0')}`,
    status, courseName: `演示课程 ${(index % 4) + 1}`, submittedAt, sourceLabel: '演示渠道',
    mobileMasked: '138****0000', location: '演示地区', timeline
  }
}

function mockWithdrawalDetail(period: keyof typeof mockHomeStatistics, index: number, total: number, totalAmount: number) {
  const totalCents = Math.round(totalAmount * 100)
  const baseCents = total ? Math.floor(totalCents / total) : 0
  const amount = (baseCents + (index < totalCents % Math.max(1, total) ? 1 : 0)) / 100
  const submittedAt = mockPeriodDate(period, index, 10 + (index % 5))
  const sequence = String(index + 1).padStart(4, '0')
  return {
    kind: 'withdrawal', id: -(200000 + index), mock: true,
    withdrawalNo: `TX-MOCK-${period.toUpperCase()}-${sequence}`, status: 'paid',
    applicationAmount: amount, approvedAmount: amount, submittedAt, paidAt: addHours(submittedAt, 3),
    accountNameSnapshot: '演示用户', bankNameSnapshot: '演示银行', maskedCardNumber: '**** **** **** 1234'
  }
}

function homeStatisticsDetailData(config: MockRequestConfig) {
  const requestedPeriod = String(config.params?.period || 'total') as keyof typeof mockHomeStatistics
  const period = requestedPeriod in mockHomeStatistics ? requestedPeriod : 'total'
  const requestedMetric = String(config.params?.metric || 'lead_count')
  const metric = ['lead_count', 'withdrawn_amount', 'valid_lead_count', 'converted_lead_count'].includes(requestedMetric)
    ? requestedMetric : 'lead_count'
  const { pageNo, pageSize } = pageParams(config)
  const summary = mockHomeStatistics[period]
  const total = metric === 'withdrawn_amount' ? mockWithdrawalCounts[period]
    : metric === 'valid_lead_count' ? summary.validLeadCount
      : metric === 'converted_lead_count' ? summary.convertedLeadCount : summary.leadCount
  const totalAmount = metric === 'withdrawn_amount' ? summary.withdrawnAmount : undefined
  const start = Math.max(0, (pageNo - 1) * pageSize)
  const end = Math.min(total, start + pageSize)
  const list = Array.from({ length: Math.max(0, end - start) }, (_, offset) => {
    const index = start + offset
    return metric === 'withdrawn_amount'
      ? mockWithdrawalDetail(period, index, total, totalAmount || 0)
      : mockLeadDetail(period, metric, index)
  })
  return { period, metric, list, total, ...(totalAmount == null ? {} : { totalAmount }) }
}

const mockLeaderboardRows = [
  { partnerId: 91001, displayName: '张*', rank: 1, estimated_income: 2860, withdrawn_amount: 2100, lead_count: 58, valid_lead_count: 41 },
  { partnerId: 91002, displayName: '王*', rank: 2, estimated_income: 2240, withdrawn_amount: 1680, lead_count: 47, valid_lead_count: 35 },
  { partnerId: 91003, displayName: '周*', rank: 2, estimated_income: 2240, withdrawn_amount: 1680, lead_count: 47, valid_lead_count: 35 },
  { partnerId: 91004, displayName: '赵*', rank: 4, estimated_income: 1760, withdrawn_amount: 1320, lead_count: 39, valid_lead_count: 28 },
  { partnerId: 91005, displayName: '孙*', rank: 5, estimated_income: 1320, withdrawn_amount: 980, lead_count: 31, valid_lead_count: 24 },
  { partnerId: 91006, displayName: '李*', rank: 6, estimated_income: 980, withdrawn_amount: 760, lead_count: 26, valid_lead_count: 19, isMe: true },
  { partnerId: 91007, displayName: '陈*', rank: 7, estimated_income: 860, withdrawn_amount: 630, lead_count: 22, valid_lead_count: 16 },
  { partnerId: 91008, displayName: '刘*', rank: 8, estimated_income: 720, withdrawn_amount: 510, lead_count: 19, valid_lead_count: 14 },
  { partnerId: 91009, displayName: '杨*', rank: 9, estimated_income: 610, withdrawn_amount: 420, lead_count: 17, valid_lead_count: 12 },
  { partnerId: 91010, displayName: '黄*', rank: 10, estimated_income: 480, withdrawn_amount: 350, lead_count: 14, valid_lead_count: 10 },
  { partnerId: 91011, displayName: '吴*', rank: 11, estimated_income: 360, withdrawn_amount: 240, lead_count: 11, valid_lead_count: 8 }
]

function leaderboardData(config: MockRequestConfig) {
  const { pageNo, pageSize } = pageParams(config)
  const period = String(config.params?.period || 'month')
  const type = String(config.params?.type || 'estimated_income') as 'estimated_income' | 'withdrawn_amount' | 'lead_count' | 'valid_lead_count'
  const option = mockLeaderboardConfig.typeOptions.find(item => item.key === type) || mockLeaderboardConfig.typeOptions[0]
  const periodFactor: Record<string, number> = { today: 0.08, week: 0.32, month: 1, total: 4.6 }
  const periodLabel: Record<string, string> = { today: '今日', week: '本周', month: '本月', total: '总榜' }
  const factor = periodFactor[period] || 1
  const rows = mockLeaderboardRows.map((item, index) => {
    const value = option.valueUnit === 'count' ? Math.round(item[type] * factor) : Number((item[type] * factor).toFixed(2))
    const previous = index > 0 ? mockLeaderboardRows[index - 1] : undefined
    const previousValue = previous
      ? (option.valueUnit === 'count' ? Math.round(previous[type] * factor) : Number((previous[type] * factor).toFixed(2)))
      : undefined
    return {
      partnerId: item.partnerId,
      displayName: item.displayName,
      rank: item.rank,
      value,
      isMe: item.isMe === true,
      gapToPrevious: previousValue == null ? null : Math.max(0, previousValue - value)
    }
  })
  const myRank = rows.find(item => item.isMe) || null
  const previousGapValue = myRank?.gapToPrevious || 0
  const formatGap = (value: number) => option.valueUnit === 'money' ? `¥${value.toFixed(2)}` : `${Math.round(value)}条`
  const start = (pageNo - 1) * pageSize
  return {
    period, periodLabel: periodLabel[period] || period, type, typeLabel: option.label,
    valueLabel: option.valueLabel, valueUnit: option.valueUnit, ruleText: option.ruleText,
    total: rows.length, pageNo, pageSize, list: rows.slice(start, start + pageSize), top3: rows.filter(item => item.rank <= 3),
    myRank,
    previousGap: myRank ? { targetRank: Math.max(1, myRank.rank - 1), value: previousGapValue, displayValue: formatGap(previousGapValue), targetReached: previousGapValue === 0 } : null,
    top10Gap: myRank ? { targetRank: 10, value: 0, displayValue: formatGap(0), targetReached: myRank.rank <= 10 } : null,
    nearbyRanks: myRank ? rows.filter(item => Math.abs(item.rank - myRank.rank) <= 1) : []
  }
}

function mockData(config: MockRequestConfig, endpoint: string): unknown {
  const params = config.params || {}
  const method = config.method?.toLowerCase() || 'get'
  const body = requestBody(config)

  if (endpoint === '/zsjos/partner/leaderboard/config' && method === 'get') return mockLeaderboardConfig
  if (endpoint === '/zsjos/partner/leaderboard' && method === 'get') return leaderboardData(config)
  if (endpoint === '/zsjos/partner/home-statistics' && method === 'get') return homeStatisticsData(config)
  if (endpoint === '/zsjos/partner/home-statistics/details' && method === 'get') return homeStatisticsDetailData(config)
  if (endpoint === '/zsjos/lead/inbox/submitted/summary' && method === 'get') {
    return { followUpPendingCount: 3, unreachableCount: 1, invalidCount: 2 }
  }

  if (endpoint === '/zsjos/feedback/options' && method === 'get') return mockFeedbackOptions
  if (endpoint === '/zsjos/feedback/my-page' && method === 'get') {
    const { pageNo, pageSize } = pageParams(config)
    const status = String(params.status || '')
    const keyword = String(params.keyword || '').trim().toLowerCase()
    const filtered = mockFeedbacks.filter(item =>
      (!status || item.status === status) &&
      (!keyword || String(item.feedbackNo).toLowerCase().includes(keyword) || String(item.title).toLowerCase().includes(keyword))
    )
    const start = (pageNo - 1) * pageSize
    return { list: filtered.slice(start, start + pageSize), total: filtered.length }
  }
  const feedbackMatch = endpoint.match(/^\/zsjos\/feedback\/my\/(\d+)$/)
  if (feedbackMatch && method === 'get') return mockFeedbacks.find(item => item.id === Number(feedbackMatch[1])) || mockFeedbacks[0]
  if (endpoint === '/zsjos/feedback/create' && method === 'post') {
    const category = mockFeedbackOptions.categories.find(item => item.value === body.category)
    const severity = mockFeedbackOptions.severities.find(item => item.value === body.severity)
    const now = new Date().toISOString()
    const item = {
      id: Date.now(), feedbackNo: `FB-MOCK-${String(mockFeedbacks.length + 1).padStart(3, '0')}`,
      category: String(body.category || ''), categoryText: category?.label || String(body.category || ''),
      severity: String(body.severity || ''), severityText: severity?.label || String(body.severity || ''),
      title: String(body.title || ''), description: String(body.description || ''), reproduceSteps: String(body.reproduceSteps || ''),
      status: 'submitted', statusText: '待处理',
      attachments: feedbackAttachmentsOf(body.attachmentFileIds),
      events: [{ eventId: Date.now(), eventType: 'submitted', eventTypeText: '已提交', content: '演示反馈已提交', createdAt: now }],
      createdAt: now, updatedAt: now
    }
    mockFeedbacks.unshift(item)
    return item
  }
  const supplementMatch = endpoint.match(/^\/zsjos\/feedback\/my\/(\d+)\/supplement$/)
  if (supplementMatch && method === 'post') {
    const item = mockFeedbacks.find(candidate => candidate.id === Number(supplementMatch[1])) || mockFeedbacks[0]
    const events = item.events as Array<Record<string, unknown>>
    const attachments = item.attachments as Array<Record<string, unknown>>
    const now = new Date().toISOString()
    events.push({ eventId: Date.now(), eventType: 'supplemented', eventTypeText: '已补充', content: String(body.content || ''), createdAt: now })
    attachments.push(...feedbackAttachmentsOf(body.attachmentFileIds))
    item.status = 'processing'; item.statusText = '处理中'; item.updatedAt = now
    return item
  }
  if (endpoint === '/zsjos/feedback/attachment/upload' && method === 'post') {
    const file = config.data instanceof FormData ? config.data.get('file') : undefined
    const infraFileId = Date.now()
    const attachment = { infraFileId, fileUrl: file instanceof File ? URL.createObjectURL(file) : '', originalName: file instanceof File ? file.name : '演示图片.jpg', contentType: file instanceof File ? file.type : 'image/jpeg', fileSize: file instanceof File ? file.size : 0 }
    mockFeedbackAttachments.set(infraFileId, attachment)
    return attachment
  }

  if (endpoint === '/zsjos/messages/groups' && method === 'get') return [
    { key: 'lead', label: '客资', bizTypes: ['lead'] },
    { key: 'cashback', label: '收益', bizTypes: ['cashback'] },
    { key: 'withdrawal', label: '提现', bizTypes: ['withdrawal'] },
    { key: 'rights', label: '申诉投诉', bizTypes: ['appeal', 'complaint'] },
    { key: 'feedback', label: '系统反馈', bizTypes: ['feedback'] }
  ]
  const bankEditMatch = endpoint.match(/^\/zsjos\/withdrawal\/my-cards\/(\d+)$/)
  if (bankEditMatch && method === 'put') {
    const card = mockCards.find(item => item.id === Number(bankEditMatch[1]))
    if (card) {
      card.accountName = String(body.accountName || card.accountName)
      card.bankName = String(body.bankName || card.bankName)
      card.branchName = String(body.branchName || card.branchName)
      if (body.cardNumber) card.maskedCardNumber = `**** **** **** ${String(body.cardNumber).slice(-4)}`
      if (typeof sessionStorage !== 'undefined') {
        const overrides = readBankCardOverrides()
        overrides[String(card.id)] = { accountName: card.accountName, bankName: card.bankName, branchName: card.branchName, maskedCardNumber: card.maskedCardNumber }
        sessionStorage.setItem(BANK_CARD_OVERRIDES_KEY, JSON.stringify(overrides))
      }
    }
    return undefined
  }
  if (endpoint === '/system/dict-data/type') {
    const type = String(params.type || '')
    if (type === 'zsjos_lead_source_channel') return [
      { label: '线上广告', value: 'online_ad', colorType: 'primary' },
      { label: '老学员转介绍', value: 'referral', colorType: 'success' },
      { label: '其他', value: 'other', colorType: 'default' }
    ]
    if (type === 'zsjos_lead_category') return [
      { label: '成人教育', value: 'adult_education', colorType: 'primary' },
      { label: '职业培训', value: 'vocational_training', colorType: 'success' }
    ]
    return []
  }
  if (endpoint === '/system/area/tree') return [{
    id: 340000, name: '安徽省', selectionCode: '340000', leafSelectable: false,
    children: [{ id: 340100, name: '合肥市', selectionCode: '340100', leafSelectable: true, children: [] }]
  }]
  if (endpoint === '/zsjos/profile/get') return { nickname: '兼职伙伴', mobile: '13800000000', email: '', avatar: '', sex: 0 }
  if (endpoint === '/zsjos/partner/me') return {
    id: 1001, partnerNo: 'P-MOCK-001', name: 'Mock 兼职伙伴', mobile: '13800000000', status: 'enabled', enabledAt: '2026-08-01T09:00:00'
  }
  if (endpoint === '/zsjos/lead/product/catalog') return {
    categoryTree: [{ id: 90001, name: '职业培训', children: [] }],
    spus: [{ categoryId: 90001, categoryName: '职业培训', categoryPath: [{ id: 90001, name: '职业培训' }], level1CategoryId: 90001, level1CategoryName: '职业培训', spuRef: 'MOCK-SPU-001', spuName: '职业技能课程', attrs: [] }],
    skus: [{ spuRef: 'MOCK-SPU-001', skuRef: 'MOCK-SKU-001', skuName: '标准班', attrValues: {}, price: 0 }]
  }
  if (endpoint === '/zsjos/lead/partner-filter-options') return {
    appealStatuses: [{ value: 'pending', label: '申诉处理中' }, { value: 'approved', label: '申诉通过' }, { value: 'rejected', label: '申诉驳回' }],
    orderReviewStatuses: [{ value: 'pending', label: '订单待审核' }, { value: 'approved', label: '订单已通过' }, { value: 'rejected', label: '订单已驳回' }]
  }
  if (endpoint === '/zsjos/lead/inbox/submitted/page') {
    const { pageNo, pageSize } = pageParams(config)
    const view = String(params.view || '')
    const item = lead()
    if (view === 'invalid') item.status = 'invalid'
    if (view === 'unreachable') item.status = 'valid'
    const keyword = String(params.keyword || '').trim().toLowerCase()
    const matched = (!keyword || [item.leadNo, item.submittedName, item.submittedMobile].some(value => String(value).toLowerCase().includes(keyword)))
      && (!params.status || item.status === params.status)
      && (!params.simpleStatus || params.simpleStatus === item.simpleStatus)
      && (!view || ['follow_up_pending', 'unreachable', 'invalid'].includes(view))
      && (!params.assignmentStatus || item.assignmentStatus === params.assignmentStatus)
      && (!params.sourceChannel || item.sourceChannel === params.sourceChannel)
      && (!params.leadCategory || item.leadCategory === params.leadCategory)
      && (!params.mainProductRef || item.mainProductRef === params.mainProductRef)
      && (!params.appealStatus || item.appealStatus === params.appealStatus)
      && (!params.orderReviewStatus || item.orderReviewStatus === params.orderReviewStatus)
    return { list: pageNo === 1 && matched ? [item].slice(0, pageSize) : [], total: matched ? 1 : 0 }
  }
  if (endpoint === '/zsjos/lead/get') return lead(Number(params.id) || 10001)
  const partnerActivityMatch = endpoint.match(/^\/zsjos\/lead\/(\d+)\/partner-activity$/)
  if (partnerActivityMatch && method === 'get') return leadActivity(Number(partnerActivityMatch[1]))
  if (endpoint === '/zsjos/cashback/my-summary') return {
    totalAmount: 180, pendingAmount: 80, availableAmount: 100, withdrawingAmount: 0, withdrawnAmount: 0,
    counts: { pending_settlement: 1, available: 1, withdrawn: 0 }
  }
  if (endpoint === '/zsjos/cashback/my-page') {
    const { pageNo, pageSize } = pageParams(config)
    const item = { id: 20001, cashbackNo: 'CB-MOCK-001', type: 'valid', status: 'available', leadId: 10001, leadNo: 'L202608240001', productRefSnapshot: 'MOCK-SPU-001', productNameSnapshot: '职业技能课程', baseAmount: 100, rateSnapshot: 1, amount: 100, observationDaysSnapshot: 0, generatedAt: '2026-08-24T10:00:00' }
    return { list: pageNo === 1 ? [item].slice(0, pageSize) : [], total: 1 }
  }
  if (endpoint === '/zsjos/withdrawal/my-summary') return { availableAmount: 100, minimumAmount: 10, selectableCount: 1, canApply: true }
  if (endpoint === '/zsjos/withdrawal/my-cards') return mockCards
  if (endpoint === '/zsjos/withdrawal/my-page') {
    const { pageNo, pageSize } = pageParams(config)
    const item = { id: 40001, withdrawalNo: 'WD-MOCK-001', status: 'rejected', applicationAmount: 100, approvedAmount: 0, submittedAt: '2026-08-24T11:00:00', reviewedAt: '2026-08-24T15:00:00', rejectionReason: '演示驳回原因', bankNameSnapshot: '中国银行', maskedCardNumber: '**** **** **** 1234' }
    return { list: pageNo === 1 ? [item].slice(0, pageSize) : [], total: 1 }
  }
  const withdrawalMatch = endpoint.match(/^\/zsjos\/withdrawal\/my\/(\d+)$/)
  if (withdrawalMatch) return { id: Number(withdrawalMatch[1]), withdrawalNo: 'WD-MOCK-001', status: 'rejected', applicationAmount: 100, approvedAmount: 0, submittedAt: '2026-08-24T11:00:00', reviewedAt: '2026-08-24T15:00:00', rejectionReason: '演示驳回原因', bankNameSnapshot: '中国银行', maskedCardNumber: '**** **** **** 1234' }
  if (endpoint === '/zsjos/lead-complaint/my-page') {
    const { pageNo, pageSize } = pageParams(config)
    const item = { id: 50001, leadId: 10001, leadNo: 'L202608240001', reason: 'Mock 投诉记录', status: 'pending', createTime: '2026-08-24T12:00:00' }
    return { list: pageNo === 1 ? [item].slice(0, pageSize) : [], total: 1 }
  }
  const appealMatch = endpoint.match(/^\/zsjos\/lead\/appeal\/lead\/(\d+)\/list$/)
  if (appealMatch) return [{ id: 60001, leadId: Number(appealMatch[1]), leadNo: 'L202608240001', roundNo: 1, reviewStage: 'sales_manager_reviewing', reason: 'Mock 申诉记录', status: 'sales_manager_reviewing', submittedAt: '2026-08-24T12:00:00', evidence: [], canSubmitNextRound: false }]
  if (endpoint === '/zsjos/messages/page') {
    const { pageNo, pageSize } = pageParams(config)
    const item = { id: 70001, templateTitle: '演示消息', templateSummary: '这是一条开发环境演示消息', templateContent: '演示消息内容', templateType: 1, actionType: 'business_detail', bizType: 'lead', bizId: 10001, readStatus: false, createTime: '2026-08-24T13:00:00' }
    const group = String(params.group || '')
    const unreadOnly = params.readStatus === false
    const matched = (!unreadOnly || item.readStatus === false) && (!group || group === 'lead' || group === 'all')
    return { list: pageNo === 1 && matched ? [item].slice(0, pageSize) : [], total: matched ? 1 : 0 }
  }
  const messageMatch = endpoint.match(/^\/zsjos\/messages\/(\d+)$/)
  if (messageMatch) {
    const messageId = Number(messageMatch[1])
    return messageId === 70002
      ? { id: messageId, templateTitle: '系统反馈回复', templateSummary: '你的反馈有新的处理回复', templateContent: '演示反馈回复内容', templateType: 1, actionType: 'business_detail', bizType: 'feedback', bizId: 81001, readStatus: true, createTime: '2026-08-24T12:00:00' }
      : { id: messageId, templateTitle: '客资状态更新', templateSummary: '演示客资已进入跟进阶段', templateContent: '演示客资状态更新内容', templateType: 1, actionType: 'business_detail', bizType: 'lead', bizId: 10001, readStatus: false, createTime: '2026-08-24T13:00:00' }
  }
  if (endpoint === '/zsjos/messages/unread-count') return 1
  return undefined
}

export function resolveFeatureMock(config: MockRequestConfig, status?: number, message?: unknown): FeatureMockResult | undefined {
  const endpoint = endpointOf(config)
  const normalizedMessage = typeof message === 'string' ? message.toLowerCase() : ''
  if (!import.meta.env.DEV || !isMissingImplementation(status, message)) return undefined
  const method = config.method?.toLowerCase() || 'get'
  const supportsRead = method === 'get' && (
    endpoint === '/zsjos/partner/leaderboard/config' ||
    endpoint === '/zsjos/partner/leaderboard' ||
    endpoint === '/zsjos/partner/home-statistics' ||
    endpoint === '/zsjos/partner/home-statistics/details' ||
    endpoint === '/zsjos/feedback/options' ||
    endpoint === '/zsjos/feedback/my-page' ||
    /^\/zsjos\/feedback\/my\/\d+$/.test(endpoint) ||
    endpoint === '/zsjos/messages/groups' ||
    endpoint === '/zsjos/messages/page' ||
    endpoint === '/zsjos/lead/inbox/submitted/page' ||
    endpoint === '/zsjos/lead/inbox/submitted/summary' ||
    endpoint === '/zsjos/lead/partner-filter-options' ||
    /^\/zsjos\/lead\/\d+\/partner-activity$/.test(endpoint)
  )
  const supportsMutation = method !== 'get' && (
    (method === 'post' && endpoint === '/zsjos/feedback/create') ||
    (method === 'post' && /^\/zsjos\/feedback\/my\/\d+\/supplement$/.test(endpoint)) ||
    (method === 'post' && endpoint === '/zsjos/feedback/attachment/upload') ||
    (method === 'put' && /^\/zsjos\/withdrawal\/my-cards\/\d+$/.test(endpoint))
  )
  if (!supportsRead && !supportsMutation) return undefined
  const data = mockData(config, endpoint)
  const supportsVoidMutation = method !== 'get' && (
    /^\/zsjos\/withdrawal\/my-cards\/\d+$/.test(endpoint)
  )
  if (data === undefined && !supportsVoidMutation) return undefined
  usedMockEndpoints.add(endpoint)
  mockUsageVersion.value += 1
  console.warn(`[H5 mock] 使用开发环境 Mock：${method.toUpperCase()} ${endpoint}`)
  return { data, endpoint }
}

export function resolveReadMock(config: MockRequestConfig, status?: number, message?: unknown): FeatureMockResult | undefined {
  if (config.method?.toLowerCase() !== 'get') return undefined
  return resolveFeatureMock(config, status, message)
}

export function resolveHomeStatisticsDetailMock(config: MockRequestConfig): FeatureMockResult | undefined {
  const endpoint = endpointOf(config)
  if (!import.meta.env.DEV || endpoint !== '/zsjos/partner/home-statistics/details' || config.method?.toLowerCase() !== 'get') {
    return undefined
  }
  const data = mockData(config, endpoint)
  if (data === undefined) return undefined
  usedMockEndpoints.add(endpoint)
  mockUsageVersion.value += 1
  console.warn(`[H5 mock] 使用开发环境 Mock：GET ${endpoint}`)
  return { data, endpoint }
}
