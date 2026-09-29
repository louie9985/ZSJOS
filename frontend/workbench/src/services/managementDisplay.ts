import type { BusinessAudit, NotifyRule, NotifyScene, UserRelationLog } from './managementApi'

// Presentation of fixed audit/transport/command protocols; these are not configurable business options.
const auditSources: Record<BusinessAudit['sourceType'], string> = {
  ADMIN: '管理端', PARTNER: '合作方', PUBLIC_CALLBACK: '公开回调', SYSTEM: '系统任务', EXPLICIT: '业务明细',
}
const auditCategories: Record<string, string> = { business: '业务操作', sensitive_read: '敏感读取', system: '系统任务' }
const auditResults: Record<BusinessAudit['resultStatus'], string> = { STARTED: '执行中', SUCCESS: '成功', FAILURE: '失败' }
const relationActions: Record<UserRelationLog['actionType'], string> = { append: '追加', replace: '替换', remove: '移除' }
const channels: Record<NotifyRule['channelCode'], string> = { in_app: '站内信（含实时提醒）', websocket: '实时提醒', wecom: '企业微信', sms: '短信' }

function label(labels: Record<string, string>, value: string | undefined, unknown: string) {
  return !value ? '—' : Object.hasOwn(labels, value) ? labels[value] : unknown
}
export const auditSourceLabel = (value?: string) => label(auditSources, value, '未知来源')
export const auditCategoryLabel = (value?: string) => label(auditCategories, value, '未知类别')
export const auditResultLabel = (value?: string) => label(auditResults, value, '未知状态')
export const relationActionLabel = (value?: string) => label(relationActions, value, '未知操作')
export const notifyChannelLabel = (value?: string) => label(channels, value, '未知渠道')

export function notifyRecipientLabels(rule: Pick<NotifyRule, 'sceneCode' | 'recipientRoles'>, scenes: NotifyScene[], loading: boolean, failed: boolean) {
  if (!rule.recipientRoles?.length) return '—'
  if (loading) return '角色名称加载中'
  if (failed) return '角色名称加载失败'
  const roles = scenes.find(scene => scene.code === rule.sceneCode)?.recipientRoles
  return rule.recipientRoles.map(code => roles?.find(role => role.code === code)?.name || '角色名称未配置').join('、')
}
