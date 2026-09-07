import type { NotifyMessage } from './api'

export type NotifyMessageCategory = 'all' | 'lead' | 'withdrawal' | 'reward' | 'appeal' | 'system'

export type NotifyMessageCategorySource = {
  sceneCode?: string | null
  sourceEventKey?: string | null
  bizType?: string | null
}

const LEAD_BIZ_TYPES = new Set([
  'lead',
  'sales_order',
  'student',
  'student_service',
  'media-account',
  'content',
  'positioning-card',
  'production-ticket'
])

const WITHDRAWAL_BIZ_TYPES = new Set(['withdrawal'])
const REWARD_BIZ_TYPES = new Set(['reward', 'cashback', 'commission'])

const includesAny = (value: string, keywords: string[]) => keywords.some(keyword => value.includes(keyword))

const normalize = (value?: string | null) => value?.trim().toLowerCase() || ''

export const NOTIFY_MESSAGE_CATEGORY_ORDER: NotifyMessageCategory[] = [
  'all',
  'lead',
  'withdrawal',
  'reward',
  'appeal',
  'system'
]

export const notifyMessageCategoryLabel: Record<NotifyMessageCategory, string> = {
  all: '全部',
  lead: '客资',
  withdrawal: '提现',
  reward: '收益',
  appeal: '申诉',
  system: '系统'
}

export function notifyMessageCategoryOf(message: NotifyMessageCategorySource): Exclude<NotifyMessageCategory, 'all'> {
  const sceneCode = normalize(message.sceneCode)
  const sourceEventKey = normalize(message.sourceEventKey)
  const bizType = normalize(message.bizType)

  if (
    includesAny(sceneCode, ['appeal', 'complaint'])
    || includesAny(sourceEventKey, ['appeal', 'complaint'])
    || bizType === 'appeal'
    || bizType === 'complaint'
  ) return 'appeal'

  if (
    includesAny(sceneCode, ['withdrawal'])
    || includesAny(sourceEventKey, ['withdrawal'])
    || WITHDRAWAL_BIZ_TYPES.has(bizType)
  ) return 'withdrawal'

  if (
    includesAny(sceneCode, ['reward', 'cashback', 'commission'])
    || includesAny(sourceEventKey, ['reward', 'cashback', 'commission'])
    || REWARD_BIZ_TYPES.has(bizType)
  ) return 'reward'

  if (
    includesAny(sceneCode, ['lead', 'registration', 'sales_order', 'payment'])
    || includesAny(sourceEventKey, ['lead', 'registration', 'sales_order', 'payment'])
    || LEAD_BIZ_TYPES.has(bizType)
  ) return 'lead'

  return 'system'
}

export function notifyMessageMatchesCategory(message: NotifyMessageCategorySource, category: NotifyMessageCategory) {
  return category === 'all' || notifyMessageCategoryOf(message) === category
}
